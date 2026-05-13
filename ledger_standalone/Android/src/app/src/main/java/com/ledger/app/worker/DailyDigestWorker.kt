package com.ledger.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ledger.app.db.LedgerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

private const val TAG = "DailyDigestWorker"
private const val CHANNEL_ID = "ledger_daily_digest"
private const val NOTIFICATION_ID = 1001

class DailyDigestWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {

  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    try {
      val db = LedgerDatabase.getInstance(applicationContext)
      val allTransactions = db.ledgerDao().getAllTransactions().first()

      val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
      }.timeInMillis

      val todayTxns = allTransactions.filter { it.timestampMs >= todayStart }
      val revenue = todayTxns.filter { it.transactionType == "sale" || it.transactionType == "income" }.sumOf { it.amount }
      val cost = todayTxns.sumOf { it.cost }
      val purchases = todayTxns.filter { it.transactionType == "purchase" || it.transactionType == "expense" }.sumOf { it.amount }
      val profit = revenue - cost - purchases

      val stock = db.ledgerDao().getAllStock().first()
      val lowStockCount = stock.count { it.quantity <= 5.0 }

      val summary = buildSummary(revenue, profit, todayTxns.size, lowStockCount)
      postNotification(summary)

      Result.success()
    } catch (e: Exception) {
      Log.e(TAG, "Daily digest failed", e)
      Result.failure()
    }
  }

  private fun buildSummary(revenue: Double, profit: Double, txnCount: Int, lowStockCount: Int): String {
    if (txnCount == 0) return "No transactions recorded today. Open Ledger to get started."
    val sign = if (profit >= 0) "+" else ""
    val sb = StringBuilder()
    sb.append("Revenue: ${fmt(revenue)}  |  Profit: $sign${fmt(profit)}  |  $txnCount txn${if (txnCount != 1) "s" else ""}")
    if (lowStockCount > 0) sb.append("  |  ⚠ $lowStockCount low stock")
    return sb.toString()
  }

  private fun fmt(v: Double): String =
    if (v >= 1_000_000) String.format(Locale.US, "%.1fM", v / 1_000_000)
    else if (v >= 1_000) String.format(Locale.US, "%.1fK", v / 1_000)
    else String.format(Locale.US, "%.0f", v)

  private fun postNotification(summary: String) {
    val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(CHANNEL_ID, "Daily Ledger Digest", NotificationManager.IMPORTANCE_DEFAULT).apply {
      description = "End-of-day financial summary"
    }
    manager.createNotificationChannel(channel)

    val launchIntent = applicationContext.packageManager
      .getLaunchIntentForPackage(applicationContext.packageName)
      ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP } ?: Intent()
    val pendingIntent = PendingIntent.getActivity(
      applicationContext, 0, launchIntent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setContentTitle("Ledger — Daily Summary")
      .setContentText(summary)
      .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .build()

    manager.notify(NOTIFICATION_ID, notification)
  }
}
