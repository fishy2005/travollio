package com.odago.travollio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Item
import com.odago.travollio.ui.Accent
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Hairline
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.InkTertiary
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
    onItemClick: (Item) -> Unit,
    onToggleDone: (Long) -> Unit = {},
    onOpenPacking: () -> Unit = {}
) {
    val days = state.days

    if (days.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text("No trip yet.", color = InkSecondary)
            Text(
                "Make one on the Today tab — tap the tuning icon top-right.",
                style = MaterialTheme.typography.bodySmall,
                color = InkTertiary
            )
        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(day.city, style = MaterialTheme.typography.headlineLarge, color = InkPrimary)
                        Text(
                            formatDayHeader(day.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentDeep
                        )
                    }
                    PackingChip(state, onOpenPacking)
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
                        ItineraryRow(item = items[i], onToggleDone)
                    }
                }
            }
        }
    }
}

/** One itinerary row: check-off circle + monochrome kind tile + title/detail, gold time & cost. */
@Composable
private fun ItineraryRow(item: Item, onToggleDone: (Long) -> Unit = {}) {
    val done = item.doneAt != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(26.dp)
                .clip(RoundedCornerShape(99))
                .background(if (done) Accent else androidx.compose.ui.graphics.Color.Transparent)
                .then(
                    if (done) Modifier else Modifier.border(1.5.dp, Hairline, RoundedCornerShape(99))
                )
                .clickable { onToggleDone(item.id) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = if (done) "Done" else "Mark done",
                tint = if (done) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent,
                modifier = Modifier.size(16.dp)
            )
        }
        KindTile(kind = item.kind, size = 44.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) InkTertiary else InkPrimary,
                textDecoration = if (done) TextDecoration.LineThrough else null
            )
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

/** Small clickable packing-progress chip that opens the packing sheet. */
@Composable
private fun PackingChip(state: AppState, onOpenPacking: () -> Unit) {
    val rows = state.packing
    if (rows.isEmpty()) return
    val packed = rows.count { it.isPacked }
    val all = packed == rows.size
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = if (all) Accent else androidx.compose.material3.MaterialTheme.colorScheme.surface,
        border = if (all) null else androidx.compose.foundation.BorderStroke(1.dp, Hairline),
        modifier = Modifier.clickable { onOpenPacking() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                Icons.Filled.Luggage,
                contentDescription = "Packing list",
                tint = if (all) androidx.compose.ui.graphics.Color.White else AccentDeep,
                modifier = Modifier.size(14.dp)
            )
            Text(
                "  $packed/${rows.size}",
                style = MaterialTheme.typography.labelSmall,
                color = if (all) androidx.compose.ui.graphics.Color.White else AccentDeep
            )
        }
    }
}
