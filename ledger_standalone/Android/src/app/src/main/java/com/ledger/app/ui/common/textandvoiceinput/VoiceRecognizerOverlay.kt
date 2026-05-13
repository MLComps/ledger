package com.ledger.app.ui.common.textandvoiceinput

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.common.AudioAnimation

private const val TAG = "LedgerVROverlay"

@Composable
fun VoiceRecognizerOverlay(
  tintColor: Color,
  viewModel: HoldToDictateViewModel,
  bottomPadding: Dp,
  curAmplitude: Int,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    AudioAnimation(bgColor = Color.Black.copy(alpha = 0.8f), amplitude = curAmplitude)

    Text(
      uiState.recognizedText.ifEmpty { "Listening..." },
      modifier =
        Modifier.padding(horizontal = 16.dp)
          .padding(bottom = (48.dp + bottomPadding) / 2)
          .align(Alignment.Center),
      color = Color.White,
    )

    Column(
      modifier =
        Modifier.padding(bottom = bottomPadding).padding(horizontal = 16.dp).fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(
          "Release to send",
          color = Color.Black,
          style = MaterialTheme.typography.labelMedium,
        )
        Text(
          "Slide up to cancel",
          color = Color.Black,
          style = MaterialTheme.typography.labelMedium,
        )
      }

      Box(
        modifier =
          modifier
            .pointerInput(Unit) {}
            .clip(CircleShape)
            .background(tintColor)
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text("Listening...", color = Color.White)
      }
    }
  }
}
