package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.model.CatalogItem
import com.example.data.model.MovementType
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.StockMovement
import com.example.data.model.Store
import kotlinx.coroutines.flow.Flow

class PosRepository(private val db: AppDatabase) {
    private val storeDao = db.storeDao()
    private val productDao = db.productDao()
    private val saleDao = db.saleDao()
    private val kardexDao = db.kardexDao()
    private val catalogDao = db.catalogDao()

    // --- Stores ---
    fun getAllStores(): Flow<List<Store>> = storeDao.getAllStores()

    suspend fun getStoreById(id: Long): Store? = storeDao.getStoreById(id)

    suspend fun saveStore(store: Store): Long {
        return if (store.id == 0L) {
            storeDao.insertStore(store)
        } else {
            storeDao.updateStore(store)
            store.id
        }
    }

    suspend fun deleteStore(store: Store) = storeDao.deleteStore(store)

    // --- Products ---
    fun getAllProducts(): Flow<List<Product>> = productDao.getAllProducts()

    fun getProductsByStore(storeId: Long): Flow<List<Product>> =
        productDao.getProductsByStore(storeId)

    fun getAllCategories(): Flow<List<String>> = catalogDao.getCategories()

    fun getAllSuppliers(): Flow<List<String>> = catalogDao.getSuppliers()

    suspend fun addCatalogItem(name: String, type: String) {
        val cleanName = name.trim()
        require(cleanName.isNotBlank()) { "El nombre no puede estar vacío" }
        require(type == "CATEGORY" || type == "SUPPLIER") { "Tipo de catálogo no válido" }
        catalogDao.insert(CatalogItem(name = cleanName, type = type))
    }

    suspend fun deleteCatalogItem(name: String, type: String) {
        // Resolve by its unique type/name pair without touching products.
        db.withTransaction {
            val id = db.openHelper.writableDatabase.query(
                "SELECT id FROM catalog_items WHERE type = ? AND name = ? COLLATE NOCASE LIMIT 1",
                arrayOf(type, name)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            if (id != null) catalogDao.deleteById(id)
        }
    }

    suspend fun getProductById(id: Long): Product? = productDao.getProductById(id)

    suspend fun saveProduct(product: Product, initialMovementReason: String = "Inventario inicial"): Long {
        return db.withTransaction {
            val isNew = product.id == 0L
            val previousProduct = if (!isNew) productDao.getProductById(product.id) else null
            val savedId = if (isNew) {
                val newId = productDao.insertProduct(product)
                // Register initial Kardex movement if stock > 0
                if (product.stock > 0) {
                    val store = storeDao.getStoreById(product.storeId)
                    val storeName = store?.name ?: "Tienda #${product.storeId}"
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = product.storeId,
                            storeName = storeName,
                            productId = newId,
                            productName = product.name,
                            movementType = MovementType.ENTRADA,
                            quantity = product.stock,
                            previousStock = 0,
                            newStock = product.stock,
                            reason = initialMovementReason
                        )
                    )
                }
                newId
            } else {
                productDao.updateProduct(product)
                if (previousProduct != null && previousProduct.stock != product.stock) {
                    val diff = product.stock - previousProduct.stock
                    val store = storeDao.getStoreById(product.storeId)
                    val storeName = store?.name ?: "Tienda #${product.storeId}"
                    val type = if (diff > 0) MovementType.AJUSTE_POSITIVO else MovementType.AJUSTE_NEGATIVO
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = product.storeId,
                            storeName = storeName,
                            productId = product.id,
                            productName = product.name,
                            movementType = type,
                            quantity = kotlin.math.abs(diff),
                            previousStock = previousProduct.stock,
                            newStock = product.stock,
                            reason = "Ajuste directo en edición de producto"
                        )
                    )
                }
                product.id
            }
            savedId
        }
    }

    suspend fun deleteProduct(product: Product) = productDao.deleteProduct(product)

    // --- POS Sales ---
    suspend fun processSale(
        storeId: Long,
        productId: Long,
        quantity: Int,
        customerNote: String = ""
    ): Result<Sale> {
        if (quantity <= 0) {
            return Result.failure(IllegalArgumentException("La cantidad debe ser mayor a 0"))
        }

        return db.withTransaction {
            val product = productDao.getProductById(productId)
                ?: return@withTransaction Result.failure(IllegalStateException("Producto no encontrado"))

            val store = storeDao.getStoreById(storeId)
                ?: return@withTransaction Result.failure(IllegalStateException("Sucursal no encontrada"))

            if (product.storeId != storeId) {
                return@withTransaction Result.failure(
                    IllegalArgumentException("El producto no pertenece a la sucursal seleccionada")
                )
            }

            if (product.stock < quantity) {
                return@withTransaction Result.failure(
                    IllegalStateException("Stock insuficiente: disponible ${product.stock}, solicitado $quantity")
                )
            }

            val storeName = store.name

            val previousStock = product.stock
            val newStock = previousStock - quantity

            // 1. Descontar stock
            productDao.updateStock(productId, newStock)

            // 2. Registrar venta con ganancia neta
            val sale = Sale(
                storeId = storeId,
                productId = productId,
                productName = product.name,
                quantity = quantity,
                unitCost = product.costPrice,
                unitPrice = product.salePrice,
                totalAmount = product.salePrice * quantity,
                netProfit = (product.salePrice - product.costPrice) * quantity,
                customerNote = customerNote,
                timestamp = System.currentTimeMillis()
            )
            val saleId = saleDao.insertSale(sale)

            // 3. Registrar movimiento en Kardex
            val movement = StockMovement(
                storeId = storeId,
                storeName = storeName,
                productId = productId,
                productName = product.name,
                movementType = MovementType.VENTA,
                quantity = quantity,
                previousStock = previousStock,
                newStock = newStock,
                reason = if (customerNote.isNotBlank()) "Venta #$saleId ($customerNote)" else "Venta de mostrador #$saleId",
                timestamp = System.currentTimeMillis()
            )
            kardexDao.insertMovement(movement)

            Result.success(sale.copy(id = saleId))
        }
    }

    // --- Kardex / Stock Adjustments ---
    suspend fun adjustStock(
        productId: Long,
        quantityChange: Int,
        type: MovementType,
        reason: String
    ): Result<Unit> {
        if (quantityChange <= 0) {
            return Result.failure(IllegalArgumentException("La cantidad debe ser mayor a 0"))
        }
        if (type == MovementType.TRASPASO) {
            return Result.failure(
                UnsupportedOperationException(
                    "El traspaso entre sucursales aún no está implementado como operación de salida y entrada"
                )
            )
        }

        return db.withTransaction {
            val product = productDao.getProductById(productId)
                ?: return@withTransaction Result.failure(IllegalStateException("Producto no encontrado"))

            val store = storeDao.getStoreById(product.storeId)
            val storeName = store?.name ?: "Sucursal #${product.storeId}"

            val previousStock = product.stock
            val newStock = when (type) {
                MovementType.ENTRADA, MovementType.AJUSTE_POSITIVO -> previousStock + quantityChange
                MovementType.VENTA, MovementType.AJUSTE_NEGATIVO, MovementType.TRASPASO -> {
                    if (previousStock < quantityChange) {
                        return@withTransaction Result.failure(
                            IllegalStateException("Stock insuficiente para restar $quantityChange (Actual: $previousStock)")
                        )
                    }
                    previousStock - quantityChange
                }
            }

            productDao.updateStock(productId, newStock)

            val movement = StockMovement(
                storeId = product.storeId,
                storeName = storeName,
                productId = productId,
                productName = product.name,
                movementType = type,
                quantity = quantityChange,
                previousStock = previousStock,
                newStock = newStock,
                reason = reason.ifBlank { type.displayName },
                timestamp = System.currentTimeMillis()
            )
            kardexDao.insertMovement(movement)

            Result.success(Unit)
        }
    }

    // --- Sales queries ---
    fun getAllSales(): Flow<List<Sale>> = saleDao.getAllSales()
    fun getSalesByStore(storeId: Long): Flow<List<Sale>> = saleDao.getSalesByStore(storeId)

    // --- Kardex queries ---
    fun getAllKardex(): Flow<List<StockMovement>> = kardexDao.getAllMovements()
    fun getKardexByStore(storeId: Long): Flow<List<StockMovement>> = kardexDao.getMovementsByStore(storeId)

    // --- Seed Demo Data ---
    suspend fun seedInitialDataIfNeeded() {
        if (storeDao.countStores() == 0) {
            db.withTransaction {
                val store1Id = storeDao.insertStore(
                    Store(
                        name = "Sucursal Centro - Principal",
                        logoUrl = "https://images.unsplash.com/photo-1578916171728-46686eac8d58?w=200",
                        address = "Av. Principal #450, Centro",
                        phone = "+1 809-555-0101",
                        colorHex = "#0284C7"
                    )
                )

                val store2Id = storeDao.insertStore(
                    Store(
                        name = "Sucursal Plaza Norte",
                        logoUrl = "https://images.unsplash.com/photo-1534452203293-494d7ddbf7e0?w=200",
                        address = "Plaza Comercial Norte, Local 12",
                        phone = "+1 809-555-0199",
                        colorHex = "#10B981"
                    )
                )

                // Demo products for Store 1
                val productsStore1 = listOf(
                    Product(
                        storeId = store1Id,
                        name = "Café Gourmet Tostado 500g",
                        category = "Bebidas",
                        supplier = "Café Selecto S.A.",
                        costPrice = 4.50,
                        salePrice = 8.50,
                        stock = 24,
                        minStockAlert = 8,
                        photoUrl = "https://images.unsplash.com/photo-1559056199-641a0ac8b55e?w=200",
                        saleLink = "https://wa.me/?text=Hola,%20deseo%20comprar%20Cafe%20Gourmet"
                    ),
                    Product(
                        storeId = store1Id,
                        name = "Aceite de Oliva Extra Virgen 750ml",
                        category = "Abarrotes",
                        supplier = "Distribuidora Mediterránea",
                        costPrice = 7.20,
                        salePrice = 12.00,
                        stock = 4, // Stock crítico
                        minStockAlert = 6,
                        photoUrl = "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5?w=200",
                        saleLink = ""
                    ),
                    Product(
                        storeId = store1Id,
                        name = "Agua Mineral Natural 1.5L",
                        category = "Bebidas",
                        supplier = "Manantial Puro",
                        costPrice = 0.60,
                        salePrice = 1.50,
                        stock = 60,
                        minStockAlert = 15,
                        photoUrl = "https://images.unsplash.com/photo-1548839140-29a749e1bc4e?w=200",
                        saleLink = ""
                    ),
                    Product(
                        storeId = store1Id,
                        name = "Galletas Avena & Miel Artesanales",
                        category = "Snacks",
                        supplier = "Panadería Del Sol",
                        costPrice = 1.80,
                        salePrice = 3.20,
                        stock = 18,
                        minStockAlert = 5,
                        photoUrl = "https://images.unsplash.com/photo-1558961363-fa8fdf82db35?w=200",
                        saleLink = ""
                    ),
                    Product(
                        storeId = store1Id,
                        name = "Detergente Líquido Ecológico 1L",
                        category = "Limpieza",
                        supplier = "EcoClean Corp",
                        costPrice = 3.20,
                        salePrice = 5.90,
                        stock = 2, // Stock muy crítico
                        minStockAlert = 5,
                        photoUrl = "https://images.unsplash.com/photo-1583947215259-38e31be8751f?w=200",
                        saleLink = ""
                    )
                )

                // Demo products for Store 2
                val productsStore2 = listOf(
                    Product(
                        storeId = store2Id,
                        name = "Snack Mix Frutos Secos 200g",
                        category = "Snacks",
                        supplier = "Distribuidora NutriMix",
                        costPrice = 2.40,
                        salePrice = 4.80,
                        stock = 32,
                        minStockAlert = 10,
                        photoUrl = "https://images.unsplash.com/photo-1599599810769-bcde5a160d32?w=200",
                        saleLink = ""
                    ),
                    Product(
                        storeId = store2Id,
                        name = "Jugo Naranja 100% Puro 1L",
                        category = "Bebidas",
                        supplier = "Manantial Puro",
                        costPrice = 1.50,
                        salePrice = 2.80,
                        stock = 3, // Stock crítico
                        minStockAlert = 8,
                        photoUrl = "https://images.unsplash.com/photo-1621506289937-a8e4df240d0b?w=200",
                        saleLink = ""
                    ),
                    Product(
                        storeId = store2Id,
                        name = "Pasta Italiana Penne Rigate 500g",
                        category = "Abarrotes",
                        supplier = "Distribuidora Mediterránea",
                        costPrice = 1.10,
                        salePrice = 2.30,
                        stock = 45,
                        minStockAlert = 12,
                        photoUrl = "https://images.unsplash.com/photo-1551462147-37885acc36f1?w=200",
                        saleLink = ""
                    )
                )

                for (p in productsStore1) {
                    val pId = productDao.insertProduct(p)
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = store1Id,
                            storeName = "Sucursal Centro - Principal",
                            productId = pId,
                            productName = p.name,
                            movementType = MovementType.ENTRADA,
                            quantity = p.stock + 6,
                            previousStock = 0,
                            newStock = p.stock + 6,
                            reason = "Carga inicial de mercancía",
                            timestamp = System.currentTimeMillis() - 86400000L * 3
                        )
                    )
                    // Sample demo sale for this product
                    val saleQty = 3
                    val newStock = p.stock
                    saleDao.insertSale(
                        Sale(
                            storeId = store1Id,
                            productId = pId,
                            productName = p.name,
                            quantity = saleQty,
                            unitCost = p.costPrice,
                            unitPrice = p.salePrice,
                            totalAmount = p.salePrice * saleQty,
                            netProfit = (p.salePrice - p.costPrice) * saleQty,
                            customerNote = "Cliente recurrente",
                            timestamp = System.currentTimeMillis() - 3600000L * 4
                        )
                    )
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = store1Id,
                            storeName = "Sucursal Centro - Principal",
                            productId = pId,
                            productName = p.name,
                            movementType = MovementType.VENTA,
                            quantity = saleQty,
                            previousStock = p.stock + saleQty,
                            newStock = p.stock,
                            reason = "Venta demostración",
                            timestamp = System.currentTimeMillis() - 3600000L * 4
                        )
                    )
                }

                for (p in productsStore2) {
                    val pId = productDao.insertProduct(p)
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = store2Id,
                            storeName = "Sucursal Plaza Norte",
                            productId = pId,
                            productName = p.name,
                            movementType = MovementType.ENTRADA,
                            quantity = p.stock + 4,
                            previousStock = 0,
                            newStock = p.stock + 4,
                            reason = "Apertura de sucursal",
                            timestamp = System.currentTimeMillis() - 86400000L * 2
                        )
                    )
                    val saleQty = 2
                    saleDao.insertSale(
                        Sale(
                            storeId = store2Id,
                            productId = pId,
                            productName = p.name,
                            quantity = saleQty,
                            unitCost = p.costPrice,
                            unitPrice = p.salePrice,
                            totalAmount = p.salePrice * saleQty,
                            netProfit = (p.salePrice - p.costPrice) * saleQty,
                            customerNote = "Venta mostrador",
                            timestamp = System.currentTimeMillis() - 3600000L * 2
                        )
                    )
                    kardexDao.insertMovement(
                        StockMovement(
                            storeId = store2Id,
                            storeName = "Sucursal Plaza Norte",
                            productId = pId,
                            productName = p.name,
                            movementType = MovementType.VENTA,
                            quantity = saleQty,
                            previousStock = p.stock + saleQty,
                            newStock = p.stock,
                            reason = "Venta demostración",
                            timestamp = System.currentTimeMillis() - 3600000L * 2
                        )
                    )
                }
            }
        }
    }
}
