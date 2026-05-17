package com.ledger.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.db.TransactionEntity
import com.ledger.app.ui.common.SensoryBackground
import com.ledger.app.ui.ledger.LedgerTools
import com.ledger.app.ui.ledger.LedgerViewModel
import com.ledger.app.ui.theme.LocalPrivacyMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  ledgerTools: LedgerTools,
  viewModel: LedgerViewModel = hiltViewModel(),
) {
  val allTransactions by viewModel.allTransactions.collectAsState()
  val privacyMode by LocalPrivacyMode.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  val grouped = remember(allTransactions) {
    allTransactions
      .sortedByDescending { it.timestampMs }
      .groupBy { dayKey(it.timestampMs) }
      .entries
      .sortedByDescending { it.key }
      .map { it.key to it.value }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    SensoryBackground()

    Column(modifier = Modifier.fillMaxSize()) {
      LargeTopAppBar(
        title = { Text("History", fontWeight = FontWeight.Black) },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.largeTopAppBarColors(
          containerColor = Color.Transparent,
          scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        ),
      )

      if (allTransactions.isEmpty()) {
        HistoryEmptyState()
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp, ),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          itemsIndexed(grouped, key = { _, pair -> pair.first }) { index, (_, txList) ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(txList.first().id) { visible = true }

            AnimatedVisibility(
              visible = visible,
              enter = fadeIn(tween(240, delayMillis = (index * 60).coerceAtMost(300))) +
                slideInVertically(tween(240, delayMillis = (index * 60).coerceAtMost(300))) { it / 4 },
            ) {
              DayCard(
                txList = txList,
                privacyMode = privacyMode,
                onDelete = { timestamp -> viewModel.deleteTransaction(timestamp, ledgerTools) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DayCard(
  txList: List<TransactionEntity>,
  privacyMode: Boolean,
  onDelete: (Long) -> Unit,
) {
  val cardShape = RoundedCornerShape(24.dp)

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      text = formatDateHeader(txList.first().timestampMs),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp),
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
      Column {
        txList.forEachIndexed { index, tx ->
          SwipeableTransactionRow(
            tx = tx,
            privacyMode = privacyMode,
            onDelete = { onDelete(tx.timestampMs) },
          )
          if (index < txList.size - 1) {
            HorizontalDivider(
              modifier = Modifier.padding(horizontal = 20.dp),
              color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
              thickness = 0.5.dp,
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTransactionRow(
  tx: TransactionEntity,
  privacyMode: Boolean,
  onDelete: () -> Unit,
) {
  val dismissState = rememberSwipeToDismissBoxState(
    confirmValueChange = { value ->
      if (value == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
    },
  )

  SwipeToDismissBox(
    state = dismissState,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      val color by animateColorAsState(
        targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
          MaterialTheme.colorScheme.errorContainer
        else Color.Transparent,
        label = "swipe_bg",
      )
      Box(
        modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
          Icon(
            Icons.Rounded.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(22.dp),
          )
        }
      }
    },
  ) {
    TransactionRow(tx = tx, privacyMode = privacyMode)
  }
}

@Composable
private fun TransactionRow(tx: TransactionEntity, privacyMode: Boolean) {
  val isIncome = tx.transactionType == "sale" || tx.transactionType == "income"
  val badgeGradient = if (isIncome)
    Brush.linearGradient(listOf(Color(0xFF66BB6A), Color(0xFF2E7D32)))
  else
    Brush.linearGradient(listOf(Color(0xFFEF5350), Color(0xFFC62828)))
  val amountColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFEF5350)

  val confidenceColor = when (tx.confidence) {
    "low" -> Color(0xFFEF5350)
    "medium" -> Color(0xFFFF7043)
    else -> Color.Transparent
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(badgeGradient),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = if (isIncome) Icons.AutoMirrored.Rounded.TrendingUp
                      else Icons.AutoMirrored.Rounded.TrendingDown,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = Color.White,
      )
    }

    Spacer(Modifier.width(14.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = tx.item,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (confidenceColor != Color.Transparent) {
          Spacer(Modifier.width(6.dp))
          Box(
            modifier = Modifier
              .size(6.dp)
              .clip(CircleShape)
              .background(confidenceColor),
          )
        }
      }
      Spacer(Modifier.height(2.dp))
      Text(
        text = "${tx.transactionType}  ·  ${fmtQty(tx.quantity)} ${tx.unit}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
      )
    }

    Spacer(Modifier.width(12.dp))

    Text(
      text = if (privacyMode) "••••" else "${tx.currency} ${formatAmount(tx.amount)}",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Black,
      color = if (privacyMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
              else amountColor,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun HistoryEmptyState() {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier = Modifier
        .size(80.dp)
        .clip(CircleShape)
        .background(
          Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
          )
        ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.AutoMirrored.Rounded.TrendingUp,
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = Color.Black,
      )
    }
    Spacer(Modifier.height(24.dp))
    Text(
      "No transactions yet",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.ExtraBold,
      color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      "Start recording sales, purchases, and expenses on the Home tab.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun dayKey(timestampMs: Long): Long {
  val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
  cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
  cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
  return cal.timeInMillis
}

private fun formatDateHeader(timestampMs: Long): String {
  val today = Calendar.getInstance()
  val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
  return when {
    isSameDay(cal, today) -> "Today"
    isYesterday(cal, today) -> "Yesterday"
    else -> SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(timestampMs))
  }
}

private fun isSameDay(a: Calendar, b: Calendar) =
  a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
    a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(a: Calendar, b: Calendar): Boolean {
  val yesterday = Calendar.getInstance().apply {
    timeInMillis = b.timeInMillis
    add(Calendar.DAY_OF_YEAR, -1)
  }
  return isSameDay(a, yesterday)
}

private fun formatAmount(amount: Double): String = when {
  amount >= 1_000_000 -> "%.1fM".format(amount / 1_000_000)
  amount >= 1_000 -> "%.1fK".format(amount / 1_000)
  else -> "%.0f".format(amount)
}

private fun fmtQty(v: Double): String =
  if (v == v.toLong().toDouble()) v.toLong().toString()
  else "%.1f".format(v)
