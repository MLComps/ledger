package com.ledger.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(
  val icon: ImageVector,
  val title: String,
  val body: String,
)

private val PAGES = listOf(
  OnboardingPage(
    icon = Icons.Rounded.AccountBalanceWallet,
    title = "Welcome to Ledger",
    body = "Your offline AI bookkeeping assistant for market vendors. Gemma 4 runs entirely on your device — no cloud, no data leaving your phone.",
  ),
  OnboardingPage(
    icon = Icons.Rounded.Mic,
    title = "Speak or Type",
    body = "Tell Ledger about sales, purchases and expenses in plain language — in any language. \"Sold 10 kg of rice for 500\" and it's recorded.",
  ),
  OnboardingPage(
    icon = Icons.Rounded.Sms,
    title = "Auto-process SMS",
    body = "Enable SMS monitoring to automatically extract transactions from M-Pesa, bank alerts, and payment notifications as they arrive.",
  ),
  OnboardingPage(
    icon = Icons.Rounded.PhotoCamera,
    title = "Photograph Receipts",
    body = "Snap a photo of a receipt, bill, or stock label. Ledger reads the image and records the transaction — no typing needed.",
  ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(onDismiss: () -> Unit) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()
  var page by rememberSaveable { mutableIntStateOf(0) }

  fun dismiss() {
    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
  }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp)
        .navigationBarsPadding(),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      AnimatedContent(
        targetState = page,
        transitionSpec = {
          val forward = targetState > initialState
          (slideInHorizontally { if (forward) it else -it } + fadeIn()) togetherWith
            (slideOutHorizontally { if (forward) -it else it } + fadeOut())
        },
        label = "onboarding_page",
      ) { idx ->
        val p = PAGES[idx]
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Spacer(Modifier.height(8.dp))
          Icon(
            imageVector = p.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
          )
          Spacer(Modifier.height(20.dp))
          Text(
            text = p.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(12.dp))
          Text(
            text = p.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(32.dp))
        }
      }

      // Dot indicators
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PAGES.indices.forEach { i ->
          Box(
            modifier = Modifier
              .size(if (i == page) 8.dp else 6.dp)
              .background(
                color = if (i == page) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
              )
          )
        }
      }

      Spacer(Modifier.height(24.dp))

      // Navigation row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = { dismiss() }) {
          Text("Skip")
        }
        Button(
          onClick = {
            if (page < PAGES.lastIndex) page++ else dismiss()
          }
        ) {
          Text(if (page < PAGES.lastIndex) "Next" else "Get Started")
        }
      }

      Spacer(Modifier.height(16.dp))
    }
  }
}
