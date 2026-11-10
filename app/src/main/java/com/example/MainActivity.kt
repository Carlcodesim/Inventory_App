package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.model.Product
import com.example.data.model.Store
import com.example.data.repository.PosRepository
import com.example.ui.PosViewModel
import com.example.ui.PosViewModelFactory
import com.example.ui.components.ProductFormDialog
import com.example.ui.components.StockAdjustmentDialog
import com.example.ui.components.StoreManagementDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.KardexScreen
import com.example.ui.screens.SalesPosScreen
import com.example.ui.theme.MyApplicationTheme

enum class PosTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    DASHBOARD("Inicio", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    INVENTORY("Inventario", Icons.Filled.Inventory2, Icons.Outlined.Inventory2),
    SALES("Ventas", Icons.Filled.PointOfSale, Icons.Outlined.PointOfSale),
    KARDEX("Kardex", Icons.Filled.History, Icons.Outlined.History)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val db = AppDatabase.getDatabase(applicationContext)
        val repository = PosRepository(db)
        val factory = PosViewModelFactory(repository)

        setContent {
            MyApplicationTheme {
                MainApp(factory = factory)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(factory: PosViewModelFactory) {
    val viewModel: PosViewModel = viewModel(factory = factory)

    // Observable states
    val stores by viewModel.stores.collectAsStateWithLifecycle()
    val activeStoreId by viewModel.activeStoreId.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val filteredSales by viewModel.filteredSales.collectAsStateWithLifecycle()
    val filteredKardex by viewModel.filteredKardex.collectAsStateWithLifecycle()
    val dashboardKpis by viewModel.dashboardKpis.collectAsStateWithLifecycle()
    val allCategories by viewModel.allCategories.collectAsStateWithLifecycle()
    val allSuppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedSupplier by viewModel.selectedSupplier.collectAsStateWithLifecycle()
    val stockFilter by viewModel.stockFilter.collectAsStateWithLifecycle()
    val kardexFilter by viewModel.kardexMovementFilter.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(PosTab.DASHBOARD) }

    // Dialog states
    var showProductForm by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<Product?>(null) }
    var showStoreManagement by remember { mutableStateOf(false) }
    var storeToEdit by remember { mutableStateOf<Store?>(null) }
    var productToAdjust by remember { mutableStateOf<Product?>(null) }
    var preselectedPosProductId by remember { mutableStateOf<Long?>(null) }

    // Back handler to return to Dashboard if on other tabs
    BackHandler(enabled = currentTab != PosTab.DASHBOARD) {
        currentTab = PosTab.DASHBOARD
    }

    val activeStore = stores.find { it.id == activeStoreId }
    val lowStockProducts = remember(filteredProducts) {
        filteredProducts.filter { it.isLowStock || it.isOutOfStock }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "TiendaPOS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (activeStore != null) activeStore.name else "Todas las sucursales",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        modifier = Modifier.testTag("topbar_manage_stores")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "Administrar Tiendas",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar")
            ) {
                PosTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            if (tab == PosTab.INVENTORY && dashboardKpis.lowStockCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(dashboardKpis.lowStockCount.toString())
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            }
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                PosTab.DASHBOARD -> {
                    DashboardScreen(
                        stores = stores,
                        activeStoreId = activeStoreId,
                        kpis = dashboardKpis,
                        lowStockProducts = lowStockProducts,
                        recentSales = filteredSales,
                        onSelectStore = { viewModel.setActiveStore(it) },
                        onAddStoreClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onManageStoresClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onAdjustStockClick = { productToAdjust = it },
                        onNavigateToPos = {
                            preselectedPosProductId = null
                            currentTab = PosTab.SALES
                        },
                        onNavigateToInventory = { currentTab = PosTab.INVENTORY }
                    )
                }

                PosTab.INVENTORY -> {
                    InventoryScreen(
                        stores = stores,
                        activeStoreId = activeStoreId,
                        products = filteredProducts,
                        categories = allCategories,
                        suppliers = allSuppliers,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        selectedSupplier = selectedSupplier,
                        stockFilter = stockFilter,
                        onSelectStore = { viewModel.setActiveStore(it) },
                        onAddStoreClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onManageStoresClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onSearchChange = { viewModel.setSearchQuery(it) },
                        onCategoryChange = { viewModel.setSelectedCategory(it) },
                        onSupplierChange = { viewModel.setSelectedSupplier(it) },
                        onStockFilterChange = { viewModel.setStockFilter(it) },
                        onAddProductClick = {
                            productToEdit = null
                            showProductForm = true
                        },
                        onQuickSaleClick = { product ->
                            preselectedPosProductId = product.id
                            viewModel.setActiveStore(product.storeId)
                            currentTab = PosTab.SALES
                        },
                        onAdjustStockClick = { productToAdjust = it },
                        onEditProductClick = {
                            productToEdit = it
                            showProductForm = true
                        },
                        onDeleteProductClick = { product ->
                            viewModel.deleteProduct(product)
                        }
                    )
                }

                PosTab.SALES -> {
                    SalesPosScreen(
                        stores = stores,
                        activeStoreId = activeStoreId,
                        products = filteredProducts,
                        recentSales = filteredSales,
                        initialSelectedProductId = preselectedPosProductId,
                        onSelectStore = { viewModel.setActiveStore(it) },
                        onAddStoreClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onManageStoresClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onConfirmSale = { storeId, productId, quantity, customerNote, onResult ->
                            viewModel.registerSale(storeId, productId, quantity, customerNote, onResult)
                        }
                    )
                }

                PosTab.KARDEX -> {
                    KardexScreen(
                        stores = stores,
                        activeStoreId = activeStoreId,
                        movements = filteredKardex,
                        selectedTypeFilter = kardexFilter,
                        onSelectStore = { viewModel.setActiveStore(it) },
                        onAddStoreClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onManageStoresClick = {
                            storeToEdit = null
                            showStoreManagement = true
                        },
                        onTypeFilterChange = { viewModel.setKardexMovementFilter(it) }
                    )
                }
            }
        }
    }

    // Product Form Dialog (Create / Edit)
    if (showProductForm) {
        ProductFormDialog(
            stores = stores,
            selectedStoreId = activeStoreId,
            existingCategories = allCategories,
            existingSuppliers = allSuppliers,
            initialProduct = productToEdit,
            onDismiss = {
                showProductForm = false
                productToEdit = null
            },
            onSaveProduct = { product ->
                viewModel.saveProduct(product) {
                    showProductForm = false
                    productToEdit = null
                }
            }
        )
    }

    // Store Management Dialog
    if (showStoreManagement) {
        StoreManagementDialog(
            stores = stores,
            initialStoreToEdit = storeToEdit,
            onDismiss = {
                showStoreManagement = false
                storeToEdit = null
            },
            onSaveStore = { store ->
                viewModel.saveStore(store)
            },
            onDeleteStore = { store ->
                viewModel.deleteStore(store)
            }
        )
    }

    // Stock Adjustment Dialog (Kardex)
    productToAdjust?.let { product ->
        StockAdjustmentDialog(
            product = product,
            onDismiss = { productToAdjust = null },
            onConfirmAdjustment = { qty, type, reason ->
                viewModel.adjustStock(product.id, qty, type, reason) {
                    productToAdjust = null
                }
            }
        )
    }
}
