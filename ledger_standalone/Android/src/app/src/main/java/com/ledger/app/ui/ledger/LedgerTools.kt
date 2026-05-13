package com.ledger.app.ui.ledger

import android.util.Log
import com.ledger.app.db.LedgerRepository
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val TAG = "LedgerTools"

data class LedgerEntry(
  val item: String,
  val amount: Double,
  val currency: String,
  val transactionType: String,
  val cost: Double,
  val quantity: Double,
  val unit: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class LedgerStockItem(val quantity: Double, val unit: String)

class LedgerTools(
  val onStateChanged: () -> Unit,
  private val ledgerRepository: LedgerRepository? = null,
  private val coroutineScope: CoroutineScope? = null,
) : ToolSet {
  val entries: MutableList<LedgerEntry> = Collections.synchronizedList(mutableListOf())
  val stock: MutableMap<String, LedgerStockItem> = Collections.synchronizedMap(mutableMapOf())

  @Tool(description = "Record a sale, purchase, income, or expense in the ledger.")
  fun addTransaction(
    @ToolParam(description = "Product or service name") item: String,
    @ToolParam(description = "Total transaction amount") amount: Double,
    @ToolParam(description = "Currency code e.g. KES, RWF, GHS") currency: String,
    @ToolParam(description = "Type: sale, purchase, expense, or income") transactionType: String,
    @ToolParam(description = "Cost of goods (0 if not applicable)") cost: Double = 0.0,
    @ToolParam(description = "Quantity") quantity: Double = 1.0,
    @ToolParam(description = "Unit e.g. kg, pieces, sack") unit: String = "unit",
  ): Map<String, Any> {
    Log.d(TAG, "addTransaction: item=$item amount=$amount currency=$currency type=$transactionType")
    val ts = System.currentTimeMillis()
    val entry =
      LedgerEntry(
        item = item,
        amount = amount,
        currency = currency,
        transactionType = transactionType.lowercase(),
        cost = cost,
        quantity = quantity,
        unit = unit,
        timestampMs = ts,
      )
    entries.add(entry)

    // Persist to Room asynchronously
    coroutineScope?.launch {
      ledgerRepository?.addTransaction(
        item = item,
        amount = amount,
        currency = currency,
        transactionType = transactionType.lowercase(),
        cost = cost,
        quantity = quantity,
        unit = unit,
        timestampMs = ts,
      )
    }

    onStateChanged()
    return mapOf(
      "status" to "ok",
      "ledger_size" to entries.size,
      "item" to item,
      "amount" to amount,
      "currency" to currency,
      "transaction_type" to transactionType.lowercase(),
    )
  }

  @Tool(
    description =
      "Adjust stock quantity for an item. Use positive delta to restock, negative when items are sold or used."
  )
  fun updateStock(
    @ToolParam(description = "Item name") item: String,
    @ToolParam(description = "Change in quantity (positive = add, negative = remove)")
    quantityDelta: Double,
    @ToolParam(description = "Unit of measurement") unit: String = "unit",
  ): Map<String, Any> {
    Log.d(TAG, "updateStock: item=$item delta=$quantityDelta unit=$unit")
    val current = stock.getOrDefault(item, LedgerStockItem(0.0, unit))
    val updated = LedgerStockItem(quantity = current.quantity + quantityDelta, unit = unit)
    stock[item] = updated

    // Persist to Room asynchronously
    coroutineScope?.launch {
      ledgerRepository?.upsertStock(item = item, quantityDelta = quantityDelta, unit = unit)
    }

    onStateChanged()
    return mapOf(
      "status" to "ok",
      "item" to item,
      "quantity" to updated.quantity,
      "unit" to updated.unit,
    )
  }

  @Tool(description = "Get today's total revenue, cost, net profit, and transaction count.")
  fun getFinancialHealth(): Map<String, Any> {
    Log.d(TAG, "getFinancialHealth")
    val revenue =
      entries
        .filter { it.transactionType == "sale" || it.transactionType == "income" }
        .sumOf { it.amount }
    val cogs = entries.sumOf { it.cost }
    val purchases =
      entries
        .filter { it.transactionType == "purchase" || it.transactionType == "expense" }
        .sumOf { it.amount }
    val totalCost = cogs + purchases
    return mapOf(
      "total_revenue" to revenue,
      "total_cost" to totalCost,
      "net_profit" to revenue - totalCost,
      "transaction_count" to entries.size,
      "stock_items" to
        stock.map { (k, v) ->
          mapOf("item" to k, "quantity" to v.quantity, "unit" to v.unit)
        },
    )
  }

  fun deleteTransaction(timestampMs: Long) {
    synchronized(entries) { entries.removeIf { it.timestampMs == timestampMs } }
    coroutineScope?.launch {
      ledgerRepository?.deleteByTimestamp(timestampMs)
    }
    onStateChanged()
  }

  fun clear() {
    entries.clear()
    stock.clear()
    coroutineScope?.launch {
      ledgerRepository?.clear()
    }
  }
}
