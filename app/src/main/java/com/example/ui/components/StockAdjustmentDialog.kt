package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.MovementType
import com.example.data.model.Product
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ProfitGreen

@Composable
fun StockAdjustmentDialog(
    product: Product,
    onDismiss: () -> Unit,
    onConfirmAdjustment: (quantity: Int, type: MovementType, reason: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(MovementType.ENTRADA) }
    var quantityStr by remember { mutableStateOf("1") }
    var reason by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val quantity = quantityStr.toIntOrNull() ?: 0
    val previousStock = product.stock
    val newStock = when (selectedType) {
        MovementType.ENTRADA, MovementType.AJUSTE_POSITIVO -> previousStock + quantity
        MovementType.VENTA, MovementType.AJUSTE_NEGATIVO, MovementType.TRASPASO -> previousStock - quantity
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Movimiento Kardex",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Current stock vs New stock calculation preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Stock Actual", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$previousStock uds.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Icon(
                            imageVector = if (selectedType.isPositive) Icons.Default.Add else Icons.Default.Remove,
                            contentDescription = null,
                            tint = if (selectedType.isPositive) ProfitGreen else DangerRed,
                            modifier = Modifier.size(20.dp)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nuevo Stock", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$newStock uds.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (newStock < 0) DangerRed else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Type of movement selector
                Text("Tipo de Movimiento:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == MovementType.ENTRADA,
                        onClick = {
                            selectedType = MovementType.ENTRADA
                            if (reason.isBlank()) reason = "Compra a proveedor"
                        },
                        label = { Text("Entrada (+)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == MovementType.AJUSTE_POSITIVO,
                        onClick = {
                            selectedType = MovementType.AJUSTE_POSITIVO
                            if (reason.isBlank()) reason = "Inventario físico"
                        },
                        label = { Text("Ajuste (+)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == MovementType.AJUSTE_NEGATIVO,
                        onClick = {
                            selectedType = MovementType.AJUSTE_NEGATIVO
                            if (reason.isBlank()) reason = "Merma / Pérdida"
                        },
                        label = { Text("Merma (-)", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quantity selector with +/-
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            val q = (quantityStr.toIntOrNull() ?: 1)
                            if (q > 1) quantityStr = (q - 1).toString()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos")
                    }

                    OutlinedTextField(
                        value = quantityStr,
                        onValueChange = { quantityStr = it },
                        label = { Text("Cantidad") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("adjustment_qty_input"),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            val q = (quantityStr.toIntOrNull() ?: 0)
                            quantityStr = (q + 1).toString()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Más")
                    }
                }

                // Reason / Note
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo / Observación") },
                    placeholder = { Text("Ej. Factura #9812, Conteo mensual...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjustment_reason_input"),
                    singleLine = true
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (quantity <= 0) {
                        errorMsg = "La cantidad debe ser mayor a 0"
                        return@Button
                    }
                    if (!selectedType.isPositive && previousStock < quantity) {
                        errorMsg = "No se puede descontar más de lo disponible ($previousStock uds.)"
                        return@Button
                    }
                    errorMsg = null
                    onConfirmAdjustment(quantity, selectedType, reason.trim())
                },
                modifier = Modifier.testTag("confirm_adjustment_btn")
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
