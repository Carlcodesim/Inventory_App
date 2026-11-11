package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.CatalogItem
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT name FROM catalog_items WHERE type = 'CATEGORY' ORDER BY name COLLATE NOCASE ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT name FROM catalog_items WHERE type = 'SUPPLIER' ORDER BY name COLLATE NOCASE ASC")
    fun getSuppliers(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CatalogItem): Long

    @Query("DELETE FROM catalog_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
