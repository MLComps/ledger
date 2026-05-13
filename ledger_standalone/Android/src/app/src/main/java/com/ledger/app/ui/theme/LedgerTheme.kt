package com.ledger.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green800 = Color(0xFF1B5E20)
private val Green600 = Color(0xFF388E3C)
private val Green200 = Color(0xFFA5D6A7)
private val Green50  = Color(0xFFE8F5E9)

private val LightColors = lightColorScheme(
  primary = Green600,
  onPrimary = Color.White,
  primaryContainer = Green50,
  onPrimaryContainer = Green800,
  secondary = Green800,
  onSecondary = Color.White,
  tertiary = Color(0xFF00695C),
  onTertiary = Color.White,
  tertiaryContainer = Color(0xFFB2DFDB),
  onTertiaryContainer = Color(0xFF00251A),
)

private val DarkColors = darkColorScheme(
  primary = Green200,
  onPrimary = Color(0xFF003300),
  primaryContainer = Green800,
  onPrimaryContainer = Green50,
  secondary = Green200,
  onSecondary = Color(0xFF003300),
  tertiary = Color(0xFF80CBC4),
  onTertiary = Color(0xFF003731),
  tertiaryContainer = Color(0xFF004D40),
  onTertiaryContainer = Color(0xFFB2DFDB),
)

@Composable
fun LedgerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColors
    else -> LightColors
  }

  MaterialTheme(
    colorScheme = colorScheme,
    content = content,
  )
}
