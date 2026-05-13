package com.ledger.app.ui.modelsetup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.data.Model
import com.ledger.app.data.ModelDownloadStatus
import com.ledger.app.data.ModelDownloadStatusType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(
  onModelReady: (Model) -> Unit,
  viewModel: ModelSetupViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val tintColor = MaterialTheme.colorScheme.primary

  Scaffold { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(48.dp))

      Icon(
        imageVector = Icons.Rounded.AccountBalanceWallet,
        contentDescription = "Ledger",
        tint = tintColor,
        modifier = Modifier.size(72.dp),
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Ledger",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = tintColor,
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = "Your offline AI financial assistant",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(32.dp))

      Text(
        text = "Select a model to get started",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
      )

      Spacer(modifier = Modifier.height(16.dp))

      when {
        uiState.loading -> {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = tintColor)
          }
        }
        uiState.error.isNotEmpty() -> {
          Text(
            text = uiState.error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(onClick = { viewModel.loadModels() }) {
            Text("Retry")
          }
        }
        else -> {
          LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(uiState.models) { model ->
              val status = uiState.downloadStatus[model.name]
                ?: ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
              ModelCard(
                model = model,
                downloadStatus = status,
                onDownload = { viewModel.downloadModel(model) },
                onCancel = { viewModel.cancelDownload(model) },
                onDelete = { viewModel.deleteModel(model) },
                onStart = { onModelReady(model) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ModelCard(
  model: Model,
  downloadStatus: ModelDownloadStatus,
  onDownload: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onStart: () -> Unit,
) {
  val tintColor = MaterialTheme.colorScheme.primary

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = model.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
          )
          Text(
            text = formatBytes(model.sizeInBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        when (downloadStatus.status) {
          ModelDownloadStatusType.SUCCEEDED -> {
            IconButton(onClick = onDelete) {
              Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
              )
            }
          }
          else -> {}
        }
      }

      if (model.info.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = model.info,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      when (downloadStatus.status) {
        ModelDownloadStatusType.NOT_DOWNLOADED, ModelDownloadStatusType.FAILED -> {
          if (downloadStatus.status == ModelDownloadStatusType.FAILED) {
            Text(
              text = "Download failed. Tap to retry.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
          }
          Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = tintColor),
          ) {
            Icon(
              imageVector = Icons.Rounded.CloudDownload,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download")
          }
        }

        ModelDownloadStatusType.IN_PROGRESS,
        ModelDownloadStatusType.PARTIALLY_DOWNLOADED,
        ModelDownloadStatusType.UNZIPPING -> {
          val progress = if (downloadStatus.totalBytes > 0) {
            downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes
          } else 0f
          val received = formatBytes(downloadStatus.receivedBytes)
          val total = formatBytes(downloadStatus.totalBytes)

          Text(
            text = "Downloading... $received / $total",
            style = MaterialTheme.typography.bodySmall,
          )
          Spacer(modifier = Modifier.height(4.dp))
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = tintColor,
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
          }
        }

        ModelDownloadStatusType.SUCCEEDED -> {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CheckCircle,
              contentDescription = null,
              tint = tintColor,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Ready",
              style = MaterialTheme.typography.bodySmall,
              color = tintColor,
            )
          }
          Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = tintColor),
          ) {
            Text("Start Ledger")
          }
        }
      }
    }
  }
}

private fun formatBytes(bytes: Long): String {
  return when {
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
  }
}
