package com.example.feature.product.data.mapper

import com.example.feature.product.data.local.ProductEntity
import com.example.feature.product.domain.model.Product

fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    description = description,
    priceCop = priceCop,
    quantity = quantity,
    updatedAt = updatedAt,
    isPendingSync = pending
)

fun Product.toEntity(
    userId: String,
    pending: Boolean = true,
    isDeleted: Boolean = false,
    syncedToCloud: Boolean = false
): ProductEntity = ProductEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    priceCop = priceCop,
    quantity = quantity,
    updatedAt = updatedAt,
    pending = pending,
    isDeleted = isDeleted,
    syncedToCloud = syncedToCloud
)
