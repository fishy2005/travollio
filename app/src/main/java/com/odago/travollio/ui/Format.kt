package com.odago.travollio.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared display formatting (kept free of any Android dependency so it stays testable)

// App-wide date format is dd.MM.yyyy. Stored values stay ISO yyyy-MM-dd (agent/DB);
// these formatters convert for display only.
private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH)
private val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH)

/** "20.09.2026" from a yyyy-MM-dd string. Falls back to the raw string on garbage input. */
fun formatDayHeader(date: String): String = try {
    LocalDate.parse(date).format(dayFmt)
} catch (_: Exception) {
    date
}

/** "20.09.2026" from a yyyy-MM-dd string (same as [formatDayHeader]). */
fun formatDayShort(date: String): String = try {
    LocalDate.parse(date).format(shortFmt)
} catch (_: Exception) {
    date
}

/** ISO yyyy-MM-dd → dd.MM.yyyy; returns the input unchanged if unparseable. */
fun formatDate(iso: String?): String = try {
    iso?.let { LocalDate.parse(it).format(dayFmt) } ?: ""
} catch (_: Exception) {
    iso.orEmpty()
}

/** ₪12,345 (home currency, 0 decimals). */
fun formatHome(amount: Double): String = "₪" + "%,.0f".format(amount)

/** ¥12,345 (local currency, 0 decimals). */
fun formatLocal(amount: Double?, ccy: String?): String = when {
    amount == null -> ""
    ccy.isNullOrBlank() -> "%,.0f".format(amount)
    else -> "$ccy " + "%,.0f".format(amount)
}
