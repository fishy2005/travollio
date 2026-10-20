package com.odago.travollio.ui

import com.odago.travollio.data.AppState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Engagement math (check-off XP, streaks, levels) — shared by the Today screen,
 * the Settings explorer card and anywhere else progress is shown.
 */
object Engagement {

    /** XP for the traveler's lifetime actions (done moments ~ spending breeze). */
    fun xp(state: AppState): Int {
        val done = state.items.count { it.doneAt != null } * 15
        val tx = state.transactions.size * 3
        val photos = state.attachments.size * 10
        val trips = state.availableTrips.size * 60
        return done + tx + photos + trips
    }

    /** Level: the sqrt curve keeps early levels quick, later ones earned. */
    fun level(xp: Int): Int = 1 + kotlin.math.floor(kotlin.math.sqrt(xp.toDouble() / 50.0)).toInt()

    /** XP needed to reach the next level (for the progress bar). */
    fun xpForNextLevel(level: Int): Int = (50 * (level * level)).coerceAtLeast(50)

    /** Consecutive days (ending today or yesterday) with any checked moment or logged spend. */
    fun streak(state: AppState): Int {
        val days = mutableSetOf<LocalDate>()
        state.items.forEach { i ->
            i.doneAt?.let { ts ->
                runCatching { Instant.parse(ts).atZone(ZoneId.systemDefault()).toLocalDate() }
                    .getOrNull()?.let { days += it }
            }
        }
        state.transactions.forEach { t ->
            val d = runCatching { Instant.parse(t.ts).atZone(ZoneId.systemDefault()).toLocalDate() }
                .recoverCatching { java.time.LocalDateTime.parse(t.ts).toLocalDate() }
                .getOrNull()
            if (d != null) days += d
        }
        if (days.isEmpty()) return 0
        val today = LocalDate.now()
        var streak = 0
        var cursor = if (today in days) today else today.minusDays(1)
        while (cursor in days) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
