package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface KardexDao {
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC")
    fun getAllMovements(): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE storeId = :storeId ORDER BY timestamp DESC")
    fun getMovementsByStore(storeId: Long): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC")
    fun getMovementsByProduct(productId: Long): Flow<List<StockMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovement): Long

    @Query("SELECT COUNT(*) FROM stock_movements")
    suspend fun countMovements(): Int
}
