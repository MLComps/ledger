package com.ledger.app.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ledger.app.db.TransactionEntity
import com.ledger.app.ui.ledger.LedgerTools
import com.ledger.app.ui.ledger.LedgerViewModel
import com.ledger.app.ui.theme.LocalPrivacyMode
import com.valentinilk.shimmer.shimmer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val typeColorMap = mapOf(
  "sale" to Color(0xFF2E7D32),
  "income" to Color(0xFF2E7D32),
  "purchase" to Color(0xFFC62828),
  "expense" to Color(0xFFC62828),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  ledgerTools: LedgerTools,
  viewModel: LedgerViewModel = hiltViewModel(),
) {
  val allTransactions by viewModel.allTransactions.collectAsState()
  val privacyMode by LocalPrivacyMode.current
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

  // Group by calendar day
  val grouped = remember(allTransactions) {
    allTransactions
      .sortedByDescending { it.timestampMs }
      .groupBy { dayKey(it.timestampMs) }
      .entries.sortedByDescending { it.key }
  }

  Column(modifier = Modifier.fillMaxSize()) {
    LargeTopAppBar(
      title = { Text("History") },
      scrollBehavior = scrollBehavior,
      colors = TopAppBarDefaults.largeTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
      ),
    )

    if (allTransactions.isEmpty()) {
      HistoryEmptyState()
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
      ) {
        grouped.forEach { (_, txList) ->
          stickyHeader {
            DateHeader(timestampMs = txList.first().timestampMs)
          }
          itemsIndexed(
            items = txList,
            key = { _, tx -> tx.id },
          ) { index, tx ->
            var visible by remember { mutableStateOf(false) }
            LaunchedEffect(tx.id) { visible = true }

            AnimatedVisibility(
              visible = visible,
              enter = fadeIn(tween(180, delayMillis = (index * 30).coerceAtMost(240))) +
                slideInVertically(tween(180, delayMillis = (index * 30).coerceAtMost(240))) { it / 3 },
            ) {
              SwipeableTransactionRow(
                tx = tx,
                privacyMode = privacyMode,
                onDelete = { viewModel.deleteTransaction(tx.timestampMs, ledgerTools) },
              )
            }
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
          MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
        label = "swipe_bg",
      )
      Box(
        modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(
          Icons.Rounded.Delete,
          contentDescription = "Delete",
          tint = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.size(22.dp),
        )
      }
    },
  ) {
    TransactionRow(tx = tx, privacyMode = privacyMode)
  }
}

@Composable
private fun TransactionRow(tx: TransactionEntity, privacyMode: Boolean) {
  val typeColor = typeColorMap[tx.transactionType] ?: MaterialTheme.colorScheme.onSurfaceVariant
  val confidenceColor = when (tx.confidence) {
    "low" -> Color(0xFFC62828)
    "medium" -> Color(0xFFE65100)
    else -> Color.Transparent
  }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 16.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Type colour stripe
    Box(
      modifier = Modifier
        .width(3.dp)
        .height(40.dp)
        .clip(CircleShape)
        .background(typeColor),
    )
    Spacer(Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = tx.item,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
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
      Text(
        text = "${tx.transactionType}  ·  ${fmtQty(tx.quantity)} ${tx.unit}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
      )
    }

    Spacer(Modifier.width(12.dp))
    Text(
      text = if (privacyMode) "••••" else "${tx.currency} ${formatAmount(tx.amount)}",
      style = MaterialTheme.typography.bodySmall,
      fontWeight = FontWeight.Bold,
      color = if (privacyMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else typeColor,
      textAlign = TextAlign.End,
    )
  }
}

@Composable
private fun DateHeader(timestampMs: Long) {
  val label = remember(timestampMs) { formatDateHeader(timestampMs) }
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .padding(horizontal = 16.dp, vertical = 6.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.SemiBold,
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
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Rounded.Receipt,
        contentDescription = null,
        modifier = Modifier.size(40.dp),
        tint = MaterialTheme.colorScheme.onPrimaryContainer,
      )
    }
    Spacer(Modifier.height(24.dp))
    Text(
      "No transactions yet",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
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

@Composable
private fun ShimmerTransactionRow() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .shimmer()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(modifier = Modifier.width(3.dp).height(40.dp).clip(CircleShape).background(Color.LightGray))
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Box(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray))
      Box(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray))
    }
    Spacer(Modifier.width(12.dp))
    Box(modifier = Modifier.width(60.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color.LightGray))
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
