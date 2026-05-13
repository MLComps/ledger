package com.ledger.app.ui.ledger

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.R
import com.ledger.app.data.Model
import com.ledger.app.ui.common.chat.AudioRecorderPanel
import com.ledger.app.ui.common.chat.ChatMessageText
import com.ledger.app.ui.common.chat.ChatMessageWarning
import com.ledger.app.ui.common.chat.ChatSide
import com.ledger.app.ui.common.textandvoiceinput.HoldToDictateViewModel
import com.ledger.app.ui.common.textandvoiceinput.TextAndVoiceInput
import com.ledger.app.ui.common.textandvoiceinput.VoiceRecognizerOverlay
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

private const val TAG = "LedgerScreen"

private fun buildSystemPrompt(currency: String) =
  """You are Ledger, an offline bookkeeping assistant for market vendors.

For every input — whether typed text, spoken audio, or image — extract any financial transaction or stock update mentioned, then respond with ONLY a valid JSON object. Output no other text.

JSON schema:
{
  "action": "add_transaction" | "update_stock" | "get_health" | "unknown",
  "transactions": [
    {
      "transaction_type": "sale" | "purchase" | "expense" | "income",
      "item": "product or service name",
      "amount": <number>,
      "currency": "<3-letter code e.g. KES RWF GHS NGN ZAR>",
      "quantity": <number>,
      "unit": "<kg pieces sack litre etc>",
      "cost": <cost-of-goods, 0 if unknown>,
      "confidence": "high" | "medium" | "low"
    }
  ],
  "stock_updates": [
    { "item": "<name>", "quantity_delta": <number>, "unit": "<unit>" }
  ],
  "message": "<one sentence in the user's language confirming what was recorded>"
}

Rules:
- action="add_transaction" for sales, purchases, income, or expenses
- action="update_stock" for restocking or stock level changes
- action="get_health" when asked for totals, summary, or financial health
- action="unknown" if no financial action is found
- transactions and stock_updates may be empty arrays []
- currency defaults to $currency if not mentioned
- cost defaults to 0 if not mentioned
- transaction_type="sale" or "income" when the vendor RECEIVES money; "purchase" or "expense" when the vendor PAYS money
- For "paid 500 for previous sale", "customer paid", "received payment" → transaction_type="income"
- confidence="high" when all details are explicit; "medium" when most details are clear but some were inferred; "low" when the transaction is ambiguous or key details were guessed
- Output a single JSON object. Start your response with { and end with }. No other text."""

@Composable
fun LedgerMainScreen(
  model: Model,
  stateFlow: Flow<Unit>,
  ledgerTools: LedgerTools,
  bottomPadding: Dp = 16.dp,
  viewModel: LedgerViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val context = LocalContext.current
  var showErrorDialog by remember { mutableStateOf(false) }
  var errorDialogContent by remember { mutableStateOf("") }
  val taskColor = MaterialTheme.colorScheme.primary

  LaunchedEffect(Unit) {
    stateFlow.collect { viewModel.syncFromTools(ledgerTools) }
  }

  LaunchedEffect(model.name) {
    viewModel.initModel(
      context = context,
      model = model,
      systemPrompt = buildSystemPrompt(uiState.selectedCurrency),
      onError = { error ->
        errorDialogContent = error
        showErrorDialog = true
      },
    )
  }

  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding(),
    contentAlignment = Alignment.Center
  ) {
    if (!uiState.isModelInitialized) {
      CircularProgressIndicator(
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeWidth = 3.dp,
        modifier = Modifier.size(24.dp),
      )
    } else {
      LedgerMainUi(
        model = model,
        ledgerTools = ledgerTools,
        bottomPadding = bottomPadding,
        viewModel = viewModel,
        onError = { error ->
          errorDialogContent = error
          showErrorDialog = true
        },
      )
    }

    AnimatedVisibility(
      uiState.resettingEngine,
      enter = fadeIn() + scaleIn(initialScale = 0.9f),
      exit = fadeOut() + scaleOut(targetScale = 0.9f),
    ) {
      Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
      ) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          CircularProgressIndicator(
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 3.dp,
            modifier = Modifier.size(24.dp),
          )
          Text(
            stringResource(R.string.resetting_engine),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  if (showErrorDialog) {
    AlertDialog(
      title = { Text(stringResource(R.string.error)) },
      text = { Text(errorDialogContent, style = MaterialTheme.typography.bodyMedium) },
      onDismissRequest = {
        showErrorDialog = false
        errorDialogContent = ""
      },
      dismissButton = {
        TextButton(onClick = {
          showErrorDialog = false
          errorDialogContent = ""
        }) {
          Text(stringResource(R.string.cancel))
        }
      },
      confirmButton = {
        Button(
          onClick = {
            showErrorDialog = false
            errorDialogContent = ""
            viewModel.resetEngine(
              context = context,
              model = model,
              systemPrompt = buildSystemPrompt(uiState.selectedCurrency),
              onError = { errorDialogContent = it; showErrorDialog = true },
            )
          },
          colors = ButtonDefaults.buttonColors(containerColor = taskColor),
        ) {
          Text(stringResource(R.string.reset), color = Color.White)
        }
      },
    )
  }
}

@Composable
private fun LedgerMainUi(
  model: Model,
  ledgerTools: LedgerTools,
  bottomPadding: Dp,
  viewModel: LedgerViewModel,
  onError: (String) -> Unit,
  holdToDictateViewModel: HoldToDictateViewModel = hiltViewModel(),
) {
  val uiState by viewModel.uiState.collectAsState()
  val holdToDictateUiState by holdToDictateViewModel.uiState.collectAsState()
  val scrollState = rememberScrollState()
  val context = LocalContext.current
  var curAmplitude by remember { mutableIntStateOf(0) }
  var showAudioPanel by remember { mutableStateOf(false) }
  var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
  var clearTextTrigger by remember { mutableLongStateOf(0L) }
  var pendingTransactions by remember { mutableStateOf<List<PendingTransaction>>(emptyList()) }
  val tintColor = MaterialTheme.colorScheme.primary

  fun handleResponse(response: String) {
    val parsed = parseResponse(response, ledgerTools, uiState.selectedCurrency)
    val mode = uiState.validationMode
    val needsConfirm = parsed.filter { tx ->
      when (mode) {
        "all" -> true
        "critical" -> tx.confidence == "low"
        else -> false
      }
    }
    val autoApply = parsed - needsConfirm.toSet()
    autoApply.forEach { commitTransaction(it, ledgerTools) }
    if (needsConfirm.isNotEmpty()) {
      pendingTransactions = needsConfirm
    }
    viewModel.addMessage(ChatMessageText(content = response, side = ChatSide.AGENT))
  }

  val shareLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
  ) {}

  fun exportAndShare() {
    viewModel.exportPdf(
      context = context,
      onDone = { uri ->
        val intent = Intent(Intent.ACTION_SEND).apply {
          type = "application/pdf"
          putExtra(Intent.EXTRA_STREAM, uri)
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        shareLauncher.launch(Intent.createChooser(intent, context.getString(R.string.share_report)))
      },
      onError = onError,
    )
  }

  fun speakOrStop() {
    if (uiState.isSpeaking) viewModel.stopSpeaking()
    else viewModel.speakSummary(onError = onError)
  }

  var smsPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  val smsPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted -> smsPermissionGranted = granted }

  DisposableEffect(smsPermissionGranted, model.name) {
    if (!smsPermissionGranted || model.instance == null) return@DisposableEffect onDispose {}
    val receiver = LedgerSmsReceiver { sender, body ->
      viewModel.sendSmsMessage(
        model = model,
        sender = sender,
        body = body,
        onDone = { response -> handleResponse(response) },
        onError = onError,
      )
    }
    val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
    } else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      context.registerReceiver(receiver, filter)
    }
    onDispose { context.unregisterReceiver(receiver) }
  }

  val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
  ) { success ->
    if (success) {
      cameraImageUri?.let { uri ->
        viewModel.sendImageMessage(
          model = model,
          uri = uri,
          onDone = { response -> handleResponse(response) },
          onError = onError,
        )
      }
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) {
      val imageFile = File(context.cacheDir, "images/ledger_${System.currentTimeMillis()}.jpg")
        .also { it.parentFile?.mkdirs() }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
      cameraImageUri = uri
      cameraLauncher.launch(uri)
    }
  }

  val imagePickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.sendImageMessage(
      model = model,
      uri = uri,
      onDone = { response -> handleResponse(response) },
      onError = onError,
    )
  }

  val audioPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    if (granted) showAudioPanel = true
  }

  val wavFileLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
  ) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.sendWavFileMessage(
      model = model,
      uri = uri,
      onDone = { response -> handleResponse(response) },
      onError = onError,
    )
  }

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) {
      scrollState.animateScrollTo(Int.MAX_VALUE)
    }
  }

  fun processText(text: String) {
    if (text.trim().isEmpty()) return
    clearTextTrigger = System.currentTimeMillis()
    viewModel.sendMessage(
      model = model,
      text = text,
      onDone = { response -> handleResponse(response) },
      onError = onError,
    )
  }

  fun processAudio(pcmBytes: ByteArray) {
    showAudioPanel = false
    viewModel.sendAudioMessage(
      model = model,
      pcmBytes = pcmBytes,
      onDone = { response -> handleResponse(response) },
      onError = onError,
    )
  }

  if (pendingTransactions.isNotEmpty()) {
    ConfirmTransactionsDialog(
      transactions = pendingTransactions,
      onConfirm = {
        pendingTransactions.forEach { commitTransaction(it, ledgerTools) }
        pendingTransactions = emptyList()
      },
      onDismiss = { pendingTransactions = emptyList() },
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier =
        Modifier.padding(
          bottom =
            if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) bottomPadding else 12.dp
        )
    ) {
      LedgerDashboard(
        uiState = uiState,
        onExportPdf = { exportAndShare() },
        onExportCsv = {
          viewModel.exportCsv(
            context = context,
            onDone = { uri ->
              val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
              shareLauncher.launch(Intent.createChooser(intent, context.getString(R.string.share_report)))
            },
            onError = onError,
          )
        },
        onSpeakToggle = { speakOrStop() },
        onDeleteTransaction = { ts -> viewModel.deleteTransaction(ts, ledgerTools) },
        onCurrencyChange = { code ->
          viewModel.saveCurrency(code, buildSystemPrompt(code))
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val validationLabel = when (uiState.validationMode) {
          "all" -> "Validate: All"
          "critical" -> "Validate: Low conf."
          else -> "Validate: Off"
        }
        FilterChip(
          selected = uiState.validationMode != "none",
          onClick = {
            val next = when (uiState.validationMode) {
              "none" -> "critical"
              "critical" -> "all"
              else -> "none"
            }
            viewModel.saveValidationMode(next)
          },
          label = { Text(text = validationLabel, style = MaterialTheme.typography.labelSmall) },
          leadingIcon = {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
          },
        )
        FilterChip(
          selected = smsPermissionGranted,
          onClick = {
            if (!smsPermissionGranted) {
              smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            }
          },
          label = {
            Text(
              text = if (smsPermissionGranted) "SMS: Active" else "SMS: Off",
              style = MaterialTheme.typography.labelSmall,
            )
          },
          leadingIcon = {
            Icon(
              Icons.Rounded.Sms,
              contentDescription = null,
              modifier = Modifier.size(14.dp),
            )
          },
        )
      }

      Column(
        modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        for (message in uiState.messages) {
          when (message) {
            is ChatMessageText -> {
              val isUser = message.side == ChatSide.USER
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
              ) {
                Card(
                  shape = RoundedCornerShape(12.dp),
                  colors =
                    CardDefaults.cardColors(
                      containerColor =
                        if (isUser) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                  modifier = Modifier.padding(vertical = 2.dp),
                ) {
                  Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color =
                      if (isUser) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                    style =
                      if (isUser) MaterialTheme.typography.bodyMedium
                      else MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  )
                }
              }
            }

            is ChatMessageWarning -> {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
              ) {
                Text(
                  text = message.content,
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                  modifier = Modifier.padding(vertical = 4.dp),
                )
              }
            }

            else -> {}
          }
        }

        if (uiState.processing) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            CircularProgressIndicator(
              trackColor = MaterialTheme.colorScheme.surfaceVariant,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp).padding(top = 4.dp),
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (showAudioPanel) {
          AudioRecorderPanel(
            tintColor = tintColor,
            onAmplitudeChanged = { curAmplitude = it },
            onSendAudioClip = { pcmBytes -> processAudio(pcmBytes) },
            onClose = { showAudioPanel = false },
            modifier = Modifier.weight(1f),
          )
        } else {
          TextAndVoiceInput(
            tintColor = tintColor,
            processing = uiState.processing,
            holdToDictateViewModel = holdToDictateViewModel,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            onDone = { text -> processText(text) },
            onAmplitudeChanged = { curAmplitude = it },
            clearTextTrigger = clearTextTrigger,
            defaultTextInputMode = true,
          )
          if (model.llmSupportImage) {
            IconButton(
              onClick = {
                imagePickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !uiState.processing,
              modifier = Modifier.padding(start = 4.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              ),
            ) {
              Icon(
                Icons.Rounded.Image,
                contentDescription = "Upload image",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
            IconButton(
              onClick = {
                when {
                  ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED -> {
                    val imageFile = File(
                      context.cacheDir,
                      "images/ledger_${System.currentTimeMillis()}.jpg"
                    ).also { it.parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(
                      context,
                      "${context.packageName}.provider",
                      imageFile,
                    )
                    cameraImageUri = uri
                    cameraLauncher.launch(uri)
                  }
                  else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
              },
              enabled = !uiState.processing,
              modifier = Modifier.padding(start = 4.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
              ),
            ) {
              Icon(
                Icons.Rounded.PhotoCamera,
                contentDescription = "Take photo",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
              )
            }
          }
          if (model.llmSupportAudio) {
            IconButton(
              onClick = { wavFileLauncher.launch("audio/*") },
              enabled = !uiState.processing,
              modifier = Modifier.padding(start = 4.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
              ),
            ) {
              Icon(
                Icons.Rounded.Audiotrack,
                contentDescription = "Upload WAV file",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
            IconButton(
              onClick = {
                when {
                  ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED -> showAudioPanel = true
                  else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              },
              enabled = !uiState.processing,
              modifier = Modifier.padding(end = 8.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
              ),
            ) {
              Icon(
                Icons.Rounded.Mic,
                contentDescription = "Record audio for AI",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
          }
        }
      }
    }

    val recognizing = holdToDictateUiState.recognizing
    AnimatedVisibility(
      visible = recognizing,
      enter = fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)),
      exit =
        fadeOut(
          animationSpec =
            tween(durationMillis = 100, easing = FastOutSlowInEasing, delayMillis = 300)
        ),
    ) {
      VoiceRecognizerOverlay(
        tintColor = tintColor,
        viewModel = holdToDictateViewModel,
        curAmplitude = curAmplitude,
        bottomPadding = bottomPadding,
      )
    }
  }
}

data class PendingTransaction(
  val item: String,
  val amount: Double,
  val currency: String,
  val transactionType: String,
  val cost: Double,
  val quantity: Double,
  val unit: String,
  val confidence: String,
  val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Parses the model response. Stock updates are always applied immediately.
 * Returns pending transactions — caller decides whether to confirm or auto-apply.
 */
private fun parseResponse(
  jsonStr: String,
  tools: LedgerTools,
  defaultCurrency: String,
): List<PendingTransaction> {
  return try {
    val start = jsonStr.indexOf('{')
    val end = jsonStr.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) {
      Log.w(TAG, "No JSON object found in response: $jsonStr")
      return emptyList()
    }
    val json = JSONObject(jsonStr.substring(start, end + 1))
    val action = json.optString("action", "unknown")
    val transactions = json.optJSONArray("transactions")
    val stockUpdates = json.optJSONArray("stock_updates")

    // Stock updates always apply immediately — no validation needed
    if (stockUpdates != null) {
      for (i in 0 until stockUpdates.length()) {
        val upd = stockUpdates.getJSONObject(i)
        tools.updateStock(
          item = upd.optString("item", "item"),
          quantityDelta = upd.optDouble("quantity_delta", 0.0),
          unit = upd.optString("unit", "unit"),
        )
      }
    }

    if (action == "add_transaction" && transactions != null) {
      (0 until transactions.length()).map { i ->
        val tx = transactions.getJSONObject(i)
        PendingTransaction(
          item = tx.optString("item", "item"),
          amount = tx.optDouble("amount", 0.0),
          currency = tx.optString("currency", defaultCurrency),
          transactionType = tx.optString("transaction_type", "sale"),
          cost = tx.optDouble("cost", 0.0),
          quantity = tx.optDouble("quantity", 1.0),
          unit = tx.optString("unit", "unit"),
          confidence = tx.optString("confidence", "high"),
        )
      }
    } else emptyList()
  } catch (e: Exception) {
    Log.w(TAG, "Could not parse JSON response: $jsonStr", e)
    emptyList()
  }
}

private fun commitTransaction(tx: PendingTransaction, tools: LedgerTools) {
  tools.addTransaction(
    item = tx.item,
    amount = tx.amount,
    currency = tx.currency,
    transactionType = tx.transactionType,
    cost = tx.cost,
    quantity = tx.quantity,
    unit = tx.unit,
    confidence = tx.confidence,
  )
}

// ─── Dashboard ───────────────────────────────────────────────────────────────

private enum class DashboardSection { TRANSACTIONS, INVENTORY }

private val SUPPORTED_CURRENCIES = listOf(
  "KES" to "Kenya Shilling",
  "NGN" to "Nigeria Naira",
  "GHS" to "Ghana Cedi",
  "UGX" to "Uganda Shilling",
  "TZS" to "Tanzania Shilling",
  "ETB" to "Ethiopia Birr",
  "ZAR" to "South Africa Rand",
  "RWF" to "Rwanda Franc",
  "USD" to "US Dollar",
  "EUR" to "Euro",
  "GBP" to "British Pound",
)

@Composable
private fun LedgerDashboard(
  uiState: LedgerUiState,
  onExportPdf: () -> Unit,
  onExportCsv: () -> Unit,
  onSpeakToggle: () -> Unit,
  onDeleteTransaction: (Long) -> Unit,
  onCurrencyChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf<DashboardSection?>(null) }
  var showCurrencyPicker by remember { mutableStateOf(false) }

  if (showCurrencyPicker) {
    AlertDialog(
      onDismissRequest = { showCurrencyPicker = false },
      title = { Text("Select Currency") },
      text = {
        Column {
          SUPPORTED_CURRENCIES.forEach { (code, name) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  onCurrencyChange(code)
                  showCurrencyPicker = false
                }
                .padding(vertical = 10.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = code,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (code == uiState.selectedCurrency) FontWeight.Bold else FontWeight.Normal,
                color = if (code == uiState.selectedCurrency) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(48.dp),
              )
              Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showCurrencyPicker = false }) { Text("Close") }
      },
    )
  }
  val hasTransactions = uiState.recentTransactions.isNotEmpty()
  val hasStock = uiState.stockItemNames.isNotEmpty()

  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column {
      // Metrics row + export icon
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
          SummaryMetric(label = "Revenue", value = formatAmount(uiState.revenue))
          SummaryMetric(label = "Cost", value = formatAmount(uiState.totalCost))
          SummaryMetric(
            label = "Profit",
            value = formatAmount(uiState.netProfit),
            valueColor = when {
              uiState.netProfit > 0 -> Color(0xFF2E7D32)
              uiState.netProfit < 0 -> Color(0xFFC62828)
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            trailingIcon = when {
              uiState.netProfit > 0 -> {
                { Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, modifier = Modifier.size(14.dp), tint = Color(0xFF2E7D32)) }
              }
              uiState.netProfit < 0 -> {
                { Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, modifier = Modifier.size(14.dp), tint = Color(0xFFC62828)) }
              }
              else -> null
            },
          )
          SummaryMetric(label = "Txns", value = uiState.transactionCount.toString())
        }
        TextButton(
          onClick = { showCurrencyPicker = true },
          modifier = Modifier.height(36.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Text(
            text = uiState.selectedCurrency,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onSpeakToggle,
          modifier = Modifier.size(36.dp),
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (uiState.isSpeaking)
              MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
            else
              MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
          ),
        ) {
          Icon(
            if (uiState.isSpeaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
            contentDescription = if (uiState.isSpeaking) "Stop speaking" else "Speak summary",
            modifier = Modifier.size(18.dp),
            tint = if (uiState.isSpeaking) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.secondary,
          )
        }
        IconButton(
          onClick = onExportCsv,
          modifier = Modifier.size(36.dp),
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
          ),
        ) {
          Icon(
            Icons.Rounded.GridOn,
            contentDescription = "Export CSV",
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
        IconButton(
          onClick = onExportPdf,
          modifier = Modifier.size(36.dp),
          colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ),
        ) {
          Icon(
            Icons.Rounded.Share,
            contentDescription = stringResource(R.string.export_pdf),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

      if (!hasTransactions && !hasStock) {
        DashboardEmptyState()
      } else {
        // Section toggles
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          SectionToggle(
            label = if (hasTransactions) "Transactions (${uiState.recentTransactions.size})" else "Transactions",
            icon = { Icon(Icons.Rounded.Receipt, null, modifier = Modifier.size(14.dp)) },
            selected = expanded == DashboardSection.TRANSACTIONS,
            onClick = {
              expanded = if (expanded == DashboardSection.TRANSACTIONS) null else DashboardSection.TRANSACTIONS
            },
            modifier = Modifier.weight(1f),
          )
          val lowCount = uiState.lowStockItems.size
          SectionToggle(
            label = if (lowCount > 0) "Inventory ⚠ $lowCount" else if (hasStock) "Inventory (${uiState.stockItemNames.size})" else "Inventory",
            icon = { Icon(Icons.Rounded.Inventory2, null, modifier = Modifier.size(14.dp)) },
            selected = expanded == DashboardSection.INVENTORY,
            onClick = {
              expanded = if (expanded == DashboardSection.INVENTORY) null else DashboardSection.INVENTORY
            },
            modifier = Modifier.weight(1f),
            warn = lowCount > 0,
          )
        }

        // Expanded section content
        AnimatedVisibility(
          visible = expanded != null,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut(),
        ) {
          Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            when (expanded) {
              DashboardSection.TRANSACTIONS -> TransactionSection(uiState, onDeleteTransaction)
              DashboardSection.INVENTORY -> InventorySection(uiState)
              null -> {}
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ConfirmTransactionsDialog(
  transactions: List<PendingTransaction>,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.Warning,
          contentDescription = null,
          tint = Color(0xFFE65100),
          modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (transactions.size == 1) "Confirm Transaction" else "Confirm ${transactions.size} Transactions")
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Low-confidence extraction — please verify before saving:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        transactions.forEach { tx ->
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = tx.item,
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.weight(1f),
                )
                val badgeColor = when (tx.confidence) {
                  "low" -> Color(0xFFC62828)
                  "medium" -> Color(0xFFE65100)
                  else -> Color(0xFF2E7D32)
                }
                Text(
                  text = tx.confidence,
                  style = MaterialTheme.typography.labelSmall,
                  color = badgeColor,
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                text = "${tx.transactionType}  ·  ${tx.currency} ${formatAmount(tx.amount)}  ·  ${fmtQty(tx.quantity)} ${tx.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Discard") }
    },
    confirmButton = {
      Button(
        onClick = onConfirm,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
      ) {
        Text("Save")
      }
    },
  )
}

@Composable
private fun DashboardEmptyState() {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 24.dp, horizontal = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector = Icons.Rounded.Receipt,
      contentDescription = null,
      modifier = Modifier.size(40.dp),
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
    )
    Text(
      text = "Nothing recorded yet",
      style = MaterialTheme.typography.titleSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
    Text(
      text = "Tell Ledger what you sold, purchased, or spent today",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Row(
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      HintIcon(icon = Icons.Rounded.Mic, label = "Voice")
      HintIcon(icon = Icons.Rounded.PhotoCamera, label = "Photo")
      HintIcon(icon = Icons.Rounded.Sms, label = "SMS")
    }
  }
}

@Composable
private fun HintIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(18.dp),
      tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
    )
  }
}

@Composable
private fun SectionToggle(
  label: String,
  icon: @Composable () -> Unit,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  warn: Boolean = false,
) {
  val bg = when {
    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    warn -> Color(0xFFFFF3E0)
    else -> Color.Transparent
  }
  val contentColor = when {
    selected -> MaterialTheme.colorScheme.primary
    warn -> Color(0xFFE65100)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
  }
  Row(
    modifier = modifier
      .background(bg, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.size(14.dp)) {
      androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalContentColor provides contentColor
      ) { icon() }
    }
    Spacer(Modifier.width(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = contentColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.width(2.dp))
    Icon(
      if (selected) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
      contentDescription = null,
      modifier = Modifier.size(12.dp),
      tint = contentColor,
    )
  }
}

@Composable
private fun TransactionSection(uiState: LedgerUiState, onDelete: (Long) -> Unit) {
  var editMode by remember { mutableStateOf(false) }
  val displayed = uiState.recentTransactions.take(10)

  Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.recent_transactions),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.weight(1f),
      )
      IconButton(
        onClick = { editMode = !editMode },
        modifier = Modifier.size(28.dp),
      ) {
        Icon(
          if (editMode) Icons.Rounded.StopCircle else Icons.Rounded.Edit,
          contentDescription = if (editMode) "Exit edit mode" else "Edit transactions",
          modifier = Modifier.size(14.dp),
          tint = if (editMode) MaterialTheme.colorScheme.error
          else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
      }
    }
    Spacer(Modifier.height(4.dp))

    if (displayed.isEmpty()) {
      Text(
        text = "No transactions yet. Tell Ledger about a sale, purchase, or expense.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 8.dp),
      )
    }

    for ((index, tx) in displayed.withIndex()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val typeColor = when (tx.transactionType) {
          "sale", "income" -> Color(0xFF2E7D32)
          else -> Color(0xFFC62828)
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = tx.item,
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f, fill = false),
            )
            if (tx.confidence == "low" || tx.confidence == "medium") {
              Spacer(Modifier.width(4.dp))
              val dotColor = if (tx.confidence == "low") Color(0xFFC62828) else Color(0xFFE65100)
              Icon(
                Icons.Rounded.Warning,
                contentDescription = "Confidence: ${tx.confidence}",
                modifier = Modifier.size(10.dp),
                tint = dotColor,
              )
            }
          }
          Text(
            text = "${tx.transactionType}  ·  ${fmtQty(tx.quantity)} ${tx.unit}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
          )
        }
        Spacer(Modifier.width(8.dp))
        Text(
          text = "${tx.currency} ${formatAmount(tx.amount)}",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = typeColor,
        )
        AnimatedVisibility(visible = editMode, enter = fadeIn(), exit = fadeOut()) {
          IconButton(
            onClick = { onDelete(tx.timestampMs) },
            modifier = Modifier.size(28.dp).padding(start = 4.dp),
          ) {
            Icon(
              Icons.Rounded.Delete,
              contentDescription = "Delete transaction",
              modifier = Modifier.size(14.dp),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
      if (index < displayed.size - 1) {
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
          modifier = Modifier.padding(vertical = 1.dp),
        )
      }
    }
    if (uiState.recentTransactions.size > 10) {
      Text(
        text = "+${uiState.recentTransactions.size - 10} more",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(top = 4.dp),
      )
    }
  }
}

@Composable
private fun InventorySection(uiState: LedgerUiState) {
  Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(
      text = stringResource(R.string.inventory),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
      modifier = Modifier.padding(bottom = 6.dp),
    )
    if (uiState.stockItemNames.isEmpty()) {
      Text(
        text = "No stock tracked yet. Tell Ledger about a restock or purchase to populate this.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      )
    }
    for ((name, stock) in uiState.stockItemNames.zip(uiState.stockItems)) {
      val isLow = name in uiState.lowStockItems
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = name,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.weight(1f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${fmtQty(stock.quantity)} ${stock.unit}",
          style = MaterialTheme.typography.bodySmall,
          color = if (isLow) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLow) {
          Spacer(Modifier.width(4.dp))
          Icon(
            Icons.Rounded.Warning,
            contentDescription = stringResource(R.string.low_stock),
            modifier = Modifier.size(13.dp),
            tint = Color(0xFFE65100),
          )
        }
      }
      if (uiState.stockItemNames.indexOf(name) < uiState.stockItemNames.size - 1) {
        HorizontalDivider(
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
          modifier = Modifier.padding(vertical = 1.dp),
        )
      }
    }
  }
}

@Composable
private fun SummaryMetric(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  trailingIcon: (@Composable () -> Unit)? = null,
) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = valueColor,
      )
      if (trailingIcon != null) {
        Spacer(Modifier.width(2.dp))
        trailingIcon()
      }
    }
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
  }
}

private fun formatAmount(amount: Double): String {
  return if (amount == 0.0) "0"
  else if (amount >= 1_000_000) String.format(Locale.US, "%.1fM", amount / 1_000_000)
  else if (amount >= 1_000) String.format(Locale.US, "%.1fK", amount / 1_000)
  else String.format(Locale.US, "%.0f", amount)
}

private fun fmtQty(v: Double): String =
  if (v == v.toLong().toDouble()) v.toLong().toString()
  else String.format(Locale.US, "%.1f", v)
