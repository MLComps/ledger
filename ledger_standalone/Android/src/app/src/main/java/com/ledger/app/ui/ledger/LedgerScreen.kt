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
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.ledger.app.ui.common.chat.ChatMessageClarification
import com.ledger.app.ui.common.chat.ChatMessageText
import com.ledger.app.ui.common.chat.ChatMessageWarning
import com.ledger.app.ui.common.chat.ChatSide
import com.ledger.app.ui.common.SensoryBackground
import com.ledger.app.ui.theme.LocalPrivacyMode
import com.ledger.app.common.HapticManager
import java.io.File
import java.util.Locale
import java.util.Calendar
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

private const val TAG = "LedgerScreen"

private fun buildSystemPrompt(currency: String) =
  """You are Ledger, an offline bookkeeping assistant for market vendors.

For every input — whether typed text, spoken audio, or image — extract any financial transaction or stock update mentioned, then respond with ONLY a valid JSON object. Output no other text.

JSON schema:
{
  "action": "add_transaction" | "update_stock" | "get_health" | "clarify" | "unknown",
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
  "question": "<one short question in the user's language — only present when action=clarify>",
  "message": "<one sentence in the user's language confirming what was recorded — only present when action is not clarify>"
}

Rules:
- action="add_transaction" for sales, purchases, income, or expenses
- action="update_stock" for restocking or stock level changes
- action="get_health" when asked for totals, summary, or financial health
- action="unknown" if no financial action is found
- action="clarify" ONLY when the amount is completely absent AND cannot be inferred; examples that MUST clarify: "Sold mangoes" → clarify; "I sold some tomatoes" → clarify; "tomatoes transaction" → clarify; do NOT clarify for unclear item names — use confidence="low" instead; do NOT clarify for stock-only updates
- If the amount IS present, ALWAYS record even if the item is vague (use item="goods") — never clarify when an amount is stated
- Corrections WITH a stated amount ("actually it was 500 not 5000 for the rice") → action="add_transaction", confidence="medium", default transaction_type="sale" unless context says otherwise
- Corrections WITHOUT a stated amount ("cancel that", "it was a purchase not a sale") → action="unknown"
- When action="clarify": transactions and stock_updates MUST be empty arrays []; include a "question" field with one short question in the user's language; omit the "message" field entirely
- transactions and stock_updates may be empty arrays []
- currency defaults to $currency if not mentioned; always output the 3-letter ISO code (e.g. KES not KSH)
- cost defaults to 0 if not mentioned
- IMPORTANT: the "action" field must ONLY be one of: "add_transaction", "update_stock", "get_health", "clarify", "unknown" — never "sale", "purchase", "income", or "expense"
- transaction_type rules (applies to transaction_type field only, not action):
  - "sale": vendor sold goods or services directly; use when no explicit buy/sell direction is stated (default to sale); examples: "sold tomatoes 80"→sale, "3 packets uji at 30 each"→sale, "customer paid GHS 80 for charcoal"→sale, "sold airtime 500"→sale, "5 mangoes for 200"→sale
  - "income": vendor received money NOT from a direct product sale — past debt repayment; examples: "paid 500 for previous sale"→income, "customer paid what they owed"→income, "received 1500 from John for debt"→income; these income transactions with clearly stated amounts → confidence="high"
  - "purchase": vendor explicitly paid money to buy goods to restock or resell — requires "I bought", "I paid for", "from supplier", "purchased", "nilinunua" (Swahili)
  - "expense": vendor paid money for services or overhead (rent, electricity, transport, bills)
- For corrections like "actually it was X not Y": transaction_type="sale" unless message explicitly says "purchase" or "bought"; confidence="medium"
- Corrections WITHOUT a stated amount ("cancel that", "it was a purchase not a sale") → action="unknown"
- For M-Pesa confirmations: "Ksh X received from NAME" → action="add_transaction", transaction_type="income", item="M-Pesa payment", currency="KES"; "Ksh X sent to NAME" → action="add_transaction", transaction_type="expense", item=the stated purpose if given, currency="KES"
- When a purchase involves adding goods to inventory for resale, include BOTH a transactions entry (transaction_type="purchase") AND a stock_updates entry in the same response
- item: vague category words like "stuff", "things" → item="goods", confidence="medium"; deictic/pronoun references like "that thing", "something", "it", "this" → item="goods", confidence="low"
- Swahili number words: moja=1, mbili=2, tatu=3, nne=4, tano=5, kumi=10, ishirini=20, thelathini=30, hamsini=50, mia=100 (hundred), elfu=1000 (thousand); examples: "mia moja"=100, "mia mbili"=200, "elfu mbili"=2000; verbs: uza/uliuza=sold, nunua/nilinunua=bought, lipa/nilipa=paid; example: "uza sukari kilo moja mia moja" → sold 1 kg sugar for 100
- confidence levels — strict rules, not guidelines:
  - "high": specific named item AND directly stated amount (a number or number word like "two hundred", "fifty bob"); voice fillers (um, uh, like, so) do NOT reduce confidence; currency defaulting does NOT reduce confidence; income payments with clearly stated amounts → high
  - "medium": if ANY of these apply: (1) YOU computed the total (e.g. "3 at 30 each"→90, "5 crates at 500 each"→2500), (2) approximate amount ("about", "around", "roughly"), (3) input starts with correction phrase ("actually it was", "wait it was", "no it was"), (4) generic category word item ("goods", "stuff") — MUST be medium if any apply
  - "low": user referred to the item with a deictic or bare pronoun ("that thing", "something", "it", "this thing") — ALWAYS low for these; examples: "sold that thing for 200"→low, "paid me 800 for something"→low, even if item="goods"
- Output a single JSON object. Start your response with { and end with }. No other text."""

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
            viewModel.resetEngine(context, model, buildSystemPrompt(uiState.selectedCurrency)) { errorDialogContent = it; showErrorDialog = true }
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
  var pendingTransactions by remember { mutableStateOf<List<PendingTransaction>>(emptyList()) }
  var showRitualSummary by remember { mutableStateOf(false) }
  var showDebugOverlay by remember { mutableStateOf(false) }
  var debugTapCount by remember { mutableIntStateOf(0) }

  fun handleResponse(response: String) {
    viewModel.setLastRawJson(response)
    when (val result = parseResponse(response, ledgerTools, uiState.selectedCurrency)) {
      is ParseResult.ClarificationNeeded -> {
        viewModel.setClarificationPending(true)
        viewModel.addMessage(ChatMessageClarification(result.question))
      }
      is ParseResult.Transactions -> {
        viewModel.setClarificationPending(false)
        val mode = uiState.validationMode
        val needsConfirm = result.list.filter { tx ->
          when (mode) {
            "all" -> true
            "critical" -> tx.confidence == "low"
            else -> false
          }
        }
        val autoApply = result.list - needsConfirm.toSet()
        autoApply.forEach {
          commitTransaction(it, ledgerTools)
          if (it.transactionType == "sale" || it.transactionType == "income") hapticManager.playSaleClink()
          else hapticManager.playExpenseThud()
        }
        if (needsConfirm.isNotEmpty()) pendingTransactions = needsConfirm
        viewModel.addMessage(ChatMessageText(content = result.message, side = ChatSide.AGENT))
      }
      is ParseResult.Empty -> {
        viewModel.setClarificationPending(false)
        viewModel.addMessage(ChatMessageText(content = "I couldn't understand that. Could you rephrase?", side = ChatSide.AGENT))
      }
    }
  }

  if (pendingTransactions.isNotEmpty()) {
    ConfirmTransactionsDialog(
      transactions = pendingTransactions,
      onConfirm = { pendingTransactions.forEach { commitTransaction(it, ledgerTools) }; pendingTransactions = emptyList() },
      onDismiss = { pendingTransactions = emptyList() },
    )
  }

  val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

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

  var smsPermissionGranted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
  }
  val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> smsPermissionGranted = granted }

  DisposableEffect(smsPermissionGranted, model.name) {
    if (!smsPermissionGranted || model.instance == null) return@DisposableEffect onDispose {}
    val receiver = LedgerSmsReceiver { sender, body ->
      viewModel.sendSmsMessage(model, sender, body, onDone = { handleResponse(it) }, onError = onError)
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
      viewModel.sendImageMessage(model, uri, onDone = { handleResponse(it) }, onError = onError)
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
    viewModel.sendImageMessage(model, uri, onDone = { handleResponse(it) }, onError = onError)
  }
  val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) showAudioPanel = true
  }
  val wavFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
    uri ?: return@rememberLauncherForActivityResult
    viewModel.sendWavFileMessage(model, uri, onDone = { handleResponse(it) }, onError = onError)
  }

  LaunchedEffect(uiState.messages.size) {
    if (uiState.messages.isNotEmpty()) scrollState.animateScrollTo(Int.MAX_VALUE)
  }

  fun processText(text: String) {
    if (text.trim().isEmpty()) return
    inputText = ""
    // Sending any message resolves a pending clarification
    if (uiState.clarificationPending) viewModel.setClarificationPending(false)
    viewModel.sendMessage(model, text, onDone = { handleResponse(it) }, onError = onError)
  }

  fun processAudio(pcmBytes: ByteArray) {
    showAudioPanel = false
    viewModel.sendAudioMessage(model, pcmBytes, onDone = { handleResponse(it) }, onError = onError)
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
          onSpeakToggle = { if (uiState.isSpeaking) viewModel.stopSpeaking() else viewModel.speakSummary(onError = onError) },
        )

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val (fabLabel, fabIcon) = remember(currentHour) {
          when (currentHour) {
            in 6..10 -> "Restock" to Icons.Rounded.Inventory2
            in 20..23, in 0..5 -> "Close Day" to Icons.Rounded.History
            else -> "Quick Sale" to Icons.Rounded.Add
          }
        }

        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
          Button(
            onClick = {
              hapticManager.playTick()
              if (fabLabel == "Close Day") showRitualSummary = true
              else if (fabLabel == "Restock") inputText = "Restocked "
              else inputText = "Sold "
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = Color.White.copy(alpha = 0.25f),
              contentColor = Color.White
            ),
            modifier = Modifier.height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
          ) {
            Icon(fabIcon, null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(fabLabel, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
          }
        }
      }

      if (showRitualSummary) {
        com.ledger.app.ui.common.RitualSummaryDialog(
          onDismiss = { showRitualSummary = false },
          onShare = { showRitualSummary = false; exportAndShare() },
          revenue = "${uiState.selectedCurrency} ${formatAmount(uiState.revenue)}",
          profit = "${uiState.selectedCurrency} ${formatAmount(uiState.netProfit)}",
          txCount = uiState.transactionCount
        )
      }

      if (showDebugOverlay && BuildConfig.DEBUG) {
        AlertDialog(
          onDismissRequest = { showDebugOverlay = false },
          title = { Text("Debug — Last inference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Text("Raw JSON response:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                  uiState.lastRawJson.ifBlank { "No inference yet." },
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  modifier = Modifier.padding(10.dp),
                )
              }
            }
          },
          confirmButton = { TextButton(onClick = { showDebugOverlay = false }) { Text("Close") } },
        )
      }

      // ── Control chips ─────────────────────────────────────────────────────
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
      ) {
        val validationLabel = when (uiState.validationMode) {
          "all" -> "Validate: All"
          "critical" -> "Validate: Low conf."
          else -> "Validate: Off"
        }
        FilterChip(
          selected = uiState.validationMode != "none",
          onClick = { viewModel.saveValidationMode(when (uiState.validationMode) { "none" -> "critical"; "critical" -> "all"; else -> "none" }) },
          label = { Text(validationLabel, style = MaterialTheme.typography.labelSmall) },
          leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, modifier = Modifier.size(14.dp)) },
        )
        FilterChip(
          selected = smsPermissionGranted,
          onClick = { if (!smsPermissionGranted) smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
          label = { Text(if (smsPermissionGranted) "SMS: Active" else "SMS: Off", style = MaterialTheme.typography.labelSmall) },
          leadingIcon = { Icon(Icons.Rounded.Sms, null, modifier = Modifier.size(14.dp)) },
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
            is ChatMessageClarification -> ClarificationBubble(
              message = message,
              onSkip = { viewModel.setClarificationPending(false) },
            )
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
  onSpeakToggle: () -> Unit,
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
    Box(
      modifier = Modifier.fillMaxWidth().background(
        Brush.linearGradient(
          colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary,
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
            IconButton(onClick = onSpeakToggle, modifier = Modifier.size(32.dp)) {
              Icon(
                if (uiState.isSpeaking) Icons.Rounded.StopCircle else Icons.Rounded.VolumeUp,
                contentDescription = "Speak",
                modifier = Modifier.size(18.dp),
                tint = Color.White.copy(alpha = 0.85f),
              )
            }
            IconButton(onClick = onExportCsv, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Rounded.GridOn, contentDescription = "Export CSV", modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.85f))
            }
            IconButton(onClick = onExportPdf, modifier = Modifier.size(32.dp)) {
              Icon(Icons.Rounded.Share, contentDescription = "Export PDF", modifier = Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.85f))
            }
          }
        }

        Spacer(Modifier.height(12.dp))

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
private fun ClarificationBubble(message: ChatMessageClarification, onSkip: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.Start,
  ) {
    ElevatedCard(
      modifier = Modifier.widthIn(max = 300.dp),
      colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
      ),
      shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
    ) {
      Column(modifier = Modifier.padding(12.dp, 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
          )
          Text(
            "Need a detail",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
          )
        }
        Text(
          message.question,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        TextButton(
          onClick = onSkip,
          modifier = Modifier.align(Alignment.End).height(28.dp),
          contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
          Text("Skip", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
        }
      }
    }
  }
}

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
                .blur(30.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color.White.copy(alpha = 0.15f),
                      Color.White.copy(alpha = 0.05f)
                    )
                  ),
                  RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
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
          Text(
            text = message.content,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (isUser) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
          )
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

// ── Confirm dialog ────────────────────────────────────────────────────────────

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
  // Stock deltas attached to this transaction — applied only when transaction is committed
  val stockDeltas: List<Triple<String, Double, String>> = emptyList(),
)

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
        Icon(Icons.Rounded.Warning, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (transactions.size == 1) "Confirm Transaction" else "Confirm ${transactions.size} Transactions")
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Low-confidence extraction — please verify:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        transactions.forEach { tx ->
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(tx.item, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(tx.confidence, style = MaterialTheme.typography.labelSmall, color = when (tx.confidence) { "low" -> Color(0xFFC62828); "medium" -> Color(0xFFE65100); else -> Color(0xFF2E7D32) }, fontWeight = FontWeight.Bold)
              }
              Text("${tx.transactionType}  ·  ${tx.currency} ${formatAmount(tx.amount)}  ·  ${fmtQty(tx.quantity)} ${tx.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
    confirmButton = { Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Save") } },
  )
}

// ── JSON parsing ──────────────────────────────────────────────────────────────

sealed class ParseResult {
  data class Transactions(val list: List<PendingTransaction>, val message: String) : ParseResult()
  data class ClarificationNeeded(val question: String) : ParseResult()
  object Empty : ParseResult()
}

private fun parseResponse(jsonStr: String, tools: LedgerTools, defaultCurrency: String): ParseResult {
  return try {
    val start = jsonStr.indexOf('{'); val end = jsonStr.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) return ParseResult.Empty
    val json = JSONObject(jsonStr.substring(start, end + 1))
    val action = json.optString("action", "unknown")

    if (action == "clarify") {
      val question = json.optString("question", "").ifBlank { "Could you provide more details?" }
      return ParseResult.ClarificationNeeded(question)
    }

    val stockUpdatesArr = json.optJSONArray("stock_updates")
    val stockDeltaList: List<Triple<String, Double, String>> = buildList {
      if (stockUpdatesArr != null) {
        for (i in 0 until stockUpdatesArr.length()) {
          val upd = stockUpdatesArr.getJSONObject(i)
          add(Triple(upd.optString("item", "item"), upd.optDouble("quantity_delta", 0.0), upd.optString("unit", "unit")))
        }
      }
    }
    // For pure stock actions, apply immediately. For add_transaction, defer to commitTransaction
    // so that stock is only updated if the user actually confirms the transaction.
    if (action != "add_transaction") {
      stockDeltaList.forEach { (item, delta, unit) -> tools.updateStock(item, delta, unit) }
    }
    val message = json.optString("message", "Done.")
    val transactions = json.optJSONArray("transactions")
    val list = if (action == "add_transaction" && transactions != null)
      (0 until transactions.length()).mapIndexed { i, _ ->
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
          // Attach stock deltas to first transaction only; combined responses have one tx per stock update
          stockDeltas = if (i == 0) stockDeltaList else emptyList(),
        )
      }
    else emptyList()
    ParseResult.Transactions(list, message)
  } catch (e: Exception) {
    Log.w(TAG, "JSON parse failed: ${e.message}")
    ParseResult.Empty
  }
}

private fun commitTransaction(tx: PendingTransaction, tools: LedgerTools) {
  tools.addTransaction(tx.item, tx.amount, tx.currency, tx.transactionType, tx.cost, tx.quantity, tx.unit, tx.confidence)
  tx.stockDeltas.forEach { (item, delta, unit) -> tools.updateStock(item, delta, unit) }
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
