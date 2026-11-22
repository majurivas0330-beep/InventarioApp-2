package com.example.feature.product.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query(
        "SELECT * FROM products " +
            "WHERE userId = :userId AND isDeleted = 0 " +
            "ORDER BY updatedAt DESC, name ASC"
    )
    fun observeVisible(userId: String): Flow<List<ProductEntity>>

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId")
    suspend fun countAllForUser(userId: String): Int

    @Query("SELECT * FROM products WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun find(userId: String, id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE userId = :userId AND pending = 1")
    suspend fun getPending(userId: String): List<ProductEntity>

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query(
        "UPDATE products SET pending = 0, syncedToCloud = 1 " +
            "WHERE userId = :userId AND id = :id AND updatedAt = :version"
    )
    suspend fun markSynced(userId: String, id: String, version: Long)
}
