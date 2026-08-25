package com.odago.travollio.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Item
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.components.AiSummaryCard
import com.odago.travollio.ui.components.GlassCard
import com.odago.travollio.ui.components.KindTile
import com.odago.travollio.ui.components.Pill
import com.odago.travollio.ui.components.pressScale
import com.odago.travollio.ui.formatDayHeader
import com.odago.travollio.ui.formatDayShort
import com.odago.travollio.ui.formatLocal

/** Trip: full 8-day plan. Day pills up top (animated selection), timeline below. */
@Composable
fun TripScreen(
    state: AppState,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onItemClick: (Item) -> Unit
) {
    val days = state.days

    if (days.isEmpty()) {
        Text("No trip yet.", modifier = Modifier.padding(24.dp), color = InkSecondary)
        return
    }
    val day = days[selectedDay.coerceIn(days.indices)]
    val items = state.items.filter { it.dayId == day.id }

    Column(modifier = Modifier.fillMaxSize()) {
        // Day selector — mono pills with spring fill animation; selected reads bright gold.
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { i, d ->
                Pill(
                    text = "Day ${i + 1}",
                    eyebrow = formatDayShort(d.date),
                    selected = i == selectedDay,
                    onClick = { onSelectDay(i) },
                    color = AccentDeep,
                    modifier = Modifier.pressScale()
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(day.city, style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                    Text(
                        formatDayHeader(day.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentDeep
                    )
                }
            }
            state.notes.firstOrNull { it.dayId == day.id }?.let { note ->
                item(key = "summary") { AiSummaryCard(note.content) }
            }
            if (items.isEmpty()) {
                item(key = "empty") {
                    GlassCard(contentPadding = 20.dp) {
                        Text(
                            "Nothing planned for this day yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }
            } else {
                items(items.size, key = { items[it].id }) { i ->
                    GlassCard(
                        modifier = Modifier.animateItem(),
                        cornerRadius = 18.dp,
                        contentPadding = 14.dp,
                        onClick = { onItemClick(items[i]) }
                    ) {
                        ItineraryRow(item = items[i])
                    }
                }
            }
        }
    }
}

/** One itinerary row: monochrome kind tile + title/detail, Cinzel gold time & cost on the right. */
@Composable
private fun ItineraryRow(item: Item) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        KindTile(kind = item.kind, size = 44.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
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
        Column(horizontalAlignment = Alignment.End) {
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
