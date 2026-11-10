package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val logoUrl: String = "",
    val address: String = "",
    val phone: String = "",
    val colorHex: String = "#0284C7",
    val createdAt: Long = System.currentTimeMillis()
)
