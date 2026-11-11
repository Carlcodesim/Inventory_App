package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.CatalogItem
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
        StockMovement::class,
        CatalogItem::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun storeDao(): StoreDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun kardexDao(): KardexDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS catalog_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, type TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_catalog_items_type_name ON catalog_items(type, name)")
                // Preserve existing product categories and suppliers in the new independent catalogs.
                db.execSQL("INSERT OR IGNORE INTO catalog_items (name, type) SELECT DISTINCT TRIM(category), 'CATEGORY' FROM products WHERE TRIM(category) != ''")
                db.execSQL("INSERT OR IGNORE INTO catalog_items (name, type) SELECT DISTINCT TRIM(supplier), 'SUPPLIER' FROM products WHERE TRIM(supplier) != '' AND LOWER(TRIM(supplier)) != 'general'")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tienda_pos_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
