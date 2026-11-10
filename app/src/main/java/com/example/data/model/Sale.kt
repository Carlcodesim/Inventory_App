package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sales",
    indices = [
        Index(value = ["storeId"]),
        Index(value = ["productId"])
    ]
)
data class Sale(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val storeId: Long,
    val productId: Long,
    val productName: String,
    val quantity: Int,
    val unitCost: Double,
    val unitPrice: Double,
    val totalAmount: Double = unitPrice * quantity,
    val netProfit: Double = (unitPrice - unitCost) * quantity,
    val customerNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
