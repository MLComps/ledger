package com.ledger.app.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object DailyDigestScheduler {

  private const val WORK_TAG = "ledger_daily_digest"

  fun schedule(context: Context, hourOfDay: Int = 18) {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, hourOfDay)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    val initialDelayMs = target.timeInMillis - now.timeInMillis

    val request = PeriodicWorkRequestBuilder<DailyDigestWorker>(24, TimeUnit.HOURS)
      .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
      .addTag(WORK_TAG)
      .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      WORK_TAG,
      ExistingPeriodicWorkPolicy.KEEP,
      request,
    )
  }
}
