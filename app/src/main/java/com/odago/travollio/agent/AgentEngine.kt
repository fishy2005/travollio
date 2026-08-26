package com.odago.travollio.agent

import com.odago.travollio.data.AppState
import com.odago.travollio.data.Currencies
import com.odago.travollio.ui.formatDate
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Phase 1 agent: deterministic, local, read-only.
 * Answers budget / itinerary / document questions grounded in Room data only —
 * no cloud calls, no invented facts (spec offline-honesty contract).
 * Phase 2 swaps this for the Gemini gemini-2.5-flash tool-calling loop behind the same interface.
 */
object AgentEngine {

    const val MODE_LABEL = "local · offline-safe"

    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun respond(state: AppState, userText: String): String {
        val t = userText.lowercase()
        val trip = state.trip ?: return "No active trip yet — create one and I'll take it from there."

        if (t.contains("budget") || t.contains("spent") || t.contains("money") || t.contains("cost")) {
            return moneySummary(state)
        }
        if (t.contains("passport") || t.contains("document") || t.contains("visa")) {
            return documentsAnswer(state)
        }
        if (t.contains("attach") || t.contains("photo") || t.contains("receipt") || t.contains("file")) {
            return "I'm the offline-safe fallback right now (cloud agent unreachable) — I can't read or file attachments. When the online agent is back, attach it again and it will save it to the right place."
        }
        if ((t.contains("add") || t.contains("move") || t.contains("remove") || t.contains("delete")) && t.contains("day")) {
            return "I'm the offline-safe fallback right now (cloud agent unreachable) and I'm read-only — I can't change your plan. When the online agent is back, ask it to add or move items and it will update your itinerary live."
        }
        if (t.contains("today") || t.contains("plan") || t.contains("itinerary")) {
            return itineraryAnswer(state)
        }
        return "I can answer about your budget, any day's plan, and document expiry. Try “how’s my budget?”"
    }

    private fun moneySummary(s: AppState): String {
        val trip = s.trip ?: return "No active trip."
        val cap = trip.budgetCapHomeCcy
        val spent = s.spentHomeCcy
        val sym = Currencies.symbol(trip.homeCcy)
        val pct = if (cap > 0) spent / cap * 100 else 0.0
        val pace = paceLine(s, cap, spent)
        return "You've spent $sym${"%,.0f".format(spent)} of your $sym${"%,.0f".format(cap)} cap (${pct.toInt()}%) — ≈ ${"%,.0f".format(s.spentLocal)} ${s.localCcy} local. $pace"
    }

    private fun paceLine(s: AppState, cap: Double, spent: Double): String {
        val trip = s.trip ?: return ""
        val sym = Currencies.symbol(trip.homeCcy)
        val start = LocalDate.parse(trip.startDate)
        val end = LocalDate.parse(trip.endDate)
        val today = LocalDate.now()
        if (today.isBefore(start)) return "Trip hasn't started — nothing to pace yet."
        if (today.isAfter(end)) return "Trip is over — final spend stands at $sym${"%,.0f".format(spent)}."
        val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
        val elapsed = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
        val projected = spent / (elapsed.toDouble() / totalDays)
        return if (projected > cap * 1.05) {
            "Pacing hot — at this rate you'd land ~$sym${"%,.0f".format(projected)}. Biggest lever: ${topCategory(s)}."
        } else {
            "On pace — projected finish ≈ $sym${"%,.0f".format(projected)}."
        }
    }

    private fun topCategory(s: AppState): String {
        val trip = s.trip ?: return "—"
        val sym = Currencies.symbol(trip.homeCcy)
        val byCat = s.transactions.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amountHomeCcy } }
        val (cat, amt) = byCat.maxByOrNull { it.value } ?: return "—"
        return "$cat ($sym${"%,.0f".format(amt)})"
    }

    private fun documentsAnswer(s: AppState): String {
        val docs = s.documents.filter { it.expiresAt != null }
        if (docs.isEmpty()) return "No documents with expiry dates on file."
        val today = LocalDate.now()
        val parts = docs.map { d ->
            val exp = LocalDate.parse(d.expiresAt!!)
            val daysLeft = ChronoUnit.DAYS.between(today, exp)
            when {
                daysLeft < 0 -> "${d.kind}: expired ${-daysLeft} days ago"
                else -> "${d.kind}: valid until ${exp.format(dayFmt)} ($daysLeft days)"
            }
        }
        return parts.joinToString(" · ")
    }

    private fun itineraryAnswer(s: AppState): String {
        val today = LocalDate.now().toString()
        val day = s.days.firstOrNull { it.date == today } ?: s.days.firstOrNull()
            ?: return "No days on the calendar yet."
        val items = s.items.filter { it.dayId == day.id }
        if (items.isEmpty()) return "${day.city}, ${formatDate(day.date)} — nothing planned yet. Ask the online agent to add something once you're back online."
        val lines = items.map { i ->
            val time = i.timeLocal ?: "—"
            "$time  ${i.title}"
        }
        return "${day.city}, ${formatDate(day.date)}:\n" + lines.joinToString("\n")
    }
}
