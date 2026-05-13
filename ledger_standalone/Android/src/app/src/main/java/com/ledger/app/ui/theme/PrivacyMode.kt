package com.ledger.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

val LocalPrivacyMode = compositionLocalOf { mutableStateOf(false) }
