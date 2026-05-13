package com.ledger.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class, StockEntity::class], version = 1)
abstract class LedgerDatabase : RoomDatabase() {
  abstract fun ledgerDao(): LedgerDao

  companion object {
    @Volatile
    private var INSTANCE: LedgerDatabase? = null

    fun getInstance(context: Context): LedgerDatabase =
      INSTANCE ?: synchronized(this) {
        Room.databaseBuilder(context, LedgerDatabase::class.java, "ledger_db")
          .build()
          .also { INSTANCE = it }
      }
  }
}
