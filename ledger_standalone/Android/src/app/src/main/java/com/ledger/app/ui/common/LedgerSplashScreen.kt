package com.ledger.app.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LedgerSplashScreen(onAnimationFinished: () -> Unit) {
  var startAnimation by remember { mutableStateOf(false) }
  val alphaAnim by animateFloatAsState(
    targetValue = if (startAnimation) 1f else 0f,
    animationSpec = tween(1000, easing = FastOutSlowInEasing),
    label = "alpha"
  )
  val scaleAnim by animateFloatAsState(
    targetValue = if (startAnimation) 1.2f else 0.8f,
    animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
    label = "scale"
  )

  LaunchedEffect(Unit) {
    startAnimation = true
    delay(1200)
    onAnimationFinished()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center
  ) {
    SensoryBackground(isProfit = true)
    
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .scale(scaleAnim)
          .alpha(alphaAnim)
          .size(120.dp)
          .background(Color(0xFF0B0D0F), CircleShape)
          .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          Icons.AutoMirrored.Rounded.TrendingUp,
          contentDescription = null,
          modifier = Modifier.size(70.dp),
          tint = MaterialTheme.colorScheme.primaryContainer
        )
      }
      
      Spacer(Modifier.height(24.dp))
      
      Text(
        text = "LEDGER",
        style = MaterialTheme.typography.displaySmall.copy(
          letterSpacing = 8.sp,
          fontWeight = FontWeight.Black,
          color = MaterialTheme.colorScheme.onBackground
        ),
        modifier = Modifier.alpha(alphaAnim)
      )
      
      Text(
        text = "Your Business, Intelligent.",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.alpha(alphaAnim).padding(top = 8.dp)
      )
    }
  }
}
