package com.ledger.app.ui.ledger

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Process
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ledger.app.R
import com.ledger.app.data.BuiltInTaskId
import com.ledger.app.data.DataStoreRepository
import com.ledger.app.data.Model
import com.ledger.app.db.LedgerRepository
import com.ledger.app.db.TransactionEntity
import com.ledger.app.data.SAMPLE_RATE
import com.ledger.app.llm.LlmChatModelHelper
import com.ledger.app.runtime.LlmModelInstance
import com.ledger.app.ui.common.chat.ChatMessage
import com.ledger.app.ui.common.chat.ChatMessageAudioClip
import com.ledger.app.ui.common.chat.ChatMessageText
import com.ledger.app.ui.common.chat.ChatMessageWarning
import com.ledger.app.ui.common.chat.ChatSide
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val LOW_STOCK_THRESHOLD = 5.0

private const val TAG = "LedgerViewModel"

private val inferenceDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
  Thread(r, "litert-inference").apply {
    priority = Thread.MIN_PRIORITY
    isDaemon = true
  }
}.asCoroutineDispatcher()

private val inferenceMutex = Mutex()

private data class SyncSnapshot(
  val revenue: Double,
  val cogs: Double,
  val purchases: Double,
  val count: Int,
  val stockItems: List<LedgerStockItem>,
  val stockItemNames: List<String>,
  val recentTransactions: List<LedgerEntry>,
  val lowStockItems: Set<String>,
)

data class LedgerUiState(
  val processing: Boolean = false,
  val resettingEngine: Boolean = false,
  val messages: List<ChatMessage> = listOf(),
  val revenue: Double = 0.0,
  val totalCost: Double = 0.0,
  val netProfit: Double = 0.0,
  val transactionCount: Int = 0,
  val stockItems: List<LedgerStockItem> = listOf(),
  val stockItemNames: List<String> = listOf(),
  val selectedModel: Model? = null,
  val isModelInitialized: Boolean = false,
  val recentTransactions: List<LedgerEntry> = listOf(),
  val lowStockItems: Set<String> = emptySet(),
  val isSpeaking: Boolean = false,
  val selectedCurrency: String = "KES",
  val validationMode: String = "critical",
)

@HiltViewModel
class LedgerViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val dataStoreRepository: DataStoreRepository,
  private val ledgerRepository: LedgerRepository,
) : ViewModel() {

  val allTransactions: kotlinx.coroutines.flow.StateFlow<List<TransactionEntity>> =
    ledgerRepository.transactions
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
  private val _uiState = MutableStateFlow(LedgerUiState())
  val uiState = _uiState.asStateFlow()

  private var inferenceCount = 0
  private val autoResetThreshold = 8
  private var lastSystemPrompt: String = ""
  private var lastModel: Model? = null

  init {
    _uiState.update {
      it.copy(
        selectedCurrency = dataStoreRepository.readCurrencyCode(),
        validationMode = dataStoreRepository.readValidationMode(),
      )
    }
  }

  fun syncFromTools(tools: LedgerTools) {
    val (revenue, cogs, purchases, count, stockItems, stockItemNames, recentTransactions, lowStockItems) =
      synchronized(tools.entries) {
        val entries = tools.entries.toList()
        val rev = entries.filter { it.transactionType == "sale" || it.transactionType == "income" }.sumOf { it.amount }
        val cogs = entries.sumOf { it.cost }
        val pur = entries.filter { it.transactionType == "purchase" || it.transactionType == "expense" }.sumOf { it.amount }
        val stockSnapshot = synchronized(tools.stock) { tools.stock.toMap() }
        val recent = entries.takeLast(30).reversed()
        val lowStock = stockSnapshot.entries
          .filter { (_, v) -> v.quantity <= LOW_STOCK_THRESHOLD }
          .map { it.key }.toSet()
        SyncSnapshot(rev, cogs, pur, entries.size, stockSnapshot.values.toList(), stockSnapshot.keys.toList(), recent, lowStock)
      }
    val totalCost = cogs + purchases
    _uiState.update {
      it.copy(
        revenue = revenue,
        totalCost = totalCost,
        netProfit = revenue - totalCost,
        transactionCount = count,
        stockItems = stockItems,
        stockItemNames = stockItemNames,
        recentTransactions = recentTransactions,
        lowStockItems = lowStockItems,
      )
    }
  }

  fun saveCurrency(code: String, newSystemPrompt: String) {
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.saveCurrencyCode(code)
    }
    lastSystemPrompt = newSystemPrompt
    _uiState.update { it.copy(selectedCurrency = code) }
  }

  fun saveValidationMode(mode: String) {
    viewModelScope.launch(Dispatchers.IO) {
      dataStoreRepository.saveValidationMode(mode)
    }
    _uiState.update { it.copy(validationMode = mode) }
  }

  fun exportPdf(context: Context, onDone: (Uri) -> Unit, onError: (String) -> Unit) {
    val snapshot = _uiState.value
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val uri = LedgerPdfExporter.export(context, snapshot, snapshot.selectedCurrency)
        withContext(Dispatchers.Main) { onDone(uri) }
      } catch (e: Exception) {
        Log.e(TAG, "PDF export failed", e)
        withContext(Dispatchers.Main) { onError(e.message ?: context.getString(R.string.pdf_export_failed)) }
      }
    }
  }

  fun exportCsv(context: Context, onDone: (Uri) -> Unit, onError: (String) -> Unit) {
    val snapshot = _uiState.value
    viewModelScope.launch(Dispatchers.IO) {
      try {
        val uri = LedgerCsvExporter.export(context, snapshot)
        withContext(Dispatchers.Main) { onDone(uri) }
      } catch (e: Exception) {
        Log.e(TAG, "CSV export failed", e)
        withContext(Dispatchers.Main) { onError(e.message ?: context.getString(R.string.pdf_export_failed)) }
      }
    }
  }

  fun initModel(
    context: Context,
    model: Model,
    systemPrompt: String,
    onError: (String) -> Unit,
  ) {
    _uiState.update { it.copy(selectedModel = model) }
    lastSystemPrompt = systemPrompt
    lastModel = model
    viewModelScope.launch(inferenceDispatcher) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      LlmChatModelHelper.initialize(
        context = context,
        model = model,
        taskId = BuiltInTaskId.LLM_LEDGER,
        supportImage = model.llmSupportImage,
        supportAudio = model.llmSupportAudio,
        onDone = { error ->
          if (error.isNotEmpty()) {
            onError(error)
          } else {
            _uiState.update { it.copy(isModelInitialized = true, selectedModel = model) }
          }
        },
        systemInstruction = Contents.of(systemPrompt),
        tools = emptyList(),
        enableConversationConstrainedDecoding = false,
      )
    }
  }

  fun cleanUpModel(model: Model) {
    viewModelScope.launch(inferenceDispatcher) {
      LlmChatModelHelper.cleanUp(model = model, onDone = {})
    }
    _uiState.update { it.copy(isModelInitialized = false) }
  }

  fun sendMessage(
    model: Model,
    text: String,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) {
      setProcessing(false)
      return
    }

    addMessage(ChatMessageText(content = text, side = ChatSide.USER))

    viewModelScope.launch(inferenceDispatcher) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      inferenceMutex.withLock {
        setProcessing(true)
        val instance = model.instance as LlmModelInstance
        val conversation = instance.conversation
        val contents = mutableListOf<Content>()
        if (text.trim().isNotEmpty()) contents.add(Content.Text(text))
        try {
          val responseMessage = conversation.sendMessage(Contents.of(contents))
          onDone(responseMessage.toString())
          checkAutoReset()
        } catch (e: Exception) {
          Log.e(TAG, "Inference failed", e)
          onError(e.message ?: context.getString(R.string.unknown_error))
        } finally {
          setProcessing(false)
        }
      }
    }
  }

  fun sendAudioMessage(
    model: Model,
    pcmBytes: ByteArray,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) {
      setProcessing(false)
      return
    }

    val audioClip = ChatMessageAudioClip(audioData = pcmBytes, sampleRate = SAMPLE_RATE, side = ChatSide.USER)
    addMessage(ChatMessageText(content = "🎤 Voice message (${audioClip.getDurationInSeconds().let { "%.1fs".format(it) }})", side = ChatSide.USER))

    viewModelScope.launch(inferenceDispatcher) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      inferenceMutex.withLock {
        setProcessing(true)
        val instance = model.instance as LlmModelInstance
        val conversation = instance.conversation
        try {
          val responseMessage = conversation.sendMessage(
            Contents.of(listOf(Content.AudioBytes(audioClip.genByteArrayForWav())))
          )
          onDone(responseMessage.toString())
          checkAutoReset()
        } catch (e: Exception) {
          Log.e(TAG, "Audio inference failed", e)
          onError(e.message ?: context.getString(R.string.unknown_error))
        } finally {
          setProcessing(false)
        }
      }
    }
  }

  fun sendSmsMessage(
    model: Model,
    sender: String,
    body: String,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) return

    addMessage(ChatMessageText(content = "📱 SMS from $sender:\n$body", side = ChatSide.USER))

    viewModelScope.launch(inferenceDispatcher) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      inferenceMutex.withLock {
        setProcessing(true)
        val instance = model.instance as LlmModelInstance
        val conversation = instance.conversation
        try {
          val responseMessage = conversation.sendMessage(
            Contents.of(listOf(Content.Text(body)))
          )
          onDone(responseMessage.toString())
          checkAutoReset()
        } catch (e: Exception) {
          Log.e(TAG, "SMS inference failed", e)
          onError(e.message ?: context.getString(R.string.unknown_error))
        } finally {
          setProcessing(false)
        }
      }
    }
  }

  fun sendImageMessage(
    model: Model,
    uri: Uri,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) {
      setProcessing(false)
      return
    }

    addMessage(ChatMessageText(content = "🖼 Image submitted", side = ChatSide.USER))
    setProcessing(true)

    viewModelScope.launch(Dispatchers.IO) {
      val bitmap = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(context.contentResolver, uri)
          ) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          }
        } else {
          @Suppress("DEPRECATION")
          android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
        }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to decode image", e)
        null
      }

      if (bitmap == null) {
        setProcessing(false)
        onError(context.getString(R.string.unknown_error))
        return@launch
      }

      withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
          setProcessing(true)
          val instance = model.instance as LlmModelInstance
          val conversation = instance.conversation
          val scaled = scaleBitmapToMax(bitmap, 1024)
          val imageBytes = ByteArrayOutputStream().also {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
          }.toByteArray()
          try {
            val responseMessage = conversation.sendMessage(
              Contents.of(listOf(Content.ImageBytes(imageBytes)))
            )
            onDone(responseMessage.toString())
            checkAutoReset()
          } catch (e: Exception) {
            Log.e(TAG, "Image inference failed", e)
            onError(e.message ?: context.getString(R.string.unknown_error))
          } finally {
            setProcessing(false)
          }
        }
      }
    }
  }

  fun sendWavFileMessage(
    model: Model,
    uri: Uri,
    onDone: (String) -> Unit,
    onError: (String) -> Unit,
  ) {
    if (model.instance == null) {
      setProcessing(false)
      return
    }

    addMessage(ChatMessageText(content = "📎 Audio file uploaded", side = ChatSide.USER))
    setProcessing(true)

    viewModelScope.launch(Dispatchers.IO) {
      val wavBytes = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
      } catch (e: Exception) {
        Log.e(TAG, "Failed to read WAV file", e)
        null
      }

      if (wavBytes == null) {
        setProcessing(false)
        onError(context.getString(R.string.unknown_error))
        return@launch
      }

      withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
          setProcessing(true)
          val instance = model.instance as LlmModelInstance
          val conversation = instance.conversation
          try {
            val responseMessage = conversation.sendMessage(
              Contents.of(listOf(Content.AudioBytes(wavBytes)))
            )
            onDone(responseMessage.toString())
          } catch (e: Exception) {
            Log.e(TAG, "WAV file inference failed", e)
            onError(e.message ?: context.getString(R.string.unknown_error))
          } finally {
            setProcessing(false)
          }
        }
      }
    }
  }

  private fun scaleBitmapToMax(src: Bitmap, maxPx: Int): Bitmap {
    val w = src.width
    val h = src.height
    if (w <= maxPx && h <= maxPx) return src
    val scale = maxPx.toFloat() / maxOf(w, h)
    return Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
  }

  // ── Auto-reset to prevent KV cache OOM ───────────────────────────────────

  private fun checkAutoReset() {
    inferenceCount++
    if (inferenceCount < autoResetThreshold) return
    val model = lastModel ?: return
    inferenceCount = 0
    Log.i(TAG, "Auto-resetting engine after $autoResetThreshold inferences to free KV cache")
    LlmChatModelHelper.cleanUp(model = model, onDone = {
      LlmChatModelHelper.initialize(
        context = context,
        model = model,
        taskId = BuiltInTaskId.LLM_LEDGER,
        supportImage = model.llmSupportImage,
        supportAudio = model.llmSupportAudio,
        onDone = { error ->
          if (error.isEmpty()) {
            addMessage(ChatMessageWarning(context.getString(R.string.engin_reset_message)))
          }
        },
        systemInstruction = Contents.of(lastSystemPrompt),
        tools = emptyList(),
        enableConversationConstrainedDecoding = false,
      )
    })
  }

  // ── TTS ──────────────────────────────────────────────────────────────────

  private var tts: TextToSpeech? = null
  private var ttsReady = false

  private fun ensureTts(onReady: () -> Unit, onError: (String) -> Unit = {}) {
    if (ttsReady) { onReady(); return }
    tts = TextToSpeech(context) { status ->
      if (status != TextToSpeech.SUCCESS) {
        Log.e(TAG, "TTS init failed with status $status")
        onError("Text-to-speech engine failed to initialize (status $status)")
        return@TextToSpeech
      }
      val langResult = tts?.setLanguage(Locale.US) ?: TextToSpeech.LANG_NOT_SUPPORTED
      if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
        Log.w(TAG, "TTS language not supported, trying device default")
        tts?.setLanguage(Locale.getDefault())
      }
      tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(id: String) = _uiState.update { it.copy(isSpeaking = true) }
        override fun onDone(id: String) = _uiState.update { it.copy(isSpeaking = false) }
        @Deprecated("Deprecated in Java")
        override fun onError(id: String) = _uiState.update { it.copy(isSpeaking = false) }
      })
      ttsReady = true
      onReady()
    }
  }

  fun speakSummary(onError: (String) -> Unit = {}) {
    ensureTts(
      onReady = {
        val text = buildTtsSummary(_uiState.value)
        Log.d(TAG, "TTS speaking: $text")
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ledger_summary")
        _uiState.update { it.copy(isSpeaking = true) }
      },
      onError = onError,
    )
  }

  fun stopSpeaking() {
    tts?.stop()
    _uiState.update { it.copy(isSpeaking = false) }
  }

  override fun onCleared() {
    super.onCleared()
    tts?.shutdown()
    tts = null
  }

  // ── Correction loop ───────────────────────────────────────────────────────

  fun deleteTransaction(timestampMs: Long, tools: LedgerTools) {
    tools.deleteTransaction(timestampMs)
    syncFromTools(tools)
  }

  fun deleteTransactionById(timestampMs: Long) {
    viewModelScope.launch(Dispatchers.IO) {
      ledgerRepository.deleteByTimestamp(timestampMs)
    }
    _uiState.update { state ->
      state.copy(recentTransactions = state.recentTransactions.filter { it.timestampMs != timestampMs })
    }
  }

  fun addMessage(message: ChatMessage) {
    _uiState.update { it.copy(messages = it.messages + message) }
  }

  fun clearMessages() {
    _uiState.update { it.copy(messages = listOf()) }
  }

  fun setProcessing(processing: Boolean) {
    _uiState.update { it.copy(processing = processing) }
  }

  fun setResettingEngine(resetting: Boolean) {
    _uiState.update { it.copy(resettingEngine = resetting) }
  }

  fun resetEngine(
    context: Context,
    model: Model,
    systemPrompt: String,
    onError: (String) -> Unit,
  ) {
    viewModelScope.launch(inferenceDispatcher) {
      Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
      setResettingEngine(true)
      LlmChatModelHelper.cleanUp(
        model = model,
        onDone = {
          LlmChatModelHelper.initialize(
            context = context,
            model = model,
            taskId = BuiltInTaskId.LLM_LEDGER,
            supportImage = model.llmSupportImage,
            supportAudio = model.llmSupportAudio,
            onDone = { error ->
              setResettingEngine(false)
              if (error.isNotEmpty()) {
                onError(error)
              }
              addMessage(ChatMessageWarning(context.getString(R.string.engin_reset_message)))
            },
            systemInstruction = Contents.of(systemPrompt),
            tools = emptyList(),
            enableConversationConstrainedDecoding = false,
          )
        },
      )
    }
  }
}

private fun buildTtsSummary(state: LedgerUiState): String {
  if (state.transactionCount == 0) return "No transactions recorded yet. Start by telling me what you sold today."
  val ccy = state.selectedCurrency
  val sb = StringBuilder("Here is your Ledger summary. ")
  sb.append("You recorded ${state.transactionCount} transaction${if (state.transactionCount != 1) "s" else ""}. ")
  sb.append("Revenue: $ccy ${fmtTts(state.revenue)}. ")
  sb.append("Total cost: $ccy ${fmtTts(state.totalCost)}. ")
  if (state.netProfit >= 0) {
    sb.append("Net profit: $ccy ${fmtTts(state.netProfit)}. ")
    if (state.netProfit > 0) sb.append("Good work today! ")
  } else {
    sb.append("Net loss: $ccy ${fmtTts(-state.netProfit)}. Consider reviewing your expenses. ")
  }
  if (state.lowStockItems.isNotEmpty()) {
    val names = state.lowStockItems.take(3).joinToString(", ")
    sb.append("Warning: ${state.lowStockItems.size} item${if (state.lowStockItems.size != 1) "s are" else " is"} running low on stock: $names.")
  }
  return sb.toString()
}

private fun fmtTts(v: Double): String = String.format(Locale.US, "%.0f", v)
