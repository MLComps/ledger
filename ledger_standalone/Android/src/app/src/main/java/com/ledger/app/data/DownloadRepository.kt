package com.ledger.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ledger.app.worker.DownloadWorker
import java.util.UUID
import java.util.concurrent.Executors

private const val TAG = "LedgerDownloadRepo"
private const val MODEL_NAME_TAG = "modelName"

interface DownloadRepository {
  fun downloadModel(
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  )

  fun cancelDownloadModel(model: Model)

  fun cancelAll(onComplete: () -> Unit)

  fun observerWorkerProgress(
    workerId: UUID,
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  )
}

class DefaultDownloadRepository(
  private val context: Context,
) : DownloadRepository {
  private val workManager = WorkManager.getInstance(context)
  private val downloadStartTimeSharedPreferences =
    context.getSharedPreferences("download_start_time_ms", Context.MODE_PRIVATE)

  override fun downloadModel(
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  ) {
    val builder = Data.Builder()
    val totalBytes = model.totalBytes + model.extraDataFiles.sumOf { it.sizeInBytes }
    val inputDataBuilder =
      builder
        .putString(KEY_MODEL_NAME, model.name)
        .putString(KEY_MODEL_URL, model.url)
        .putString(KEY_MODEL_COMMIT_HASH, model.version)
        .putString(KEY_MODEL_DOWNLOAD_MODEL_DIR, model.normalizedName)
        .putString(KEY_MODEL_DOWNLOAD_FILE_NAME, model.downloadFileName)
        .putBoolean(KEY_MODEL_IS_ZIP, model.isZip)
        .putString(KEY_MODEL_UNZIPPED_DIR, model.unzipDir)
        .putLong(KEY_MODEL_TOTAL_BYTES, totalBytes)

    if (model.extraDataFiles.isNotEmpty()) {
      inputDataBuilder
        .putString(KEY_MODEL_EXTRA_DATA_URLS, model.extraDataFiles.joinToString(",") { it.url })
        .putString(
          KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES,
          model.extraDataFiles.joinToString(",") { it.downloadFileName },
        )
    }
    if (model.accessToken != null) {
      inputDataBuilder.putString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN, model.accessToken)
    }
    val inputData = inputDataBuilder.build()

    val downloadWorkRequest =
      OneTimeWorkRequestBuilder<DownloadWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(inputData)
        .addTag("$MODEL_NAME_TAG:${model.name}")
        .build()

    val workerId = downloadWorkRequest.id

    workManager.enqueueUniqueWork(model.name, ExistingWorkPolicy.REPLACE, downloadWorkRequest)

    observerWorkerProgress(
      workerId = workerId,
      model = model,
      onStatusUpdated = onStatusUpdated,
    )
  }

  override fun cancelDownloadModel(model: Model) {
    workManager.cancelAllWorkByTag("$MODEL_NAME_TAG:${model.name}")
  }

  override fun cancelAll(onComplete: () -> Unit) {
    workManager
      .cancelAllWork()
      .result
      .addListener({ onComplete() }, Executors.newSingleThreadExecutor())
  }

  override fun observerWorkerProgress(
    workerId: UUID,
    model: Model,
    onStatusUpdated: (model: Model, status: ModelDownloadStatus) -> Unit,
  ) {
    workManager.getWorkInfoByIdLiveData(workerId).observeForever { workInfo ->
      if (workInfo != null) {
        when (workInfo.state) {
          WorkInfo.State.ENQUEUED -> {
            downloadStartTimeSharedPreferences.edit {
              putLong(model.name, System.currentTimeMillis())
            }
          }

          WorkInfo.State.RUNNING -> {
            val receivedBytes = workInfo.progress.getLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, 0L)
            val downloadRate = workInfo.progress.getLong(KEY_MODEL_DOWNLOAD_RATE, 0L)
            val remainingSeconds = workInfo.progress.getLong(KEY_MODEL_DOWNLOAD_REMAINING_MS, 0L)
            val startUnzipping = workInfo.progress.getBoolean(KEY_MODEL_START_UNZIPPING, false)

            if (!startUnzipping) {
              if (receivedBytes != 0L) {
                onStatusUpdated(
                  model,
                  ModelDownloadStatus(
                    status = ModelDownloadStatusType.IN_PROGRESS,
                    totalBytes = model.totalBytes,
                    receivedBytes = receivedBytes,
                    bytesPerSecond = downloadRate,
                    remainingMs = remainingSeconds,
                  ),
                )
              }
            } else {
              onStatusUpdated(
                model,
                ModelDownloadStatus(status = ModelDownloadStatusType.UNZIPPING),
              )
            }
          }

          WorkInfo.State.SUCCEEDED -> {
            Log.d(TAG, "worker $workerId success")
            onStatusUpdated(model, ModelDownloadStatus(status = ModelDownloadStatusType.SUCCEEDED))
            sendNotification(
              title = context.getString(com.ledger.app.R.string.notification_title_success),
              text = context.getString(com.ledger.app.R.string.notification_content_success).format(model.name),
            )
            downloadStartTimeSharedPreferences.edit { remove(model.name) }
          }

          WorkInfo.State.FAILED,
          WorkInfo.State.CANCELLED -> {
            var status = ModelDownloadStatusType.FAILED
            val errorMessage = workInfo.outputData.getString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE) ?: ""
            Log.d(TAG, "worker $workerId FAILED or CANCELLED: $errorMessage")
            if (workInfo.state == WorkInfo.State.CANCELLED) {
              status = ModelDownloadStatusType.NOT_DOWNLOADED
            } else {
              sendNotification(
                title = context.getString(com.ledger.app.R.string.notification_title_fail),
                text = context.getString(com.ledger.app.R.string.notification_content_fail).format(model.name),
              )
            }
            onStatusUpdated(
              model,
              ModelDownloadStatus(status = status, errorMessage = errorMessage),
            )
            downloadStartTimeSharedPreferences.edit { remove(model.name) }
          }

          else -> {}
        }
      }
    }
  }

  private fun sendNotification(title: String, text: String) {
    val channelId = "ledger_download_notification"
    val channelName = "Ledger download notification"

    val importance = NotificationManager.IMPORTANCE_HIGH
    val channel = NotificationChannel(channelId, channelName, importance)
    val notificationManager: NotificationManager =
      context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.createNotificationChannel(channel)

    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!

    val pendingIntent: PendingIntent =
      PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

    val builder =
      NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    with(NotificationManagerCompat.from(context)) {
      if (
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
          PackageManager.PERMISSION_GRANTED
      ) {
        return
      }
      notify(1, builder.build())
    }
  }
}
