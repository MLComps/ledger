package com.ledger.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TransactionEntity::class, StockEntity::class], version = 2)
abstract class LedgerDatabase : RoomDatabase() {
  abstract fun ledgerDao(): LedgerDao

  companion object {
    @Volatile
    private var INSTANCE: LedgerDatabase? = null

    private val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN confidence TEXT NOT NULL DEFAULT 'high'")
      }
    }

    fun getInstance(context: Context): LedgerDatabase =
      INSTANCE ?: synchronized(this) {
        Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger_db")
          .addMigrations(MIGRATION_1_2)
          .build()
          .also { INSTANCE = it }
      }
  }
}
