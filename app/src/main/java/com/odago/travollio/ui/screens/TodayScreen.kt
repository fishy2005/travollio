package com.odago.travollio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Item
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.components.AiSummaryCard
import com.odago.travollio.ui.components.GlassCard
import com.odago.travollio.ui.components.KindTile
import com.odago.travollio.ui.formatDate
import com.odago.travollio.ui.formatDayHeader
import com.odago.travollio.ui.formatHome
import com.odago.travollio.ui.formatLocal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Today: Fable Mono hero (Cinzel overline + Playfair city title) + compact budget card
 *  and the day's plan as Meta-style cards with monochrome KindTile icons. */
@Composable
fun TodayScreen(state: AppState, onItemClick: (Item) -> Unit, onOpenTrips: () -> Unit) {
    val today = LocalDate.now().toString()
    val day = state.days.firstOrNull { it.date == today } ?: state.days.firstOrNull()
    val items = if (day != null) state.items.filter { it.dayId == day.id } else emptyList()
    val dayIndex = if (day != null) state.days.indexOf(day) else -1
    val trip = state.trip
    val dayNote = if (day != null) state.notes.firstOrNull { it.dayId == day.id } else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "hero") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val metaLine = when {
                            trip == null -> null
                            else -> {
                                val s = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
                                val e = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()
                                val now = LocalDate.now()
                                when {
                                    s == null || e == null -> "${trip.name.uppercase()} · ${formatDate(trip.startDate)} → ${formatDate(trip.endDate)}"
                                    now.isBefore(s) -> "STARTS IN ${ChronoUnit.DAYS.between(now, s)} DAY${if (ChronoUnit.DAYS.between(now, s) == 1L) "" else "S"} · ${trip.name.uppercase()}"
                                    now.isAfter(e) -> "${trip.name.uppercase()} · ENDED — ASK ME TO ARCHIVE IT"
                                    day != null && dayIndex >= 0 -> "${trip.name.uppercase()} · DAY ${dayIndex + 1} OF ${state.days.size}"
                                    else -> trip.name.uppercase()
                                }
                            }
                        }
                        metaLine?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = AccentDeep)
                        }
                        Text(
                            day?.city ?: "Today",
                            style = MaterialTheme.typography.headlineLarge,
                            color = InkPrimary
                        )
                    }
                    IconButton(onClick = onOpenTrips, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Tune,
                            contentDescription = "Manage trips",
                            tint = InkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (day != null) {
                    Text(
                        "${day.city} · ${formatDayHeader(day.date)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                } else {
                    Text("No trip days yet", style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                }
            }
        }

        if (trip != null && trip.budgetCapHomeCcy > 0) {
            item(key = "budget") {
                BudgetCard(state)
            }
        }

        if (dayNote != null) {
            item(key = "summary") { AiSummaryCard(dayNote.content) }
        }

        item(key = "todayHeader") {
            Text(
                "Today".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AccentDeep,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
            )
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                GlassCard(contentPadding = 20.dp) {
                    Text(
                        "Nothing planned yet — ask Travollio to fill this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        } else {
            items(items.size, key = { items[it].id }) { i ->
                val item = items[i]
                GlassCard(
                    modifier = Modifier.animateItem(),
                    cornerRadius = 18.dp,
                    contentPadding = 14.dp,
                    onClick = { onItemClick(item) }
                ) {
                    ItineraryRow(item)
                }
            }
        }
    }
}

/** Compact trip-budget card: overline + big warm-cream numerals + thin gold progress bar. */
@Composable
private fun BudgetCard(state: AppState) {
    val trip = state.trip ?: return
    val cap = trip.budgetCapHomeCcy
    if (cap <= 0) return
    val spent = state.spentHomeCcy
    val ratio = (spent / cap).coerceIn(0.0, 1.0)

    GlassCard(
        cornerRadius = 22.dp,
        contentPadding = 18.dp,
        fill = MaterialTheme.colorScheme.surface
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TRIP BUDGET", style = MaterialTheme.typography.labelSmall, color = AccentDeep)
            Text(
                "${formatHome(spent)} of ${formatHome(cap)}",
                style = MaterialTheme.typography.headlineMedium,
                color = InkPrimary
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(99))
                    .background(Hairline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(ratio.toFloat())
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(Accent, AccentDeep)))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${(ratio * 100).toInt()}% spent",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSecondary
                )
                Text("On pace", style = MaterialTheme.typography.labelSmall, color = InkSecondary)
            }
        }
    }
}

/** One Meta-style itinerary row: KindTile glyph + title/detail, gold time & cost on the right. */
@Composable
private fun ItineraryRow(item: Item) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KindTile(kind = item.kind, size = 44.dp, modifier = Modifier.padding(end = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
            item.place?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
            if (item.detail.isNotBlank()) {
                Text(
                    item.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(start = 8.dp)
        ) {
            Text(
                item.timeLocal ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = AccentDeep
            )
            if (item.costLocal != null) {
                Text(
                    formatLocal(item.costLocal, item.ccy),
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentDeep
                )
            }
        }
    }
}
