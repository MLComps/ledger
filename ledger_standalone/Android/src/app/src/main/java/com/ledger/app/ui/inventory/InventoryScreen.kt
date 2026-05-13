package com.ledger.app.ui.inventory

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.ui.ledger.LedgerViewModel
import androidx.compose.animation.AnimatedVisibility

private const val LOW_STOCK = 5.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: LedgerViewModel = hiltViewModel()) {
  val uiState by viewModel.uiState.collectAsState()
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val items = remember(uiState.stockItemNames, uiState.stockItems) {
    uiState.stockItemNames.zip(uiState.stockItems)
  }

  Column(modifier = Modifier.fillMaxSize()) {
    LargeTopAppBar(
      title = {
        if (uiState.lowStockItems.isNotEmpty()) {
          BadgedBox(badge = {
            Badge { Text(uiState.lowStockItems.size.toString()) }
          }) {
            Text("Inventory")
          }
        } else {
          Text("Inventory")
        }
      },
      scrollBehavior = scrollBehavior,
      colors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
      ),
    )

    if (items.isEmpty()) {
      InventoryEmptyState()
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        itemsIndexed(items, key = { _, pair -> pair.first }) { index, (name, stock) ->
          var visible by remember { mutableStateOf(false) }
          LaunchedEffect(name) { visible = true }

          AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180, delayMillis = (index * 40).coerceAtMost(300))) +
              slideInVertically(tween(180, delayMillis = (index * 40).coerceAtMost(300))) { it / 3 },
          ) {
            InventoryCard(
              name = name,
              quantity = stock.quantity,
              unit = stock.unit,
              isLow = name in uiState.lowStockItems,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InventoryCard(name: String, quantity: Double, unit: String, isLow: Boolean) {
  val cardColor = if (isLow)
    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
  else
    MaterialTheme.colorScheme.surfaceVariant

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = cardColor),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isLow) 2.dp else 0.dp),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // Icon badge
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(CircleShape)
          .background(
            if (isLow) MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (isLow) Icons.Rounded.Warning else Icons.Rounded.Inventory2,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          tint = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
      }

      Spacer(Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isLow) {
          Spacer(Modifier.height(2.dp))
          Text(
            text = "Low stock — consider restocking",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }

      Spacer(Modifier.width(12.dp))

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = fmtQty(quantity),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = if (isLow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = unit,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun InventoryEmptyState() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Rounded.Inventory2,
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
    Spacer(Modifier.height(24.dp))
    Text(
      "No stock tracked yet",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "Tell Ledger about a restock or purchase and it'll appear here.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

private fun fmtQty(v: Double): String =
  if (v == v.toLong().toDouble()) v.toLong().toString()
  else "%.1f".format(v)
