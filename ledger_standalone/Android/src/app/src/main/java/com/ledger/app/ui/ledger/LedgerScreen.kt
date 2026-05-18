package com.ledger.app.ui.ledger

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.text.format.DateUtils
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.BuildConfig
import com.ledger.app.R
import com.ledger.app.data.Model
import com.ledger.app.ui.common.chat.AudioRecorderPanel
import com.ledger.app.ui.common.chat.ChatMessageText
import com.ledger.app.ui.common.chat.ChatMessageWarning
import com.ledger.app.ui.common.chat.ChatSide
import com.ledger.app.ui.common.SensoryBackground
import com.ledger.app.ui.theme.LocalPrivacyMode
import com.ledger.app.common.HapticManager
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import com.google.ai.edge.litertlm.tool

private const val TAG = "LedgerScreen"

private fun buildSystemPrompt(currency: String) =
  """You are Ledger, an offline bookkeeping assistant for market vendors. You have three tools: addTransaction, updateStock, and getFinancialHealth.

For every user input, identify any financial transaction or inventory change and call the appropriate tool immediately. After calling a tool, reply with a brief friendly confirmation in the user's language (one sentence).

Tool usage rules:
- getFinancialHealth: FIRST check — if the user asks ANY question about profit, loss, earnings, revenue, totals, summary, or business status, IMMEDIATELY call getFinancialHealth without asking anything. Trigger phrases: "profit", "summary", "how am I doing", "earnings", "revenue", "loss", "how much did I make", "business health", "today's numbers". Examples: "What is my profit today?" → call getFinancialHealth. "Give me a summary of today's business" → call getFinancialHealth.
- addTransaction: for sales, purchases, expenses, and income. Default transactionType is "sale" unless context says otherwise.
  - confidence="high": amount directly stated ("sold sugar 100", "paid 500 for rent")
  - confidence="medium": you computed the total (e.g. "3 at 30 each" → 90) or amount is approximate ("about 350")
  - confidence="low": item is vague ("that thing", "something", "it", "stuff") — ALWAYS call addTransaction with confidence="low" when an amount IS stated, even if the item is unclear. Use item="goods" for completely unknown items.
  - RULE: if an amount IS stated, ALWAYS call addTransaction, no matter how vague the item. Never clarify when an amount is given.
  - Always provide the item parameter. Use item="goods" if the item is completely unclear.
  - NEVER guess or infer an amount. ONLY record amounts the user explicitly stated. If no amount is given, ask — even for common items like electricity, airtime, or bread where you might know typical prices.
- updateStock: ONLY for pure inventory adjustments where NO purchase price is stated (e.g. "I received 10 kg of rice" with no price, "added 50 soap to shelf" with no price). If a purchase amount is mentioned alongside storage or restocking, call addTransaction (transactionType="purchase") instead — do NOT call updateStock. Example: "Got 50 packets soap from supplier for 3000, added to shelf" → addTransaction(purchase, 3000), NOT updateStock.

Currency defaults to $currency. Always use the 3-letter ISO code (e.g. KES not KSH).
If the amount is completely absent AND cannot be inferred, ask one short clarifying question in the user's language. Do NOT call any tool in this case. Examples that require clarification: "Sold mangoes" (no amount), "I sold some milk today" (no amount), "tomatoes transaction" (no amount or direction), "Got payment from a customer" (no amount stated).

M-Pesa / mobile money: "Ksh X received from NAME" → addTransaction, transactionType="income", item="M-Pesa payment", currency="KES"; "Ksh X sent to NAME" → addTransaction, transactionType="expense", currency="KES", item=what was paid for (e.g. "airtime", "bill payment"). Only apply M-Pesa rules when the message uses "Ksh" prefix or mentions M-Pesa/Mpesa explicitly. For other payments without M-Pesa context, use item describing the payment nature (e.g. "previous sale payment", "debt repayment", "loan repayment").
Swahili: uza/uliuza=sold, nunua/nilinunua=bought, lipa/nilipa=paid. Numbers: moja=1, mbili=2, tatu=3, nne=4, tano=5, kumi=10, ishirini=20, thelathini=30, hamsini=50, mia=100, elfu=1000. Example: "uza sukari kilo moja mia moja" → sold 1 kg sugar for 100 $currency."""

// ── Entry point ───────────────────────────────────────────────────────────────

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

  LaunchedEffect(Unit) {
    stateFlow.collect { viewModel.syncFromTools(ledgerTools) }
  }

  LaunchedEffect(model.name) {
    viewModel.initModel(
      context = context,
      model = model,
      systemPrompt = buildSystemPrompt(uiState.selectedCurrency),
      tools = listOf(tool(ledgerTools)),
      onError = { error -> errorDialogContent = error; showErrorDialog = true },
    )
  }

  Box(modifier = Modifier.fillMaxSize().imePadding()) {
    SensoryBackground(isProfit = uiState.netProfit >= 0)
    
    if (!uiState.isModelInitialized) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        CircularProgressIndicator(
          trackColor = MaterialTheme.colorScheme.surfaceVariant,
          strokeWidth = 3.dp,
          modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Loading model…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LedgerMainUi(
        model = model,
        ledgerTools = ledgerTools,
        bottomPadding = bottomPadding,
        viewModel = viewModel,
        onError = { error -> errorDialogContent = error; showErrorDialog = true },
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
          Text(stringResource(R.string.resetting_engine), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }

  if (showErrorDialog) {
    AlertDialog(
      title = { Text(stringResource(R.string.error)) },
      text = { Text(errorDialogContent, style = MaterialTheme.typography.bodyMedium) },
      onDismissRequest = { showErrorDialog = false; errorDialogContent = "" },
      dismissButton = {
        TextButton(onClick = { showErrorDialog = false; errorDialogContent = "" }) { Text(stringResource(R.string.cancel)) }
      },
      confirmButton = {
        Button(
          onClick = {
            showErrorDialog = false; errorDialogContent = ""
            viewModel.resetEngine(context, model, buildSystemPrompt(uiState.selectedCurrency), tools = listOf(tool(ledgerTools))) { errorDialogContent = it; showErrorDialog = true }
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) { Text(stringResource(R.string.reset)) }
      },
    )
  }
}

// ── Main UI ───────────────────────────────────────────────────────────────────

@Composable
private fun LedgerMainUi(
  model: Model,
  ledgerTools: LedgerTools,
  bottomPadding: Dp,
  viewModel: LedgerViewModel,
  onError: (String) -> Unit,
) {
  val uiState by viewModel.uiState.collectAsState()
  val scrollState = rememberScrollState()
  val context = LocalContext.current
  val hapticManager = remember { HapticManager(context) }
  val privacyMode = LocalPrivacyMode.current
  var inputText by remember { mutableStateOf("") }
  var showAudioPanel by remember { mutableStateOf(false) }
  var showAttachMenu by remember { mutableStateOf(false) }
  var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
  var curAmplitude by remember { mutableIntStateOf(0) }
  var showDebugOverlay by remember { mutableStateOf(false) }
  var debugTapCount by remember { mutableIntStateOf(0) }
  var showEmptyExportDialog by remember { mutableStateOf(false) }
  var pendingNewEntries by remember { mutableStateOf<List<LedgerEntry>>(emptyList()) }

  fun captureEntryCount(): Int = synchronized(ledgerTools.entries) { ledgerTools.entries.size }

  fun handleResponse(response: String, countBefore: Int) {
    viewModel.addMessage(ChatMessageText(content = response, side = ChatSide.AGENT))
    val currentEntries = synchronized(ledgerTools.entries) { ledgerTools.entries.toList() }
    val newEntries = if (countBefore < currentEntries.size) currentEntries.drop(countBefore) else emptyList()
    newEntries.forEach { entry ->
      if (entry.transactionType == "sale" || entry.transactionType == "income") hapticManager.playSaleClink()
      else hapticManager.playExpenseThud()
    }
    val toConfirm = when (uiState.validationMode) {
      "all" -> newEntries
      "critical" -> newEntries.filter { it.confidence == "low" }
      else -> emptyList()
    }
    if (toConfirm.isNotEmpty()) pendingNewEntries = toConfirm
  }

  if (pendingNewEntries.isNotEmpty()) {
    NewEntriesConfirmDialog(
      entries = pendingNewEntries,
      onKeep = { pendingNewEntries = emptyList() },
      onUndo = {
        pendingNewEntries.forEach { ledgerTools.deleteTransaction(it.timestampMs) }
        viewModel.syncFromTools(ledgerTools)
        pendingNewEntries = emptyList()
      },
    )
  }

  if (showEmptyExportDialog) {
    AlertDialog(
      onDismissRequest = { showEmptyExportDialog = false },
      title = { Text("Nothing to export") },
      text = { Text("No transactions have been recorded yet. Add some transactions before exporting.") },
      confirmButton = { TextButton(onClick = { showEmptyExportDialog = false }) { Text("OK") } },
    )
  }

  val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

  fun exportAndShare() {
    if (uiState.transactionCount == 0) { showEmptyExportDialog = true; return }
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

  val smsPermissionGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

  DisposableEffect(smsPermissionGranted, uiState.smsEnabled, model.name) {
    if (!smsPermissionGranted || !uiState.smsEnabled || model.instance == null) return@DisposableEffect onDispose {}
    val receiver = LedgerSmsReceiver { sender, body ->
      val countBefore = captureEntryCount()
      viewModel.sendSmsMessage(model, sender, body, onDone = { handleResponse(it, countBefore) }, onError = onError)
    }
    val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
      context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_EXPORTED)
    else {
      @Suppress("UnspecifiedRegisterReceiverFlag")
      context.registerReceiver(receiver, filter)
    }
    onDispose { context.unregisterReceiver(receiver) }
  }

  val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
    if (success) cameraImageUri?.let { uri ->
      val countBefore = captureEntryCount()
      viewModel.sendImageMessage(model, uri, onDone = { handleResponse(it, countBefore) }, onError = onError)
    }
  }
  val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) {
      val imageFile = File(context.cacheDir, "images/ledger_${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
      cameraImageUri = uri
      cameraLauncher.launch(uri)
    }
  }
  val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    val countBefore = captureEntryCount()
    viewModel.sendImageMessage(model, uri, onDone = { handleResponse(it, countBefore) }, onError = onError)
  }
  val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) showAudioPanel = true
  }
  val wavFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    val countBefore = captureEntryCount()
    viewModel.sendWavFileMessage(model, uri, onDone = { handleResponse(it, countBefore) }, onError = onError)
  }

  LaunchedEffect(Unit) { viewModel.syncFromTools(ledgerTools) }

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) scrollState.animateScrollTo(Int.MAX_VALUE)
  }

  fun processText(text: String) {
    if (text.trim().isEmpty()) return
    inputText = ""
    val countBefore = captureEntryCount()
    viewModel.sendMessage(model, text, onDone = { handleResponse(it, countBefore) }, onError = onError)
  }

  fun processAudio(pcmBytes: ByteArray) {
    showAudioPanel = false
    val countBefore = captureEntryCount()
    viewModel.sendAudioMessage(model, pcmBytes, onDone = { handleResponse(it, countBefore) }, onError = onError)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Column(
      modifier = Modifier.padding(
        bottom = if (WindowInsets.ime.getBottom(LocalDensity.current) == 0) bottomPadding else 12.dp
      )
    ) {
      // ── Hero balance card ─────────────────────────────────────────────────
      Box(modifier = Modifier
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clickable {
          debugTapCount++
          if (debugTapCount >= 3) { debugTapCount = 0; showDebugOverlay = true }
        }
      ) {
        HeroBalanceCard(
          uiState = uiState,
          privacyModeEnabled = privacyMode.value,
          onTogglePrivacy = { privacyMode.value = !privacyMode.value },
          onExportPdf = { exportAndShare() },
          onExportCsv = {
            if (uiState.transactionCount == 0) { showEmptyExportDialog = true; return@HeroBalanceCard }
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
          dashboardPeriod = uiState.dashboardPeriod,
          onPeriodChange = { period ->
            viewModel.setDashboardPeriod(period)
            viewModel.syncFromTools(ledgerTools)
          },
        )

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
          Box(
            modifier = Modifier
              .height(32.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.White.copy(alpha = 0.25f))
              .clickable {
                hapticManager.playTick()
                processText("Give me 3 to 5 specific actionable recommendations to improve my business performance today.")
              }
              .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(14.dp), tint = Color.White)
              Text("Recommendations", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
          }
        }
      }

      if (showDebugOverlay && BuildConfig.DEBUG) {
        AlertDialog(
          onDismissRequest = { showDebugOverlay = false },
          title = { Text("Debug — Last response", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Last agent message:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                  uiState.messages.filterIsInstance<ChatMessageText>().lastOrNull { it.side == ChatSide.AGENT }?.content ?: "No inference yet.",
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          },
          confirmButton = { TextButton(onClick = { showDebugOverlay = false }) { Text("Close") } },
        )
      }

      // ── Chat messages ─────────────────────────────────────────────────────
      Column(
        modifier = Modifier.weight(1f).verticalScroll(scrollState).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        if (uiState.messages.isEmpty() && !uiState.processing) {
          ChatEmptyState()
        }

        for (message in uiState.messages) {
          when (message) {
            is ChatMessageText -> ChatBubble(message = message, onDelete = {
              viewModel.addMessage(ChatMessageWarning("Message removed"))
            })
            is ChatMessageWarning -> Box(
              modifier = Modifier.fillMaxWidth(),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = message.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 4.dp),
              )
            }
            else -> {}
          }
        }

        // Thinking indicator
        if (uiState.processing) {
          Row(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Start) {
            ThinkingBubble()
          }
        }
      }

      // ── Quick action chips ───────────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        listOf("💰 Sale", "💸 Expense", "📦 Stock", "📊 Summary").forEach { action ->
          SuggestionChip(
            onClick = {
              hapticManager.playTick()
              when(action) {
                "💰 Sale" -> inputText = "Sold "
                "💸 Expense" -> inputText = "I spent "
                "📦 Stock" -> inputText = "Restocked "
                "📊 Summary" -> processText("Give me a summary of my business today")
              }
            },
            label = { Text(action, style = MaterialTheme.typography.labelSmall) },
            shape = CircleShape,
            colors = SuggestionChipDefaults.suggestionChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
          )
        }
      }

      // ── Input bar ─────────────────────────────────────────────────────────
      if (showAudioPanel) {
        AudioRecorderPanel(
          tintColor = MaterialTheme.colorScheme.primary,
          onAmplitudeChanged = { curAmplitude = it },
          onSendAudioClip = { pcmBytes -> processAudio(pcmBytes) },
          onClose = { showAudioPanel = false },
          modifier = Modifier.padding(vertical = 4.dp),
        )
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          // Single attach button for all file types
          if (model.llmSupportImage || model.llmSupportAudio) {
            Box {
              IconButton(
                onClick = { showAttachMenu = true },
                enabled = !uiState.processing,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              ) {
                Icon(Icons.Rounded.AttachFile, contentDescription = "Attach file", tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                if (model.llmSupportImage) {
                  DropdownMenuItem(
                    text = { Text("Image") },
                    leadingIcon = { Icon(Icons.Rounded.Image, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                      showAttachMenu = false
                      imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                  )
                }
                if (model.llmSupportAudio) {
                  DropdownMenuItem(
                    text = { Text("Audio file (WAV)") },
                    leadingIcon = { Icon(Icons.Rounded.Audiotrack, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                      showAttachMenu = false
                      wavFileLauncher.launch("audio/*")
                    },
                  )
                }
              }
            }
          }

          // Text field — fills all remaining space
          val fieldShape = RoundedCornerShape(24.dp)
          BasicTextField(
            value = inputText,
            onValueChange = { inputText = it },
            enabled = !uiState.processing,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { processText(inputText) }),
            minLines = 1,
            maxLines = 4,
            modifier = Modifier
              .weight(1f)
              .clip(fieldShape)
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .border(1.dp, MaterialTheme.colorScheme.outlineVariant, fieldShape)
              .padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
              if (inputText.isEmpty()) {
                Text(
                  "Message…",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
              }
              innerTextField()
            },
          )

          // Camera — only when model supports images
          if (model.llmSupportImage) {
            IconButton(
              onClick = {
                when {
                  ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                    val imageFile = File(context.cacheDir, "images/ledger_${System.currentTimeMillis()}.jpg").also { it.parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
                    cameraImageUri = uri
                    cameraLauncher.launch(uri)
                  }
                  else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
              },
              enabled = !uiState.processing,
              colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
              Icon(Icons.Rounded.PhotoCamera, contentDescription = "Take photo", tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
          }

          // Send (when text present) or mic (when empty)
          AnimatedContent(targetState = inputText.isNotBlank(), label = "send_mic") { hasText ->
            if (hasText) {
              IconButton(
                onClick = { processText(inputText) },
                enabled = !uiState.processing,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
              ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
              }
            } else {
              IconButton(
                onClick = {
                  when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> showAudioPanel = true
                    else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  }
                },
                enabled = !uiState.processing,
                colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
              ) {
                Icon(Icons.Rounded.Mic, contentDescription = "Record audio", tint = MaterialTheme.colorScheme.onPrimaryContainer)
              }
            }
          }
        }
      }
    }
  }
}

// ── Hero balance card ─────────────────────────────────────────────────────────

@Composable
private fun HeroBalanceCard(
  uiState: LedgerUiState,
  privacyModeEnabled: Boolean,
  onTogglePrivacy: () -> Unit,
  onExportPdf: () -> Unit,
  onExportCsv: () -> Unit,
  dashboardPeriod: String,
  onPeriodChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val mask = "••••"
  val animatedRevenue by animateFloatAsState(uiState.revenue.toFloat(), tween(600, easing = FastOutSlowInEasing), label = "rev")
  val animatedCost by animateFloatAsState(uiState.totalCost.toFloat(), tween(600, easing = FastOutSlowInEasing), label = "cost")
  val animatedProfit by animateFloatAsState(uiState.netProfit.toFloat(), tween(700, easing = FastOutSlowInEasing), label = "profit")

  ElevatedCard(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
  ) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(
      modifier = Modifier.fillMaxWidth().background(
        Brush.linearGradient(
          colors = listOf(
            if (isDark) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
            if (isDark) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiary,
          )
        )
      )
    ) {
      Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        // Revenue / Cost / action buttons row
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          // Revenue
          Column(modifier = Modifier.weight(1f)) {
            Text("Revenue", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (uiState.netProfit >= 0)
                Icon(Icons.AutoMirrored.Rounded.TrendingUp, null, modifier = Modifier.size(13.dp), tint = Color.White.copy(alpha = 0.8f))
              Spacer(Modifier.width(2.dp))
              Text(
                text = if (privacyModeEnabled) mask else "${uiState.selectedCurrency} ${formatAmount(animatedRevenue.toDouble())}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
            }
          }
          // Cost
          Column(modifier = Modifier.weight(1f)) {
            Text("Cost", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (uiState.netProfit < 0)
                Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, modifier = Modifier.size(13.dp), tint = Color.White.copy(alpha = 0.8f))
              Spacer(Modifier.width(2.dp))
              Text(
                text = if (privacyModeEnabled) mask else "${uiState.selectedCurrency} ${formatAmount(animatedCost.toDouble())}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
              )
            }
          }
          // Icon row
          Row {
            IconButton(onClick = onTogglePrivacy, modifier = Modifier.size(32.dp)) {
              Icon(
                if (privacyModeEnabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = "Toggle privacy",
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = if (privacyModeEnabled) 1f else 0.65f),
              )
            }
            var showShareMenu by remember { mutableStateOf(false) }
            Box {
              IconButton(onClick = { showShareMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Share, contentDescription = "Export", modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.85f))
              }
              DropdownMenu(expanded = showShareMenu, onDismissRequest = { showShareMenu = false }) {
                DropdownMenuItem(
                  text = { Text("Export CSV") },
                  leadingIcon = { Icon(Icons.Rounded.GridOn, null, modifier = Modifier.size(16.dp)) },
                  onClick = { showShareMenu = false; onExportCsv() },
                )
                DropdownMenuItem(
                  text = { Text("Export PDF") },
                  leadingIcon = { Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp)) },
                  onClick = { showShareMenu = false; onExportPdf() },
                )
              }
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Period toggle
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          listOf("today" to "Today", "month" to "Month").forEach { (value, label) ->
            val selected = dashboardPeriod == value
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = if (selected) 0.25f else 0.08f))
                .clickable { onPeriodChange(value) }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
              Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White.copy(alpha = if (selected) 1f else 0.6f),
              )
            }
          }
        }

        Spacer(Modifier.height(10.dp))

        // Big net profit
        AnimatedContent(
          targetState = if (privacyModeEnabled) null else animatedProfit.toDouble(),
          transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }) togetherWith fadeOut(tween(200)) },
          label = "profit_anim",
        ) { amount ->
          Column {
            Text(
              text = if (amount == null) mask else "${uiState.selectedCurrency} ${formatAmount(amount)}",
              style = MaterialTheme.typography.displaySmall,
              fontWeight = FontWeight.Bold,
              color = Color.White,
            )
            Text(
              text = if (uiState.netProfit >= 0) "Net Profit  ·  ${uiState.transactionCount} txn${if (uiState.transactionCount != 1) "s" else ""}"
              else "Net Loss  ·  ${uiState.transactionCount} txn${if (uiState.transactionCount != 1) "s" else ""}",
              style = MaterialTheme.typography.labelSmall,
              color = Color.White.copy(alpha = 0.75f),
            )
          }
        }
      }
    }
  }
}

// ── Chat composables ──────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: ChatMessageText, onDelete: () -> Unit) {
  val isUser = message.side == ChatSide.USER
  var showMenu by remember { mutableStateOf(false) }
  val clipboard = LocalClipboardManager.current

  val timeLabel = remember(message.timestampMs) {
    DateUtils.getRelativeTimeSpanString(
      message.timestampMs,
      System.currentTimeMillis(),
      DateUtils.MINUTE_IN_MILLIS,
      DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    Column(
      horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
      modifier = Modifier.widthIn(max = 300.dp),
    ) {
      Box {
        Card(
          shape = RoundedCornerShape(
            topStart = if (isUser) 18.dp else 4.dp,
            topEnd = if (isUser) 4.dp else 18.dp,
            bottomStart = 18.dp,
            bottomEnd = 18.dp,
          ),
          colors = CardDefaults.cardColors(
            containerColor = if (isUser) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
          ),
          modifier = (if (!isUser) Modifier.clickable { showMenu = true } else Modifier)
            .then(
              if (!isUser) Modifier
                .border(
                  width = 1.dp,
                  brush = Brush.verticalGradient(
                    colors = listOf(
                      Color.White.copy(alpha = 0.3f),
                      Color.White.copy(alpha = 0.1f)
                    )
                  ),
                  shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
              else Modifier
            ),
          elevation = CardDefaults.cardElevation(defaultElevation = if (isUser) 2.dp else 0.dp),
        ) {
          if (isUser) {
            Text(
              text = message.content,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              style = MaterialTheme.typography.bodyMedium,
            )
          } else {
            MarkdownText(
              text = message.content,
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              color = MaterialTheme.colorScheme.onSurface,
              style = MaterialTheme.typography.bodyMedium,
            )
          }
        }

        if (!isUser) {
          DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
              text = { Text("Copy") },
              leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp)) },
              onClick = { clipboard.setText(AnnotatedString(message.content)); showMenu = false },
            )
            DropdownMenuItem(
              text = { Text("Delete") },
              leadingIcon = { Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(16.dp)) },
              onClick = { onDelete(); showMenu = false },
            )
          }
        }
      }

      Text(
        text = timeLabel,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
      )
    }
  }
}

@Composable
private fun MarkdownText(
  text: String,
  color: androidx.compose.ui.graphics.Color,
  style: androidx.compose.ui.text.TextStyle,
  modifier: Modifier = Modifier,
) {
  // Renders **bold**, *italic*, and gives numbered/bullet list items a top spacing
  val paragraphs = text.split("\n")
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
    paragraphs.forEachIndexed { index, line ->
      val isListItem = line.trimStart().matches(Regex("^(\\d+\\.|[-•*]).*"))
      val topPad = if (isListItem && index > 0) 6.dp else 2.dp
      val annotated = buildAnnotatedString {
        var i = 0
        while (i < line.length) {
          when {
            line.startsWith("**", i) -> {
              val end = line.indexOf("**", i + 2)
              if (end != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.substring(i + 2, end)) }
                i = end + 2
              } else { append(line[i++]) }
            }
            line.startsWith("*", i) && !line.startsWith("**", i) -> {
              val end = line.indexOf("*", i + 1)
              if (end != -1) {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) { append(line.substring(i + 1, end)) }
                i = end + 1
              } else { append(line[i++]) }
            }
            else -> append(line[i++])
          }
        }
      }
      if (annotated.isNotEmpty()) {
        Text(
          text = annotated,
          color = color,
          style = style,
          modifier = Modifier.padding(top = topPad),
        )
      }
    }
  }
}

@Composable
private fun ThinkingBubble() {
  Card(
    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(5.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val transition = rememberInfiniteTransition(label = "thinking")
      (0..2).forEach { index ->
        val offsetY by transition.animateFloat(
          initialValue = 0f,
          targetValue = -6f,
          animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = index * 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
          label = "dot_$index",
        )
        Box(
          modifier = Modifier
            .size(7.dp)
            .graphicsLayer { translationY = offsetY }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
        )
      }
    }
  }
}

@Composable
private fun ChatEmptyState() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text("Start recording", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    Text("Tell Ledger what you sold, purchased, or spent today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), textAlign = TextAlign.Center)
  }
}

// ── Post-transaction confirm dialog ──────────────────────────────────────────

@Composable
private fun NewEntriesConfirmDialog(
  entries: List<LedgerEntry>,
  onKeep: () -> Unit,
  onUndo: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onKeep,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (entries.size == 1) "Transaction recorded" else "${entries.size} transactions recorded")
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          if (entries.any { it.confidence == "low" }) "Low-confidence extraction — review and keep or undo:"
          else "Review and keep or undo:",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entries.forEach { entry ->
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.item, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                  entry.confidence,
                  style = MaterialTheme.typography.labelSmall,
                  color = when (entry.confidence) {
                    "low" -> Color(0xFFC62828); "medium" -> Color(0xFFE65100); else -> Color(0xFF2E7D32)
                  },
                  fontWeight = FontWeight.Bold,
                )
              }
              Text(
                "${entry.transactionType}  ·  ${entry.currency} ${formatAmount(entry.amount)}  ·  ${fmtQty(entry.quantity)} ${entry.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onUndo) { Text("Undo") } },
    confirmButton = {
      Button(onClick = onKeep, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Text("Keep")
      }
    },
  )
}

// ── Formatters ────────────────────────────────────────────────────────────────

private fun formatAmount(amount: Double): String = when {
  amount >= 1_000_000 -> "%.1fM".format(amount / 1_000_000)
  amount >= 1_000 -> "%.1fK".format(amount / 1_000)
  else -> "%.0f".format(amount)
}

private fun fmtQty(v: Double): String =
  if (v == v.toLong().toDouble()) v.toLong().toString()
  else "%.1f".format(v)
