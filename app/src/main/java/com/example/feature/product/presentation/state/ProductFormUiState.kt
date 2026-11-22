package com.example.feature.product.presentation.state

data class ProductFormUiState(
    val name: String = "",
    val description: String = "",
    val price: String = "",
    val quantity: String = "1",
    val isEditing: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false
)
