package com.example.feature.product.presentation.state

import com.example.feature.product.domain.model.Product

enum class StockFilter(val label: String) {
    ALL("Todos"),
    AVAILABLE("Con stock"),
    LOW_STOCK("Stock bajo")
}

data class ProductListUiState(
    val products: List<Product> = emptyList(),
    val searchQuery: String = "",
    val stockFilter: StockFilter = StockFilter.ALL,
    val isLoading: Boolean = true,
    val isSyncing: Boolean = false,
    val isRealtimeConnected: Boolean = false,
    val message: String? = null
) {
    val filteredProducts: List<Product>
        get() {
            val query = searchQuery.trim().lowercase()
            return products.filter { product ->
                val matchesQuery = query.isEmpty() ||
                    product.name.lowercase().contains(query) ||
                    product.description.lowercase().contains(query)
                val matchesFilter = when (stockFilter) {
                    StockFilter.ALL -> true
                    StockFilter.AVAILABLE -> product.quantity > 5
                    StockFilter.LOW_STOCK -> product.quantity <= 5
                }
                matchesQuery && matchesFilter
            }
        }

    val totalUnits: Int
        get() = products.sumOf { it.quantity }

    val totalValueCop: Long
        get() = products.sumOf { it.priceCop * it.quantity }

    val pendingCount: Int
        get() = products.count { it.isPendingSync }

    val lowStockCount: Int
        get() = products.count { it.quantity <= 5 }
}
