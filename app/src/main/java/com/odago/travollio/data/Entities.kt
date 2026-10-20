package com.odago.travollio.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Max characters of extracted document text (PDF/Word/TXT) sent to the agent per turn.
 *  Large enough for a full multi-week itinerary, bounded to protect model context. */
const val MAX_DOC_TEXT = 20_000

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: String, // yyyy-MM-dd
    val endDate: String,   // yyyy-MM-dd
    val baseCity: String,
    val budgetCapHomeCcy: Double,
    val homeCcy: String, // e.g. "ILS"
    @ColumnInfo(defaultValue = "JPY") val localCcy: String = "JPY", // e.g. "JPY", "EUR"
    @ColumnInfo(defaultValue = "0") val isArchived: Boolean = false // finished trip kept for reference; not the active trip
)

@Entity(
    tableName = "days",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Day(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val date: String, // yyyy-MM-dd
    val city: String
)

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(entity = Day::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dayId")]
)
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val sort: Int,
    val timeLocal: String?, // HH:mm or null
    val title: String,
    val detail: String,
    val kind: String, // transit|food|sight|hotel|errand
    val lat: Double? = null,
    val lng: Double? = null,
    val costLocal: Double? = null,
    val ccy: String? = null,
    // v0.2.1 "moment dossier" fields — everything the agent files for one moment:
    val place: String? = null,        // venue name, e.g. "Spa & Massage Asakusa"
    val address: String? = null,      // street address
    val link: String? = null,         // booking URL / maps link / ticket link
    val confirmation: String? = null, // booking confirmation code
    // v0.4.2 check-off: when the traveler completed this moment (ISO ts) — drives
    // progress, streaks, and Explorer XP. Null = still to do.
    val doneAt: String? = null
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val ts: String, // ISO local date-time
    val itemId: Long? = null,
    val amountHomeCcy: Double,
    val amountLocal: Double?,
    val ccy: String, // currency of amountLocal; equals home ccy when amountLocal is null
    val category: String,
    val note: String
)

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val kind: String, // passport|insurance|visa|other
    val filePath: String,
    val expiresAt: String?, // yyyy-MM-dd or null
    val metaJson: String? = null
)

/**
 * Evidence filed against an itinerary moment: a photo/file copied into the app's
 * private sandbox (never the caller's URI — we own the bytes). Linked to an item
 * by the agent (attachmentIds on add/update) or directly from the dossier sheet.
 * itemId is null while the agent decides where it belongs.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long? = null,
    val fileName: String,
    val mimeType: String,
    val filePath: String, // app-private absolute path under filesDir/attachments/
    val note: String? = null, // agent-added caption, e.g. "receipt — 9,800 ¥"
    val createdAt: String
)

@Entity(tableName = "poi_saved")
data class PoiSaved(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val name: String,
    val lat: Double,
    val lng: Double,
    val notes: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: String,
    val role: String, // user|assistant
    val text: String,
    val cardJson: String? = null,
    val actionJson: String? = null // deep-link target ({"type":"item","itemId":..} or {"type":"day","day":..})
)

/**
 * AI-generated note / daily summary for one itinerary day. One per day (unique
 * dayId). Written by the agent via the `day.note` tool; shown as a summary card
 * on the Today and Trip screens. The AI is a first-class part of the app — it
 * writes these where it sees fit.
 */
@Entity(
    tableName = "daily_notes",
    foreignKeys = [ForeignKey(entity = Day::class, parentColumns = ["id"], childColumns = ["dayId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["dayId"], unique = true)]
)
data class DailyNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val content: String, // AI summary / notes for this day
    val updatedAt: String // ISO timestamp
)

@Entity(tableName = "routine_state")
data class RoutineState(
    @PrimaryKey val routineKey: String,
    val lastRunTs: String?,
    val lastActionJson: String? = null,
    val stateJson: String? = null
)

@Entity(tableName = "routine_log")
data class RoutineLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineKey: String,
    val ts: String,
    val observedJson: String?,
    val decision: String,
    val actionTaken: String? = null
)

/**
 * On-phone price watch (backend parity, no server): track a target price for
 * something trip-related (a hotel night, activity, flight). The traveler (or the
 * agent) records observed prices via `pricewatch_check`; the worker alerts when an
 * observation hits the target. Manual but honest — no fake provider API.
 */
@Entity(
    tableName = "price_watches",
    foreignKeys = [
        ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Item::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("tripId"), Index("itemId")]
)
data class PriceWatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val label: String,              // e.g. "teamLab Planets tickets"
    val itemId: Long? = null,       // linked itinerary moment, optional
    val targetAmount: Double,       // alert when observed <= target
    val targetCcy: String,           // currency of target + observations
    val lastObserved: Double? = null,
    val lastObservedTs: String? = null,
    val lastAlertTs: String? = null, // set when an alert fired for the current observation
    val note: String? = null,
    val createdAt: String
)

/**
 * Per-trip packing list (v0.4.2). Toggled rows with satisfying progress, filled by
 * the traveler or generated by the agent (`packing_generate` suggests sensible
 * items from trip length + planned activity kinds + destination).
 */
@Entity(
    tableName = "packing_items",
    foreignKeys = [ForeignKey(entity = Trip::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")]
)
data class PackingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val label: String,        // e.g. "Passport", "Rain jacket"
    val category: String,     // clothes|toiletries|tech|documents|other
    val isPacked: Boolean = false,
    val aiSuggested: Boolean = false, // added by the agent's suggestion, not by hand
    val createdAt: String
)

/** Attachment handed to the agent after the UI copied it into the sandbox. */
data class AttachmentMeta(
    val id: Long,
    val name: String,
    val mime: String,
    val path: String,
    val text: String? = null // extracted text for Word (.docx) documents, null otherwise
)
