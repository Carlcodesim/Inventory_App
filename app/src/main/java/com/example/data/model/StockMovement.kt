package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MovementType(val displayName: String, val isPositive: Boolean) {
    ENTRADA("Entrada de Mercancía", true),
    VENTA("Venta de Mostrador", false),
    AJUSTE_POSITIVO("Ajuste Positivo (+)", true),
    AJUSTE_NEGATIVO("Ajuste Negativo (-)", false),
    TRASPASO("Traspaso", false)
}

@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["productId"]),
        Index(value = ["timestamp"])
    ]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeId: Long,
    val storeName: String,
    val productId: Long,
    val productName: String,
    val movementType: MovementType,
    val quantity: Int, // Cantidad involucrada
    val previousStock: Int,
    val newStock: Int,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
