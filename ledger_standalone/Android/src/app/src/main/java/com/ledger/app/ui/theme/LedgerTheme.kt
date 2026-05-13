package com.ledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Light palette ─────────────────────────────────────────────────────────────
// Seed: deep forest teal-green #006B54 — trustworthy, growth, prosperity
private val LightColors = lightColorScheme(
  primary                = Color(0xFF006B54),
  onPrimary              = Color(0xFFFFFFFF),
  primaryContainer       = Color(0xFF88FBCD),
  onPrimaryContainer     = Color(0xFF00201A),
  secondary              = Color(0xFF4C6359),
  onSecondary            = Color(0xFFFFFFFF),
  secondaryContainer     = Color(0xFFCEE9DC),
  onSecondaryContainer   = Color(0xFF082018),
  tertiary               = Color(0xFF3D6373),
  onTertiary             = Color(0xFFFFFFFF),
  tertiaryContainer      = Color(0xFFC1E8FA),
  onTertiaryContainer    = Color(0xFF001F29),
  error                  = Color(0xFFBA1A1A),
  onError                = Color(0xFFFFFFFF),
  errorContainer         = Color(0xFFFFDAD6),
  onErrorContainer       = Color(0xFF410002),
  background             = Color(0xFFF5FBF7),
  onBackground           = Color(0xFF181D19),
  surface                = Color(0xFFF5FBF7),
  onSurface              = Color(0xFF181D19),
  surfaceVariant         = Color(0xFFDCE5DC),
  onSurfaceVariant       = Color(0xFF404943),
  outline                = Color(0xFF707972),
  outlineVariant         = Color(0xFFC0C9C1),
  inverseSurface         = Color(0xFF2D322E),
  inverseOnSurface       = Color(0xFFECF2ED),
  inversePrimary         = Color(0xFF6CDEB6),
  surfaceTint            = Color(0xFF006B54),
)

// ── Dark palette ──────────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
  primary                = Color(0xFF6CDEB6),
  onPrimary              = Color(0xFF00382B),
  primaryContainer       = Color(0xFF005140),
  onPrimaryContainer     = Color(0xFF88FBCD),
  secondary              = Color(0xFFB2CCBF),
  onSecondary            = Color(0xFF1E352A),
  secondaryContainer     = Color(0xFF354B40),
  onSecondaryContainer   = Color(0xFFCEE9DC),
  tertiary               = Color(0xFFA5CCE0),
  onTertiary             = Color(0xFF063444),
  tertiaryContainer      = Color(0xFF234B5A),
  onTertiaryContainer    = Color(0xFFC1E8FA),
  error                  = Color(0xFFFFB4AB),
  onError                = Color(0xFF690005),
  errorContainer         = Color(0xFF93000A),
  onErrorContainer       = Color(0xFFFFDAD6),
  background             = Color(0xFF0F1511),
  onBackground           = Color(0xFFE1E4DF),
  surface                = Color(0xFF0F1511),
  onSurface              = Color(0xFFE1E4DF),
  surfaceVariant         = Color(0xFF3C4540),
  onSurfaceVariant       = Color(0xFFBCC5BB),
  outline                = Color(0xFF869186),
  outlineVariant         = Color(0xFF3C4540),
  inverseSurface         = Color(0xFFE1E4DF),
  inverseOnSurface       = Color(0xFF2D322E),
  inversePrimary         = Color(0xFF006B54),
  surfaceTint            = Color(0xFF6CDEB6),
)

@Composable
fun LedgerTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = LedgerTypography,
    content = content,
  )
}
