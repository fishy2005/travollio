package com.odago.travollio.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Transaction
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Blush
import com.odago.travollio.ui.BlushTint
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
import com.odago.travollio.ui.Sage
import com.odago.travollio.ui.SageTint
import com.odago.travollio.ui.components.BudgetRing
import com.odago.travollio.ui.components.GlassCard
import com.odago.travollio.ui.components.KindTile
import com.odago.travollio.ui.components.springGentle
import com.odago.travollio.ui.formatHome
import com.odago.travollio.ui.formatLocal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Money: spring-animated budget ring + pace readout + transaction feed. */
@Composable
fun MoneyScreen(state: AppState) {
    val trip = state.trip ?: return
    val spent = state.spentHomeCcy
    val cap = trip.budgetCapHomeCcy
    val pct = if (cap > 0) spent / cap else 0.0
    // Animated counter: number eases to its new value whenever transactions change.
    val animatedSpent by animateFloatAsState(
        targetValue = spent.toFloat(),
        animationSpec = springGentle(),
        label = "spentCounter"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "title") {
            Text("Money", style = MaterialTheme.typography.displayMedium, color = InkPrimary)
        }

        item(key = "hero") {
            GlassCard(cornerRadius = 28.dp, contentPadding = 20.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    BudgetRing(progress = pct.toFloat(), size = 128.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${(pct * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium,
                                color = InkPrimary
                            )
                            Text(
                                "used",
                                style = MaterialTheme.typography.labelSmall,
                                color = InkTertiary
                            )
                        }
                    }
                    Column {
                        Text("Spent", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                        Text(
                            formatHome(animatedSpent.toDouble()),
                            style = MaterialTheme.typography.displaySmall,
                            color = InkPrimary
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = InkSecondary)) { append("of ") }
                                withStyle(SpanStyle(color = AccentDeep)) { append(formatHome(cap)) }
                                withStyle(SpanStyle(color = InkSecondary)) { append(" cap") }
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                        PacePill(state)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "≈ ${formatLocal(state.spentLocal, state.localCcy)} local",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkTertiary
                        )
                    }
                }
            }
        }

        item(key = "txHeader") {
            Text(
                "TRANSACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = AccentDeep,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
            )
        }

        if (state.transactions.isEmpty()) {
            item(key = "empty") {
                GlassCard(cornerRadius = 18.dp, contentPadding = 20.dp) {
                    Text(
                        "No transactions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        } else {
            items(state.transactions.size, key = { state.transactions[it].id }) { i ->
                val tx = state.transactions[i]
                GlassCard(
                    modifier = Modifier.animateItem(),
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp
                ) {
                    TransactionRow(tx)
                }
            }
        }
    }
}

/** Muted sage/blush pill: current budget pace derived from spend ÷ elapsed days. */
@Composable
private fun PacePill(state: AppState) {
    val trip = state.trip ?: return
    val start = runCatching { LocalDate.parse(trip.startDate) }.getOrNull() ?: return
    val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull() ?: return
    val today = LocalDate.now()
    if (today.isBefore(start) || today.isAfter(end)) return
    val cap = trip.budgetCapHomeCcy
    if (cap <= 0) return

    val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
    val elapsed = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
    val projected = state.spentHomeCcy / (elapsed.toDouble() / totalDays)
    val hot = projected > cap * 1.05
    val label = if (hot) "Pacing hot · ≈${formatHome(projected)}" else "On pace · ≈${formatHome(projected)}"

    val bg = if (hot) BlushTint else SageTint
    val fg = if (hot) Blush else Sage
    SurfacePill(label = label, bg = bg, fg = fg)
}

@Composable
private fun SurfacePill(label: String, bg: Color, fg: Color) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        color = bg
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

/** One transaction: monochrome kind tile + note + dual-currency amounts. */
@Composable
private fun TransactionRow(tx: Transaction) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KindTile(
            kind = tx.category,
            size = 40.dp,
            modifier = Modifier.padding(end = 12.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(tx.note, style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
            Text(
                "${tx.category} · ${tx.ts.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSecondary
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatHome(tx.amountHomeCcy),
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary
            )
            if (tx.amountLocal != null) {
                Text(
                    formatLocal(tx.amountLocal, tx.ccy),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary
                )
            }
        }
    }
}
