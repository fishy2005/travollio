package com.odago.travollio.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Trip
import com.odago.travollio.data.Transaction
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Blush
import com.odago.travollio.ui.BlushTint
import com.odago.travollio.ui.Hairline
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
import com.odago.travollio.ui.tvColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val EXPENSE_CATEGORIES = listOf("food", "transit", "lodging", "shopping", "entertainment", "errand", "other")

/** Money: spring-animated budget ring + pace readout + transaction feed, plus a manual
 *  "add expense" entry so you don't have to go through chat for a quick purchase. */
@Composable
fun MoneyScreen(
    state: AppState,
    onAddExpense: (Double, String?, String, String) -> Unit = { _, _, _, _ -> }
) {
    val trip = state.trip
    var showAdd by remember { mutableStateOf(false) }

    // No active trip → a friendly empty state instead of a blank screen.
    if (trip == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Money", style = MaterialTheme.typography.displayMedium, color = InkPrimary)
            GlassCard(contentPadding = 20.dp) {
                Text(
                    "No active trip yet. Ask Travollio to plan one, then track your spending here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
            }
        }
        return
    }

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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Money",
                    style = MaterialTheme.typography.displayMedium,
                    color = InkPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { showAdd = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add expense",
                        tint = AccentDeep,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
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
                            formatHome(animatedSpent.toDouble(), trip.homeCcy),
                            style = MaterialTheme.typography.displaySmall,
                            color = InkPrimary
                        )
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = InkSecondary)) { append("of ") }
                                withStyle(SpanStyle(color = AccentDeep)) { append(formatHome(cap, trip.homeCcy)) }
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
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TRANSACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentDeep
                )
                Text(
                    "${state.transactions.size} logged",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTertiary
                )
            }
        }

        if (state.transactions.isEmpty()) {
            item(key = "empty") {
                GlassCard(cornerRadius = 18.dp, contentPadding = 20.dp) {
                    Text(
                        "No expenses logged yet. Use the + button, or ask Travollio to log a purchase.",
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
                    TransactionRow(tx = state.transactions[i], homeCcy = trip.homeCcy)
                }
            }
        }
    }

    if (showAdd) {
        AddExpenseDialog(
            trip = trip,
            onDismiss = { showAdd = false },
            onSave = { amount, ccy, note, category ->
                onAddExpense(amount, ccy, note, category)
                showAdd = false
            }
        )
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
    val label = if (hot) "Pacing hot · ≈${formatHome(projected, trip.homeCcy)}" else "On pace · ≈${formatHome(projected, trip.homeCcy)}"

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

/** Manual expense form — amount in the trip's local currency, converted to home on save. */
@Composable
private fun AddExpenseDialog(
    trip: Trip,
    onDismiss: () -> Unit,
    onSave: (Double, String?, String, String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
    var openCat by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.tvColors.glassPanel,
        title = { Text("Add expense", color = InkPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = false },
                    label = { Text("What was it for?") },
                    singleLine = true,
                    placeholder = { Text("e.g. Sushi dinner") }
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it; error = false },
                    label = { Text("Amount (${trip.localCcy})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = error
                )
                if (error) {
                    Text(
                        "Enter a valid amount.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                CategoryPicker(category, onPick = { category = it }, open = openCat, onOpenChange = { openCat = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amt = amount.trim().toDoubleOrNull()
                if (amt == null || amt <= 0) { error = true; return@TextButton }
                onSave(amt, trip.localCcy, note, category)
            }) { Text("Save", color = AccentDeep) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSecondary) } }
    )
}

@Composable
private fun CategoryPicker(
    value: String,
    onPick: (String) -> Unit,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit
) {
    Box {
        Surface(
            onClick = { onOpenChange(true) },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Category", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                    Text(value, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = InkSecondary)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { onOpenChange(false) }) {
            EXPENSE_CATEGORIES.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c, color = InkPrimary) },
                    onClick = { onPick(c); onOpenChange(false) }
                )
            }
        }
    }
}

/** One transaction: kind tile + note + dual-currency amounts. */
@Composable
private fun TransactionRow(tx: Transaction, homeCcy: String) {
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
                formatHome(tx.amountHomeCcy, homeCcy),
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
