package com.odago.travollio.data

/**
 * Rule-based packing suggestions (used by both the quick-fill button and the
 * agent's `packing_generate` tool). Scales with trip length and reacts to the
 * planned activity kinds (sight→sun/rain, transit→power bank, hotel→toiletries).
 */
object Packing {

    /** (label, category) suggestions for a trip. Returned without duplicates. */
    fun suggest(trip: Trip, items: List<Item>): List<Pair<String, String>> {
        val days = runCatching {
            java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(trip.startDate),
                java.time.LocalDate.parse(trip.endDate)
            ).toInt() + 1
        }.getOrDefault(3).coerceIn(1, 30)
        val kinds = items.map { it.kind }.toSet()
        val out = mutableListOf<Pair<String, String>>()

        // Documents — always.
        out += "Passport / ID card" to "documents"
        out += "Insurance card" to "documents"
        if (kinds.contains("sight")) out += "Tickets / passes (printed or app)" to "documents"

        // Tech — always, more for long trips.
        out += "Phone charger" to "tech"
        out += "Power bank" to "tech"
        out += "Travel plug adapter" to "tech"
        if (days >= 5) out += "Earphones / headphones" to "tech"

        // Clothes — scale with duration.
        out += "Shirts ×${(days / 2).coerceIn(2, 7)}" to "clothes"
        out += "Underwear ×${days.coerceIn(3, 10)}" to "clothes"
        out += "Socks ×${days.coerceIn(3, 10)}" to "clothes"
        out += "Comfortable walking shoes" to "clothes"
        if (kinds.contains("sight")) out += "Sunhat / cap" to "clothes"
        if (kinds.contains("hotel")) out += "Sleep shirt" to "clothes"

        // Toiletries.
        out += "Toothbrush + toothpaste" to "toiletries"
        out += "Deodorant" to "toiletries"
        out += "Medications" to "toiletries"
        out += "Sunscreen SPF30+" to "toiletries"
        if (kinds.contains("sight")) out += "Lip balm" to "toiletries"

        // Extras by kind.
        if (kinds.contains("sight")) out += "Small daypack" to "other"
        if (kinds.contains("food")) out += "Pocket tissues" to "other"
        out += "Refillable water bottle" to "other"
        out += "Pen (forms on planes)" to "other"
        return out.distinctBy { it.first }
    }
}
