package com.odago.travollio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One aggregated day from the 5-day / 3-hour OpenWeatherMap forecast. */
data class DayForecast(
    val date: String,       // yyyy-MM-dd
    val condition: String,  // Clear|Clouds|Rain|Thunderstorm|Snow|...
    val description: String,
    val tempC: Double,      // day max, °C
    val rainMm: Double      // total, mm
)

/**
 * Live weather pulled directly from OpenWeatherMap — no server involved.
 * 30-minute in-memory cache per coordinate pair; all network on Dispatchers.IO,
 * never throws (null/empty on any failure so callers degrade to offline honesty).
 */
object Weather {
    private const val TTL_MS = 30 * 60 * 1000L
    private data class Entry(val at: Long, val days: List<DayForecast>)
    private val cache = HashMap<String, Entry>()

    /** Backend parity (intelligence spec): rain/storm/snow/mist/fog + extreme heat. */
    fun isUnfavorable(condition: String, tempC: Double): Boolean {
        if (tempC >= 40.0) return true
        return when (condition) {
            "Thunderstorm", "Drizzle", "Rain", "Snow", "Mist", "Smoke", "Haze",
            "Dust", "Fog", "Sand", "Ash", "Squall", "Tornado" -> true
            else -> false // Clear, Clouds
        }
    }

    /** Which itinerary kinds count as weather-sensitive (outdoor). */
    fun isOutdoorKind(kind: String): Boolean = kind == "sight"

    /** Indoor fallback kinds for same-day swaps. */
    fun isIndoorKind(kind: String): Boolean = kind == "food" || kind == "hotel"

    suspend fun forecast(lat: Double, lng: Double, apiKey: String): List<DayForecast>? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            val key = "%.2f,%.2f".format(lat, lng)
            val now = System.currentTimeMillis()
            cache[key]?.let { if (now - it.at < TTL_MS) return@withContext it.days }
            val list = runCatching { fetch(lat, lng, apiKey) }.getOrNull()
                ?: return@withContext null
            cache[key] = Entry(now, list)
            list
        }

    private fun fetch(lat: Double, lng: Double, apiKey: String): List<DayForecast> {
        val url = URL(
            "https://api.openweathermap.org/data/2.5/forecast" +
                "?lat=$lat&lon=$lng&appid=$apiKey&units=metric"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Travollio")
        }
        try {
            if (conn.responseCode !in 200..299) return emptyList()
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return aggregate(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun aggregate(body: String): List<DayForecast> {
        val arr = JSONObject(body).optJSONArray("list") ?: return emptyList()
        fun rank(c: String) = when (c) {
            "Tornado" -> 12; "Thunderstorm" -> 11; "Snow" -> 10; "Rain" -> 9
            "Drizzle" -> 8; "Squall" -> 7; "Ash" -> 6; "Sand", "Dust" -> 5
            "Smoke" -> 4; "Haze" -> 3; "Fog", "Mist" -> 2; "Clouds" -> 1; else -> 0
        }
        data class Slot(val cond: String, val desc: String, val maxT: Double, val rain: Double)
        val byDate = LinkedHashMap<String, MutableList<Slot>>()
        for (i in 0 until arr.length()) {
            val slot = arr.optJSONObject(i) ?: continue
            val dt = slot.optString("dt_txt", "")
            if (dt.length < 10) continue
            val w = slot.optJSONArray("weather")?.optJSONObject(0) ?: continue
            val main = slot.optJSONObject("main") ?: continue
            val rain = slot.optJSONObject("rain")?.optDouble("3h", 0.0) ?: 0.0
            val maxT = main.optDouble("temp_max", main.optDouble("temp", Double.NaN))
            byDate.getOrPut(dt.substring(0, 10)) { mutableListOf() }.add(
                Slot(w.optString("main", ""), w.optString("description", ""), maxT, rain)
            )
        }
        return byDate.map { (date, slots) ->
            val worst = slots.maxByOrNull { rank(it.cond) } ?: Slot("Clear", "clear sky", 0.0, 0.0)
            DayForecast(
                date = date,
                condition = worst.cond.ifEmpty { "Clear" },
                description = worst.desc,
                tempC = slots.map { it.maxT }.filter { it.isFinite() }.maxOrNull() ?: 0.0,
                rainMm = slots.sumOf { it.rain }
            )
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
