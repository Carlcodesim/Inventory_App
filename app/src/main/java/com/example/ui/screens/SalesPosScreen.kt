package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.Store
import com.example.ui.components.StoreSelectorBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.util.Formatters

@Composable
fun SalesPosScreen(
    stores: List<Store>,
    activeStoreId: Long?,
    products: List<Product>,
    recentSales: List<Sale>,
    initialSelectedProductId: Long? = null,
    onSelectStore: (Long?) -> Unit,
    onAddStoreClick: () -> Unit,
    onManageStoresClick: () -> Unit,
    onConfirmSale: (storeId: Long, productId: Long, quantity: Int, customerNote: String, onResult: (Result<Sale>) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    // Current store for POS (must be a specific store)
    val currentStore = stores.find { it.id == activeStoreId } ?: stores.firstOrNull()

    // Store products for selection
    val storeProducts = remember(products, currentStore) {
        if (currentStore != null) {
            products.filter { it.storeId == currentStore.id }
        } else products
    }

    var selectedProduct by remember(storeProducts, initialSelectedProductId) {
        mutableStateOf(
            storeProducts.find { it.id == initialSelectedProductId }
                ?: storeProducts.firstOrNull { it.stock > 0 }
        )
    }

    var productDropdownExpanded by remember { mutableStateOf(false) }
    var quantityStr by remember { mutableStateOf("1") }
    var customerNote by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val quantity = quantityStr.toIntOrNull() ?: 1
    val isStockSufficient = selectedProduct != null && selectedProduct!!.stock >= quantity && quantity > 0

    // Profit calculations in real time
    val unitPrice = selectedProduct?.salePrice ?: 0.0
    val unitCost = selectedProduct?.costPrice ?: 0.0
    val totalAmount = unitPrice * quantity
    val totalCost = unitCost * quantity
    val netProfit = (unitPrice - unitCost) * quantity
    val marginPercent = if (totalAmount > 0) (netProfit / totalAmount) * 100.0 else 0.0

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            kotlinx.coroutines.delay(4000)
            successMessage = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("pos_screen"),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Multi-Store dynamic selector
        item {
            StoreSelectorBar(
                stores = stores,
                activeStoreId = activeStoreId,
                onSelectStore = onSelectStore,
                onAddStoreClick = onAddStoreClick,
                onManageStoresClick = onManageStoresClick
            )
        }

        // POS Terminal Form
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Punto de Venta (Nueva Venta)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentStore != null) {
                            Text(
                                text = currentStore.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Success Feedback Alert
                    AnimatedVisibility(visible = successMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ProfitGreenContainer),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ProfitGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = successMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ProfitGreen
                                )
                            }
                        }
                    }

                    // Product Selector Dropdown
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedProduct?.let { "${it.name} (Stock: ${it.stock})" } ?: "Seleccionar producto...",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Producto a Vender *") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    modifier = Modifier.clickable { productDropdownExpanded = true }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { productDropdownExpanded = true }
                                .testTag("pos_product_select")
                        )

                        DropdownMenu(
                            expanded = productDropdownExpanded,
                            onDismissRequest = { productDropdownExpanded = false }
                        ) {
                            if (storeProducts.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No hay productos en esta sucursal") },
                                    onClick = { productDropdownExpanded = false }
                                )
                            } else {
                                storeProducts.forEach { prod ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(prod.name, fontWeight = FontWeight.Medium)
                                                Text(
                                                    text = "Stock: ${prod.stock} • ${Formatters.currency(prod.salePrice)}",
                                                    color = if (prod.stock <= 0) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedProduct = prod
                                            productDropdownExpanded = false
                                            errorMessage = null
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Quantity Counter with +/-
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (quantity > 1) quantityStr = (quantity - 1).toString()
                            },
                            enabled = quantity > 1,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Menos")
                        }

                        OutlinedTextField(
                            value = quantityStr,
                            onValueChange = {
                                quantityStr = it
                                errorMessage = null
                            },
                            label = { Text("Cantidad") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pos_quantity_input"),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                val max = selectedProduct?.stock ?: 999
                                if (quantity < max) quantityStr = (quantity + 1).toString()
                            },
                            enabled = selectedProduct != null && quantity < selectedProduct!!.stock,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Más")
                        }
                    }

                    // Optional customer note
                    OutlinedTextField(
                        value = customerNote,
                        onValueChange = { customerNote = it },
                        label = { Text("Nota / Cliente (Opcional)") },
                        placeholder = { Text("Ej. Juan Pérez, Ticket #102") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_note_input"),
                        singleLine = true
                    )

                    // LIVE PROFIT & TOTALS CALCULATIONS CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pos_calculation_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Precio Unitario:", style = MaterialTheme.typography.bodyMedium)
                                Text(Formatters.currency(unitPrice), fontWeight = FontWeight.SemiBold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Costo Unitario:", style = MaterialTheme.typography.bodyMedium)
                                Text(Formatters.currency(unitCost), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total a Cobrar:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    Formatters.currency(totalAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Highlight automatic profit
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ProfitGreenContainer)
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = ProfitGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Ganancia Neta Calculada:",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ProfitGreen
                                        )
                                    }

                                    Text(
                                        text = "+${Formatters.currency(netProfit)} (${Formatters.percent(marginPercent)})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ProfitGreen
                                    )
                                }
                            }
                        }
                    }

                    // Stock Validation Warnings
                    if (selectedProduct != null && !isStockSufficient) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (selectedProduct!!.stock <= 0) "Producto sin stock disponible" else "Stock insuficiente (disponible: ${selectedProduct!!.stock})",
                                color = DangerRed,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = DangerRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Register Sale Button
                    Button(
                        onClick = {
                            val prod = selectedProduct
                            val store = currentStore
                            if (prod == null || store == null) {
                                errorMessage = "Seleccione un producto y sucursal válida"
                                return@Button
                            }
                            if (quantity <= 0) {
                                errorMessage = "La cantidad debe ser mayor a 0"
                                return@Button
                            }
                            if (prod.stock < quantity) {
                                errorMessage = "Stock insuficiente (Disponible: ${prod.stock})"
                                return@Button
                            }

                            onConfirmSale(store.id, prod.id, quantity, customerNote.trim()) { result ->
                                result.onSuccess { sale ->
                                    successMessage = "¡Venta de ${sale.productName} registrada con éxito! Ganancia: +${Formatters.currency(sale.netProfit)}"
                                    errorMessage = null
                                    customerNote = ""
                                    quantityStr = "1"
                                }.onFailure { err ->
                                    errorMessage = err.message ?: "Error al registrar la venta"
                                }
                            }
                        },
                        enabled = isStockSufficient,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_sale_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cobrar y Descontar Stock (${Formatters.currency(totalAmount)})",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        // Sales History Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Historial de Tickets",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "${recentSales.size} ventas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (recentSales.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "No hay ventas registradas aún.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        recentSales.forEach { sale ->
                            val store = stores.find { it.id == sale.storeId }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sale.productName,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "${sale.quantity} uds. x ${Formatters.currency(sale.unitPrice)}${if (store != null) " • ${store.name}" else ""}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (sale.customerNote.isNotBlank()) {
                                            Text(
                                                text = "Nota: ${sale.customerNote}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                        Text(
                                            text = Formatters.dateTime(sale.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = Formatters.currency(sale.totalAmount),
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Ganancia: +${Formatters.currency(sale.netProfit)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = ProfitGreen
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
