package com.ledger.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.ui.common.SensoryBackground
import com.ledger.app.ui.ledger.LedgerViewModel
import com.ledger.app.ui.theme.LocalPrivacyMode

private val CURRENCIES = listOf(
  "KES" to "Kenya Shilling",
  "NGN" to "Nigeria Naira",
  "GHS" to "Ghana Cedi",
  "UGX" to "Uganda Shilling",
  "TZS" to "Tanzania Shilling",
  "ETB" to "Ethiopia Birr",
  "ZAR" to "South Africa Rand",
  "RWF" to "Rwanda Franc",
  "USD" to "US Dollar",
  "EUR" to "Euro",
  "GBP" to "British Pound",
)

private val VALIDATION_OPTIONS = listOf("off" to "Off", "critical" to "Low conf.", "all" to "All")
private val THEME_OPTIONS = listOf("light" to "Light", "system" to "System", "dark" to "Dark")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  ledgerVm: LedgerViewModel = hiltViewModel(),
) {
  val uiState by ledgerVm.uiState.collectAsState()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val privacyMode = LocalPrivacyMode.current
  val context = LocalContext.current
  var showCurrencyPicker by remember { mutableStateOf(false) }

  var smsPermissionGranted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED)
  }
  val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    smsPermissionGranted = granted
    if (granted) ledgerVm.saveSmsEnabled(true)
  }

  if (showCurrencyPicker) {
    AlertDialog(
      onDismissRequest = { showCurrencyPicker = false },
      title = { Text("Select Currency") },
      text = {
        Column {
          CURRENCIES.forEach { (code, name) ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clickable { ledgerVm.saveCurrency(code, buildSystemPrompt(code)); showCurrencyPicker = false }
                .padding(vertical = 10.dp, horizontal = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              if (code == uiState.selectedCurrency) {
                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
              } else {
                Spacer(Modifier.size(16.dp))
              }
              Spacer(Modifier.width(8.dp))
              Text(code, style = MaterialTheme.typography.bodyMedium, fontWeight = if (code == uiState.selectedCurrency) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(44.dp))
              Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { showCurrencyPicker = false }) { Text("Close") } },
    )
  }

  Box(modifier = Modifier.fillMaxSize()) {
    SensoryBackground()

    Column(modifier = Modifier.fillMaxSize()) {
      LargeTopAppBar(
        title = { Text("Settings", fontWeight = FontWeight.Black) },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
      )

      LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          SettingsSection(title = "Appearance") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Rounded.DarkMode, null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                  Text("Theme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = when (uiState.themeMode) {
                      "light" -> "Always light"
                      "dark" -> "Always dark"
                      else -> "Follows system setting"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                THEME_OPTIONS.forEachIndexed { index, (value, label) ->
                  SegmentedButton(
                    selected = uiState.themeMode == value,
                    onClick = { ledgerVm.saveThemeMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, THEME_OPTIONS.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                  )
                }
              }
            }
          }
        }

        item {
          SettingsSection(title = "Display") {
            SettingsRow(
              icon = Icons.Rounded.Visibility,
              label = "Hide amounts",
              sublabel = if (privacyMode.value) "Amounts are hidden" else "Amounts are visible",
              trailing = {
                androidx.compose.material3.Switch(
                  checked = privacyMode.value,
                  onCheckedChange = { privacyMode.value = it },
                )
              },
            )
          }
        }

        item {
          SettingsSection(title = "Currency") {
            SettingsRow(
              icon = Icons.Rounded.CurrencyExchange,
              label = "Active currency",
              sublabel = CURRENCIES.find { it.first == uiState.selectedCurrency }?.let {
                "${it.first} — ${it.second}"
              } ?: uiState.selectedCurrency,
              onClick = { showCurrencyPicker = true },
            )
          }
        }

        item {
          SettingsSection(title = "Transaction Validation") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(20.dp), tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column {
                  Text("Confirm before saving", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                  Text(
                    text = when (uiState.validationMode) {
                      "all" -> "Every transaction shows a confirmation dialog"
                      "critical" -> "Only low-confidence entries need confirmation"
                      else -> "Transactions are saved immediately"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                VALIDATION_OPTIONS.forEachIndexed { index, (value, label) ->
                  SegmentedButton(
                    selected = uiState.validationMode == value,
                    onClick = { ledgerVm.saveValidationMode(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, VALIDATION_OPTIONS.size),
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                  )
                }
              }
            }
          }
        }

        item {
          SettingsSection(title = "SMS Monitoring") {
            SettingsRow(
              icon = Icons.Rounded.Sms,
              label = "Auto-extract from SMS",
              sublabel = when {
                uiState.smsEnabled && smsPermissionGranted -> "Reading incoming messages for transactions"
                uiState.smsEnabled && !smsPermissionGranted -> "Permission required — tap to grant"
                else -> "Off — incoming M-Pesa / bank SMS ignored"
              },
              trailing = {
                androidx.compose.material3.Switch(
                  checked = uiState.smsEnabled && smsPermissionGranted,
                  onCheckedChange = { enable ->
                    when {
                      enable && !smsPermissionGranted -> smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                      enable -> ledgerVm.saveSmsEnabled(true)
                      else -> ledgerVm.saveSmsEnabled(false)
                    }
                  },
                )
              },
            )
          }
        }

      }
    }
  }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  val cardShape = RoundedCornerShape(24.dp)
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(cardShape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
        .border(
          width = 1.dp,
          brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.25f), Color.Transparent)
          ),
          shape = cardShape,
        )
    ) {
      content()
    }
  }
}

@Composable
private fun SettingsRow(
  icon: ImageVector,
  label: String,
  sublabel: String? = null,
  iconTint: Color = Color.Unspecified,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
          )
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
    }
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
      if (sublabel != null) {
        Text(sublabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    if (trailing != null) {
      Spacer(Modifier.width(8.dp))
      trailing()
    }
  }
}

// Minimal system prompt builder for currency change from settings
private fun buildSystemPrompt(currency: String) =
  "You are Ledger. Currency defaults to $currency."
