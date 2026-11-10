package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Product
import com.example.data.model.Store
import com.example.ui.StockFilter
import com.example.ui.components.ProductCard
import com.example.ui.components.StoreSelectorBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    stores: List<Store>,
    activeStoreId: Long?,
    products: List<Product>,
    categories: List<String>,
    suppliers: List<String>,
    searchQuery: String,
    selectedCategory: String?,
    selectedSupplier: String?,
    stockFilter: StockFilter,
    onSelectStore: (Long?) -> Unit,
    onAddStoreClick: () -> Unit,
    onManageStoresClick: () -> Unit,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onSupplierChange: (String?) -> Unit,
    onStockFilterChange: (StockFilter) -> Unit,
    onAddProductClick: () -> Unit,
    onQuickSaleClick: (Product) -> Unit,
    onAdjustStockClick: (Product) -> Unit,
    onEditProductClick: (Product) -> Unit,
    onDeleteProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    var supplierMenuExpanded by remember { mutableStateOf(false) }
    val categoryScrollState = rememberScrollState()
    val stockScrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.testTag("inventory_screen"),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddProductClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Producto")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Multi-Store Selector Bar
            StoreSelectorBar(
                stores = stores,
                activeStoreId = activeStoreId,
                onSelectStore = onSelectStore,
                onAddStoreClick = onAddStoreClick,
                onManageStoresClick = onManageStoresClick
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Buscar producto, categoría o proveedor...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_input"),
                singleLine = true
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(categoryScrollState)
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategoryChange(null) },
                    label = { Text("Todas categorías", style = MaterialTheme.typography.bodySmall) }
                )

                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { onCategoryChange(if (selectedCategory == cat) null else cat) },
                        label = { Text(cat, style = MaterialTheme.typography.bodySmall) }
                    )
                }
            }

            // Stock Status Filter & Supplier Filter in a compact row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(stockScrollState)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Supplier Dropdown trigger
                Box {
                    OutlinedButton(
                        onClick = { supplierMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedSupplier == null) "Proveedor: Todos" else "Prov: $selectedSupplier",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }

                    DropdownMenu(
                        expanded = supplierMenuExpanded,
                        onDismissRequest = { supplierMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Todos los proveedores") },
                            onClick = {
                                onSupplierChange(null)
                                supplierMenuExpanded = false
                            }
                        )
                        suppliers.forEach { sup ->
                            DropdownMenuItem(
                                text = { Text(sup) },
                                onClick = {
                                    onSupplierChange(sup)
                                    supplierMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Stock Filter Pills
                StockFilter.values().forEach { filter ->
                    ElevatedFilterChip(
                        selected = stockFilter == filter,
                        onClick = { onStockFilterChange(filter) },
                        label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            // Product Count Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${products.size} productos encontrados",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (selectedCategory != null || selectedSupplier != null || searchQuery.isNotBlank() || stockFilter != StockFilter.ALL) {
                    Text(
                        text = "Limpiar filtros",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }

            // Product List
            if (products.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No se encontraron productos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Intenta cambiar los filtros de búsqueda o agrega un nuevo producto.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(products, key = { it.id }) { product ->
                        val store = stores.find { it.id == product.storeId }
                        ProductCard(
                            product = product,
                            storeName = store?.name,
                            onQuickSaleClick = onQuickSaleClick,
                            onAdjustStockClick = onAdjustStockClick,
                            onEditClick = onEditProductClick,
                            onDeleteClick = onDeleteProductClick
                        )
                    }
                }
            }
        }
    }
}
