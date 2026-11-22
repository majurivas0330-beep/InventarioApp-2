package com.example.feature.product.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feature.product.domain.model.Product
import com.example.feature.product.domain.repository.ProductRepository
import com.example.feature.product.domain.usecase.SaveProductUseCase
import com.example.feature.product.presentation.state.ProductFormUiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ProductFormViewModel(
    private val repository: ProductRepository,
    private val saveProduct: SaveProductUseCase,
    private val currentUserId: String
) : ViewModel() {

    private val _ui = MutableStateFlow(ProductFormUiState())
    val ui: StateFlow<ProductFormUiState> = _ui.asStateFlow()

    private var editingId: String? = null
    private var loaded = false

    fun load(id: String?) {
        if (loaded) return
        loaded = true
        editingId = id
        if (id == null) {
            _ui.update { it.copy(isEditing = false) }
            return
        }
        viewModelScope.launch {
            val product = repository.findById(currentUserId, id) ?: return@launch
            _ui.value = ProductFormUiState(
                name = product.name,
                description = product.description,
                price = product.priceCop.toString(),
                quantity = product.quantity.toString(),
                isEditing = true
            )
        }
    }

    fun updateName(value: String) {
        _ui.update { it.copy(name = value, error = null) }
    }

    fun updateDescription(value: String) {
        _ui.update { it.copy(description = value, error = null) }
    }

    fun updatePrice(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _ui.update { it.copy(price = digitsOnly, error = null) }
    }

    fun updateQuantity(value: String) {
        val digitsOnly = value.filter { it.isDigit() }
        _ui.update { it.copy(quantity = digitsOnly, error = null) }
    }

    fun adjustQuantity(delta: Int) {
        val currentQty = _ui.value.quantity.toIntOrNull() ?: 0
        val nextQty = (currentQty + delta).coerceIn(0, 1_000_000)
        _ui.update { it.copy(quantity = nextQty.toString(), error = null) }
    }

    fun save() {
        viewModelScope.launch {
            val current = _ui.value
            val price = current.price.toLongOrNull()
            val quantity = current.quantity.toIntOrNull()
            if (price == null || quantity == null) {
                _ui.update { it.copy(error = "Ingresa valores numéricos válidos para precio y cantidad") }
                return@launch
            }

            _ui.update { it.copy(saving = true, error = null) }

            val product = Product(
                id = editingId ?: UUID.randomUUID().toString(),
                name = current.name.trim(),
                description = current.description.trim(),
                priceCop = price,
                quantity = quantity
            )

            val result = saveProduct(currentUserId, product)
            result.onSuccess {
                _ui.update { it.copy(saving = false, saved = true) }
            }.onFailure { error ->
                val isPermissionDenied =
                    (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                if (error is IllegalArgumentException || isPermissionDenied) {
                    _ui.update {
                        it.copy(
                            saving = false,
                            error = error.message ?: "No se pudo guardar el producto"
                        )
                    }
                } else {
                    // Guardado local exitoso en Room como pendiente si no hay conexión a Firestore
                    _ui.update { it.copy(saving = false, saved = true) }
                }
            }
        }
    }
}
