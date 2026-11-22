package com.example

import com.example.feature.product.domain.model.Product
import com.example.feature.product.domain.repository.ProductRepository
import com.example.feature.product.domain.repository.SyncReport
import com.example.feature.product.domain.usecase.SaveProductUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeProductRepository : ProductRepository {
    private val items = linkedMapOf<String, Product>()
    private val flow = MutableStateFlow<List<Product>>(emptyList())

    override fun observeProducts(userId: String): Flow<List<Product>> = flow

    override fun observeRealtimeSync(userId: String): Flow<Result<Int>> =
        MutableStateFlow(Result.success(items.size))

    override suspend fun findById(userId: String, id: String): Product? = items[id]

    override suspend fun save(userId: String, product: Product): Result<Unit> {
        items[product.id] = product
        flow.value = items.values.toList()
        return Result.success(Unit)
    }

    override suspend fun delete(userId: String, id: String): Result<Unit> {
        items.remove(id)
        flow.value = items.values.toList()
        return Result.success(Unit)
    }

    override suspend fun syncNow(userId: String): Result<SyncReport> =
        Result.success(SyncReport(uploaded = 0, downloaded = items.size))
}

class SaveProductUseCaseTest {

    @Test
    fun `valid product is trimmed and saved`() = runTest {
        val repo = FakeProductRepository()
        val useCase = SaveProductUseCase(repo)

        val result = useCase(
            userId = "user-1",
            product = Product(
                id = "prod-1",
                name = "  Teclado mecánico  ",
                description = "  Switches táctiles  ",
                priceCop = 220000L,
                quantity = 12
            )
        )

        assertTrue(result.isSuccess)
        val saved = repo.findById("user-1", "prod-1")
        assertEquals("Teclado mecánico", saved?.name)
        assertEquals("Switches táctiles", saved?.description)
    }

    @Test
    fun `blank name fails validation`() = runTest {
        val useCase = SaveProductUseCase(FakeProductRepository())
        val result = useCase(
            userId = "user-1",
            product = Product(
                id = "prod-1",
                name = "   ",
                description = "Desc",
                priceCop = 10000L,
                quantity = 1
            )
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `non-positive price fails validation`() = runTest {
        val useCase = SaveProductUseCase(FakeProductRepository())
        val result = useCase(
            userId = "user-1",
            product = Product(
                id = "prod-1",
                name = "Mouse",
                description = "Desc",
                priceCop = 0L,
                quantity = 5
            )
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `negative quantity fails validation`() = runTest {
        val useCase = SaveProductUseCase(FakeProductRepository())
        val result = useCase(
            userId = "user-1",
            product = Product(
                id = "prod-1",
                name = "Monitor",
                description = "Desc",
                priceCop = 990000L,
                quantity = -1
            )
        )
        assertTrue(result.isFailure)
    }
}
