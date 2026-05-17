package com.ledger.app.llm

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ledger.app.common.cleanUpMediapipeTaskErrorMessage
import com.ledger.app.data.Accelerator
import com.ledger.app.data.ConfigKeys
import com.ledger.app.data.DEFAULT_MAX_TOKEN
import com.ledger.app.data.DEFAULT_TEMPERATURE
import com.ledger.app.data.DEFAULT_TOPK
import com.ledger.app.data.DEFAULT_TOPP
import com.ledger.app.data.DEFAULT_VISION_ACCELERATOR
import com.ledger.app.data.Model
import com.ledger.app.data.ModelCapability
import com.ledger.app.runtime.CleanUpListener
import com.ledger.app.runtime.LlmModelHelper
import com.ledger.app.runtime.LlmModelInstance
import com.ledger.app.runtime.ResultListener
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Channel
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ToolProvider
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope

private const val TAG = "LedgerLlmChatModelHelper"

object LlmChatModelHelper : LlmModelHelper {
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()

  @OptIn(ExperimentalApi::class)
  override fun initialize(
    context: Context,
    model: Model,
    taskId: String,
    supportImage: Boolean,
    supportAudio: Boolean,
    onDone: (String) -> Unit,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    enableThinking: Boolean,
    coroutineScope: CoroutineScope?,
  ) {
    val maxTokens =
      model.getIntConfigValue(key = ConfigKeys.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    val shouldEnableImage = supportImage
    val shouldEnableAudio = supportAudio
    val preferredBackend = Backend.CPU()
    Log.d(TAG, "Preferred backend: $preferredBackend")

    val modelPath = model.getPath(context = context)
    val cacheDir = if (modelPath.startsWith("/data/local/tmp"))
      context.getExternalFilesDir(null)?.absolutePath else null

    var supportsSpeculativeDecoding = false
    try {
      com.google.ai.edge.litertlm.Capabilities(modelPath).use {
        supportsSpeculativeDecoding = it.hasSpeculativeDecodingSupport()
      }
    } catch (e: Exception) {
      // Ignore exceptions and assume not supported.
    }

    try {
      var speculativeDecoding = false
      if (
        supportsSpeculativeDecoding &&
          model.capabilityToTaskTypes[ModelCapability.SPECULATIVE_DECODING]?.contains(taskId) ==
            true
      ) {
        speculativeDecoding =
          model.getBooleanConfigValue(
            key = ConfigKeys.ENABLE_SPECULATIVE_DECODING,
            defaultValue = false,
          )
      }
      ExperimentalFlags.enableSpeculativeDecoding = speculativeDecoding
      Log.d(TAG, "Speculative decoding enabled: $speculativeDecoding")

      // Try GPU vision first; fall back to CPU if the device doesn't support it.
      val engine = run {
        if (shouldEnableImage) {
          try {
            Engine(EngineConfig(
              modelPath = modelPath, backend = preferredBackend,
              visionBackend = Backend.GPU(), audioBackend = if (shouldEnableAudio) Backend.CPU() else null,
              maxNumTokens = maxTokens, cacheDir = cacheDir,
            )).also { it.initialize() }
          } catch (e: Exception) {
            Log.w(TAG, "GPU vision backend failed, falling back to CPU: ${e.message}")
            Engine(EngineConfig(
              modelPath = modelPath, backend = preferredBackend,
              visionBackend = Backend.CPU(), audioBackend = if (shouldEnableAudio) Backend.CPU() else null,
              maxNumTokens = maxTokens, cacheDir = cacheDir,
            )).also { it.initialize() }
          }
        } else {
          Engine(EngineConfig(
            modelPath = modelPath, backend = preferredBackend,
            visionBackend = null, audioBackend = if (shouldEnableAudio) Backend.CPU() else null,
            maxNumTokens = maxTokens, cacheDir = cacheDir,
          )).also { it.initialize() }
        }
      }
      ExperimentalFlags.enableSpeculativeDecoding = false

      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      ExperimentalFlags.filterChannelContentFromKvCache = enableThinking
      val thinkingChannels = if (enableThinking)
        listOf(Channel(channelName = "thought", start = "<thinking>", end = "</thinking>"))
      else emptyList()
      val conversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (preferredBackend is Backend.NPU) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
            automaticToolCalling = tools.isNotEmpty(),
            channels = thinkingChannels,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      ExperimentalFlags.filterChannelContentFromKvCache = false
      model.instance = LlmModelInstance(engine = engine, conversation = conversation)
    } catch (e: Exception) {
      onDone(cleanUpMediapipeTaskErrorMessage(e.message ?: "Unknown error"))
      return
    }
    onDone("")
  }

  @OptIn(ExperimentalApi::class)
  override fun resetConversation(
    model: Model,
    supportImage: Boolean,
    supportAudio: Boolean,
    systemInstruction: Contents?,
    tools: List<ToolProvider>,
    enableConversationConstrainedDecoding: Boolean,
    enableThinking: Boolean,
  ) {
    try {
      Log.d(TAG, "Resetting conversation for model '${model.name}'")

      val instance = model.instance as LlmModelInstance? ?: return
      instance.conversation.close()

      val engine = instance.engine
      val topK = model.getIntConfigValue(key = ConfigKeys.TOPK, defaultValue = DEFAULT_TOPK)
      val topP = model.getFloatConfigValue(key = ConfigKeys.TOPP, defaultValue = DEFAULT_TOPP)
      val temperature =
        model.getFloatConfigValue(key = ConfigKeys.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
      val accelerator =
        model.getStringConfigValue(
          key = ConfigKeys.ACCELERATOR,
          defaultValue = Accelerator.GPU.label,
        )
      ExperimentalFlags.enableConversationConstrainedDecoding =
        enableConversationConstrainedDecoding
      ExperimentalFlags.filterChannelContentFromKvCache = enableThinking
      val thinkingChannels = if (enableThinking)
        listOf(Channel(channelName = "thought", start = "<thinking>", end = "</thinking>"))
      else emptyList()
      val newConversation =
        engine.createConversation(
          ConversationConfig(
            samplerConfig =
              if (accelerator == Accelerator.NPU.label || accelerator == Accelerator.TPU.label) {
                null
              } else {
                SamplerConfig(
                  topK = topK,
                  topP = topP.toDouble(),
                  temperature = temperature.toDouble(),
                )
              },
            systemInstruction = systemInstruction,
            tools = tools,
            automaticToolCalling = tools.isNotEmpty(),
            channels = thinkingChannels,
          )
        )
      ExperimentalFlags.enableConversationConstrainedDecoding = false
      ExperimentalFlags.filterChannelContentFromKvCache = false
      instance.conversation = newConversation

      Log.d(TAG, "Resetting done")
    } catch (e: Exception) {
      Log.d(TAG, "Failed to reset conversation", e)
    }
  }

  override fun cleanUp(model: Model, onDone: () -> Unit) {
    if (model.instance == null) {
      onDone()
      return
    }

    val instance = model.instance as LlmModelInstance

    try {
      instance.conversation.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the conversation: ${e.message}")
    }

    try {
      instance.engine.close()
    } catch (e: Exception) {
      Log.e(TAG, "Failed to close the engine: ${e.message}")
    }

    val onCleanUp = cleanUpListeners.remove(model.name)
    onCleanUp?.invoke()
    model.instance = null

    onDone()
    Log.d(TAG, "Clean up done.")
  }

  override fun stopResponse(model: Model) {
    val instance = model.instance as? LlmModelInstance ?: return
    instance.conversation.cancelProcess()
  }

  override fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
    onError: (message: String) -> Unit,
    images: List<Bitmap>,
    audioClips: List<ByteArray>,
    coroutineScope: CoroutineScope?,
    extraContext: Map<String, String>?,
  ) {
    val instance = model.instance as? LlmModelInstance
    if (instance == null) {
      onError("LlmModelInstance is not initialized.")
      return
    }

    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    val conversation = instance.conversation

    val contents = mutableListOf<Content>()
    for (image in images) {
      contents.add(Content.ImageBytes(image.toPngByteArray()))
    }
    for (audioClip in audioClips) {
      contents.add(Content.AudioBytes(audioClip))
    }
    if (input.trim().isNotEmpty()) {
      contents.add(Content.Text(input))
    }

    conversation.sendMessageAsync(
      Contents.of(contents),
      object : MessageCallback {
        override fun onMessage(message: Message) {
          resultListener(message.toString(), false, message.channels["thought"])
        }

        override fun onDone() {
          resultListener("", true, null)
        }

        override fun onError(throwable: Throwable) {
          if (throwable is CancellationException) {
            Log.i(TAG, "The inference is cancelled.")
            resultListener("", true, null)
          } else {
            Log.e(TAG, "onError", throwable)
            onError("Error: ${throwable.message}")
          }
        }
      },
      extraContext ?: emptyMap(),
    )
  }

  private fun Bitmap.toPngByteArray(): ByteArray {
    val stream = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
  }
}
