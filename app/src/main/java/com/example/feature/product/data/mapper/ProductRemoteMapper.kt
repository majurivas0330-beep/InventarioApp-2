package com.example.feature.product.data.mapper

import com.example.feature.product.data.local.ProductEntity
import com.example.feature.product.data.remote.ProductDto

fun ProductEntity.toDto(): ProductDto = ProductDto(
    id = id,
    name = name,
    description = description,
    priceCop = priceCop,
    quantity = quantity,
    deleted = isDeleted
)

fun ProductDto.toEntity(userId: String): ProductEntity = ProductEntity(
    id = id,
    userId = userId,
    name = name,
    description = description,
    priceCop = priceCop,
    quantity = quantity,
    updatedAt = updatedAt?.toDate()?.time ?: createdAt?.toDate()?.time ?: System.currentTimeMillis(),
    pending = false,
    isDeleted = deleted,
    syncedToCloud = true
)
