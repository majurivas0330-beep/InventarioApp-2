package com.example.feature.product.domain.usecase

import com.example.feature.product.domain.model.Product
import com.example.feature.product.domain.repository.ProductRepository

class SaveProductUseCase(
    private val repository: ProductRepository
) {
    suspend operator fun invoke(userId: String, product: Product): Result<Unit> {
        val trimmedName = product.name.trim()
        val trimmedDescription = product.description.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("El nombre del producto es obligatorio"))
        }
        if (trimmedName.length > 80) {
            return Result.failure(IllegalArgumentException("El nombre no puede superar 80 caracteres"))
        }
        if (trimmedDescription.length > 500) {
            return Result.failure(IllegalArgumentException("La descripción no puede superar 500 caracteres"))
        }
        if (product.priceCop <= 0L || product.priceCop > 1_000_000_000L) {
            return Result.failure(IllegalArgumentException("El precio en COP debe estar entre 1 y 1.000.000.000"))
        }
        if (product.quantity < 0 || product.quantity > 1_000_000) {
            return Result.failure(IllegalArgumentException("La cantidad debe estar entre 0 y 1.000.000"))
        }
        return repository.save(
            userId = userId,
            product = product.copy(
                name = trimmedName,
                description = trimmedDescription
            )
        )
    }
}
