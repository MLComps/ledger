package com.ledger.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ledger.app.data.KEY_MODEL_COMMIT_HASH
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_ACCESS_TOKEN
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_ERROR_MESSAGE
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_FILE_NAME
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_MODEL_DIR
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_RATE
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_RECEIVED_BYTES
import com.ledger.app.data.KEY_MODEL_DOWNLOAD_REMAINING_MS
import com.ledger.app.data.KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES
import com.ledger.app.data.KEY_MODEL_EXTRA_DATA_URLS
import com.ledger.app.data.KEY_MODEL_IS_ZIP
import com.ledger.app.data.KEY_MODEL_NAME
import com.ledger.app.data.KEY_MODEL_START_UNZIPPING
import com.ledger.app.data.KEY_MODEL_TOTAL_BYTES
import com.ledger.app.data.KEY_MODEL_UNZIPPED_DIR
import com.ledger.app.data.KEY_MODEL_URL
import com.ledger.app.data.TMP_FILE_EXT
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LedgerDownloadWorker"

data class UrlAndFileName(val url: String, val fileName: String)

private const val FOREGROUND_NOTIFICATION_CHANNEL_ID = "ledger_model_download_channel"
private var channelCreated = false

class DownloadWorker(context: Context, params: WorkerParameters) :
  CoroutineWorker(context, params) {
  private val externalFilesDir = context.getExternalFilesDir(null)
  private val notificationManager =
    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  private val notificationId: Int = params.id.hashCode()

  init {
    if (!channelCreated) {
      val channel = NotificationChannel(
        FOREGROUND_NOTIFICATION_CHANNEL_ID,
        "Model Downloading",
        NotificationManager.IMPORTANCE_LOW,
      ).apply { description = "Notifications for model downloading" }
      notificationManager.createNotificationChannel(channel)
      channelCreated = true
    }
  }

  override suspend fun doWork(): Result {
    val fileUrl = inputData.getString(KEY_MODEL_URL)
    val modelName = inputData.getString(KEY_MODEL_NAME) ?: "Model"
    val version = inputData.getString(KEY_MODEL_COMMIT_HASH)!!
    val fileName = inputData.getString(KEY_MODEL_DOWNLOAD_FILE_NAME)
    val modelDir = inputData.getString(KEY_MODEL_DOWNLOAD_MODEL_DIR)!!
    val isZip = inputData.getBoolean(KEY_MODEL_IS_ZIP, false)
    val unzippedDir = inputData.getString(KEY_MODEL_UNZIPPED_DIR)
    val extraDataFileUrls = inputData.getString(KEY_MODEL_EXTRA_DATA_URLS)?.split(",") ?: listOf()
    val extraDataFileNames =
      inputData.getString(KEY_MODEL_EXTRA_DATA_DOWNLOAD_FILE_NAMES)?.split(",") ?: listOf()
    val totalBytes = inputData.getLong(KEY_MODEL_TOTAL_BYTES, 0L)
    val accessToken = inputData.getString(KEY_MODEL_DOWNLOAD_ACCESS_TOKEN)

    return withContext(Dispatchers.IO) {
      if (fileUrl == null || fileName == null) {
        Result.failure()
      } else {
        return@withContext try {
          setForeground(createForegroundInfo(progress = 0, modelName = modelName))

          val allFiles: MutableList<UrlAndFileName> = mutableListOf()
          allFiles.add(UrlAndFileName(url = fileUrl, fileName = fileName))
          for (index in extraDataFileUrls.indices) {
            allFiles.add(
              UrlAndFileName(url = extraDataFileUrls[index], fileName = extraDataFileNames[index])
            )
          }

          var downloadedBytes = 0L
          val bytesReadSizeBuffer: MutableList<Long> = mutableListOf()
          val bytesReadLatencyBuffer: MutableList<Long> = mutableListOf()

          for (file in allFiles) {
            val url = URL(file.url)
            val connection = url.openConnection() as HttpURLConnection
            if (accessToken != null) {
              connection.setRequestProperty("Authorization", "Bearer $accessToken")
            }

            val outputDir = File(
              applicationContext.getExternalFilesDir(null),
              listOf(modelDir, version).joinToString(separator = File.separator),
            )
            if (!outputDir.exists()) outputDir.mkdirs()

            val outputTmpFile = File(
              applicationContext.getExternalFilesDir(null),
              listOf(modelDir, version, "${file.fileName}.$TMP_FILE_EXT")
                .joinToString(separator = File.separator),
            )
            val existingBytes = outputTmpFile.length()
            if (existingBytes > 0) {
              connection.setRequestProperty("Range", "bytes=${existingBytes}-")
              connection.setRequestProperty("Accept-Encoding", "identity")
            }
            connection.connect()

            val responseCode = connection.responseCode
            val resuming = responseCode == HttpURLConnection.HTTP_PARTIAL
            when {
              resuming -> {
                // Server honoured Range — append to existing partial file
                downloadedBytes += existingBytes
              }
              responseCode == HttpURLConnection.HTTP_OK -> {
                // Server ignored Range and sent full file — discard partial, start fresh
                if (outputTmpFile.exists()) outputTmpFile.delete()
                downloadedBytes = 0L
              }
              else -> throw IOException("HTTP error code: $responseCode")
            }

            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(outputTmpFile, resuming)

            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            var lastSetProgressTs: Long = 0
            var deltaBytes = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
              outputStream.write(buffer, 0, bytesRead)
              downloadedBytes += bytesRead
              deltaBytes += bytesRead

              val curTs = System.currentTimeMillis()
              if (curTs - lastSetProgressTs > 200) {
                var bytesPerMs = 0f
                if (lastSetProgressTs != 0L) {
                  if (bytesReadSizeBuffer.size == 5) bytesReadSizeBuffer.removeAt(0)
                  bytesReadSizeBuffer.add(deltaBytes)
                  if (bytesReadLatencyBuffer.size == 5) bytesReadLatencyBuffer.removeAt(0)
                  bytesReadLatencyBuffer.add(curTs - lastSetProgressTs)
                  deltaBytes = 0L
                  bytesPerMs = bytesReadSizeBuffer.sum().toFloat() / bytesReadLatencyBuffer.sum()
                }

                var remainingMs = 0f
                if (bytesPerMs > 0f && totalBytes > 0L) {
                  remainingMs = (totalBytes - downloadedBytes) / bytesPerMs
                }

                setProgress(
                  Data.Builder()
                    .putLong(KEY_MODEL_DOWNLOAD_RECEIVED_BYTES, downloadedBytes)
                    .putLong(KEY_MODEL_DOWNLOAD_RATE, (bytesPerMs * 1000).toLong())
                    .putLong(KEY_MODEL_DOWNLOAD_REMAINING_MS, remainingMs.toLong())
                    .build()
                )
                if (totalBytes > 0) {
                  setForeground(
                    createForegroundInfo(
                      progress = (downloadedBytes * 100 / totalBytes).toInt(),
                      modelName = modelName,
                    )
                  )
                }
                lastSetProgressTs = curTs
              }
            }

            outputStream.close()
            inputStream.close()

            val originalFilePath = outputTmpFile.absolutePath.replace(".$TMP_FILE_EXT", "")
            val originalFile = File(originalFilePath)
            if (originalFile.exists()) originalFile.delete()
            outputTmpFile.renameTo(originalFile)
            Log.d(TAG, "Download done: $fileName")

            if (isZip && unzippedDir != null) {
              setProgress(Data.Builder().putBoolean(KEY_MODEL_START_UNZIPPING, true).build())

              val destDir = File(
                externalFilesDir,
                listOf(modelDir, version, unzippedDir).joinToString(File.separator),
              )
              if (!destDir.exists()) destDir.mkdirs()

              val unzipBuffer = ByteArray(4096)
              val zipFilePath =
                "${externalFilesDir}${File.separator}$modelDir${File.separator}$version${File.separator}${fileName}"
              val zipIn = ZipInputStream(BufferedInputStream(FileInputStream(zipFilePath)))
              var zipEntry: ZipEntry? = zipIn.nextEntry

              while (zipEntry != null) {
                val filePath = destDir.absolutePath + File.separator + zipEntry.name
                if (!zipEntry.isDirectory) {
                  val bos = FileOutputStream(filePath)
                  bos.use { curBos ->
                    var len: Int
                    while (zipIn.read(unzipBuffer).also { len = it } > 0) {
                      curBos.write(unzipBuffer, 0, len)
                    }
                  }
                } else {
                  File(filePath).mkdirs()
                }
                zipIn.closeEntry()
                zipEntry = zipIn.nextEntry
              }
              zipIn.close()
              File(zipFilePath).delete()
            }
          }
          Result.success()
        } catch (e: IOException) {
          Log.e(TAG, e.message, e)
          Result.failure(
            Data.Builder().putString(KEY_MODEL_DOWNLOAD_ERROR_MESSAGE, e.message).build()
          )
        }
      }
    }
  }

  override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

  private fun createForegroundInfo(progress: Int, modelName: String? = null): ForegroundInfo {
    val title = if (modelName != null) "Downloading \"$modelName\"" else "Downloading model"
    val content = "Download in progress: $progress%"

    val intent = applicationContext.packageManager
      .getLaunchIntentForPackage(applicationContext.packageName)
      ?.apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
      ?: Intent()

    val pendingIntent = PendingIntent.getActivity(
      applicationContext, 0, intent,
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    val notification = NotificationCompat.Builder(applicationContext, FOREGROUND_NOTIFICATION_CHANNEL_ID)
      .setContentTitle(title)
      .setContentText(content)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setOngoing(true)
      .setProgress(100, progress, false)
      .setContentIntent(pendingIntent)
      .build()

    return ForegroundInfo(
      notificationId,
      notification,
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }
}
