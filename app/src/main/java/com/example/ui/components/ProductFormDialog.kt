package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Product
import com.example.data.model.Store
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedContainer
import com.example.ui.theme.ProfitGreen
import com.example.ui.theme.ProfitGreenContainer
import com.example.ui.util.Formatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    stores: List<Store>,
    selectedStoreId: Long?,
    existingCategories: List<String>,
    existingSuppliers: List<String>,
    initialProduct: Product? = null,
    onDismiss: () -> Unit,
    onSaveProduct: (Product) -> Unit
) {
    val isEditing = initialProduct != null
    var storeId by remember {
        mutableStateOf(initialProduct?.storeId ?: selectedStoreId ?: stores.firstOrNull()?.id ?: 1L)
    }
    var storeDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }
    var photoLoadFailed by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "") }
    var supplier by remember { mutableStateOf(initialProduct?.supplier ?: "") }
    var costPriceStr by remember { mutableStateOf(initialProduct?.costPrice?.toString() ?: "") }
    var salePriceStr by remember { mutableStateOf(initialProduct?.salePrice?.toString() ?: "") }
    var stockStr by remember { mutableStateOf(initialProduct?.stock?.toString() ?: "10") }
    var minStockStr by remember { mutableStateOf(initialProduct?.minStockAlert?.toString() ?: "5") }
    var photoUrl by remember { mutableStateOf(initialProduct?.photoUrl ?: "") }
    var saleLink by remember { mutableStateOf(initialProduct?.saleLink ?: "") }

    var nameError by remember { mutableStateOf(false) }
    var costError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    // Real-time automatic calculations
    val costPrice = costPriceStr.toDoubleOrNull() ?: 0.0
    val salePrice = salePriceStr.toDoubleOrNull() ?: 0.0
    val profitAmount = salePrice - costPrice
    val profitMarginCostPercent = if (costPrice > 0) ((salePrice - costPrice) / costPrice) * 100.0 else 0.0
    val marginOnSalePercent = if (salePrice > 0) ((salePrice - costPrice) / salePrice) * 100.0 else 0.0
    val isLoss = salePrice < costPrice && salePrice > 0.0

    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Editar Producto" else "Nuevo Producto",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Store Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentStore = stores.find { it.id == storeId }
                    OutlinedTextField(
                        value = currentStore?.name ?: "Seleccione una sucursal",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Sucursal Destino *") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.clickable { storeDropdownExpanded = true }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { storeDropdownExpanded = true }
                            .testTag("product_store_select")
                    )

                    DropdownMenu(
                        expanded = storeDropdownExpanded,
                        onDismissRequest = { storeDropdownExpanded = false }
                    ) {
                        stores.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.name) },
                                onClick = {
                                    storeId = s.id
                                    storeDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Product Name
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = it.isBlank()
                    },
                    label = { Text("Nombre del Producto *") },
                    isError = nameError,
                    supportingText = { if (nameError) Text("El nombre es requerido") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input"),
                    singleLine = true
                )

                // Categories and suppliers are selected from the catalogs in Settings.
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = category.ifBlank { "Seleccionar categoría" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoría *") },
                        isError = category.isBlank(),
                        supportingText = { if (category.isBlank()) Text("Selecciona una categoría desde Configuración") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { categoryDropdownExpanded = true }
                            .testTag("product_category_input")
                    )
                    DropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        existingCategories.distinct().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { category = option; categoryDropdownExpanded = false }
                            )
                        }
                        if (existingCategories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Agrega categorías desde Configuración") },
                                enabled = false,
                                onClick = {}
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = supplier.ifBlank { "Sin proveedor" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Proveedor (opcional)") },
                        trailingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { supplierDropdownExpanded = true }
                            .testTag("product_supplier_input")
                    )
                    DropdownMenu(
                        expanded = supplierDropdownExpanded,
                        onDismissRequest = { supplierDropdownExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sin proveedor") },
                            onClick = { supplier = ""; supplierDropdownExpanded = false }
                        )
                        existingSuppliers.distinct().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { supplier = option; supplierDropdownExpanded = false }
                            )
                        }
                    }
                }

                // Cost and Sale Price
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = costPriceStr,
                        onValueChange = {
                            costPriceStr = it
                            costError = it.toDoubleOrNull() == null
                        },
                        label = { Text("Costo Unitario ($) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = costError,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_cost_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = salePriceStr,
                        onValueChange = {
                            salePriceStr = it
                            priceError = it.toDoubleOrNull() == null
                        },
                        label = { Text("Precio Venta ($) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = priceError,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_price_input"),
                        singleLine = true
                    )
                }

                // AUTOMATIC PROFIT CALCULATION CARD (Real-time feedback)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profit_calculation_preview"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLoss) DangerRedContainer else ProfitGreenContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isLoss) Icons.Default.TrendingDown else Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = if (isLoss) DangerRed else ProfitGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isLoss) "Alerta de Pérdida" else "Cálculo Automático de Margen",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isLoss) DangerRed else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Text(
                                text = "${if (profitAmount >= 0) "+" else ""}${Formatters.currency(profitAmount)} / ud.",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isLoss) DangerRed else ProfitGreen
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Margen s/ Costo: ${if (profitMarginCostPercent >= 0) "+" else ""}${Formatters.percent(profitMarginCostPercent)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLoss) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Margen Comercial: ${if (marginOnSalePercent >= 0) "+" else ""}${Formatters.percent(marginOnSalePercent)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isLoss) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Stock and Minimum Alert
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = stockStr,
                        onValueChange = { stockStr = it },
                        label = { Text("Stock Actual *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_stock_input"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = minStockStr,
                        onValueChange = { minStockStr = it },
                        label = { Text("Alerta Stock Mínimo") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("product_min_stock_input"),
                        singleLine = true
                    )
                }

                // Photo URL
                OutlinedTextField(
                    value = photoUrl,
                    onValueChange = { photoUrl = it; photoLoadFailed = false },
                    label = { Text("URL directa de la Foto") },
                    placeholder = { Text("https://sitio.com/imagen.jpg") },
                    supportingText = { Text("Debe ser un enlace directo a un archivo de imagen, no a la página del producto.") },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_photo_input"),
                    singleLine = true
                )

                // Visual Preview of the photo if entered
                if (photoUrl.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Vista previa:", style = MaterialTheme.typography.bodySmall)
                        if (photoLoadFailed) {
                            Text(
                                "No se pudo cargar. Usa un enlace HTTPS directo a la imagen (JPG, PNG o WEBP).",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl.trim())
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Foto producto",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                                onError = { photoLoadFailed = true },
                                onSuccess = { photoLoadFailed = false }
                            )
                        }
                    }
                }

                // Sale Link (WhatsApp / Web)
                OutlinedTextField(
                    value = saleLink,
                    onValueChange = { saleLink = it },
                    label = { Text("Link de Venta (WhatsApp / Web)") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                    placeholder = { Text("https://wa.me/...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_sale_link_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val isNameValid = name.isNotBlank()
                    val isCategoryValid = category.isNotBlank()
                    val cost = costPriceStr.toDoubleOrNull()
                    val sale = salePriceStr.toDoubleOrNull()
                    val isCostValid = cost != null && cost >= 0
                    val isPriceValid = sale != null && sale >= 0

                    nameError = !isNameValid
                    
                    costError = !isCostValid
                    priceError = !isPriceValid

                    if (isNameValid && isCategoryValid && isCostValid && isPriceValid) {
                        val stock = stockStr.toIntOrNull() ?: 0
                        val minStock = minStockStr.toIntOrNull() ?: 5

                        onSaveProduct(
                            Product(
                                id = initialProduct?.id ?: 0L,
                                storeId = storeId,
                                name = name.trim(),
                                category = category.trim(),
                                supplier = supplier.trim(),
                                costPrice = cost!!,
                                salePrice = sale!!,
                                stock = stock,
                                minStockAlert = minStock,
                                photoUrl = photoUrl.trim(),
                                saleLink = saleLink.trim(),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                },
                modifier = Modifier.testTag("save_product_btn")
            ) {
                Text(if (isEditing) "Actualizar" else "Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
