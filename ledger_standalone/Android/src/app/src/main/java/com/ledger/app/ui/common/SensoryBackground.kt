package com.ledger.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * A fluid, animating mesh gradient that reacts to the business "vibe".
 * High profit = Vibrant, warm movement.
 * Neutral/Loss = Calm, deep rhythmic flows.
 */
@Composable
fun SensoryBackground(
  isProfit: Boolean = true,
  modifier: Modifier = Modifier.fillMaxSize()
) {
  val infiniteTransition = rememberInfiniteTransition(label = "mesh")
  
  // Base background color
  val bgColor = MaterialTheme.colorScheme.background

  // Animate colors based on profit/loss state
  val color1 by animateColorAsState(
    if (isProfit) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
    label = "c1"
  )
  val color2 by animateColorAsState(
    if (isProfit) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f),
    label = "c2"
  )

  // Fluid movement parameters
  val time by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = Math.PI.toFloat() * 2,
    animationSpec = infiniteRepeatable(
      animation = tween(24000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "time"
  )

  Canvas(modifier = modifier) {
    drawRect(bgColor)
    
    // Draw fluid orbs
    drawOrb(
      color = color1,
      center = Offset(
        x = size.width * (0.5f + 0.3f * sin(time)),
        y = size.height * (0.3f + 0.2f * cos(time * 0.8f))
      ),
      radius = size.width * 0.8f
    )

    drawOrb(
      color = color2,
      center = Offset(
        x = size.width * (0.2f + 0.2f * cos(time * 0.5f)),
        y = size.height * (0.7f + 0.3f * sin(time * 1.2f))
      ),
      radius = size.width * 0.9f
    )
  }
}

private fun DrawScope.drawOrb(color: Color, center: Offset, radius: Float) {
  drawCircle(
    brush = Brush.radialGradient(
      colors = listOf(color, Color.Transparent),
      center = center,
      radius = radius
    ),
    center = center,
    radius = radius
  )
}
