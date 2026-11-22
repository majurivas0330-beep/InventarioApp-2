package com.example.feature.product.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.product.domain.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ProductListViewModel(
    private val repository: ProductRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _ui = MutableStateFlow(com.example.feature.product.presentation.state.ProductListUiState())
    val ui: StateFlow<com.example.feature.product.presentation.state.ProductListUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeProducts(currentUserId)
                .catch { error ->
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            message = error.localizedMessage ?: "Error al leer inventario local"
                        )
                    }
                }
                .collect { products ->
                    _ui.update {
                        it.copy(
                            products = products,
                            isLoading = false
                        )
                    }
                }
        }

        viewModelScope.launch {
            repository.observeRealtimeSync(currentUserId)
                .collect { result ->
                    result.onSuccess {
                        _ui.update { it.copy(isRealtimeConnected = true) }
                    }.onFailure {
                        _ui.update {
                            it.copy(
                                isRealtimeConnected = false,
                                message = "Modo local activo: esperando conexión con Firebase"
                            )
                        }
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _ui.update { it.copy(searchQuery = query) }
    }

    fun updateStockFilter(filter: com.example.feature.product.presentation.state.StockFilter) {
        _ui.update { it.copy(stockFilter = filter) }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun sync() {
        viewModelScope.launch {
            _ui.update { it.copy(isSyncing = true, message = null) }
            val result = repository.syncNow(currentUserId)
            result.onSuccess { report ->
                _ui.update {
                    it.copy(
                        isSyncing = false,
                        isRealtimeConnected = true,
                        message = "Sincronizado en tiempo real: ${report.uploaded} subidos, ${report.downloaded} en nube"
                    )
                }
            }.onFailure { error ->
                _ui.update {
                    it.copy(
                        isSyncing = false,
                        isRealtimeConnected = false,
                        message = error.localizedMessage ?: "Sin conexión: datos locales disponibles"
                    )
                }
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val result = repository.delete(currentUserId, id)
            result.onSuccess {
                _ui.update { it.copy(message = "Producto eliminado y sincronizado") }
            }.onFailure {
                _ui.update { it.copy(message = "Eliminado localmente; pendiente de sincronizar") }
            }
        }
    }
}
