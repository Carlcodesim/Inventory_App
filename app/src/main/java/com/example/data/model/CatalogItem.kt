package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_items", indices = [Index(value = ["type", "name"], unique = true)])
data class CatalogItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // CATEGORY or SUPPLIER
    val type: String
)
