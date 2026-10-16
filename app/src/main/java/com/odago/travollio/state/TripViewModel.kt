package com.odago.travollio.state

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odago.travollio.agent.AgentEngine
import com.odago.travollio.agent.GeminiAgent
import com.odago.travollio.data.AppDatabase
import com.odago.travollio.data.AppState
import com.odago.travollio.data.Attachment
import com.odago.travollio.data.AttachmentMeta
import com.odago.travollio.data.ChatMessage
import com.odago.travollio.data.Fx
import com.odago.travollio.data.MAX_DOC_TEXT
import com.odago.travollio.data.SettingsPrefs
import com.odago.travollio.data.Transaction
import com.odago.travollio.data.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app.applicationContext)

    /** Which trip is active: the selected non-archived trip (falls back to oldest non-archived). */
    private val activeTrip: Flow<Trip?> = combine(
        db.tripDao().all(),
        db.routineStateDao().stateFlow(AppDatabase.ACTIVE_KEY)
    ) { trips, activeId ->
        val id = activeId?.toLongOrNull()
        if (id != null) trips.firstOrNull { it.id == id && !it.isArchived }
            ?: trips.filter { !it.isArchived }.minByOrNull { it.id }
        else trips.filter { !it.isArchived }.minByOrNull { it.id }
    }

    val state = activeTrip
        .flatMapLatest { trip ->
            if (trip == null) flowOf(AppState.empty()) // trip-data UI shows a "no trip" state
            else combine(
                db.dayDao().daysForTrip(trip.id),
                db.transactionDao().forTrip(trip.id),
                db.itemDao().itemsForTrip(trip.id),
                db.documentDao().forTrip(trip.id)
            ) { days, txs, items, docs ->
                AppState(
                    trip = trip,
                    days = days,
                    items = items,
                    transactions = txs,
                    documents = docs
                )
            }
                .combine(db.dailyNoteDao().forTrip(trip.id)) { s, notes ->
                    s.copy(notes = notes)
                }
        }
        // Chat is NOT trip-scoped: it must render even when no trip exists, otherwise every
        // reply (including error/offline replies) is inserted but invisible — the original
        // "thinking → stops → nothing appears" bug.
        .combine(db.chatMessageDao().all()) { s, chat ->
            s.copy(chat = chat)
        }
        .combine(db.attachmentDao().all()) { s, attachments ->
            s.copy(attachments = attachments)
        }
        .combine(db.tripDao().all()) { s, trips ->
            s.copy(availableTrips = trips)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppState.empty())

    /** True while a reply is being produced (cloud round-trip or local), so the UI can show a "thinking" state. */
    private val _isThinking = MutableStateFlow(false)
    val isThinking: StateFlow<Boolean> = _isThinking.asStateFlow()

    /** Survives for this session: the outcome of the LAST send, shown on-screen so the user never
     *  has to hunt for a log file to understand why the agent didn't answer. */
    private val _lastOutcome = MutableStateFlow<String?>(null)
    val lastOutcome: StateFlow<String?> = _lastOutcome.asStateFlow()

    /** The reply text flowing in token-by-token while the cloud agent streams (empty when idle).
     *  Kept out of Room so we don't hammer the DB on every delta; finalized on completion. */
    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    /**
     * Send a chat turn, optionally with attachments (photos/files the user picked).
     * Attachments are copied into the app-private sandbox first — the agent only ever
     * sees our own paths, never the caller's URIs (OWASP storage/URI-hygiene).
     */
    fun send(text: String, attachmentUris: List<Uri> = emptyList()) {
        val trimmed = text.trim().take(2000)
        if (trimmed.isEmpty() && attachmentUris.isEmpty()) return
        if (_isThinking.value) return // no re-entrant sends while a reply is in flight
        viewModelScope.launch {
            _isThinking.value = true
            _streamingText.value = ""
            try {
                val history = state.value.chat
                val metas = attachmentUris.mapNotNull { importAttachment(it) }
                val cardJson = if (metas.isEmpty()) null else JSONArray().apply {
                    metas.forEach { m -> put(JSONObject().put("id", m.id).put("name", m.name)) }
                }.toString()
                db.chatMessageDao().insert(
                    ChatMessage(
                        ts = Instant.now().toString(),
                        role = "user",
                        text = trimmed.ifEmpty { "(attachment)" },
                        cardJson = cardJson
                    )
                )
                // GUARANTEE a reply: never leave the user with "thinking" and no message.
                var reply: String
                var actionJson: String? = null
                try {
                    // Stream the answer live into streamingText; blocking IO is bounded by the
                    // connection's connect/read timeouts, so no coroutine timeout (which can't
                    // cancel a blocking socket read anyway) is needed here.
                    val app = getApplication<Application>()
                    val result = GeminiAgent(db) { SettingsPrefs.weatherApiKey(app) }
                        .respond(history, trimmed, metas) { delta ->
                        _streamingText.value += delta
                    }
                    recordOutcome(OUTCOME_OK, "DeepSeek agent reply")
                    actionJson = result.actionJson
                    reply = if (result.text.isNotBlank()) result.text else localFallback(trimmed, null)
                } catch (e: GeminiAgent.CloudException) {
                    recordOutcome(OUTCOME_OFFLINE, "couldn't reach online agent: ${e.message ?: "unknown error"}")
                    reply = localFallback(trimmed, e.message)
                } catch (e: Exception) {
                    val ex = "${e.javaClass.simpleName}: ${e.message}"
                    recordOutcome(OUTCOME_OFFLINE, "agent threw: $ex")
                    reply = localFallback(trimmed, null)
                }
                _streamingText.value = ""
                db.chatMessageDao().insert(
                    ChatMessage(ts = Instant.now().toString(), role = "assistant", text = reply, actionJson = actionJson)
                )
            } catch (e: Exception) {
                // Last-resort: even if the DB write failed, surface an honest message and never hang.
                recordOutcome(OUTCOME_ERROR, "send() failed: ${e.javaClass.simpleName}: ${e.message}")
                try {
                    db.chatMessageDao().insert(
                        ChatMessage(
                            ts = Instant.now().toString(),
                            role = "assistant",
                            text = "I hit a snag (${e.javaClass.simpleName}${e.message?.let { ": $it" } ?: ""}). Try again in a moment."
                        )
                    )
                } catch (_: Exception) {}
            } finally {
                _streamingText.value = ""
                _isThinking.value = false
            }
        }
    }

    /** "Restart the agent": wipe the conversation so the next send has no prior chat context.
     *  From that point the agent knows the trip only from what is saved (fetched via its tools). */
    fun resetConversation() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { db.chatMessageDao().clear() }
            } catch (_: Exception) {}
            _lastOutcome.value = null
            _streamingText.value = ""
        }
    }

    // ---- Trip management (available from the Today hub, not only via the agent) ----

    fun switchTrip(tripId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val t = db.tripDao().tripOnce(tripId) ?: return@withContext
            if (t.isArchived) db.tripDao().update(t.copy(isArchived = false))
            db.setActiveTrip(tripId)
        }
    }

    fun archiveTrip(tripId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val t = db.tripDao().tripOnce(tripId) ?: return@withContext
            db.tripDao().update(t.copy(isArchived = true))
            val next = db.tripDao().allOnce().filter { !it.isArchived }.minByOrNull { it.id }
            db.setActiveTrip(next?.id)
        }
    }

    fun updateTrip(trip: Trip) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            if (trip.id > 0) db.tripDao().update(trip)
        }
    }

    fun createTrip(trip: Trip) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val id = db.tripDao().insert(trip.copy(isArchived = false))
            db.setActiveTrip(id)
        }
    }

    /** Manually remove an itinerary moment (the agent can do this via `itinerary_remove`). */
    fun deleteItem(itemId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val item = db.itemDao().itemOnce(itemId) ?: return@withContext
            // Delete linked attachment rows + files from disk (no orphans).
            try {
                db.attachmentDao().forItemOnce(itemId).forEach { a ->
                    runCatching { File(a.filePath).delete() }
                    runCatching { db.attachmentDao().delete(a) }
                }
            } catch (_: Exception) {}
            db.itemDao().delete(item)
        }
    }

    /** Manually log an expense without going through chat — same shape the agent writes.
     *  Amount is given in the trip's LOCAL currency; we convert to home for the budget. */
    fun addExpense(amountLocal: Double, ccy: String?, note: String, category: String) {
        if (amountLocal <= 0) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val trip = db.activeTrip() ?: return@withContext
                val localCcy = ccy ?: trip.localCcy
                val home = Fx.toHome(amountLocal, localCcy, trip.homeCcy)
                if (home == null) return@withContext // no rate — bail rather than corrupt the budget
                val ts = java.time.LocalDateTime.now().withNano(0).toString()
                db.transactionDao().insert(
                    Transaction(
                        tripId = trip.id,
                        ts = ts,
                        amountHomeCcy = home,
                        amountLocal = amountLocal,
                        ccy = localCcy,
                        category = category.trim().ifBlank { "other" },
                        note = note.trim().ifBlank { "Expense" }
                    )
                )
            }
        }
    }

    fun deleteTrip(tripId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val t = db.tripDao().tripOnce(tripId) ?: return@withContext
            try {
                db.attachmentDao().forTripOnce(tripId).forEach { a ->
                    runCatching { File(a.filePath).delete() }
                    runCatching { db.attachmentDao().delete(a) }
                }
            } catch (_: Exception) {}
            db.transactionDao().deleteAll(tripId)
            db.documentDao().deleteAll(tripId)
            db.tripDao().delete(t)
            val next = db.tripDao().allOnce().filter { !it.isArchived }.minByOrNull { it.id }
            db.setActiveTrip(next?.id)
        }
    }

    fun deleteTransaction(txId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val trip = db.activeTrip() ?: return@withContext
            val tx = db.transactionDao().forTripOnce(trip.id).firstOrNull { it.id == txId } ?: return@withContext
            db.transactionDao().delete(tx)
        }
    }

    fun deleteDocument(docId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val trip = db.activeTrip()
            val docs = if (trip != null) db.documentDao().forTripOnce(trip.id) else db.documentDao().allOnce()
            docs.firstOrNull { it.id == docId }?.let { db.documentDao().delete(it) }
        }
    }

    fun deleteAttachment(attachmentId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) {
            val a = db.attachmentDao().once(attachmentId) ?: return@withContext
            runCatching { File(a.filePath).delete() }
            runCatching { db.attachmentDao().delete(a) }
        }
    }

    /** Grounded local-engine answer used whenever the cloud agent is unavailable. */
    private suspend fun localFallback(text: String, reason: String?): String = try {        val local = AgentEngine.respond(state.value, text).ifBlank {
            "I'm offline right now and couldn't pull that together — try again shortly."
        }
        if (local.contains("offline", ignoreCase = true) || local.contains("fallback", ignoreCase = true)) {
            local
        } else {
            val why = if (reason != null) " (couldn't reach the online agent: $reason)" else ""
            "The online agent is unavailable$why, so here's the offline answer:\n\n$local"
        }
    } catch (e: Exception) {
        "I couldn't reach the online agent right now. Please try again in a moment."
    }

    /** Outcome tags for the on-screen status line. */
    private companion object {
        const val OUTCOME_OK = "ok"
        const val OUTCOME_OFFLINE = "offline"
        const val OUTCOME_ERROR = "error"
    }

    /**
     * Record the result of a send attempt on-screen via [lastOutcome] so the user
     * never has to hunt for a file. Crash-only logging policy: no file/logcat writes
     * here — only uncaught crashes are logged (see TravollioApp).
     */
    private fun recordOutcome(kind: String, detail: String) {
        // Only a non-success surfaces an on-screen banner; a good reply keeps the UI clean.
        _lastOutcome.value = if (kind == OUTCOME_OK) null else detail
    }

    /** File attachments straight onto a moment from the dossier sheet. */
    fun attachToItem(itemId: Long, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                importAttachment(uri)?.let { m ->
                    db.attachmentDao().insert(
                        Attachment(
                            itemId = itemId,
                            fileName = m.name,
                            mimeType = m.mime,
                            filePath = m.path,
                            createdAt = Instant.now().toString()
                        )
                    )
                }
            }
        }
    }

    /** Copy a picked file into filesDir/attachments/ (sandbox), insert an unlinked row, return its meta. */
    private suspend fun importAttachment(uri: Uri): AttachmentMeta? = withContext(Dispatchers.IO) {
        try {
            val ctx = getApplication<Application>()
            val resolver = ctx.contentResolver
            val mime = resolver.getType(uri) ?: guessMime(uri.lastPathSegment)
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) c.getString(idx) else null
                } else null
            } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            val dir = File(ctx.filesDir, "attachments").apply { mkdirs() }
            val dest = File(dir, UUID.randomUUID().toString() + extFor(mime, name))
            val ok = resolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
                true
            } ?: false
            if (!ok || dest.length() == 0L || dest.length() > 20L * 1024 * 1024) {
                dest.delete()
                return@withContext null
            }
            val id = db.attachmentDao().insert(
                Attachment(
                    itemId = null,
                    fileName = name,
                    mimeType = mime,
                    filePath = dest.absolutePath,
                    createdAt = Instant.now().toString()
                )
            )
            val text = extractAttachmentText(dest, mime, name)
            AttachmentMeta(id = id, name = name, mime = mime, path = dest.absolutePath, text = text)
        } catch (_: Exception) {
            null
        }
    }

    private fun isDocx(mime: String, name: String): Boolean =
        mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            name.substringAfterLast('.', "").equals("docx", ignoreCase = true)

    private fun isPdf(mime: String, name: String): Boolean =
        mime == "application/pdf" || name.substringAfterLast('.', "").equals("pdf", ignoreCase = true)

    private fun isPlainText(mime: String, name: String): Boolean =
        mime.startsWith("text/") ||
            name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "csv", "log")

    /** Extract readable text from a picked file so the agent can actually read what you attach. */
    private fun extractAttachmentText(file: File, mime: String, name: String): String? = when {
        isDocx(mime, name) -> extractDocxText(file)
        isPdf(mime, name) -> extractPdfText(file)
        isPlainText(mime, name) -> runCatching { cleanText(file.readText()).take(MAX_DOC_TEXT).ifBlank { null } }.getOrNull()
        else -> null
    }

    /**
     * Extract plain text from a .pdf via pdfbox-android (on-device, no permissions).
     * Returns up to MAX_DOC_TEXT chars, or null on any failure.
     */
    private fun extractPdfText(file: File): String? = try {
        com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
            val raw = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            cleanText(raw).take(MAX_DOC_TEXT).ifBlank { null }
        }
    } catch (_: Exception) {
        null
    }

    /** Normalize extracted prose: collapse spaces, tidy blank lines, strip non-break spaces. */
    private fun cleanText(raw: String): String = raw
        .replace("\u00a0", " ")
        .replace(Regex("[\\t ]+"), " ")
        .replace(Regex("\\r\\n?"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    /**
     * Extract plain text from a .docx (OOXML) — it's a zip containing word/document.xml.
     * Uses only built-in Android APIs (java.util.zip + XML text stripping), no added dependency.
     * Returns up to MAX_DOC_TEXT chars of readable text, or null on any failure.
     */
    private fun extractDocxText(file: File): String? = try {
        val sb = StringBuilder()
        java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name == "word/document.xml") {
                    val raw = zip.bufferedReader().readText()
                    // Turn paragraph/table/break boundaries into newlines, then strip every
                    // <...> tag and decode XML entities so the agent reads plain prose, not
                    // a wall of OOXML markup.
                    val cleaned = raw
                        .replace("</w:p>", "\n")
                        .replace("</w:tr>", "\n")
                        .replace("<w:tab/>", "\t")
                        .replace("<w:br/>", "\n")
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&lt;", "<").replace("&gt;", ">")
                        .replace("&quot;", "\"").replace("&apos;", "'")
                        .replace("&amp;", "&")
                    sb.append(cleaned)
                    break
                }
                e = zip.nextEntry
            }
        }
        cleanText(sb.toString()).take(MAX_DOC_TEXT).ifBlank { null }
    } catch (_: Exception) {
        null
    }

    private fun guessMime(name: String?): String = when (name?.substringAfterLast('.', "")?.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "docx", "doc" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "txt" -> "text/plain"
        "csv" -> "text/csv"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }

    private fun extFor(mime: String, name: String): String {
        val byMime = when {
            mime == "image/jpeg" -> ".jpg"
            mime == "image/png" -> ".png"
            mime == "image/webp" -> ".webp"
            mime == "image/gif" -> ".gif"
            mime == "application/pdf" -> ".pdf"
            mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
            else -> null
        }
        if (byMime != null) return byMime
        val fromName = name.substringAfterLast('.', "").takeIf { it.length in 1..5 }
        return if (!fromName.isNullOrBlank()) ".$fromName" else ".bin"
    }
}
