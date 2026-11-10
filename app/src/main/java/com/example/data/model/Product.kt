package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Store::class,
            parentColumns = ["id"],
            childColumns = ["storeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["storeId"])]
)
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeId: Long,
    val name: String,
    val category: String,
    val supplier: String,
    val costPrice: Double,
    val salePrice: Double,
    val stock: Int,
    val minStockAlert: Int = 5,
    val photoUrl: String = "",
    val saleLink: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * Ganancia monetaria por unidad: Precio Venta - Costo Unitario
     */
    val profitAmount: Double
        get() = salePrice - costPrice

    /**
     * Margen sobre el costo (% de ganancia sobre costo):
     * ((Precio Venta - Costo) / Costo) * 100
     */
    val profitMarginPercent: Double
        get() = if (costPrice > 0) ((salePrice - costPrice) / costPrice) * 100.0 else 0.0

    /**
     * Margen comercial sobre el precio de venta (% de ganancia del ticket):
     * ((Precio Venta - Costo) / Precio Venta) * 100
     */
    val marginOnSalePercent: Double
        get() = if (salePrice > 0) ((salePrice - costPrice) / salePrice) * 100.0 else 0.0

    val isLowStock: Boolean
        get() = stock in 1..minStockAlert

    val isOutOfStock: Boolean
        get() = stock <= 0
}
