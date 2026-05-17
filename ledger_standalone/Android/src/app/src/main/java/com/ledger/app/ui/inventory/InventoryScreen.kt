package com.ledger.app.ui.inventory

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.ui.ledger.LedgerTools
import com.ledger.app.ui.ledger.LedgerViewModel
import kotlinx.coroutines.flow.Flow

private const val LOW_STOCK = 3.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
  ledgerTools: LedgerTools,
  stateFlow: Flow<Unit>,
  viewModel: LedgerViewModel = hiltViewModel(),
) {
  LaunchedEffect(Unit) { viewModel.syncFromTools(ledgerTools) }
  LaunchedEffect(Unit) { stateFlow.collect { viewModel.syncFromTools(ledgerTools) } }

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
  val amberColor = Color(0xFFFFAB00)
  val cardShape = RoundedCornerShape(24.dp)
  
  // Breathing animation for low stock glow
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.2f,
    targetValue = 0.65f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "alpha"
  )

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .clip(cardShape)
      .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
      .then(
        if (isLow) Modifier.border(
          width = 1.5.dp,
          brush = Brush.verticalGradient(listOf(amberColor.copy(alpha = pulseAlpha), Color.Transparent)),
          shape = cardShape
        ) else Modifier.border(
          width = 1.dp,
          brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)),
          shape = cardShape
        )
      )
  ) {
    // Ambient Glow Layer
    if (isLow) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(
            Brush.radialGradient(
              colors = listOf(amberColor.copy(alpha = pulseAlpha), Color.Transparent),
              center = androidx.compose.ui.geometry.Offset(0f, 0f),
              radius = 400f
            )
          )
      )
    }

    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      // High-Fidelity Gradient Badge
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(
            if (isLow) Brush.linearGradient(listOf(amberColor, Color(0xFFFF6D00)))
            else Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))
          ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = if (isLow) Icons.Rounded.Warning else Icons.Rounded.Inventory2,
          contentDescription = null,
          modifier = Modifier.size(24.dp),
          tint = Color.White
        )
      }

      Spacer(Modifier.width(16.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = name,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (isLow) {
          Box(
            modifier = Modifier
              .padding(top = 4.dp)
              .clip(CircleShape)
              .background(amberColor.copy(alpha = 0.2f))
              .padding(horizontal = 8.dp, vertical = 2.dp)
          ) {
            Text(
              text = "LOW STOCK",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Black,
              color = amberColor,
            )
          }
        }
      }

      Spacer(Modifier.width(12.dp))

      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = fmtQty(quantity),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Black,
          color = if (isLow) amberColor else MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = unit,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
