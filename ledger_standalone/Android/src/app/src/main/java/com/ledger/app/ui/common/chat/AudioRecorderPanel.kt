package com.ledger.app.ui.common.chat

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ledger.app.common.calculatePeakAmplitude
import com.ledger.app.data.MAX_AUDIO_CLIP_DURATION_SEC
import com.ledger.app.data.SAMPLE_RATE
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val TAG = "LedgerAudioRecorderPanel"
private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
private const val PANEL_ALPHA = 0.7f

@Composable
fun AudioRecorderPanel(
  tintColor: Color,
  onAmplitudeChanged: (Int) -> Unit,
  onSendAudioClip: (ByteArray) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var isRecording by remember { mutableStateOf(false) }
  val elapsedMs = remember { mutableLongStateOf(0L) }
  val audioRecordState = remember { mutableStateOf<AudioRecord?>(null) }
  val audioStream = remember { ByteArrayOutputStream() }

  val elapsedSeconds by remember {
    derivedStateOf { "%.1f".format(elapsedMs.longValue.toFloat() / 1000f) }
  }

  DisposableEffect(Unit) { onDispose { audioRecordState.value?.release() } }

  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(
      onClick = {
        if (isRecording) {
          val unused = stopRecording(audioRecordState = audioRecordState, audioStream = audioStream)
          isRecording = false
        }
        onClose()
      },
      colors =
        IconButtonDefaults.iconButtonColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA)
        ),
    ) {
      Icon(
        Icons.Rounded.Close,
        contentDescription = "Close",
        tint = MaterialTheme.colorScheme.onSurface,
      )
    }

    Row(
      modifier =
        Modifier.clip(CircleShape)
          .weight(1f)
          .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = PANEL_ALPHA))
          .padding(start = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      if (!isRecording) {
        Text(
          "Tap the record button to start",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      } else {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.size(8.dp).background(Color.Red, CircleShape)
          )
          Text("$elapsedSeconds s")
        }
      }

      IconButton(
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        onClick = {
          coroutineScope.launch {
            if (!isRecording) {
              isRecording = true
              startRecording(
                context = context,
                audioRecordState = audioRecordState,
                audioStream = audioStream,
                elapsedMs = elapsedMs,
                onAmplitudeChanged = onAmplitudeChanged,
                onMaxDurationReached = {
                  val curRecordedBytes =
                    stopRecording(audioRecordState = audioRecordState, audioStream = audioStream)
                  onSendAudioClip(curRecordedBytes)
                  isRecording = false
                },
              )
            } else {
              val curRecordedBytes =
                stopRecording(audioRecordState = audioRecordState, audioStream = audioStream)
              onSendAudioClip(curRecordedBytes)
              isRecording = false
            }
          }
        },
        colors = IconButtonDefaults.iconButtonColors(containerColor = tintColor),
      ) {
        Icon(
          if (isRecording) Icons.Rounded.ArrowUpward else Icons.Rounded.Mic,
          contentDescription = if (isRecording) "Send audio clip" else "Start recording",
          tint = Color.White,
        )
      }
    }
  }
}

@SuppressLint("MissingPermission")
private suspend fun startRecording(
  context: Context,
  audioRecordState: MutableState<AudioRecord?>,
  audioStream: ByteArrayOutputStream,
  elapsedMs: MutableLongState,
  onAmplitudeChanged: (Int) -> Unit,
  onMaxDurationReached: () -> Unit,
) {
  Log.d(TAG, "Start recording...")
  val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)

  audioRecordState.value?.release()
  val recorder =
    AudioRecord(
      MediaRecorder.AudioSource.MIC,
      SAMPLE_RATE,
      CHANNEL_CONFIG,
      AUDIO_FORMAT,
      minBufferSize,
    )

  audioRecordState.value = recorder
  val buffer = ByteArray(minBufferSize)

  coroutineScope {
    launch(Dispatchers.IO) {
      recorder.startRecording()

      val startMs = System.currentTimeMillis()
      elapsedMs.longValue = 0L
      while (audioRecordState.value?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
        val bytesRead = recorder.read(buffer, 0, buffer.size)
        if (bytesRead > 0) {
          val currentAmplitude = calculatePeakAmplitude(buffer = buffer, bytesRead = bytesRead)
          onAmplitudeChanged(currentAmplitude)
          audioStream.write(buffer, 0, bytesRead)
        }
        elapsedMs.longValue = System.currentTimeMillis() - startMs
        if (elapsedMs.longValue >= MAX_AUDIO_CLIP_DURATION_SEC * 1000) {
          onMaxDurationReached()
          break
        }
      }
    }
  }
}

private fun stopRecording(
  audioRecordState: MutableState<AudioRecord?>,
  audioStream: ByteArrayOutputStream,
): ByteArray {
  Log.d(TAG, "Stopping recording...")

  val recorder = audioRecordState.value
  if (recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
    recorder.stop()
  }
  recorder?.release()
  audioRecordState.value = null

  val recordedBytes = audioStream.toByteArray()
  audioStream.reset()
  Log.d(TAG, "Stopped. Recorded ${recordedBytes.size} bytes.")

  return recordedBytes
}
