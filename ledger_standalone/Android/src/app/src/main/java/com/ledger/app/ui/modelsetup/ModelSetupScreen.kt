package com.ledger.app.ui.modelsetup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.data.Model
import com.ledger.app.data.ModelDownloadStatus
import com.ledger.app.data.ModelDownloadStatusType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSetupScreen(
  onModelReady: (Model) -> Unit,
  viewModel: ModelSetupViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val authResultLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    viewModel.handleAuthResult(result) { success, error ->
      if (!success) scope.launch { snackbarHostState.showSnackbar(error ?: "Login failed") }
    }
  }

  Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(bottom = 32.dp),
    ) {
      item {
        HeroSection()
      }

      item {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
          Spacer(Modifier.height(24.dp))
          Text(
            "Choose a model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
          )
          Text(
            "Download once, runs fully offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(16.dp))
        }
      }

      when {
        uiState.loading -> item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(48.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.primary,
              strokeWidth = 3.dp,
              modifier = Modifier.size(36.dp),
            )
          }
        }
        uiState.error.isNotEmpty() -> item {
          Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(uiState.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.loadModels() }) { Text("Retry") }
          }
        }
        else -> itemsIndexed(uiState.models) { index, model ->
          val status = uiState.downloadStatus[model.name]
            ?: ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
          var visible by remember { mutableStateOf(false) }
          LaunchedEffect(model.name) { visible = true }
          AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220, delayMillis = index * 80)) +
              slideInVertically(tween(220, delayMillis = index * 80)) { it / 3 },
          ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
              ModelCard(
                model = model,
                downloadStatus = status,
                onDownload = { viewModel.downloadModel(model) },
                onResume = { viewModel.downloadModel(model) },
                onCancel = { viewModel.cancelDownload(model) },
                onDiscard = { viewModel.discardPartialDownload(model) },
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
private fun HeroSection() {
  var entered by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { entered = true }

  val iconScale by animateFloatAsState(
    targetValue = if (entered) 1f else 0.5f,
    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    label = "icon_scale",
  )
  val textAlpha by animateFloatAsState(
    targetValue = if (entered) 1f else 0f,
    animationSpec = tween(500, delayMillis = 150, easing = FastOutSlowInEasing),
    label = "text_alpha",
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.background,
          ),
          startY = 0f,
          endY = Float.POSITIVE_INFINITY,
        )
      )
      .padding(horizontal = 24.dp, vertical = 48.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Animated icon badge
      Box(
        modifier = Modifier
          .scale(iconScale)
          .size(88.dp)
          .clip(RoundedCornerShape(24.dp))
          .background(Color.White.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.TrendingUp,
          contentDescription = null,
          modifier = Modifier.size(48.dp),
          tint = Color.White,
        )
      }

      Spacer(Modifier.height(4.dp))

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsAlpha(textAlpha),
      ) {
        Text(
          "Ledger",
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
        )
        Text(
          "AI Bookkeeping · 100% Offline",
          style = MaterialTheme.typography.bodyMedium,
          color = Color.White.copy(alpha = 0.80f),
          textAlign = TextAlign.Center,
        )
      }

      Spacer(Modifier.height(4.dp))

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeaturePill("Voice")
        FeaturePill("Camera")
        FeaturePill("SMS")
      }
    }
  }
}

@Composable
private fun FeaturePill(label: String) {
  Box(
    modifier = Modifier
      .clip(CircleShape)
      .background(Color.White.copy(alpha = 0.20f))
      .padding(horizontal = 14.dp, vertical = 5.dp),
  ) {
    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
  }
}

private fun Modifier.graphicsAlpha(a: Float): Modifier = this.alpha(a)

@Composable
private fun ModelCard(
  model: Model,
  downloadStatus: ModelDownloadStatus,
  onDownload: () -> Unit,
  onResume: () -> Unit,
  onCancel: () -> Unit,
  onDiscard: () -> Unit,
  onDelete: () -> Unit,
  onStart: () -> Unit,
) {
  val isReady = downloadStatus.status == ModelDownloadStatusType.SUCCEEDED

  ElevatedCard(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.elevatedCardColors(
      containerColor = if (isReady) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
      else MaterialTheme.colorScheme.surfaceVariant,
    ),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isReady) 3.dp else 1.dp),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {

      // Header row: name + size + capability chips + delete
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            model.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            formatBytes(model.sizeInBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (model.llmSupportImage == true) {
              CapabilityChip(Icons.Rounded.Image, "Vision")
            }
            if (model.llmSupportAudio == true) {
              CapabilityChip(Icons.Rounded.Audiotrack, "Audio")
            }
            CapabilityChip(Icons.Rounded.Memory, "On-device")
          }
        }

        if (isReady) {
          IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
          }
        }
      }

      if (model.info.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
          model.info,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
        )
      }

      Spacer(Modifier.height(14.dp))

      when (downloadStatus.status) {
        ModelDownloadStatusType.NOT_DOWNLOADED -> {
          Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          ) {
            Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download  ·  ${formatBytes(model.sizeInBytes)}", fontWeight = FontWeight.SemiBold)
          }
        }

        ModelDownloadStatusType.FAILED -> {
          Text(
            "Download failed: ${downloadStatus.errorMessage.ifEmpty { "Unknown error" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
          Spacer(Modifier.height(8.dp))
          Button(
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          ) {
            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Retry", fontWeight = FontWeight.SemiBold)
          }
        }

        ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
          val progress = if (downloadStatus.totalBytes > 0)
            downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes else 0f
          Text(
            "Incomplete — ${formatBytes(downloadStatus.receivedBytes)} / ${formatBytes(downloadStatus.totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().clip(CircleShape).height(5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Discard") }
            Button(onClick = onResume, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
              Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(4.dp))
              Text("Resume", fontWeight = FontWeight.SemiBold)
            }
          }
        }

        ModelDownloadStatusType.IN_PROGRESS,
        ModelDownloadStatusType.UNZIPPING -> {
          val progress = if (downloadStatus.totalBytes > 0)
            downloadStatus.receivedBytes.toFloat() / downloadStatus.totalBytes else 0f
          val received = formatBytes(downloadStatus.receivedBytes)
          val total = formatBytes(downloadStatus.totalBytes)
          val speedLabel = if (downloadStatus.bytesPerSecond > 0) "  ·  ${formatBytes(downloadStatus.bytesPerSecond)}/s" else ""
          val etaLabel = if (downloadStatus.remainingMs > 0) "  ·  ${formatEta(downloadStatus.remainingMs)}" else ""
          Text(
            if (downloadStatus.status == ModelDownloadStatusType.UNZIPPING) "Extracting…"
            else "$received / $total$speedLabel$etaLabel",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.height(6.dp))
          LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().clip(CircleShape).height(5.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
          )
          Spacer(Modifier.height(10.dp))
          OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }

        ModelDownloadStatusType.SUCCEEDED -> {
          Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          ) {
            Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start Ledger", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}

@Composable
private fun CapabilityChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
  SuggestionChip(
    onClick = {},
    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp)) },
    shape = CircleShape,
    colors = SuggestionChipDefaults.suggestionChipColors(
      containerColor = MaterialTheme.colorScheme.surface,
    ),
    border = SuggestionChipDefaults.suggestionChipBorder(
      enabled = true,
      borderColor = MaterialTheme.colorScheme.outlineVariant,
    ),
  )
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
  bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
  bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
  else -> "$bytes B"
}

private fun formatEta(remainingMs: Long): String {
  val totalSec = remainingMs / 1000
  return when {
    totalSec >= 3600 -> "${totalSec / 3600}h ${(totalSec % 3600) / 60}m"
    totalSec >= 60 -> "${totalSec / 60}m ${totalSec % 60}s"
    else -> "${totalSec}s"
  }
}
