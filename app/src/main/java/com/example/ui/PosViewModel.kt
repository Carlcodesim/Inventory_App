package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.MovementType
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.StockMovement
import com.example.data.model.Store
import com.example.data.repository.PosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StockFilter(val label: String) {
    ALL("Todos"),
    LOW_STOCK("Bajo Stock"),
    IN_STOCK("En Stock"),
    OUT_OF_STOCK("Agotados")
}

data class DashboardKpis(
    val totalSalesAmount: Double = 0.0,
    val totalNetProfit: Double = 0.0,
    val profitMarginPercentage: Double = 0.0,
    val topSellingProductName: String = "Sin ventas",
    val topSellingProductUnits: Int = 0,
    val lowStockCount: Int = 0,
    val totalStockUnits: Int = 0,
    val totalInventoryValueCost: Double = 0.0,
    val totalInventoryValueSale: Double = 0.0,
    val potentialProfit: Double = 0.0
)

class PosViewModel(private val repository: PosRepository) : ViewModel() {

    // Store selection: null means "Todas las tiendas"
    val activeStoreId = MutableStateFlow<Long?>(null)

    // Filters for inventory
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow<String?>(null)
    val selectedSupplier = MutableStateFlow<String?>(null)
    val stockFilter = MutableStateFlow(StockFilter.ALL)

    // Filter for Kardex
    val kardexMovementFilter = MutableStateFlow<MovementType?>(null)

    // Data streams from repository
    val stores: StateFlow<List<Store>> = repository.getAllStores()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCategories: StateFlow<List<String>> = repository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<String>> = repository.getAllSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allProducts = repository.getAllProducts()
    private val allSales = repository.getAllSales()
    private val allKardex = repository.getAllKardex()

    data class InventoryFilterCriteria(
        val query: String,
        val category: String?,
        val supplier: String?,
        val stockFilter: StockFilter
    )

    private val inventoryFilterCriteria = combine(
        searchQuery,
        selectedCategory,
        selectedSupplier,
        stockFilter
    ) { query, category, supplier, stock ->
        InventoryFilterCriteria(query, category, supplier, stock)
    }

    // Filtered products
    val filteredProducts: StateFlow<List<Product>> = combine(
        allProducts,
        activeStoreId,
        inventoryFilterCriteria
    ) { products, storeId, criteria ->
        products.filter { product ->
            val matchesStore = storeId == null || product.storeId == storeId
            val matchesQuery = criteria.query.isBlank() ||
                    product.name.contains(criteria.query, ignoreCase = true) ||
                    product.category.contains(criteria.query, ignoreCase = true) ||
                    product.supplier.contains(criteria.query, ignoreCase = true)
            val matchesCategory = criteria.category == null || product.category.equals(criteria.category, ignoreCase = true)
            val matchesSupplier = criteria.supplier == null || product.supplier.equals(criteria.supplier, ignoreCase = true)
            val matchesStock = when (criteria.stockFilter) {
                StockFilter.ALL -> true
                StockFilter.LOW_STOCK -> product.isLowStock
                StockFilter.IN_STOCK -> product.stock > product.minStockAlert
                StockFilter.OUT_OF_STOCK -> product.isOutOfStock
            }
            matchesStore && matchesQuery && matchesCategory && matchesSupplier && matchesStock
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered sales
    val filteredSales: StateFlow<List<Sale>> = combine(allSales, activeStoreId) { sales, storeId ->
        if (storeId == null) sales else sales.filter { it.storeId == storeId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtered Kardex
    val filteredKardex: StateFlow<List<StockMovement>> = combine(
        allKardex,
        activeStoreId,
        kardexMovementFilter
    ) { movements, storeId, typeFilter ->
        movements.filter { m ->
            val matchesStore = storeId == null || m.storeId == storeId
            val matchesType = typeFilter == null || m.movementType == typeFilter
            matchesStore && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard KPIs calculated reactively
    val dashboardKpis: StateFlow<DashboardKpis> = combine(
        allProducts,
        allSales,
        activeStoreId
    ) { products, sales, storeId ->
        val relevantProducts = if (storeId == null) products else products.filter { it.storeId == storeId }
        val relevantSales = if (storeId == null) sales else sales.filter { it.storeId == storeId }

        val totalSales = relevantSales.sumOf { it.totalAmount }
        val totalNetProfit = relevantSales.sumOf { it.netProfit }
        val marginPercent = if (totalSales > 0) (totalNetProfit / totalSales) * 100.0 else 0.0

        val salesByProduct = relevantSales.groupBy { it.productName }
            .mapValues { entry -> entry.value.sumOf { it.quantity } }
        val topSelling = salesByProduct.maxByOrNull { it.value }

        val lowStockCount = relevantProducts.count { it.isLowStock || it.isOutOfStock }
        val totalUnits = relevantProducts.sumOf { it.stock }
        val invCost = relevantProducts.sumOf { it.costPrice * it.stock }
        val invSale = relevantProducts.sumOf { it.salePrice * it.stock }
        val potProfit = invSale - invCost

        DashboardKpis(
            totalSalesAmount = totalSales,
            totalNetProfit = totalNetProfit,
            profitMarginPercentage = marginPercent,
            topSellingProductName = topSelling?.key ?: "Sin ventas aún",
            topSellingProductUnits = topSelling?.value ?: 0,
            lowStockCount = lowStockCount,
            totalStockUnits = totalUnits,
            totalInventoryValueCost = invCost,
            totalInventoryValueSale = invSale,
            potentialProfit = potProfit
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardKpis())

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
        }
    }

    // --- Actions ---

    fun setActiveStore(storeId: Long?) {
        activeStoreId.value = storeId
    }

    fun setSearchQuery(q: String) {
        searchQuery.value = q
    }

    fun setSelectedCategory(cat: String?) {
        selectedCategory.value = cat
    }

    fun setSelectedSupplier(sup: String?) {
        selectedSupplier.value = sup
    }

    fun setStockFilter(filter: StockFilter) {
        stockFilter.value = filter
    }

    fun setKardexMovementFilter(type: MovementType?) {
        kardexMovementFilter.value = type
    }

    fun saveStore(store: Store, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveStore(store)
            onComplete()
        }
    }

    fun deleteStore(store: Store, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteStore(store)
            if (activeStoreId.value == store.id) {
                activeStoreId.value = null
            }
            onComplete()
        }
    }

    fun saveProduct(product: Product, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveProduct(product)
            onComplete()
        }
    }

    fun deleteProduct(product: Product, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            onComplete()
        }
    }

    fun registerSale(
        storeId: Long,
        productId: Long,
        quantity: Int,
        customerNote: String,
        onResult: (Result<Sale>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.processSale(storeId, productId, quantity, customerNote)
            onResult(result)
        }
    }

    fun adjustStock(
        productId: Long,
        quantityChange: Int,
        type: MovementType,
        reason: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.adjustStock(productId, quantityChange, type, reason)
            onResult(result)
        }
    }
}

class PosViewModelFactory(private val repository: PosRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            return PosViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
