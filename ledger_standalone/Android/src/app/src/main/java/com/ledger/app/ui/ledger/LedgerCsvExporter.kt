package com.ledger.app.ui.ledger

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LedgerCsvExporter {

  fun export(context: Context, state: LedgerUiState): Uri {
    val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
    val file = File(dir, "ledger_report_$ts.csv")

    file.bufferedWriter().use { w ->
      // Transactions section
      w.write("Transactions\n")
      w.write("Timestamp,Item,Type,Amount,Currency,Quantity,Unit,Cost\n")
      val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
      for (tx in state.recentTransactions) {
        w.write(
          "${dateFmt.format(Date(tx.timestampMs))}," +
            "${tx.item.escapeCsv()}," +
            "${tx.transactionType}," +
            "%.2f,".format(tx.amount) +
            "${tx.currency}," +
            "${fmtCsvQty(tx.quantity)}," +
            "${tx.unit.escapeCsv()}," +
            "%.2f\n".format(tx.cost)
        )
      }

      w.write("\n")

      // Summary section
      w.write("Summary\n")
      w.write("Revenue,%.2f\n".format(state.revenue))
      w.write("Total Cost,%.2f\n".format(state.totalCost))
      w.write("Net Profit,%.2f\n".format(state.netProfit))
      w.write("Transaction Count,${state.transactionCount}\n")

      w.write("\n")

      // Inventory section
      w.write("Inventory\n")
      w.write("Item,Quantity,Unit,Status\n")
      for ((name, stock) in state.stockItemNames.zip(state.stockItems)) {
        val status = if (name in state.lowStockItems) "LOW STOCK" else "OK"
        w.write("${name.escapeCsv()},${fmtCsvQty(stock.quantity)},${stock.unit.escapeCsv()},$status\n")
      }
    }

    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
  }

  private fun String.escapeCsv(): String {
    return if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this
  }

  private fun fmtCsvQty(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else "%.3f".format(v)
}
