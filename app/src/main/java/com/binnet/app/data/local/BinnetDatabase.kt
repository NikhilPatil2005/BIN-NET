package com.binnet.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.binnet.app.data.local.dao.TransactionDao
import com.binnet.app.data.local.entity.TransactionEntity

/**
 * BinnetDatabase - Room database for BIN-NET app
 * Stores transaction history locally for offline-first functionality
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BinnetDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: BinnetDatabase? = null

        fun getDatabase(context: Context): BinnetDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BinnetDatabase::class.java,
                    "binnet_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
