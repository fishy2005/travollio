package com.odago.travollio.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Item
import com.odago.travollio.data.Trip
import com.odago.travollio.ui.AccentDeep
import com.odago.travollio.ui.Blush
import com.odago.travollio.ui.InkPrimary
import com.odago.travollio.ui.InkSecondary
import com.odago.travollio.ui.Playfair
import com.odago.travollio.ui.Sage
import com.odago.travollio.ui.components.AiSummaryCard
import com.odago.travollio.ui.components.CompletionCheck
import com.odago.travollio.ui.components.DuoIconContainer
import com.odago.travollio.ui.components.GlassCard
import com.odago.travollio.ui.components.MediaThumb
import com.odago.travollio.ui.components.MetadataChip
import com.odago.travollio.ui.components.MiniMapWidget
import com.odago.travollio.ui.components.Pill
import com.odago.travollio.ui.components.RoutePoint
import com.odago.travollio.ui.components.ScenicBackdrop
import com.odago.travollio.ui.components.StatusBadge
import com.odago.travollio.ui.components.TimePill
import com.odago.travollio.ui.components.pressScale
import com.odago.travollio.ui.formatDayHeader
import com.odago.travollio.ui.formatDayShort
import com.odago.travollio.ui.formatLocal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.sin

/**
 * Trip: the full 8-day plan as an award-grade glass itinerary. A blurred scenic
 * backdrop + hero serif header with a floating mini-map, day pills up top, and a
 * list of frosted glass cards with duotone icons, metadata chips, time pills,
 * inline media and animated completion checkboxes.
 */
@Composable
fun TripScreen(
    state: AppState,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
    onItemClick: (Item) -> Unit
) {
    val days = state.days

    if (days.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            ScenicBackdrop()
            Text(
                "No trip yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkSecondary,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        return
    }

    val day = days[selectedDay.coerceIn(days.indices)]
    val items = state.items.filter { it.dayId == day.id }
    // UI-appreciation of completed moments (in-memory, keyed by item id).
    var doneIds by rememberSaveable { mutableStateOf(listOf<Long>()) }

    Box(Modifier.fillMaxSize()) {
        ScenicBackdrop()

        Column(Modifier.fillMaxSize()) {
            // Day selector — mono pills with spring fill animation over the backdrop.
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
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero") { HeroHeader(state.trip, day.city, day.date, days.size, selectedDay, items) }

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
                        val item = items[i]
                        ItineraryCard(
                            modifier = Modifier.animateItem(),
                            item = item,
                            attachmentPath = state.attachments.firstOrNull {
                                it.itemId == item.id && it.mimeType.startsWith("image/")
                            }?.filePath,
                            checked = item.id in doneIds,
                            onToggleCheck = {
                                doneIds = if (item.id in doneIds) doneIds - item.id else doneIds + item.id
                            },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

/** Hero: serif city title, dot-separated sub-header, status badge and a floating mini-map. */
@Composable
private fun HeroHeader(
    trip: Trip?,
    city: String,
    date: String,
    totalDays: Int,
    dayIndex: Int,
    items: List<Item>
) {
    val (status, statusColor) = tripStatus(trip, dayIndex, totalDays)
    val (route, pins) = miniMapRoute(items)

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(text = status, dotColor = statusColor)
            Text(
                city,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = Playfair,
                    fontWeight = FontWeight.Bold
                ),
                color = InkPrimary
            )
            Text(
                "$city · ${formatDayHeader(date)}",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary
            )
        }
        MiniMapWidget(
            points = route,
            pins = pins,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/**
 * One glass itinerary card: duotone icon tile, title + metadata chip + detail,
 * an optional inline 1:1 media thumb (when a photo is attached), and on the
 * right edge a time pill, cost and the animated completion checkbox.
 */
@Composable
private fun ItineraryCard(
    item: Item,
    attachmentPath: String?,
    checked: Boolean,
    onToggleCheck: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        contentPadding = 16.dp,
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DuoIconContainer(kind = item.kind, size = 50.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = InkPrimary)
                item.place?.let { MetadataChip(it) }
                if (item.detail.isNotBlank()) {
                    Text(item.detail, style = MaterialTheme.typography.bodySmall, color = InkSecondary, maxLines = 2)
                }
                if (attachmentPath != null) {
                    Spacer(Modifier.size(4.dp))
                    MediaThumb(filePath = attachmentPath, kind = item.kind, size = 44.dp)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TimePill(item.timeLocal ?: "—")
                if (item.costLocal != null) {
                    Text(
                        formatLocal(item.costLocal, item.ccy),
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentDeep
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            CompletionCheck(checked = checked, onToggle = onToggleCheck)
        }
    }
}

/** Trip-wide status for the badge: "TRIP TO X · DAY N OF M" or "· ENDED". */
@Composable
private fun tripStatus(trip: Trip?, dayIndex: Int, totalDays: Int): Pair<String, androidx.compose.ui.graphics.Color> {
    val name = trip?.name?.uppercase()?.ifBlank { "TRIP TO ASIA" } ?: "NO TRIP"
    if (trip != null) {
        val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()
        if (end != null && LocalDate.now().isAfter(end)) {
            return "$name · ENDED" to Blush
        }
    }
    return "$name · DAY ${(dayIndex + 1).coerceAtLeast(1)} OF ${totalDays.coerceAtLeast(1)}" to Sage
}

/** Normalized route + pins for the mini-map. Uses real lat/lng when present,
 *  otherwise a smooth synthetic "journey" so the map always reads as a route. */
private fun miniMapRoute(items: List<Item>): Pair<List<RoutePoint>, List<RoutePoint>> {
    val geo = items.filter { it.lat != null && it.lng != null }
    if (geo.size >= 2) {
        val minLat = geo.minOf { it.lat!! }
        val maxLat = geo.maxOf { it.lat!! }
        val minLng = geo.minOf { it.lng!! }
        val maxLng = geo.maxOf { it.lng!! }
        val spanLat = (maxLat - minLat).takeIf { it > 0.0 } ?: 1.0
        val spanLng = (maxLng - minLng).takeIf { it > 0.0 } ?: 1.0
        val pts = geo.map {
            RoutePoint(
                x = ((it.lng!! - minLng) / spanLng).toFloat().coerceIn(0.12f, 0.88f),
                y = ((maxLat - it.lat!!) / spanLat).toFloat().coerceIn(0.12f, 0.88f)
            )
        }
        return pts to pts
    }
    val n = items.size.coerceAtLeast(3)
    val syn = (0 until n).map { i ->
        val t = i.toFloat() / (n - 1).coerceAtLeast(1)
        RoutePoint(x = 0.12f + 0.74f * t, y = 0.86f - 0.52f * sin(t * PI.toFloat()))
    }
    return syn to syn
}
