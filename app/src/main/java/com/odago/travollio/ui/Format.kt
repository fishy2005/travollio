package com.odago.travollio.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Shared display formatting (kept free of any Android dependency so it stays testable)

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
private val shortFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** "Sat 20 Sep" from a yyyy-MM-dd string. Falls back to the raw string on garbage input. */
fun formatDayHeader(date: String): String = try {
    LocalDate.parse(date).format(dayFmt)
} catch (_: Exception) {
    date
}

/** "20 Sep" from a yyyy-MM-dd string. */
fun formatDayShort(date: String): String = try {
    LocalDate.parse(date).format(shortFmt)
} catch (_: Exception) {
    date
}

/** ₪12,345 (home currency, 0 decimals). */
fun formatHome(amount: Double): String = "₪" + "%,.0f".format(amount)

/** ¥12,345 (local currency, 0 decimals). */
fun formatLocal(amount: Double?, ccy: String?): String = when {
    amount == null -> ""
    ccy.isNullOrBlank() -> "%,.0f".format(amount)
    else -> "$ccy " + "%,.0f".format(amount)
}
