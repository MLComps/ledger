package com.ledger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  ledgerVm: LedgerViewModel = hiltViewModel(),
) {
  val uiState by ledgerVm.uiState.collectAsState()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val privacyMode = LocalPrivacyMode.current
  var showCurrencyPicker by remember { mutableStateOf(false) }

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

  Column(modifier = Modifier.fillMaxSize()) {
      LargeTopAppBar(
        title = { Text("Settings") },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface,
          scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
      )

      LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
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
                Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
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

      }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
  Column {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
  iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
    Spacer(Modifier.width(14.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
