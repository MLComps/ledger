package com.ledger.app.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class LedgerRepository(private val dao: LedgerDao) {
  val transactions: Flow<List<TransactionEntity>> = dao.getAllTransactions()
  val stock: Flow<List<StockEntity>> = dao.getAllStock()

  suspend fun addTransaction(
    item: String,
    amount: Double,
    currency: String,
    transactionType: String,
    cost: Double,
    quantity: Double,
    unit: String,
    confidence: String = "high",
    timestampMs: Long = System.currentTimeMillis(),
  ) = dao.insertTransaction(
    TransactionEntity(
      item = item,
      amount = amount,
      currency = currency,
      transactionType = transactionType,
      cost = cost,
      quantity = quantity,
      unit = unit,
      confidence = confidence,
      timestampMs = timestampMs,
    )
  )

  suspend fun deleteByTimestamp(timestampMs: Long) = dao.deleteTransactionByTimestamp(timestampMs)

  suspend fun upsertStock(item: String, quantityDelta: Double, unit: String) {
    val existing = dao.getAllStock().first().find { it.item == item }
    dao.upsertStock(
      StockEntity(
        item = item,
        quantity = (existing?.quantity ?: 0.0) + quantityDelta,
        unit = unit,
      )
    )
  }

  suspend fun clear() {
    dao.clearTransactions()
    dao.clearStock()
  }
}
