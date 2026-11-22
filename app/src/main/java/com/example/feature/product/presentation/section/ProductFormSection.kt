package com.example.feature.product.presentation.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.feature.product.presentation.composables.ProductFormField
import com.example.feature.product.presentation.state.ProductFormUiState

@Composable
fun ProductFormSection(
    ui: ProductFormUiState,
    onName: (String) -> Unit,
    onDescription: (String) -> Unit,
    onPrice: (String) -> Unit,
    onQuantity: (String) -> Unit,
    onAdjustQuantity: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProductFormField(
                label = stringResource(R.string.field_name),
                value = ui.name,
                onValueChange = onName,
                leadingIcon = Icons.AutoMirrored.Outlined.Label,
                supportingText = "${ui.name.length}/80 caracteres",
                testTagName = "product_name_input"
            )

            ProductFormField(
                label = stringResource(R.string.field_description),
                value = ui.description,
                onValueChange = onDescription,
                leadingIcon = Icons.Outlined.Description,
                supportingText = "${ui.description.length}/500 caracteres",
                singleLine = false,
                maxLines = 4,
                testTagName = "product_description_input"
            )

            ProductFormField(
                label = stringResource(R.string.field_price_cop),
                value = ui.price,
                onValueChange = onPrice,
                keyboardType = KeyboardType.Number,
                leadingIcon = Icons.Outlined.AttachMoney,
                supportingText = "Valor unitario en pesos colombianos enteros",
                testTagName = "product_price_input"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProductFormField(
                    label = stringResource(R.string.field_quantity),
                    value = ui.quantity,
                    onValueChange = onQuantity,
                    keyboardType = KeyboardType.Number,
                    leadingIcon = Icons.Outlined.Inventory,
                    modifier = Modifier.weight(1f),
                    testTagName = "product_quantity_input"
                )

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onAdjustQuantity(-1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("decrement_quantity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Disminuir cantidad"
                        )
                    }
                    FilledTonalIconButton(
                        onClick = { onAdjustQuantity(1) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("increment_quantity_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Aumentar cantidad"
                        )
                    }
                }
            }

            if (ui.error != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ui.error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onSave,
                enabled = !ui.saving,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_product_button")
            ) {
                if (ui.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
