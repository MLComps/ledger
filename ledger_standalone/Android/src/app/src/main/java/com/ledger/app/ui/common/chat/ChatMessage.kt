package com.ledger.app.ui.common.chat

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp

private const val TAG = "LedgerChatMessage"

enum class ChatMessageType {
  INFO,
  WARNING,
  ERROR,
  TEXT,
  AUDIO_CLIP,
  LOADING,
  CLARIFICATION,
  RECOMMENDATION,
}

enum class ChatSide {
  USER,
  AGENT,
  SYSTEM,
}

open class ChatMessage(
  open val type: ChatMessageType,
  open val side: ChatSide,
  open val latencyMs: Float = -1f,
  open val accelerator: String = "",
  open val hideSenderLabel: Boolean = false,
  open val disableBubbleShape: Boolean = false,
)

class ChatMessageLoading(
  var extraProgressLabel: String = "",
  override val accelerator: String = "",
) : ChatMessage(type = ChatMessageType.LOADING, side = ChatSide.AGENT, accelerator = accelerator)

class ChatMessageInfo(val content: String) :
  ChatMessage(type = ChatMessageType.INFO, side = ChatSide.SYSTEM)

class ChatMessageWarning(val content: String) :
  ChatMessage(type = ChatMessageType.WARNING, side = ChatSide.SYSTEM)

class ChatMessageError(val content: String) :
  ChatMessage(type = ChatMessageType.ERROR, side = ChatSide.SYSTEM)

open class ChatMessageText(
  val content: String,
  override val side: ChatSide,
  override val latencyMs: Float = 0f,
  val isMarkdown: Boolean = true,
  override val accelerator: String = "",
  override val hideSenderLabel: Boolean = false,
  var data: Any? = null,
  val timestampMs: Long = System.currentTimeMillis(),
) :
  ChatMessage(
    type = ChatMessageType.TEXT,
    side = side,
    latencyMs = latencyMs,
    accelerator = accelerator,
    hideSenderLabel = hideSenderLabel,
  )

class ChatMessageClarification(val question: String) :
  ChatMessage(type = ChatMessageType.CLARIFICATION, side = ChatSide.AGENT)

class ChatMessageRecommendation(val text: String) :
  ChatMessage(type = ChatMessageType.RECOMMENDATION, side = ChatSide.AGENT)

class ChatMessageAudioClip(
  val audioData: ByteArray,
  val sampleRate: Int,
  override val side: ChatSide,
  override val latencyMs: Float = 0f,
) : ChatMessage(type = ChatMessageType.AUDIO_CLIP, side = side, latencyMs = latencyMs) {

  fun genByteArrayForWav(): ByteArray {
    val header = ByteArray(44)
    val pcmDataSize = audioData.size
    val wavFileSize = pcmDataSize + 44
    val channels = 1
    val bitsPerSample: Short = 16
    val byteRate = sampleRate * channels * bitsPerSample / 8
    Log.d(TAG, "Wav metadata: sampleRate: $sampleRate")

    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (wavFileSize and 0xff).toByte()
    header[5] = (wavFileSize shr 8 and 0xff).toByte()
    header[6] = (wavFileSize shr 16 and 0xff).toByte()
    header[7] = (wavFileSize shr 24 and 0xff).toByte()
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1
    header[21] = 0
    header[22] = channels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = (sampleRate shr 8 and 0xff).toByte()
    header[26] = (sampleRate shr 16 and 0xff).toByte()
    header[27] = (sampleRate shr 24 and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = (byteRate shr 8 and 0xff).toByte()
    header[30] = (byteRate shr 16 and 0xff).toByte()
    header[31] = (byteRate shr 24 and 0xff).toByte()
    header[32] = (channels * bitsPerSample / 8).toByte()
    header[33] = 0
    header[34] = bitsPerSample.toByte()
    header[35] = (bitsPerSample.toInt() shr 8 and 0xff).toByte()
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (pcmDataSize and 0xff).toByte()
    header[41] = (pcmDataSize shr 8 and 0xff).toByte()
    header[42] = (pcmDataSize shr 16 and 0xff).toByte()
    header[43] = (pcmDataSize shr 24 and 0xff).toByte()

    return header + audioData
  }

  fun getDurationInSeconds(): Float {
    val bytesPerSample = 2
    val bytesPerFrame = bytesPerSample * 1
    val totalFrames = audioData.size.toFloat() / bytesPerFrame
    return totalFrames / sampleRate
  }
}
