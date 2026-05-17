package com.ledger.app.ui.modelsetup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.common.HapticManager
import com.ledger.app.data.Model
import com.ledger.app.data.ModelDownloadStatus
import com.ledger.app.data.ModelDownloadStatusType
import com.ledger.app.ui.common.SensoryBackground
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
  val context = LocalContext.current
  val hapticManager = remember { HapticManager(context) }

  val authResultLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
  ) { result ->
    viewModel.handleAuthResult(result) { success, error ->
      if (!success) scope.launch { snackbarHostState.showSnackbar(error ?: "Login failed") }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = Color.Transparent // Allow SensoryBackground to show through
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize()) {
      SensoryBackground(isProfit = true)
      
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        item {
          HeroSection()
        }

        item {
          Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
              "Intelligence Engine",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Black,
              color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
              "Select a persona for your local business partner",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
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
              modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
              enter = fadeIn(tween(300, delayMillis = index * 100)) +
                slideInVertically(tween(300, delayMillis = index * 100)) { it / 4 },
            ) {
              Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                ModelCard(
                  model = model,
                  downloadStatus = status,
                  onDownload = { hapticManager.playTick(); viewModel.downloadModel(model) },
                  onResume = { hapticManager.playTick(); viewModel.downloadModel(model) },
                  onCancel = { viewModel.cancelDownload(model) },
                  onDiscard = { viewModel.discardPartialDownload(model) },
                  onDelete = { viewModel.deleteModel(model) },
                  onStart = { hapticManager.playTick(); onModelReady(model) },
                )
              }
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
    animationSpec = tween(700, delayMillis = 200, easing = FastOutSlowInEasing),
    label = "text_alpha",
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(260.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      // Synchronized Brand Mark: Arrow in Dark Circle, Cyber Lime
      Box(
        modifier = Modifier
          .scale(iconScale)
          .size(120.dp)
          .background(Color(0xFF0B0D0F), CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.TrendingUp,
          contentDescription = null,
          modifier = Modifier.size(70.dp),
          tint = MaterialTheme.colorScheme.primaryContainer,
        )
      }

      Spacer(Modifier.height(16.dp))

      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(textAlpha),
      ) {
        Text(
          "LEDGER",
          style = MaterialTheme.typography.displaySmall.copy(
            letterSpacing = 6.sp,
            fontWeight = FontWeight.Black
          ),
          color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
          "The Future of Small Business",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
      }
    }
  }
}

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
  val isLarge = model.sizeInBytes > 3_000_000_000L
  val personaTitle = if (isLarge) "The Analyst" else "The Specialist"
  val personaSubtitle = if (isLarge) "Deep intelligence for complex accounting" else "Lightning-fast for daily market sales"

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(24.dp))
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
      .border(
        width = 1.dp,
        brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.3f), Color.Transparent)),
        shape = RoundedCornerShape(24.dp)
      )
      .padding(1.dp)
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            personaTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            model.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
          )
          Spacer(Modifier.height(8.dp))
          Text(
            personaSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
          )
        }

        if (isReady) {
          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
          ) {
            Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
          }
        }
      }

      Spacer(Modifier.height(20.dp))
      
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.llmSupportImage) CapabilityBadge(Icons.Rounded.Image, "Vision")
        if (model.llmSupportAudio) CapabilityBadge(Icons.Rounded.Audiotrack, "Voice")
        CapabilityBadge(Icons.Rounded.Memory, "Offline")
      }

      Spacer(Modifier.height(24.dp))

      StatusSection(
        status = downloadStatus,
        modelSize = model.sizeInBytes,
        onDownload = onDownload,
        onResume = onResume,
        onCancel = onCancel,
        onDiscard = onDiscard,
        onStart = onStart
      )
    }
  }
}

@Composable
private fun StatusSection(
  status: ModelDownloadStatus,
  modelSize: Long,
  onDownload: () -> Unit,
  onResume: () -> Unit,
  onCancel: () -> Unit,
  onDiscard: () -> Unit,
  onStart: () -> Unit
) {
  when (status.status) {
    ModelDownloadStatusType.NOT_DOWNLOADED -> {
      Button(
        onClick = onDownload,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
      ) {
        Icon(Icons.Rounded.CloudDownload, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Install local partner  ·  ${formatBytes(modelSize)}", fontWeight = FontWeight.Bold)
      }
    }

    ModelDownloadStatusType.FAILED -> {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Download failed. Check connection.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
          Text("Retry Installation")
        }
      }
    }

    ModelDownloadStatusType.PARTIALLY_DOWNLOADED -> {
      val progress = if (status.totalBytes > 0) status.receivedBytes.toFloat() / status.totalBytes else 0f
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CustomProgressBar(progress)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Discard") }
          Button(onClick = onResume, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { Text("Resume") }
        }
      }
    }

    ModelDownloadStatusType.IN_PROGRESS, ModelDownloadStatusType.UNZIPPING -> {
      val progress = if (status.totalBytes > 0) status.receivedBytes.toFloat() / status.totalBytes else 0f
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
          if (status.status == ModelDownloadStatusType.UNZIPPING) "Extracting Intelligence..." 
          else "Downloading Local Partner (${(progress * 100).toInt()}%)...",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CustomProgressBar(progress)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Cancel") }
      }
    }

    ModelDownloadStatusType.SUCCEEDED -> {
      Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
      ) {
        Icon(Icons.Rounded.RocketLaunch, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Launch Local Partner", fontWeight = FontWeight.Black)
      }
    }
  }
}

@Composable
private fun CustomProgressBar(progress: Float) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(8.dp)
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth(progress)
        .fillMaxHeight()
        .background(
          Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
          CircleShape
        )
    )
  }
}

@Composable
private fun CapabilityBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
  Row(
    modifier = Modifier
      .clip(CircleShape)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
      .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp)
  ) {
    Icon(icon, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

private fun formatBytes(bytes: Long): String = when {
  bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
  bytes >= 1_000_000L -> "%.1f MB".format(bytes / 1_000_000.0)
  bytes >= 1_000L -> "%.1f KB".format(bytes / 1_000.0)
  else -> "$bytes B"
}
