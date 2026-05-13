package com.ledger.app.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay

/**
 * A "Ceremony" dialog for closing the day. 
 * Features a high-end "Digital Receipt" animation.
 */
@Composable
fun RitualSummaryDialog(
  onDismiss: () -> Unit,
  onShare: () -> Unit,
  revenue: String,
  profit: String,
  txCount: Int
) {
  var showContent by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(300)
    showContent = true
  }

  Dialog(onDismissRequest = onDismiss) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(MaterialTheme.colorScheme.surface)
        .padding(24.dp)
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        AnimatedVisibility(
          visible = showContent,
          enter = expandVertically(tween(600)) + fadeIn()
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
              Icons.Rounded.CheckCircle, 
              contentDescription = null, 
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Day Well Spent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
          }
        }

        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Transactions", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(txCount.toString(), fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Total Revenue", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(revenue, fontWeight = FontWeight.Bold)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text("Net Profit", color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(profit, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(16.dp))

        Button(
          onClick = onShare,
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Share Daily Report")
        }
        
        TextButton(onClick = onDismiss) {
          Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}
