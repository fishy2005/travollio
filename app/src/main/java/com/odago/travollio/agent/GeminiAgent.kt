package com.odago.travollio.agent

import androidx.room.withTransaction
import com.odago.travollio.data.AgentConfig
import com.odago.travollio.data.AppDatabase
import com.odago.travollio.data.ChatMessage
import com.odago.travollio.data.Day
import com.odago.travollio.data.Document
import com.odago.travollio.data.Item
import com.odago.travollio.data.MAX_DOC_TEXT
import com.odago.travollio.data.PriceWatch
import com.odago.travollio.data.Transaction
import com.odago.travollio.data.Trip
import com.odago.travollio.data.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Travollio's cloud agent — DeepSeek (OpenAI-compatible chat completions with
 * function calling). This is the always-on, quota-friendly backend.
 *
 * Contract (spec v0.2, offline-honesty):
 *  - The model may only state what tools return; trip data is never invented.
 *  - Mutations (itinerary add/update/move/remove, day.note) write to Room → Flow → UI updates live.
 *  - On any network/API failure the caller falls back to the deterministic local engine
 *    and says so — no silent hallucination, no fake "cloud" answers.
 *  - Attachments are listed by name/type; images are only sent as vision parts
 *    when the user enables Vision in Settings (text-only models stay text-only).
 */
class GeminiAgent(
    private val db: AppDatabase,
    private val weatherApiKey: () -> String? = { null }
) {

    /** Raised when the cloud (DeepSeek) call fails for any reason, with a human reason. */
    class CloudException(message: String) : Exception(message)

    private var lastHttpError: String? = null

    /** Full chat-completions endpoint for the currently selected provider (runtime-configurable). */
    private fun chatUrl(): String = AgentConfig.baseUrl.trimEnd('/') + "/chat/completions"

    /** Format an ISO yyyy-MM-dd date as dd.MM.yyyy for human-facing agent replies. */
    private fun dt(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        return runCatching { java.time.LocalDate.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")) }
            .getOrDefault(iso)
    }

    private val systemPrompt = """
        You are Travollio — a crisp, smart trip assistant. You text like a fast, competent bot: short, plain, useful. No filler, no fluff, no emoji, no personality theatrics. One or two short sentences per reply, three max.
        Home currency is ILS (₪); show local currency with its ₪ equivalent when both exist.
        Rules:
        - If there's no trip yet, don't tell the user to make one — create it yourself with trip_create (name + startDate + endDate), then build out days with day_add. itinerary_get / today_plan will tell you whether a trip exists.
        - Act, don't ask. You have full control of the whole app and can drive it directly: trips (trip_create / trip_new / trip_update / trip_switch / trip_archive / trip_delete / trip_list / trip_get), days (day_add / day_update / day_remove), the itinerary items (itinerary_add / itinerary_update / itinerary_move / itinerary_remove / itinerary_import for bulk), the budget (money_add / money_summary / money_list / money_convert), travel documents (docs_add / docs_get), stored files (files_list / files_attach), daily notes (day_note), live weather (weather_get), price watches (pricewatch_add / pricewatch_list / pricewatch_remove / pricewatch_check), and today's view (today_plan). Just do what the traveler asked. Inspect state silently with itinerary_get / today_plan / money_summary first when you need details, then act in the same turn. Confirm in one short line like "Added to today's plans." or "Logged 4,500 JPY for sushi."
        - Multiple trips: when a trip is finished, offer trip_archive to keep it for reference; start a fresh one with trip_new (works even if others exist); switch to a saved trip anytime with trip_switch; enumerate with trip_list. Never delete a trip without the user confirming.
        - Preview before destructive/bulk: for trip_delete, day_remove, and itinerary_import, do NOT pass confirm:true on the first call. Instead state what you'll do (e.g. "I'll delete 'Tokyo' and all its days/items/shows — confirm?") and wait; only call it again with confirm:true after the user explicitly says yes.
        - Importing an itinerary (Word/document): when the user attaches a document, its text is provided as [Document content …]. Treat every date as anchored to the trip's startDate; expand ranges like "Days 2-3" into separate days; convert "3pm" → 15:00 and "¥9,800"/"9800 yen" → costLocal 9800 ccy JPY; put booking refs into confirmation; put per-day summaries into day_note. If a trip already exists, ask whether to replace, or archive + start a new one. Use itinerary_import to add many items in one call.
        - Only ask a follow-up question when a critical detail is genuinely missing and would change what you do (e.g. "which day?" when it's ambiguous). Otherwise infer and go. Never ask just to be safe.
        - Never invent bookings, prices, times or places. Answer only from tool results; if you can't find it, say so in one line.
        - Day numbers are 1-based; day dates come from itinerary_get.
        - When you change a day's plan, also refresh that day's note via day_note so the Today/Trip screens stay accurate.
        - The user can attach receipts/tickets/files; they appear as [Attachments: …]. Use files_list to see stored files and link them to items via attachmentIds on itinerary_add / itinerary_update.
        - When the traveler says "I booked X at Y time", create it with itinerary_add — include timeLocal, place, address, confirmation, and cost when given.
        - After you act, the app automatically shows a "Go to" button on your reply — do NOT invent or describe that button; just keep the text short.
    """.trimIndent()

    // OpenAI-style tool declarations (DeepSeek requires function names to match [a-zA-Z0-9_-]+).
    private val tools = JSONArray().apply {
        put(toolDecl("itinerary_get", "Full trip state: trip name/dates, every day (index, date, city) and its items (id, time, title, kind). Call this before any other itinerary tool.",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("itinerary_add", "Add an item to a day. 'day' is the 1-based day index from itinerary_get; 'date' (yyyy-MM-dd) also works and wins if both given. Include place/address/confirmation when the user mentions them, and link attached files with attachmentIds.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("title", JSONObject().put("type", "string").put("description", "What it is, e.g. 'Sushi dinner in Tsukishima'"))
                .put("day", JSONObject().put("type", "integer").put("description", "1-based day index"))
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional"))
                .put("timeLocal", JSONObject().put("type", "string").put("description", "HH:mm local time, optional"))
                .put("kind", JSONObject().put("type", "string").put("enum", JSONArray().put("transit").put("food").put("sight").put("hotel").put("errand")))
                .put("place", JSONObject().put("type", "string").put("description", "venue name"))
                .put("address", JSONObject().put("type", "string").put("description", "street address, optional"))
                .put("link", JSONObject().put("type", "string").put("description", "booking/ticket/maps URL, optional"))
                .put("confirmation", JSONObject().put("type", "string").put("description", "booking confirmation code, optional"))
                .put("costLocal", JSONObject().put("type", "number").put("description", "price in local currency, optional"))
                .put("ccy", JSONObject().put("type", "string").put("description", "local currency code, e.g. JPY"))
                .put("detail", JSONObject().put("type", "string").put("description", "one-line note, optional"))
                .put("attachmentIds", JSONObject().put("type", "array").put("items", JSONObject().put("type", "integer")).put("description", "ids of files the user attached that belong to this item")))))
        put(toolDecl("itinerary_update", "Edit any field of an existing item — title, time, place, address, notes, cost, confirmation, kind. Pass itemId plus ONLY the fields to change. attachmentIds attaches more files.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("itemId", JSONObject().put("type", "integer").put("description", "item id from itinerary_get"))
                .put("title", JSONObject().put("type", "string"))
                .put("timeLocal", JSONObject().put("type", "string").put("description", "HH:mm"))
                .put("detail", JSONObject().put("type", "string"))
                .put("kind", JSONObject().put("type", "string").put("enum", JSONArray().put("transit").put("food").put("sight").put("hotel").put("errand")))
                .put("place", JSONObject().put("type", "string"))
                .put("address", JSONObject().put("type", "string"))
                .put("link", JSONObject().put("type", "string"))
                .put("confirmation", JSONObject().put("type", "string"))
                .put("costLocal", JSONObject().put("type", "number"))
                .put("ccy", JSONObject().put("type", "string"))
                .put("attachmentIds", JSONObject().put("type", "array").put("items", JSONObject().put("type", "integer"))))))
        put(toolDecl("itinerary_move", "Move an existing item to another day (appended at the end of that day).",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("itemId", JSONObject().put("type", "integer"))
                .put("day", JSONObject().put("type", "integer").put("description", "target 1-based day index"))
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional")))))
        put(toolDecl("itinerary_remove", "Delete an item from the itinerary.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("itemId", JSONObject().put("type", "integer")))))
        put(toolDecl("money_summary", "Budget summary: spent vs cap, pace projection, top category. No arguments.",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("money_convert", "Convert an amount between currencies using the last known cached rate (offline-safe).",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("amount", JSONObject().put("type", "number"))
                .put("from", JSONObject().put("type", "string"))
                .put("to", JSONObject().put("type", "string")))))
        put(toolDecl("docs_get", "Travel documents on file (passport, visa, insurance) with expiry dates and days left. Optional 'kind' filter.",
            JSONObject().put("type", "object").put("properties", JSONObject().put("kind", JSONObject().put("type", "string")))))
        put(toolDecl("emergency_info", "Bundled offline emergency info for a country/city: local emergency numbers, tourist hotline, embassy note. No live web access.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("country", JSONObject().put("type", "string"))
                .put("city", JSONObject().put("type", "string")))))
        put(toolDecl("day_note", "Write or refresh the AI note/daily summary for one day of the trip. Call this when you change a day's plan or the user asks you to summarize a day. The note is shown on the Today and Trip screens.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("day", JSONObject().put("type", "integer").put("description", "1-based day index from itinerary_get"))
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional; wins if both given"))
                .put("content", JSONObject().put("type", "string").put("description", "The summary or note. 1-3 short sentences, concrete and helpful.")))))
        put(toolDecl("files_list", "List the files/photos stored in the app (receipts, tickets, screenshots) with their names, types, and which moment they're linked to (if any).",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("files_attach", "Link an existing stored file to an itinerary moment (file a photo/receipt under an item).",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("fileId", JSONObject().put("type", "integer").put("description", "attachment id from files_list"))
                .put("itemId", JSONObject().put("type", "integer")))))
        put(toolDecl("trip_update", "Change trip-level settings: rename the trip, change its dates, base city, home currency, or the budget cap. Pass only the fields to change.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("name", JSONObject().put("type", "string"))
                .put("startDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional"))
                .put("endDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional"))
                .put("baseCity", JSONObject().put("type", "string"))
                .put("budgetCapHomeCcy", JSONObject().put("type", "number").put("description", "new budget cap"))
                .put("homeCcy", JSONObject().put("type", "string")))))
        put(toolDecl("money_add", "Record a purchase/expense into the trip budget. Give amountLocal+ccy (e.g. 4500 JPY) or amountHomeCcy (ILS). Category is inferred from the note if omitted.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("amountLocal", JSONObject().put("type", "number").put("description", "amount in local currency, optional"))
                .put("ccy", JSONObject().put("type", "string").put("description", "local currency code, e.g. JPY"))
                .put("amountHomeCcy", JSONObject().put("type", "number").put("description", "amount in home currency, optional"))
                .put("note", JSONObject().put("type", "string").put("description", "what it was for, e.g. 'Sushi dinner'"))
                .put("category", JSONObject().put("type", "string").put("enum", JSONArray().put("food").put("transit").put("lodging").put("shopping").put("entertainment").put("errand").put("other")))
                .put("ts", JSONObject().put("type", "string").put("description", "yyyy-MM-ddTHH:mm:ss, optional; defaults to now")))))
        put(toolDecl("money_list", "List the trip's transactions (spend) newest-first with category, note, and amounts.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("limit", JSONObject().put("type", "integer")))))
        put(toolDecl("docs_add", "Add or update a travel document on file (passport/visa/insurance) with its expiry date. Matches by kind — existing entry is updated.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("kind", JSONObject().put("type", "string").put("enum", JSONArray().put("passport").put("visa").put("insurance").put("other")))
                .put("expiresAt", JSONObject().put("type", "string").put("description", "yyyy-MM-dd, optional"))
                .put("filePath", JSONObject().put("type", "string").put("description", "display path, optional")))))
        put(toolDecl("trip_create", "Create the trip if none exists yet. Required: name, startDate (yyyy-MM-dd), endDate (yyyy-MM-dd). Optional: baseCity, budgetCapHomeCcy, homeCcy, localCcy.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("name", JSONObject().put("type", "string").put("description", "trip name, e.g. 'Tokyo'"))
                .put("startDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("endDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("baseCity", JSONObject().put("type", "string"))
                .put("budgetCapHomeCcy", JSONObject().put("type", "number").put("description", "budget cap"))
                .put("homeCcy", JSONObject().put("type", "string").put("description", "e.g. ILS"))
                .put("localCcy", JSONObject().put("type", "string").put("description", "e.g. JPY, EUR")))))
        put(toolDecl("day_add", "Add a day to the trip (date yyyy-MM-dd + city). Call this to expand the itinerary with more days.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("city", JSONObject().put("type", "string")))))
        put(toolDecl("day_update", "Change a day's date or city. Target it with the 'day' (1-based, from itinerary_get) or existing 'date'; pass 'newDate' (yyyy-MM-dd) to move it to a different date, or 'city' to rename it.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("day", JSONObject().put("type", "integer").put("description", "1-based day index"))
                .put("date", JSONObject().put("type", "string").put("description", "existing date to target (yyyy-MM-dd)"))
                .put("newDate", JSONObject().put("type", "string").put("description", "the new date to set (yyyy-MM-dd), optional"))
                .put("city", JSONObject().put("type", "string")))))
        put(toolDecl("day_remove", "Remove a day from the trip (and its items). Pass the 'day' (1-based) or 'date'. Set confirm:true only after the user agrees.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("day", JSONObject().put("type", "integer").put("description", "1-based day index"))
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd of the day to remove"))
                .put("confirm", JSONObject().put("type", "boolean").put("description", "required; true only after the user confirms")))))
        put(toolDecl("today_plan", "What the Today tab shows: the current day (or first day), its city/date, its plan items, and the AI note. Read this before answering 'what's today'.",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("weather_get", "Live 5-day weather forecast (OpenWeatherMap) for trip coordinates. Pass lat/lng, or a 1-based 'day' to use that day's first item with coordinates, or nothing to use the next outdoor item. Returns per-day condition/temp plus an unfavorable verdict. Needs a key in Settings → Live data.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("lat", JSONObject().put("type", "number"))
                .put("lng", JSONObject().put("type", "number"))
                .put("day", JSONObject().put("type", "integer").put("description", "1-based day index from itinerary_get"))
                .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd to highlight, optional")))))
        put(toolDecl("pricewatch_add", "Watch a target price (e.g. hotel night, tickets, flight). Alerts fire when a recorded observation hits the target. Observations are recorded with pricewatch_check.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("label", JSONObject().put("type", "string").put("description", "what is being watched, e.g. 'teamLab tickets'"))
                .put("targetAmount", JSONObject().put("type", "number"))
                .put("targetCcy", JSONObject().put("type", "string").put("description", "e.g. JPY"))
                .put("itemId", JSONObject().put("type", "integer").put("description", "linked itinerary item, optional"))
                .put("note", JSONObject().put("type", "string")))))
        put(toolDecl("pricewatch_list", "List the trip's price watches with targets, last observations, and hit status.",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("pricewatch_remove", "Remove a price watch by id.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "integer")))))
        put(toolDecl("pricewatch_check", "Record an observed price for a watch (same currency as its target). Returns whether the target is hit.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("id", JSONObject().put("type", "integer").put("description", "watch id from pricewatch_list"))
                .put("observedAmount", JSONObject().put("type", "number")))))
        put(toolDecl("trip_list", "List every saved trip with its id, name, dates, city, whether it's archived, and which is active. Use before archiving/switching/importing.",
            JSONObject().put("type", "object").put("properties", JSONObject()).put("required", JSONArray())))
        put(toolDecl("trip_get", "Get one trip's full detail (by tripId, or the active trip if omitted): its metadata, spend, and each day with items.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("tripId", JSONObject().put("type", "integer").put("description", "trip id from trip_list, optional — defaults to active trip")))))
        put(toolDecl("trip_new", "Create a BRAND-NEW trip even if one already exists, and make it active. Unlike trip_create, this does not require there to be no trip. Other trips are left untouched.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("name", JSONObject().put("type", "string").put("description", "trip name, e.g. 'Paris'"))
                .put("startDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("endDate", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                .put("baseCity", JSONObject().put("type", "string"))
                .put("budgetCapHomeCcy", JSONObject().put("type", "number").put("description", "budget cap"))
                .put("homeCcy", JSONObject().put("type", "string").put("description", "e.g. ILS"))
                .put("localCcy", JSONObject().put("type", "string").put("description", "e.g. JPY, EUR")))))
        put(toolDecl("trip_switch", "Switch the active trip to another saved trip (re-opening an archived one if needed). Everything (today/trip/money) immediately shows that trip.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("tripId", JSONObject().put("type", "integer").put("description", "trip id from trip_list")))))
        put(toolDecl("trip_archive", "Archive a finished trip — it's kept for reference but is no longer active. The next trip becomes active.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("tripId", JSONObject().put("type", "integer").put("description", "trip id; defaults to the active trip")))))
        put(toolDecl("trip_delete", "Permanently delete a trip and its days/items/spend. Cannot be undone. Set confirm:true only after the user agrees.",
            JSONObject().put("type", "object").put("properties", JSONObject()
                .put("tripId", JSONObject().put("type", "integer").put("description", "trip id; defaults to the active trip"))
                .put("confirm", JSONObject().put("type", "boolean").put("description", "required; true only after the user confirms")))))
        put(toolDecl("itinerary_import", "Insert many days/items at once from structured input (e.g. a parsed Word itinerary) in one transaction — avoids many round-trips. 'day' (1-based) or 'date' picks the day; date works and wins. Days referenced by items that don't exist are created automatically.",
            run {
                val dayProps = JSONObject()
                    .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd"))
                    .put("city", JSONObject().put("type", "string"))
                val itemProps = JSONObject()
                    .put("title", JSONObject().put("type", "string"))
                    .put("day", JSONObject().put("type", "integer").put("description", "1-based day index"))
                    .put("date", JSONObject().put("type", "string").put("description", "yyyy-MM-dd; wins if both given"))
                    .put("timeLocal", JSONObject().put("type", "string").put("description", "HH:mm"))
                    .put("kind", JSONObject().put("type", "string").put("enum", JSONArray().put("transit").put("food").put("sight").put("hotel").put("errand")))
                    .put("place", JSONObject().put("type", "string"))
                    .put("address", JSONObject().put("type", "string"))
                    .put("link", JSONObject().put("type", "string"))
                    .put("confirmation", JSONObject().put("type", "string"))
                    .put("costLocal", JSONObject().put("type", "number"))
                    .put("ccy", JSONObject().put("type", "string"))
                    .put("detail", JSONObject().put("type", "string"))
                val daysSchema = JSONObject().put("type", "array")
                    .put("items", JSONObject().put("type", "object").put("properties", dayProps))
                val itemsSchema = JSONObject().put("type", "array")
                    .put("items", JSONObject().put("type", "object").put("properties", itemProps))
                JSONObject().put("type", "object").put("properties", JSONObject()
                    .put("tripId", JSONObject().put("type", "integer").put("description", "target trip id; defaults to active trip"))
                    .put("days", daysSchema)
                    .put("items", itemsSchema)
                    .put("confirm", JSONObject().put("type", "boolean").put("description", "Set true only after the user confirms this import")))
            }))
    }

    private fun toolDecl(name: String, desc: String, params: JSONObject): JSONObject =
        JSONObject().put("type", "function")
            .put("function", JSONObject().put("name", name).put("description", desc).put("parameters", params))

    /** Result of a cloud turn: the short reply text plus an optional deep-link action
     *  (e.g. which item/day was changed) that the UI turns into a "Go to" button. */
    class AgentResult(val text: String, val actionJson: String?)

    /** Set by the last mutating tool so the reply can deep-link the user to the change. */
    private var lastActionJson: String? = null

    /**
     * Run the DeepSeek tool-calling loop. Returns the assistant's final text (plus a deep-link
     * action when a change was made), or throws [CloudException] on any cloud failure
     * (network/HTTP/timeout) so the caller can fall back to the local engine and report why.
     *
     * When [streamDelta] is supplied, the answer text is pushed through it token-by-token as it
     * is produced (OpenAI-style SSE), so the UI can render it live instead of waiting. Tool-call
     * turns stream no content, but final-answer turns stream the whole reply.
     */
    suspend fun respond(
        history: List<ChatMessage>,
        userText: String,
        attachments: List<com.odago.travollio.data.AttachmentMeta> = emptyList(),
        streamDelta: (String) -> Unit = {}
    ): AgentResult = withContext(Dispatchers.IO) {
        lastActionJson = null
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        // Conversation continuity from stored text turns (tool calls aren't persisted).
        history.takeLast(12).forEach { m ->
            val txt = if (m.cardJson != null) m.text + "\n" + attachedNames(m.cardJson) else m.text
            messages.put(JSONObject().put("role", if (m.role == "user") "user" else "assistant").put("content", txt))
        }
        val names = attachments.joinToString(", ") { "${it.name} (${it.mime})" }
        val docTexts = attachments.filter { !it.text.isNullOrBlank() }
            .joinToString("\n\n") { d -> "[Document content — ${d.name}]\n${d.text!!.take(MAX_DOC_TEXT)}" }
        val attachNote = if (attachments.isEmpty()) "" else
            "\n\n[Attachments: $names]\n\n$docTexts"
        // Images are sent only when the user enabled Vision (Settings → AI Assistant):
        // a vision-capable model reads them as OpenAI image_url parts. If a
        // provider rejects the parts (text-only model → HTTP 4xx), the loop below retries
        // the turn text-only instead of failing outright.
        val images = if (AgentConfig.vision) attachments.filter { it.mime.startsWith("image/") } else emptyList()
        val userMessage = JSONObject().put("role", "user")
        if (images.isEmpty()) {
            userMessage.put("content", userText + attachNote)
        } else {
            val parts = JSONArray()
            parts.put(JSONObject().put("type", "text").put("text", userText + attachNote))
            images.forEach { m ->
                imageDataUri(m)?.let { uri ->
                    parts.put(JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", uri)))
                }
            }
            userMessage.put("content", parts)
        }
        messages.put(userMessage)
        val userMessageIndex = messages.length() - 1
        var sentImages = images.isNotEmpty()

        var lastError: String? = null
        for (turn in 0 until 6) {
            // Streaming is only requested for cloud providers: some strict local servers
            // (e.g. LM Studio) reject `stream:true` together with tool-calling, surfacing a
            // 400. Non-streaming is the safe path for them, and identical otherwise.
            val useStream = !AgentConfig.isLocal
            val body = JSONObject()
                .put("model", AgentConfig.model)
                .put("messages", messages)
                .put("tools", tools)
                .put("tool_choice", "auto")
            var resp: JSONObject? = if (useStream) streamPost(body, streamDelta) else postRaw(body)
            if (resp == null) {
                // A transport-level failure (DNS/refused/connect/timeout) is worth one idempotent
                // retry — the request likely never reached the server. An HTTP-level failure
                // (4xx/5xx, e.g. 401 bad key or 429 rate-limit) means the request was REJECTED,
                // so no mutation ran; we can safely retry once WITHOUT streaming in case the
                // provider refuses the SSE form (local servers).
                val firstErr = lastHttpError ?: "network error"
                if (sentImages && firstErr.startsWith("HTTP")) {
                    // Provider rejected the image parts (text-only model) — swap the user
                    // message for its text-only form and retry so the turn still works.
                    sentImages = false
                    messages.put(userMessageIndex, JSONObject().put("role", "user").put("content", userText + attachNote))
                    if (lastError == null) lastError = firstErr
                    continue
                }
                if (firstErr.startsWith("HTTP")) {
                    resp = postRaw(body)
                    if (resp == null) { lastError = lastHttpError ?: firstErr; break }
                } else {
                    if (lastError == null) lastError = firstErr
                    resp = if (useStream) streamPost(body, streamDelta) else postRaw(body)
                    if (resp == null) {
                        lastError = lastHttpError ?: firstErr
                        break
                    }
                }
            }
            val msg = resp.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            if (msg == null) { lastError = "empty response"; continue }
            val content = msg.optString("content", "")
            val toolCalls = msg.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                val text = content.trim().ifEmpty { "I don't have an answer for that from my tools." }
                return@withContext AgentResult(text, lastActionJson)
            }
            // Echo the assistant turn (with its tool_calls) so the conversation stays valid.
            messages.put(msg)
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val id = tc.optString("id")
                val fn = tc.optJSONObject("function")
                val name = fn?.optString("name") ?: ""
                val args = try { JSONObject(fn?.optString("arguments", "{}") ?: "{}") } catch (_: Exception) { JSONObject() }
                val result = runTool(name, args)
                // A bulk/destructive tool is awaiting the user's explicit confirmation — surface
                // that as the reply and stop the loop, so the model can't self-confirm.
                if (result.optBoolean("needsConfirm", false)) {
                    return@withContext AgentResult(result.optString("message", "Confirm?"), lastActionJson)
                }
                messages.put(JSONObject().put("role", "tool").put("tool_call_id", id).put("content", result.toString()))
            }
        }
        if (lastError != null) throw CloudException(lastError)
        AgentResult("I hit my tool-loop limit on that one — try a smaller ask.", lastActionJson)
    }

    // ---------- tools ----------

    private suspend fun runTool(name: String, args: JSONObject): JSONObject = when (name) {
        "itinerary_get" -> itineraryGet()
        "itinerary_add" -> itineraryAdd(args)
        "itinerary_update" -> itineraryUpdate(args)
        "itinerary_move" -> itineraryMove(args)
        "itinerary_remove" -> itineraryRemove(args)
        "money_summary" -> moneySummary()
        "money_convert" -> moneyConvert(args)
        "docs_get" -> docsGet(args)
        "emergency_info" -> emergencyInfo(args)
        "day_note" -> dayNote(args)
        "files_list" -> filesList()
        "files_attach" -> filesAttach(args)
        "trip_update" -> tripUpdate(args)
        "money_add" -> moneyAdd(args)
        "money_list" -> moneyList(args)
        "docs_add" -> docsAdd(args)
        "trip_create" -> tripCreate(args)
        "day_add" -> dayAdd(args)
        "day_update" -> dayUpdate(args)
        "day_remove" -> dayRemove(args)
        "today_plan" -> todayPlan()
        "trip_list" -> tripList(args)
        "trip_get" -> tripGet(args)
        "trip_new" -> tripNew(args)
        "trip_switch" -> tripSwitch(args)
        "trip_archive" -> tripArchive(args)
        "trip_delete" -> tripDelete(args)
        "itinerary_import" -> itineraryImport(args)
        "weather_get" -> weatherGet(args)
        "pricewatch_add" -> priceWatchAdd(args)
        "pricewatch_list" -> priceWatchList()
        "pricewatch_remove" -> priceWatchRemove(args)
        "pricewatch_check" -> priceWatchCheck(args)
        else -> JSONObject().put("error", "unknown tool $name")
    }

    private suspend fun itineraryGet(): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("trip", null)
        val days = db.dayDao().daysForTripOnce(trip.id)
        val items = db.itemDao().itemsForTripOnce(trip.id)
        val byDay = HashMap<Long, MutableList<Item>>()
        items.forEach { i -> byDay.getOrPut(i.dayId) { mutableListOf() }.add(i) }

        val dayArr = JSONArray()
        days.forEachIndexed { idx, d ->
            val list = byDay[d.id].orEmpty()
            val itemArr = JSONArray()
            list.forEach { it ->
                itemArr.put(JSONObject().put("id", it.id).put("time", it.timeLocal ?: "")
                    .put("title", it.title).put("kind", it.kind)
                    .put("place", it.place ?: JSONObject.NULL)
                    .put("confirmation", it.confirmation ?: JSONObject.NULL)
                    .put("lat", it.lat ?: JSONObject.NULL).put("lng", it.lng ?: JSONObject.NULL)
                    .put("costLocal", it.costLocal ?: JSONObject.NULL))
            }
            dayArr.put(JSONObject().put("day", idx + 1).put("date", d.date).put("city", d.city).put("items", itemArr))
        }
        return JSONObject().put("trip", trip.name)
            .put("start", trip.startDate).put("end", trip.endDate)
            .put("homeCcy", trip.homeCcy).put("budgetCapHomeCcy", trip.budgetCapHomeCcy)
            .put("days", dayArr)
    }

    private suspend fun itineraryAdd(args: JSONObject): JSONObject {
        val title = args.optString("title", "").trim().take(200)
        if (title.isEmpty()) return JSONObject().put("error", "missing title")
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val day = resolveDay(days, args) ?: return JSONObject().put("error", "could not resolve the requested day; use itinerary_get for valid day indices/dates")
        val kind = args.optString("kind", "").ifEmpty { guessKind(title) }
        val costLocal = if (args.has("costLocal")) args.optDouble("costLocal", Double.NaN).takeIf { !it.isNaN() } else null
        val ccy = args.optString("ccy", "")

        db.withTransaction {
            val sort = db.itemDao().maxSortForDay(day.id) + 1
            val itemId = db.itemDao().insert(
                Item(
                    dayId = day.id,
                    sort = sort,
                    timeLocal = args.optString("timeLocal", "").takeIf { it.isNotBlank() },
                    title = title,
                    detail = args.optString("detail", ""),
                    kind = kind,
                    costLocal = costLocal,
                    ccy = ccy.ifEmpty { null },
                    place = args.optString("place", "").takeIf { it.isNotBlank() },
                    address = args.optString("address", "").takeIf { it.isNotBlank() },
                    link = args.optString("link", "").takeIf { it.isNotBlank() },
                    confirmation = args.optString("confirmation", "").takeIf { it.isNotBlank() }
                )
            )
            linkAttachments(itemId, args)
            lastActionJson = JSONObject().put("type", "item").put("itemId", itemId).toString()
        }
        return JSONObject().put("ok", true)
            .put("message", "Added '$title' to day ${days.indexOf(day) + 1} (${dt(day.date)}, ${day.city})")
    }

    private suspend fun itineraryUpdate(args: JSONObject): JSONObject {
        val id = args.optLong("itemId", -1L)
        if (id < 0) return JSONObject().put("error", "missing itemId")
        val item = db.itemDao().itemOnce(id) ?: return JSONObject().put("error", "no such item $id")

        val updated = item.copy(
            title = args.optString("title", "").trim().take(200).ifEmpty { item.title },
            timeLocal = args.optString("timeLocal", "").takeIf { it.isNotBlank() } ?: item.timeLocal,
            detail = args.optString("detail", item.detail),
            kind = args.optString("kind", item.kind),
            place = if (args.has("place")) args.optString("place", "").takeIf { it.isNotBlank() } else item.place,
            address = if (args.has("address")) args.optString("address", "").takeIf { it.isNotBlank() } else item.address,
            link = if (args.has("link")) args.optString("link", "").takeIf { it.isNotBlank() } else item.link,
            confirmation = if (args.has("confirmation")) args.optString("confirmation", "").takeIf { it.isNotBlank() } else item.confirmation,
            costLocal = if (args.has("costLocal")) args.optDouble("costLocal", Double.NaN).takeIf { !it.isNaN() } else item.costLocal,
            ccy = if (args.has("ccy")) args.optString("ccy", "").takeIf { it.isNotBlank() } else item.ccy
        )
        if (updated == item) return JSONObject().put("error", "nothing to update — pass at least one field to change")

        db.withTransaction {
            db.itemDao().update(updated)
            linkAttachments(id, args)
            lastActionJson = JSONObject().put("type", "item").put("itemId", id).toString()
        }
        return JSONObject().put("ok", true).put("message", "Updated '${item.title}'")
    }

    private suspend fun linkAttachments(itemId: Long, args: JSONObject) {
        val ids = args.optJSONArray("attachmentIds") ?: return
        val list = (0 until ids.length()).mapNotNull { i -> ids.optLong(i).takeIf { it > 0 } }
        if (list.isNotEmpty()) db.attachmentDao().link(itemId, list)
    }

    private suspend fun itineraryMove(args: JSONObject): JSONObject {
        val id = args.optLong("itemId", -1L)
        if (id < 0) return JSONObject().put("error", "missing itemId")
        val item = db.itemDao().itemOnce(id) ?: return JSONObject().put("error", "no such item $id")
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val target = resolveDay(days, args) ?: return JSONObject().put("error", "could not resolve target day")
        db.withTransaction {
            val sort = db.itemDao().maxSortForDay(target.id) + 1
            db.itemDao().moveItem(id, target.id, sort)
            lastActionJson = JSONObject().put("type", "item").put("itemId", id).toString()
        }
        return JSONObject().put("ok", true)
            .put("message", "Moved '${item.title}' to day ${days.indexOf(target) + 1} (${dt(target.date)}, ${target.city})")
    }

    private suspend fun itineraryRemove(args: JSONObject): JSONObject {
        val id = args.optLong("itemId", -1L)
        if (id < 0) return JSONObject().put("error", "missing itemId")
        val item = db.itemDao().itemOnce(id) ?: return JSONObject().put("error", "no such item $id")
        db.withTransaction { db.itemDao().delete(item) }
        // The item is gone — deep-link to the day it lived on.
        val trip = db.activeTrip()
        val dayIdx = if (trip != null) {
            val days = db.dayDao().daysForTripOnce(trip.id)
            days.indexOfFirst { it.id == item.dayId } + 1
        } else 0
        if (dayIdx > 0) lastActionJson = JSONObject().put("type", "day").put("day", dayIdx).toString()
        return JSONObject().put("ok", true).put("message", "Removed '${item.title}' from the itinerary")
    }

    private fun resolveDay(days: List<com.odago.travollio.data.Day>, args: JSONObject): com.odago.travollio.data.Day? {
        val date = args.optString("date", "")
        if (date.isNotBlank()) return days.firstOrNull { it.date == date }
        val idx = args.optInt("day", -1)
        if (idx in 1..days.size) return days[idx - 1]
        return null
    }

    private fun guessKind(title: String): String {
        val t = title.lowercase()
        return when {
            t.contains("train") || t.contains("n'ex") || t.contains("flight") || t.contains("taxi") -> "transit"
            t.contains("dinner") || t.contains("lunch") || t.contains("breakfast") || t.contains("ramen") || t.contains("sushi") -> "food"
            t.contains("check in") || t.contains("hotel") -> "hotel"
            else -> "sight"
        }
    }

    private suspend fun moneySummary(): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val txs = db.transactionDao().forTripOnce(trip.id)
        val spent = txs.sumOf { it.amountHomeCcy }
        val cap = trip.budgetCapHomeCcy
        val localSpent = txs.sumOf { it.amountLocal ?: 0.0 }

        val byCat = HashMap<String, Double>()
        txs.forEach { t -> byCat[t.category] = (byCat[t.category] ?: 0.0) + t.amountHomeCcy }
        val top = byCat.maxByOrNull { it.value }

        val today = java.time.LocalDate.now()
        val start = java.time.LocalDate.parse(trip.startDate)
        val end = java.time.LocalDate.parse(trip.endDate)
        var pace: String? = null
        if (!today.isBefore(start) && !today.isAfter(end)) {
            val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt() + 1
            val elapsed = (java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
            val projected = spent / (elapsed.toDouble() / totalDays)
            pace = if (projected > cap * 1.05) "hot" else "on-pace"
        }
        return JSONObject().put("spentHomeCcy", spent).put("capHomeCcy", cap)
            .put("remainingHomeCcy", cap - spent)
            .put("spentLocal", localSpent).put("localCcy", trip.localCcy)
            .put("topCategory", top?.key ?: "").put("topCategoryAmount", top?.value ?: 0.0)
            .put("pace", pace ?: "not-in-trip-window")
    }

    private fun moneyConvert(args: JSONObject): JSONObject {
        val amount = args.optDouble("amount", Double.NaN)
        if (amount.isNaN()) return JSONObject().put("error", "missing amount")
        val from = args.optString("from", "").uppercase()
        val to = args.optString("to", "").uppercase()
        val perIls: Double? = rateToHome(from, to)
        return if (perIls != null) {
            JSONObject().put("amount", amount).put("from", from).put("to", to)
                .put("result", amount * perIls)
                .put("rateNote", "last known cached rate (offline)")
        } else {
            JSONObject().put("error", "no cached rate for $from→$to; I convert between ILS, USD, EUR, GBP, JPY, THB and more offline")
        }
    }

    private suspend fun docsGet(args: JSONObject): JSONObject {
        val kind = args.optString("kind", "")
        var docs = db.documentDao().allOnce()
        if (kind.isNotBlank()) docs = docs.filter { it.kind.equals(kind, ignoreCase = true) }
        val today = java.time.LocalDate.now()
        val arr = JSONArray()
        docs.forEach { d ->
            val o = JSONObject().put("kind", d.kind).put("filePath", d.filePath)
            if (d.expiresAt != null) {
                val exp = java.time.LocalDate.parse(d.expiresAt!!)
                val left = java.time.temporal.ChronoUnit.DAYS.between(today, exp)
                o.put("expiresAt", d.expiresAt).put("daysLeft", left)
            }
            arr.put(o)
        }
        return JSONObject().put("documents", arr)
    }

    private fun emergencyInfo(args: JSONObject): JSONObject {
        val country = args.optString("country", "").lowercase()
        if (country.contains("japan")) {
            return JSONObject()
                .put("country", "Japan")
                .put("police", "110").put("fireAmbulance", "119")
                .put("touristHotline", "JNTO Japan Visitor Hotline: 050-3816-2787 (from abroad +81-50-3816-2787), 24/7, English available")
                .put("embassyNote", "Embassy of Israel in Tokyo — not in bundled data; search 'Israel Embassy Tokyo' when online. Consular assistance for Israelis abroad: MFA emergency line +972-2-6460111.")
        }
        return JSONObject().put("error", "no bundled emergency data for that country yet (Japan is covered)")
    }

    private suspend fun dayNote(args: JSONObject): JSONObject {
        val content = args.optString("content", "").trim().take(2000)
        if (content.isEmpty()) return JSONObject().put("error", "missing content")
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val day = resolveDay(days, args) ?: return JSONObject().put("error", "could not resolve the requested day; use itinerary_get for valid day indices/dates")
        db.dailyNoteDao().upsert(
            com.odago.travollio.data.DailyNote(
                dayId = day.id,
                content = content,
                updatedAt = java.time.Instant.now().toString()
            )
        )
        lastActionJson = JSONObject().put("type", "day").put("day", days.indexOf(day) + 1).toString()
        return JSONObject().put("ok", true)
            .put("message", "Note saved for day ${days.indexOf(day) + 1} (${dt(day.date)}, ${day.city}).")
    }

    private suspend fun filesList(): JSONObject {
        val files = db.attachmentDao().all().first()
        val arr = JSONArray()
        files.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("name", a.fileName).put("mime", a.mimeType)
                .put("itemId", a.itemId ?: JSONObject.NULL)
                .put("note", a.note ?: JSONObject.NULL))
        }
        return JSONObject().put("files", arr)
    }

    // ---------- app-control tools (agent can drive the whole app) ----------

    private suspend fun filesAttach(args: JSONObject): JSONObject {
        val fileId = args.optLong("fileId", -1L)
        val itemId = args.optLong("itemId", -1L)
        if (fileId < 0 || itemId < 0) return JSONObject().put("error", "need both fileId and itemId")
        val item = db.itemDao().itemOnce(itemId) ?: return JSONObject().put("error", "no such item $itemId")
        db.attachmentDao().link(itemId, listOf(fileId))
        lastActionJson = JSONObject().put("type", "item").put("itemId", itemId).toString()
        return JSONObject().put("ok", true).put("message", "Attached file $fileId to '${item.title}'.")
    }

    private suspend fun tripUpdate(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val startDate = args.optString("startDate", trip.startDate).trim().ifBlank { trip.startDate }
        val endDate = args.optString("endDate", trip.endDate).trim().ifBlank { trip.endDate }
        // Reject invalid date strings and reversed range rather than storing garbage that
        // later crashes LocalDate.parse in the budget/today logic.
        val s = runCatching { java.time.LocalDate.parse(startDate) }.getOrNull()
            ?: return JSONObject().put("error", "startDate '$startDate' is not a valid yyyy-MM-dd date")
        val e = runCatching { java.time.LocalDate.parse(endDate) }.getOrNull()
            ?: return JSONObject().put("error", "endDate '$endDate' is not a valid yyyy-MM-dd date")
        if (e.isBefore(s)) return JSONObject().put("error", "endDate can't be before startDate")
        val updated = trip.copy(
            name = args.optString("name", trip.name).trim().ifBlank { trip.name },
            startDate = startDate,
            endDate = endDate,
            baseCity = args.optString("baseCity", trip.baseCity).trim().ifBlank { trip.baseCity },
            budgetCapHomeCcy = if (args.has("budgetCapHomeCcy")) args.optDouble("budgetCapHomeCcy", trip.budgetCapHomeCcy) else trip.budgetCapHomeCcy,
            homeCcy = args.optString("homeCcy", trip.homeCcy).trim().ifBlank { trip.homeCcy },
            localCcy = args.optString("localCcy", trip.localCcy).trim().uppercase().ifBlank { trip.localCcy }
        )
        if (updated == trip) return JSONObject().put("error", "nothing to update — pass at least one field")
        db.tripDao().update(updated)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true)
            .put("message", "Trip updated: ${updated.name} (${dt(updated.startDate)} → ${dt(updated.endDate)}, ${updated.baseCity}).")
    }

    private suspend fun moneyAdd(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val homeCcy = trip.homeCcy
        val rawLocal = if (args.has("amountLocal")) args.optDouble("amountLocal", Double.NaN).takeIf { !it.isNaN() } else null
        val rawHome = if (args.has("amountHomeCcy")) args.optDouble("amountHomeCcy", Double.NaN).takeIf { !it.isNaN() } else null
        if (rawLocal == null && rawHome == null) return JSONObject().put("error", "missing amount (give amountLocal or amountHomeCcy)")
        val localCcy = args.optString("ccy", "").uppercase().ifBlank { trip.localCcy }
        var amtLocal = rawLocal
        var amtHome = rawHome
        if (amtLocal != null) {
            val rate = rateToHome(localCcy, homeCcy) ?: return JSONObject().put("error", "no cached rate for $localCcy→$homeCcy")
            amtHome = amtLocal * rate
        } else if (amtHome != null && localCcy != homeCcy) {
            val rate = rateToHome(localCcy, homeCcy)
            if (rate == null) return JSONObject().put("error", "no cached rate for $localCcy→$homeCcy")
            amtLocal = amtHome / rate
        }
        val note = args.optString("note", "").trim().ifBlank { "Expense" }
        val category = args.optString("category", "").trim().ifBlank { guessTxCategory(note) }
        val ts = args.optString("ts", "").trim().ifBlank { java.time.LocalDateTime.now().withNano(0).toString() }
        val tx = Transaction(
            tripId = trip.id, ts = ts, itemId = args.optLong("itemId", -1L).takeIf { it > 0 },
            amountHomeCcy = amtHome ?: 0.0, amountLocal = amtLocal,
            ccy = if (amtLocal != null) localCcy else homeCcy, category = category, note = note
        )
        db.transactionDao().insert(tx)
        lastActionJson = JSONObject().put("type", "money").toString()
        val homeTxt = "${"%.2f".format(amtHome ?: 0.0)} $homeCcy"
        return JSONObject().put("ok", true)
            .put("message", "Added $category — $note ($homeTxt${if (amtLocal != null) " ≈ ${"%.0f".format(amtLocal)} $localCcy" else ""}).")
    }

    private suspend fun moneyList(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val txs = db.transactionDao().forTripOnce(trip.id)
        val limit = args.optInt("limit", 10).coerceIn(1, 50)
        val arr = JSONArray()
        txs.take(limit).forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("category", t.category).put("note", t.note).put("ts", t.ts)
                .put("amountHomeCcy", t.amountHomeCcy).put("amountLocal", t.amountLocal ?: JSONObject.NULL).put("ccy", t.ccy))
        }
        return JSONObject().put("transactions", arr)
    }

    private suspend fun docsAdd(args: JSONObject): JSONObject {
        val kind = args.optString("kind", "").trim().lowercase()
        if (kind.isBlank()) return JSONObject().put("error", "missing kind")
        val trip = db.activeTrip()
        val expiresAt = args.optString("expiresAt", "").trim().ifBlank { null }
        val filePath = args.optString("filePath", "").trim().ifBlank { "on-device/$kind" }
        val existing = db.documentDao().allOnce().firstOrNull { it.kind.equals(kind, ignoreCase = true) }
        if (existing != null) {
            db.documentDao().update(
                existing.copy(expiresAt = expiresAt ?: existing.expiresAt, filePath = filePath.ifBlank { existing.filePath })
            )
            return JSONObject().put("ok", true).put("message", "Updated $kind document${expiresAt?.let { " (expires ${dt(it)})" } ?: ""}.")
        }
        db.documentDao().insert(Document(tripId = trip?.id, kind = kind, filePath = filePath, expiresAt = expiresAt))
        return JSONObject().put("ok", true).put("message", "Added $kind document${expiresAt?.let { " (expires ${dt(it)})" } ?: ""}.")
    }

    private suspend fun tripCreate(args: JSONObject): JSONObject {
        val existing = db.activeTrip()
        if (existing != null) {
            return JSONObject().put("ok", false).put("message", "A trip already exists ('${existing.name}') — use trip_new to start another, or trip_archive to save this one and move on.")
        }
        val name = args.optString("name", "").trim()
        if (name.isBlank()) return JSONObject().put("error", "missing trip name")
        val start = args.optString("startDate", "").trim()
        val end = args.optString("endDate", "").trim()
        if (start.isBlank() || end.isBlank()) return JSONObject().put("error", "give startDate and endDate (yyyy-MM-dd)")
        val baseCity = args.optString("baseCity", "").trim().ifBlank { name }
        val homeCcy = args.optString("homeCcy", "").trim().uppercase().ifBlank { "ILS" }
        val localCcy = args.optString("localCcy", "").trim().uppercase().ifBlank { "JPY" }
        val cap = if (args.has("budgetCapHomeCcy")) args.optDouble("budgetCapHomeCcy", 0.0) else 0.0
        val id = db.tripDao().insert(Trip(name = name, startDate = start, endDate = end, baseCity = baseCity, budgetCapHomeCcy = cap, homeCcy = homeCcy, localCcy = localCcy))
        db.setActiveTrip(id)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true).put("message", "Created trip '$name' (${dt(start)} → ${dt(end)}). Add days with day_add.")
    }

    private suspend fun dayAdd(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip — create one first with trip_create")
        val date = args.optString("date", "").trim()
        if (date.isBlank()) return JSONObject().put("error", "missing date (yyyy-MM-dd)")
        val city = args.optString("city", "").trim().ifBlank { trip.baseCity }
        val days = db.dayDao().daysForTripOnce(trip.id)
        if (days.any { it.date == date }) return JSONObject().put("error", "day $date already exists")
        val id = db.dayDao().insert(Day(tripId = trip.id, date = date, city = city))
        val newDays = db.dayDao().daysForTripOnce(trip.id)
        val idx = newDays.indexOfFirst { it.id == id } + 1
        lastActionJson = JSONObject().put("type", "day").put("day", idx).toString()
        return JSONObject().put("ok", true).put("message", "Added Day ${idx.coerceAtLeast(1)} (${dt(date)}, $city).")
    }

    private suspend fun dayUpdate(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val target = resolveDay(days, args) ?: return JSONObject().put("error", "could not resolve the day; use itinerary_get for valid day indices/dates")
        val newDate = args.optString("newDate", "").trim()
        val city = args.optString("city", target.city).trim()
        val date = newDate.ifBlank { target.date }
        // Validate: a new date must be real ISO and not collide with an existing day.
        if (newDate.isNotBlank()) {
            val parsed = runCatching { java.time.LocalDate.parse(newDate) }.getOrNull()
                ?: return JSONObject().put("error", "newDate '$newDate' is not a valid yyyy-MM-dd date")
            if (days.any { it.date == newDate && it.id != target.id }) {
                return JSONObject().put("error", "a day already exists on $newDate")
            }
        }
        if (target.date == date && target.city == city) return JSONObject().put("error", "nothing to change")
        db.dayDao().update(target.copy(date = date, city = city.ifBlank { target.city }))
        val idx = days.indexOf(target) + 1
        lastActionJson = JSONObject().put("type", "day").put("day", idx).toString()
        return JSONObject().put("ok", true).put("message", "Updated Day $idx.")
    }

    private suspend fun dayRemove(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val target = resolveDay(days, args) ?: return JSONObject().put("error", "could not resolve the day")
        val idx = days.indexOf(target) + 1
        if (!args.optBoolean("confirm", false)) {
            return JSONObject().put("ok", false).put("needsConfirm", true)
                .put("message", "This removes Day $idx (${dt(target.date)}, ${target.city}) and its items. Say 'confirmed' to delete it.")
        }
        db.dayDao().delete(target)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true).put("message", "Removed Day $idx (${dt(target.date)}, ${target.city}).")
    }

    private suspend fun todayPlan(): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip — create one first with trip_create")
        val days = db.dayDao().daysForTripOnce(trip.id)
        if (days.isEmpty()) return JSONObject().put("error", "no days on the trip yet — add one with day_add")
        val today = java.time.LocalDate.now().toString()
        val day = (days.firstOrNull { it.date == today } ?: days.firstOrNull())
            ?: return JSONObject().put("error", "no day to show")
        val items = db.itemDao().itemsForTripOnce(trip.id).filter { it.dayId == day.id }
        val note = db.dailyNoteDao().forDayOnce(day.id)
        val itemArr = JSONArray()
        items.forEach { it ->
            itemArr.put(JSONObject().put("id", it.id).put("time", it.timeLocal ?: "").put("title", it.title).put("kind", it.kind)
                .put("place", it.place ?: JSONObject.NULL))
        }
        return JSONObject().put("day", days.indexOf(day) + 1).put("date", day.date).put("city", day.city)
            .put("note", note?.content ?: JSONObject.NULL).put("items", itemArr)
    }

    // ---------- live weather + price watches (on-phone, no server) ----------

    private suspend fun weatherGet(args: JSONObject): JSONObject {
        val key = weatherApiKey() ?: ""
        if (key.isBlank()) return JSONObject()
            .put("error", "no weather key — add a free OpenWeatherMap key in Settings → Live data")
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val items = db.itemDao().itemsForTripOnce(trip.id)
        // Resolve coordinates: explicit lat/lng wins, then the given day's first
        // geotagged item, then the next upcoming outdoor item with coordinates.
        var lat: Double? = args.optDouble("lat", Double.NaN).takeIf { !it.isNaN() }
        var lng: Double? = args.optDouble("lng", Double.NaN).takeIf { !it.isNaN() }
        if (lat == null || lng == null) {
            val dayIdx = args.optInt("day", -1)
            val pool = if (dayIdx > 0 && dayIdx <= days.size) {
                val dayId = days.sortedBy { it.date }[dayIdx - 1].id
                items.filter { it.dayId == dayId }
            } else {
                val today = java.time.LocalDate.now().toString()
                items.filter { Weather.isOutdoorKind(it.kind) }.sortedBy {
                    days.firstOrNull { d -> d.id == it.dayId }?.date ?: ""
                }.filter { (days.firstOrNull { d -> d.id == it.dayId }?.date ?: "") >= today }
                    .ifEmpty { items }
            }
            val geo = pool.firstOrNull { it.lat != null && it.lng != null }
                ?: return JSONObject().put("error", "no coordinates on file — pass lat/lng, or add them to an item")
            lat = geo.lat; lng = geo.lng
        }
        val days5 = Weather.forecast(lat!!, lng!!, key)
            ?: return JSONObject().put("error", "forecast unavailable (network or bad key?)")
        val wantDate = args.optString("date", "")
        val arr = JSONArray()
        days5.forEach { f ->
            arr.put(JSONObject().put("date", f.date).put("condition", f.condition)
                .put("description", f.description).put("tempC", f.tempC).put("rainMm", f.rainMm)
                .put("unfavorable", Weather.isUnfavorable(f.condition, f.tempC)))
        }
        val out = JSONObject().put("lat", lat).put("lng", lng).put("days", arr)
        if (wantDate.isNotBlank()) {
            val hit = days5.firstOrNull { it.date == wantDate }
            if (hit != null) out.put("verdict", JSONObject().put("date", hit.date)
                .put("unfavorable", Weather.isUnfavorable(hit.condition, hit.tempC))
                .put("summary", "${hit.description}, ${"%.0f".format(hit.tempC)}°C"))
        }
        return out
    }

    private suspend fun priceWatchAdd(args: JSONObject): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val label = args.optString("label", "").trim().take(120)
        if (label.isEmpty()) return JSONObject().put("error", "missing label")
        val target = args.optDouble("targetAmount", Double.NaN)
        if (target.isNaN() || target <= 0) return JSONObject().put("error", "targetAmount must be > 0")
        val ccy = args.optString("targetCcy", "").trim().uppercase().ifEmpty { trip.localCcy }
        val itemId = args.optLong("itemId", -1L).takeIf { it >= 0 }
        if (itemId != null && db.itemDao().itemOnce(itemId) == null) {
            return JSONObject().put("error", "no such item $itemId")
        }
        val id = db.priceWatchDao().insert(
            PriceWatch(
                tripId = trip.id, label = label, itemId = itemId,
                targetAmount = target, targetCcy = ccy,
                note = args.optString("note", "").takeIf { it.isNotBlank() },
                createdAt = java.time.Instant.now().toString()
            )
        )
        return JSONObject().put("ok", true).put("id", id)
            .put("message", "Watching '$label' — alert at $target $ccy or below.")
    }

    private suspend fun priceWatchList(): JSONObject {
        val trip = db.activeTrip() ?: return JSONObject().put("error", "no active trip")
        val arr = JSONArray()
        db.priceWatchDao().forTripOnce(trip.id).forEach { w ->
            arr.put(JSONObject().put("id", w.id).put("label", w.label)
                .put("targetAmount", w.targetAmount).put("targetCcy", w.targetCcy)
                .put("lastObserved", w.lastObserved ?: JSONObject.NULL)
                .put("hit", w.lastObserved != null && w.lastObserved <= w.targetAmount)
                .put("note", w.note ?: JSONObject.NULL))
        }
        return JSONObject().put("watches", arr)
    }

    private suspend fun priceWatchRemove(args: JSONObject): JSONObject {
        val w = db.priceWatchDao().once(args.optLong("id", -1L))
            ?: return JSONObject().put("error", "no such watch")
        db.priceWatchDao().delete(w)
        return JSONObject().put("ok", true).put("message", "Removed watch '${w.label}'.")
    }

    private suspend fun priceWatchCheck(args: JSONObject): JSONObject {
        val w = db.priceWatchDao().once(args.optLong("id", -1L))
            ?: return JSONObject().put("error", "no such watch — list them with pricewatch_list")
        val obs = args.optDouble("observedAmount", Double.NaN)
        if (obs.isNaN() || obs < 0) return JSONObject().put("error", "observedAmount must be >= 0")
        val now = java.time.Instant.now().toString()
        val hit = obs <= w.targetAmount
        db.priceWatchDao().update(
            w.copy(lastObserved = obs, lastObservedTs = now, lastAlertTs = if (hit) now else w.lastAlertTs)
        )
        return JSONObject().put("ok", true).put("hit", hit)
            .put("message", if (hit) "Target hit: '${w.label}' at $obs ${w.targetCcy} (target ${w.targetAmount})."
                else "'${w.label}' at $obs ${w.targetCcy} — still above target ${w.targetAmount}.")
    }

    // ---------- multi-trip + bulk import ----------

    private suspend fun tripList(args: JSONObject): JSONObject {
        val trips = db.tripDao().allOnce()
        val activeId = db.activeTrip()?.id
        val arr = JSONArray()
        trips.forEach { t ->
            arr.put(JSONObject().put("id", t.id).put("name", t.name).put("startDate", t.startDate).put("endDate", t.endDate)
                .put("baseCity", t.baseCity).put("budgetCapHomeCcy", t.budgetCapHomeCcy).put("homeCcy", t.homeCcy)
                .put("archived", t.isArchived).put("active", t.id == activeId))
        }
        return JSONObject().put("trips", arr)
    }

    private suspend fun tripGet(args: JSONObject): JSONObject {
        val trip = (if (args.has("tripId")) db.tripDao().tripOnce(args.optLong("tripId", -1L)) else db.activeTrip())
            ?: return JSONObject().put("error", "no trip found")
        val days = db.dayDao().daysForTripOnce(trip.id)
        val items = db.itemDao().itemsForTripOnce(trip.id)
        val txs = db.transactionDao().forTripOnce(trip.id)
        val byDay = HashMap<Long, MutableList<Item>>()
        items.forEach { byDay.getOrPut(it.dayId) { mutableListOf() }.add(it) }
        val dayArr = JSONArray()
        days.sortedBy { it.date }.forEachIndexed { idx, d ->
            val ia = JSONArray()
            (byDay[d.id] ?: emptyList()).forEach { it ->
                ia.put(JSONObject().put("id", it.id).put("title", it.title).put("time", it.timeLocal ?: "")
                    .put("kind", it.kind).put("place", it.place ?: JSONObject.NULL).put("costLocal", it.costLocal ?: JSONObject.NULL))
            }
            dayArr.put(JSONObject().put("day", idx + 1).put("date", d.date).put("city", d.city).put("items", ia))
        }
        return JSONObject().put("trip", JSONObject().put("id", trip.id).put("name", trip.name).put("startDate", trip.startDate).put("endDate", trip.endDate)
            .put("baseCity", trip.baseCity).put("homeCcy", trip.homeCcy).put("budgetCapHomeCcy", trip.budgetCapHomeCcy).put("archived", trip.isArchived))
            .put("spentHomeCcy", txs.sumOf { it.amountHomeCcy })
            .put("days", dayArr)
    }

    private suspend fun tripNew(args: JSONObject): JSONObject {
        val name = args.optString("name", "").trim()
        if (name.isBlank()) return JSONObject().put("error", "missing trip name")
        val start = args.optString("startDate", "").trim()
        val end = args.optString("endDate", "").trim()
        if (start.isBlank() || end.isBlank()) return JSONObject().put("error", "give startDate and endDate (yyyy-MM-dd)")
        val baseCity = args.optString("baseCity", "").trim().ifBlank { name }
        val homeCcy = args.optString("homeCcy", "").trim().uppercase().ifBlank { "ILS" }
        val localCcy = args.optString("localCcy", "").trim().uppercase().ifBlank { "JPY" }
        val cap = if (args.has("budgetCapHomeCcy")) args.optDouble("budgetCapHomeCcy", 0.0) else 0.0
        val id = db.tripDao().insert(Trip(name = name, startDate = start, endDate = end, baseCity = baseCity, budgetCapHomeCcy = cap, homeCcy = homeCcy, localCcy = localCcy))
        db.setActiveTrip(id)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true).put("message", "Created '$name' (${dt(start)} → ${dt(end)}) and made it the active trip.")
    }

    private suspend fun tripSwitch(args: JSONObject): JSONObject {
        val id = args.optLong("tripId", -1L)
        if (id < 0) return JSONObject().put("error", "missing tripId")
        val trip = db.tripDao().tripOnce(id) ?: return JSONObject().put("error", "no such trip $id")
        if (trip.isArchived) db.tripDao().update(trip.copy(isArchived = false))
        db.setActiveTrip(id)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true).put("message", "Switched to '${trip.name}'; it is now the active trip.")
    }

    private suspend fun tripArchive(args: JSONObject): JSONObject {
        val trip = (if (args.has("tripId")) db.tripDao().tripOnce(args.optLong("tripId", -1L)) else db.activeTrip())
            ?: return JSONObject().put("error", "no trip to archive")
        if (trip.isArchived) return JSONObject().put("ok", false).put("message", "'${trip.name}' is already archived.")
        db.tripDao().update(trip.copy(isArchived = true))
        val next = db.tripDao().allOnce().filter { !it.isArchived }.minByOrNull { it.id }
        db.setActiveTrip(next?.id)
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true)
            .put("message", "Archived '${trip.name}' — kept for reference." + (next?.let { " Now active: ${it.name}." } ?: ""))
    }

    private suspend fun tripDelete(args: JSONObject): JSONObject {
        val trip = (if (args.has("tripId")) db.tripDao().tripOnce(args.optLong("tripId", -1L)) else db.activeTrip())
            ?: return JSONObject().put("error", "no trip to delete")
        if (!args.optBoolean("confirm", false)) {
            return JSONObject().put("ok", false).put("needsConfirm", true)
                .put("message", "That permanently deletes '${trip.name}' and all its days/items/spend. Say 'confirmed' and I'll delete it.")
        }
        db.documentDao().deleteAll(trip.id)
        db.tripDao().delete(trip)
        val next = db.tripDao().allOnce().filter { !it.isArchived }.minByOrNull { it.id }
        db.setActiveTrip(next?.id)
        return JSONObject().put("ok", true).put("message", "Deleted '${trip.name}'. This cannot be undone.")
    }

    private suspend fun itineraryImport(args: JSONObject): JSONObject {
        val trip = (if (args.has("tripId")) db.tripDao().tripOnce(args.optLong("tripId", -1L)) else db.activeTrip())
            ?: return JSONObject().put("error", "no trip — create one first (trip_create / trip_new)")
        val itemsArr = args.optJSONArray("items") ?: return JSONObject().put("error", "missing items array")
        val daysArr = args.optJSONArray("days")
        val planned = itemsArr.length() + (daysArr?.length() ?: 0)
        if (planned > 0 && !args.optBoolean("confirm", false)) {
            return JSONObject().put("ok", false).put("needsConfirm", true)
                .put("message", "I'll add $planned entries to '${trip.name}'. Say 'confirmed' to save them.")
        }
        var addedItems = 0
        var addedDays = 0
        db.withTransaction {
            val daysArr = args.optJSONArray("days")
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) {
                    val o = daysArr.optJSONObject(i) ?: continue
                    val date = o.optString("date", "").trim()
                    if (date.isBlank()) continue
                    val city = o.optString("city", "").trim().ifBlank { trip.baseCity }
                    if (db.dayDao().daysForTripOnce(trip.id).any { it.date == date }) continue
                    db.dayDao().insert(Day(tripId = trip.id, date = date, city = city)); addedDays++
                }
            }
            for (i in 0 until itemsArr.length()) {
                val o = itemsArr.optJSONObject(i) ?: continue
                val title = o.optString("title", "").trim().take(200)
                if (title.isEmpty()) continue
                var day = resolveDay(db.dayDao().daysForTripOnce(trip.id), o)
                if (day == null) {
                    val date = o.optString("date", "").trim()
                    if (date.isNotBlank()) {
                        db.dayDao().insert(Day(tripId = trip.id, date = date, city = trip.baseCity)); addedDays++
                        day = db.dayDao().daysForTripOnce(trip.id).firstOrNull { it.date == date }
                    }
                }
                if (day == null) continue
                val cost = if (o.has("costLocal")) o.optDouble("costLocal", Double.NaN).takeIf { !it.isNaN() } else null
                val sort = db.itemDao().maxSortForDay(day.id) + 1
                db.itemDao().insert(Item(
                    dayId = day.id, sort = sort, timeLocal = o.optString("timeLocal", "").takeIf { it.isNotBlank() },
                    title = title, detail = o.optString("detail", ""), kind = o.optString("kind", "").ifEmpty { guessKind(title) },
                    costLocal = cost, ccy = o.optString("ccy", "").ifEmpty { null },
                    place = o.optString("place", "").takeIf { it.isNotBlank() },
                    address = o.optString("address", "").takeIf { it.isNotBlank() },
                    link = o.optString("link", "").takeIf { it.isNotBlank() },
                    confirmation = o.optString("confirmation", "").takeIf { it.isNotBlank() }
                )); addedItems++
            }
        }
        lastActionJson = JSONObject().put("type", "trip").toString()
        return JSONObject().put("ok", true)
            .put("message", "Imported $addedItems items${if (addedDays > 0) " across $addedDays new days" else ""} into '${trip.name}'.")
    }

    private fun rateToHome(fromCcy: String, homeCcy: String): Double? {
        val f = fromCcy.uppercase()
        val h = homeCcy.uppercase()
        if (f == h) return 1.0
        val fr = toIls[f] ?: return null
        val hr = toIls[h] ?: return null
        return fr / hr
    }

    /** Approximate offline rates: 1 unit of currency = X ILS. Covers the trip's common currencies. */
    private val toIls: Map<String, Double> = mapOf(
        "ILS" to 1.0, "USD" to 3.73, "EUR" to 4.02, "GBP" to 4.66, "CHF" to 4.16,
        "JPY" to 0.0256, "KRW" to 0.0027, "CNY" to 0.516, "THB" to 0.104, "TWD" to 0.114,
        "VND" to 0.00015, "MYR" to 0.80, "SGD" to 2.76, "HKD" to 0.477, "AUD" to 2.45,
        "CAD" to 2.72, "NZD" to 2.22, "AED" to 1.015, "SAR" to 0.994, "QAR" to 1.02,
        "INR" to 0.0447, "IDR" to 0.00024, "PHP" to 0.064, "MXN" to 0.196, "BRL" to 0.68,
        "TRY" to 0.11, "PLN" to 0.94, "CZK" to 0.16, "HUF" to 0.010, "SEK" to 0.36,
        "NOK" to 0.36, "DKK" to 0.54, "ZAR" to 0.20, "EGP" to 0.12, "MAD" to 0.38,
    )

    private fun guessTxCategory(note: String): String {
        val t = note.lowercase()
        return when {
            t.contains("train") || t.contains("taxi") || t.contains("flight") || t.contains("subway") || t.contains("bus") || t.contains("fare") || t.contains("express") -> "transit"
            t.contains("lunch") || t.contains("dinner") || t.contains("breakfast") || t.contains("ramen") || t.contains("sushi") || t.contains("food") || t.contains("cafe") || t.contains("coffee") || t.contains("meal") -> "food"
            t.contains("hotel") || t.contains("hostel") || t.contains("airbnb") || t.contains("stay") || t.contains("lodging") -> "lodging"
            t.contains("shopping") || t.contains("shop") || t.contains("souvenir") || t.contains("buy") || t.contains("mall") -> "shopping"
            t.contains("ticket") || t.contains("entry") || t.contains("museum") || t.contains("tour") || t.contains("show") || t.contains("attraction") -> "entertainment"
            else -> "other"
        }
    }

    // ---------- http ----------

    /**
     * Base64 data URI for one image attachment, ready to post as an OpenAI image_url
     * part. Files over ~2 MB (typical camera shots) are downscaled to a 1568px long
     * edge JPEG instead of being dropped, so photos always reach a vision-capable model.
     */
    private fun imageDataUri(m: com.odago.travollio.data.AttachmentMeta): String? = runCatching {
        val bytes = java.io.File(m.path).readBytes()
        if (bytes.size <= 2_000_000) {
            "data:${m.mime};base64,${java.util.Base64.getEncoder().encodeToString(bytes)}"
        } else {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
            val bmp = android.graphics.BitmapFactory.decodeFile(m.path, opts) ?: return@runCatching null
            val maxDim = maxOf(bmp.width, bmp.height)
            val scale = 1568f / maxDim
            val scaled = if (scale < 1f) {
                android.graphics.Bitmap.createScaledBitmap(
                    bmp,
                    (bmp.width * scale).toInt().coerceAtLeast(1),
                    (bmp.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else bmp
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, baos)
            "data:image/jpeg;base64,${java.util.Base64.getEncoder().encodeToString(baos.toByteArray())}"
        }
    }.getOrNull()

    private fun attachedNames(cardJson: String): String = try {
        val arr = JSONArray(cardJson)
        val names = (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("name") }
        if (names.isEmpty()) "" else "[Attached: ${names.joinToString(", ")}]"
    } catch (_: Exception) {
        ""
    }

    /**
     * Streaming variant of [post] (OpenAI SSE). Same request, but with `stream:true` the
     * server sends `data:` chunks; we parse each, push content through [onDelta] as it arrives,
     * and accumulate `tool_calls` deltas (which may be split across many chunks) before returning
     * a message object shaped exactly like [post]'s (so the caller's parsing is unchanged).
     */
    private fun streamPost(body: JSONObject, onDelta: (String) -> Unit): JSONObject? {
        return try {
            val req = JSONObject(body.toString())
            req.put("stream", true)
            val conn = URL(chatUrl()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Cache-Control", "no-cache")
            val key = AgentConfig.apiKey
            if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.connectTimeout = 8_000
            conn.readTimeout = 60_000 // idle-only; a long reply streams in many short reads
            conn.doOutput = true
            conn.outputStream.use { it.write(req.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
                } catch (_: Exception) { "" }
                lastHttpError = "HTTP $code${if (errText.isNotBlank()) " — $errText" else ""}"
                android.util.Log.w("TravollioAgent", "Agent API $code: $errText")
                return null
            }
            lastHttpError = null
            val text = StringBuilder()
            val toolCalls = HashMap<Int, JSONObject>()
            var sawToolCall = false
            conn.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val event = try { JSONObject(data) } catch (_: Exception) { continue }
                    val delta = event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                        ?: continue
                    val piece = delta.optString("content", "")
                    if (piece.isNotEmpty()) {
                        onDelta(piece)
                        text.append(piece)
                    }
                    val tcArr = delta.optJSONArray("tool_calls")
                    if (tcArr != null && tcArr.length() > 0) {
                        sawToolCall = true
                        for (i in 0 until tcArr.length()) {
                            val tc = tcArr.optJSONObject(i) ?: continue
                            val idx = tc.optInt("index", -1)
                            if (idx < 0) continue
                            val acc = toolCalls.getOrPut(idx) { JSONObject() }
                            tc.optString("id", "").takeIf { it.isNotEmpty() }?.let { acc.put("id", it) }
                            val fn = tc.optJSONObject("function") ?: continue
                            val accFn = acc.optJSONObject("function") ?: JSONObject().also { acc.put("function", it) }
                            fn.optString("name", "").takeIf { it.isNotEmpty() }?.let { accFn.put("name", it) }
                            val args = fn.optString("arguments", "")
                            if (args.isNotEmpty()) accFn.put("arguments", accFn.optString("arguments", "") + args)
                        }
                    }
                }
            }
            // Reassemble a response object shaped like post() so the loop parses it identically.
            val message = JSONObject().put("role", "assistant")
            if (text.isNotEmpty()) message.put("content", text.toString())
            if (sawToolCall) {
                val arr = JSONArray()
                toolCalls.values.sortedBy { it.optInt("index", 0) }.forEach { arr.put(it) }
                message.put("tool_calls", arr)
            }
            JSONObject().put(
                "choices",
                JSONArray().put(JSONObject().put("message", message))
            )
        } catch (e: Exception) {
            lastHttpError = "${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }

    /** Non-streaming chat-completions POST. Used for local providers and as a safe fallback
     *  when a server rejects the streaming request. Returns the full response object, or null. */
    private fun postRaw(body: JSONObject): JSONObject? {
        return try {
            val conn = URL(chatUrl()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            val key = AgentConfig.apiKey
            if (key.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $key")
            conn.connectTimeout = 8_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200) ?: ""
                } catch (_: Exception) { "" }
                lastHttpError = "HTTP $code${if (errText.isNotBlank()) " — $errText" else ""}"
                android.util.Log.w("TravollioAgent", "Agent API $code: $errText")
                null
            } else {
                lastHttpError = null
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            }
        } catch (e: Exception) {
            lastHttpError = "${e.javaClass.simpleName}: ${e.message}"
            null
        }
    }
}
