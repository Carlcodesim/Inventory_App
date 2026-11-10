package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.MovementType
import com.example.data.model.Product
import com.example.data.model.Sale
import com.example.data.model.StockMovement
import com.example.data.model.Store

class Converters {
    @TypeConverter
    fun fromMovementType(value: MovementType): String = value.name

    @TypeConverter
    fun toMovementType(value: String): MovementType = try {
        MovementType.valueOf(value)
    } catch (_: Exception) {
        MovementType.AJUSTE_POSITIVO
    }
}

@Database(
    entities = [
        Store::class,
        Product::class,
        Sale::class,
        StockMovement::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun kardexDao(): KardexDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tienda_pos_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
