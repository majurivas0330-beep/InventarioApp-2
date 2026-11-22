package com.example.feature.product.domain.repository

import com.example.feature.product.domain.model.Product
import kotlinx.coroutines.flow.Flow

data class SyncReport(
    val uploaded: Int,
    val downloaded: Int
)

interface ProductRepository {
    fun observeProducts(userId: String): Flow<List<Product>>
    fun observeRealtimeSync(userId: String): Flow<Result<Int>>
    suspend fun findById(userId: String, id: String): Product?
    suspend fun save(userId: String, product: Product): Result<Unit>
    suspend fun delete(userId: String, id: String): Result<Unit>
    suspend fun syncNow(userId: String): Result<SyncReport>
}
