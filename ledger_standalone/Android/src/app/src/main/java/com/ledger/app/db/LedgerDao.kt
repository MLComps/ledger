package com.ledger.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
  @Query("SELECT * FROM transactions ORDER BY timestampMs DESC")
  fun getAllTransactions(): Flow<List<TransactionEntity>>

  @Insert
  suspend fun insertTransaction(t: TransactionEntity)

  @Query("SELECT * FROM stock")
  fun getAllStock(): Flow<List<StockEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertStock(s: StockEntity)

  @Query("DELETE FROM transactions WHERE timestampMs = :timestampMs")
  suspend fun deleteTransactionByTimestamp(timestampMs: Long)

  @Query("DELETE FROM transactions")
  suspend fun clearTransactions()

  @Query("DELETE FROM stock")
  suspend fun clearStock()

  @Query("SELECT * FROM transactions WHERE timestampMs >= :sinceMs ORDER BY timestampMs ASC")
  fun getTransactionsSince(sinceMs: Long): Flow<List<TransactionEntity>>
}
