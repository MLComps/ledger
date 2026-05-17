package com.ledger.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Light palette ─────────────────────────────────────────────────────────────
// "Modern Air" - Ultra-clean, high-end, authoritative
private val LightColors = lightColorScheme(
  primary                = Color(0xFF000000), 
  onPrimary              = Color(0xFFFFFFFF),
  primaryContainer       = Color(0xFFA4FF00), 
  onPrimaryContainer     = Color(0xFF000000),
  secondary              = Color(0xFF6200EE), 
  onSecondary            = Color(0xFFFFFFFF),
  secondaryContainer     = Color(0xFFF3E5F5),
  onSecondaryContainer   = Color(0xFF4A0072),
  tertiary               = Color(0xFF00BFA5), 
  onTertiary             = Color(0xFFFFFFFF),
  tertiaryContainer      = Color(0xFFE0F2F1),
  onTertiaryContainer    = Color(0xFF004D40),
  error                  = Color(0xFFD50000),
  onError                = Color(0xFFFFFFFF),
  background             = Color(0xFFF9FAFB), 
  surface                = Color(0xFFFFFFFF),
  onSurface              = Color(0xFF111827),
  surfaceVariant         = Color(0xFFF3F4F6), 
  onSurfaceVariant       = Color(0xFF374151),
  outline                = Color(0xFFD1D5DB),
  surfaceTint            = Color(0xFF000000),
)

// ── Dark palette ──────────────────────────────────────────────────────────────
// "Deep Obsidian" - Luminous, high-contrast, premium
private val DarkColors = darkColorScheme(
  primary                = Color(0xFFA4FF00), // Cyber Lime (Glows)
  onPrimary              = Color(0xFF003900),
  primaryContainer       = Color(0xFF005300),
  onPrimaryContainer     = Color(0xFFA4FF00),
  secondary              = Color(0xFF00E5FF), // Electric Cyan
  onSecondary            = Color(0xFF00363D),
  secondaryContainer     = Color(0xFF004E56),
  onSecondaryContainer   = Color(0xFF00E5FF),
  tertiary               = Color(0xFFFFAB00), // Electric Amber
  onTertiary             = Color(0xFF472A00),
  tertiaryContainer      = Color(0xFF663D00),
  onTertiaryContainer    = Color(0xFFFFAB00),
  error                  = Color(0xFFFF5252),
  onError                = Color(0xFF690005),
  background             = Color(0xFF0B0D0F), // Deep Obsidian
  surface                = Color(0xFF121417), // Dark Charcoal
  onSurface              = Color(0xFFE1E4DF),
  surfaceVariant         = Color(0xFF1E2124),
  onSurfaceVariant       = Color(0xFFBCC5BB),
  outline                = Color(0xFF869186),
  inverseSurface         = Color(0xFFE1E4DF),
  inverseOnSurface       = Color(0xFF0B0D0F),
  inversePrimary         = Color(0xFF1B5E20),
  surfaceTint            = Color(0xFFA4FF00),
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
