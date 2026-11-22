package com.example.feature.product.data.repository

import com.example.core.firebase.OperationType
import com.example.core.firebase.handleFirestoreError
import com.example.feature.product.data.local.ProductDao
import com.example.feature.product.data.local.ProductEntity
import com.example.feature.product.data.mapper.toDomain
import com.example.feature.product.data.mapper.toDto
import com.example.feature.product.data.mapper.toEntity
import com.example.feature.product.data.remote.FirestoreProductDataSource
import com.example.feature.product.data.remote.ProductDto
import com.example.feature.product.domain.model.Product
import com.example.feature.product.domain.repository.ProductRepository
import com.example.feature.product.domain.repository.SyncReport
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProductRepositoryImpl(
    private val dao: ProductDao,
    private val remote: FirestoreProductDataSource,
    private val auth: FirebaseAuth
) : ProductRepository {

    private val syncMutex = Mutex()

    private fun requireUserId(expectedUserId: String): String {
        val currentUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Debes iniciar sesión con Google para acceder al inventario.")
        check(currentUid == expectedUserId) {
            "La sesión de usuario cambió; operación cancelada."
        }
        return currentUid
    }

    override fun observeProducts(userId: String): Flow<List<Product>> =
        dao.observeVisible(userId).map { rows -> rows.map { it.toDomain() } }

    override fun observeRealtimeSync(userId: String): Flow<Result<Int>> = flow {
        val uid = try {
            requireUserId(userId)
        } catch (e: Exception) {
            emit(Result.failure(e))
            return@flow
        }

        // Precarga inicial de productos si la cuenta aún no tiene registros en Room ni en Firestore
        try {
            seedIfFreshAccount(uid)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$uid/products")
        }

        emitAll(
            remote.observeProducts(uid)
                .map { cloudItems ->
                    syncMutex.withLock {
                        for (dto in cloudItems) {
                            if (dto.id.isBlank()) continue
                            val cloudEntity = dto.toEntity(uid)
                            val local = dao.find(uid, cloudEntity.id)
                            if (local?.pending == true) continue
                            if (
                                local == null ||
                                cloudEntity.updatedAt >= local.updatedAt ||
                                cloudEntity.isDeleted != local.isDeleted ||
                                cloudEntity.name != local.name ||
                                cloudEntity.description != local.description ||
                                cloudEntity.priceCop != local.priceCop ||
                                cloudEntity.quantity != local.quantity
                            ) {
                                dao.upsert(cloudEntity)
                            }
                        }
                    }
                    Result.success(cloudItems.count { !it.deleted })
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(rootError, OperationType.LIST, "users/$uid/products")
                    }
                    emit(Result.failure(rootError))
                }
        )
    }

    override suspend fun findById(userId: String, id: String): Product? =
        dao.find(userId, id)?.takeUnless { it.isDeleted }?.toDomain()

    override suspend fun save(userId: String, product: Product): Result<Unit> {
        val existing = dao.find(userId, product.id)
        val version = System.currentTimeMillis()
        val localEntity = product.copy(updatedAt = version).toEntity(
            userId = userId,
            pending = true,
            isDeleted = false,
            syncedToCloud = existing?.syncedToCloud ?: false
        )
        dao.upsert(localEntity)

        return try {
            val uid = requireUserId(userId)
            val isNewOnServer = existing == null || !existing.syncedToCloud
            remote.push(uid, localEntity.toDto(), isNewOnServer = isNewOnServer)
            dao.markSynced(uid, localEntity.id, version)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId/products/${product.id}")
            Result.failure(e)
        }
    }

    override suspend fun delete(userId: String, id: String): Result<Unit> {
        val current = dao.find(userId, id) ?: return Result.success(Unit)
        val version = System.currentTimeMillis()
        val tombstone = current.copy(
            isDeleted = true,
            pending = true,
            updatedAt = version
        )
        dao.upsert(tombstone)

        return try {
            val uid = requireUserId(userId)
            remote.push(uid, tombstone.toDto(), isNewOnServer = !current.syncedToCloud)
            dao.markSynced(uid, tombstone.id, version)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId/products/$id")
            Result.failure(e)
        }
    }

    override suspend fun syncNow(userId: String): Result<SyncReport> = syncMutex.withLock {
        val path = "users/$userId/products"
        return try {
            val uid = requireUserId(userId)
            seedIfFreshAccount(uid)

            var uploaded = 0
            for (local in dao.getPending(uid)) {
                remote.push(uid, local.toDto(), isNewOnServer = !local.syncedToCloud)
                dao.markSynced(uid, local.id, local.updatedAt)
                uploaded++
            }

            val cloudItems = remote.fetchFromServer(uid)
            for (dto in cloudItems) {
                if (dto.id.isBlank()) continue
                val cloud = dto.toEntity(uid)
                val local = dao.find(uid, cloud.id)
                if (local?.pending == true) continue
                if (local == null || cloud.updatedAt >= local.updatedAt) {
                    dao.upsert(cloud)
                }
            }
            Result.success(SyncReport(uploaded = uploaded, downloaded = cloudItems.size))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedIfFreshAccount(uid: String) {
        if (dao.countAllForUser(uid) > 0) return
        val remoteExisting = remote.fetchFromServer(uid)
        if (remoteExisting.isNotEmpty()) {
            val entities = remoteExisting
                .filter { it.id.isNotBlank() }
                .map { it.toEntity(uid) }
            if (entities.isNotEmpty()) {
                dao.upsertAll(entities)
            }
            return
        }

        val now = System.currentTimeMillis()
        val seedDtos = defaultInitialProducts()
        // Insert local entities immediately as pending=true, then push batch to Firestore
        val localSeeds = seedDtos.mapIndexed { index, dto ->
            ProductEntity(
                id = dto.id,
                userId = uid,
                name = dto.name,
                description = dto.description,
                priceCop = dto.priceCop,
                quantity = dto.quantity,
                updatedAt = now - (index * 1000L),
                pending = true,
                isDeleted = false,
                syncedToCloud = false
            )
        }
        dao.upsertAll(localSeeds)
        remote.seedInitialProducts(uid, seedDtos)
        localSeeds.forEach { entity ->
            dao.markSynced(uid, entity.id, entity.updatedAt)
        }
    }

    private fun defaultInitialProducts(): List<ProductDto> = listOf(
        ProductDto(
            id = "seed-teclado-mecanico",
            name = "Teclado mecánico",
            description = "Switches táctiles RGB, chasis de aluminio y layout español",
            priceCop = 220000L,
            quantity = 12,
            deleted = false
        ),
        ProductDto(
            id = "seed-mouse-inalambrico",
            name = "Mouse inalámbrico",
            description = "Sensor óptico 16000 DPI, recargable por USB-C",
            priceCop = 79000L,
            quantity = 20,
            deleted = false
        ),
        ProductDto(
            id = "seed-monitor-27",
            name = "Monitor 27 pulgadas",
            description = "Panel IPS QHD 144Hz con soporte ergonómico ajustable",
            priceCop = 990000L,
            quantity = 5,
            deleted = false
        ),
        ProductDto(
            id = "seed-audifonos-pro",
            name = "Audífonos Studio Pro",
            description = "Cancelación activa de ruido híbrida y Bluetooth 5.3",
            priceCop = 345000L,
            quantity = 8,
            deleted = false
        ),
        ProductDto(
            id = "seed-hub-usbc",
            name = "Hub USB-C 7 en 1",
            description = "HDMI 4K 60Hz, Ethernet Gigabit y carga rápida PD 100W",
            priceCop = 135000L,
            quantity = 3,
            deleted = false
        )
    )
}
