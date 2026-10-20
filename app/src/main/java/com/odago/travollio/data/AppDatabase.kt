package com.odago.travollio.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Trip::class, Day::class, Item::class, Transaction::class, Document::class,
        PoiSaved::class, ChatMessage::class, RoutineState::class, RoutineLog::class,
        Attachment::class, DailyNote::class, PriceWatch::class, PackingItem::class
    ],
    version = 9
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tripDao(): TripDao
    abstract fun dayDao(): DayDao
    abstract fun itemDao(): ItemDao
    abstract fun transactionDao(): TransactionDao
    abstract fun documentDao(): DocumentDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun routineStateDao(): RoutineStateDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun dailyNoteDao(): DailyNoteDao
    abstract fun priceWatchDao(): PriceWatchDao
    abstract fun packingDao(): PackingDao

    /** Resolve the currently-active (non-archived, selected) trip, or null. */
    suspend fun activeTrip(): Trip? {
        val trips = tripDao().allOnce()
        if (trips.isEmpty()) return null
        val activeId = routineStateDao().stateOnce(ACTIVE_KEY)?.toLongOrNull()
        if (activeId != null) trips.firstOrNull { it.id == activeId && !it.isArchived }?.let { return it }
        return trips.filter { !it.isArchived }.minByOrNull { it.id }
    }

    /** Persist a new active-trip selection (or clear it with null). */
    suspend fun setActiveTrip(tripId: Long?) = routineStateDao().setActiveTripId(tripId?.toString())

    companion object {
        /** Key in routine_state that stores the id of the currently-active (selected) trip. */
        const val ACTIVE_KEY = "active_trip_id"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v4 → v5: add the agent deep-link action column to chat messages (preserves data). */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN actionJson TEXT")
            }
        }

        /** v5 → v6: multiple trips — add an archived flag so finished trips can be kept for reference. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v6 → v7: store the trip's local currency so budget display isn't hardcoded to JPY. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN localCcy TEXT NOT NULL DEFAULT 'JPY'")
            }
        }

        /** v7 → v8: on-phone price watches (target + observations, no server). */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS price_watches (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "tripId INTEGER NOT NULL, label TEXT NOT NULL, itemId INTEGER, " +
                        "targetAmount REAL NOT NULL, targetCcy TEXT NOT NULL, " +
                        "lastObserved REAL, lastObservedTs TEXT, lastAlertTs TEXT, " +
                        "note TEXT, createdAt TEXT NOT NULL, " +
                        "FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(itemId) REFERENCES items(id) ON DELETE SET NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_watches_tripId ON price_watches(tripId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_price_watches_itemId ON price_watches(itemId)")
            }
        }

        /** v8 → v9: engagement layer — moment check-offs + per-trip packing lists. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN doneAt TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS packing_items (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "tripId INTEGER NOT NULL, label TEXT NOT NULL, category TEXT NOT NULL, " +
                        "isPacked INTEGER NOT NULL DEFAULT 0, aiSuggested INTEGER NOT NULL DEFAULT 0, " +
                        "createdAt TEXT NOT NULL, " +
                        "FOREIGN KEY(tripId) REFERENCES trips(id) ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_packing_items_tripId ON packing_items(tripId)")
            }
        }

        fun get(context: Context): AppDatabase {
            val ctx = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx, AppDatabase::class.java, "travollio.db")
                        // v1 seeded with a buggy raw-SQL INSERT that crashed on launch ("N'EX"
                        // apostrophe broke the literal). v2 fixed the seed; v3 adds the moment
                        // dossier (item place/address/link/confirmation + attachments table).
                        // Proper migrations are the only path — a destructive fallback would
                        // silently wipe the traveller's trips on the next schema bump.
                        .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                        .addCallback(object : Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                seedTokyoTrip(db)
                            }
                        })
                        .build()
                }
                INSTANCE!!
            }
        }

        // Demo seed so the app is alive on first launch (spec: Tokyo trip, Sep 20-27 2026).
        // IMPORTANT: always use parameterized execSQL(sql, bindArgs) — never string-concatenate
        // values into SQL literals. Seeded text contains apostrophes (e.g. "N'EX"), which would
        // terminate a '...' literal and throw a SQLiteException in onCreate → app crashes on launch.
        private fun seedTokyoTrip(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT INTO trips (id, name, startDate, endDate, baseCity, budgetCapHomeCcy, homeCcy) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf(1, "Tokyo", "2026-09-20", "2026-09-27", "Shinjuku", 15000.0, "ILS")
            )

            val days = listOf(
                "2026-09-20" to "Shinjuku",
                "2026-09-21" to "Harajuku",
                "2026-09-22" to "Asakusa",
                "2026-09-23" to "Akihabara",
                "2026-09-24" to "Shinjuku",
                "2026-09-25" to "Shibuya",
                "2026-09-26" to "Shinjuku",
                "2026-09-27" to "Narita"
            )
            days.forEachIndexed { i, (date, city) ->
                db.execSQL(
                    "INSERT INTO days (id, tripId, date, city) VALUES (?, ?, ?, ?)",
                    arrayOf(i + 1, 1, date, city)
                )
            }

            // id, dayId, sort, timeLocal, title, detail, kind, costLocal(JPY), ccy
            val items: List<List<Any?>> = listOf(
                listOf(1, 1, 0, "10:30", "Narita Express to Shinjuku", "N'EX train · ~85 min from Narita", "transit", 3070.0, "JPY"),
                listOf(2, 1, 1, "14:00", "Check in — hotel, Shinjuku", "7 nights", "hotel", null, null),
                listOf(3, 1, 2, "19:00", "Dinner near Shinjuku Station", "Izakaya", "food", 4500.0, "JPY"),
                listOf(4, 2, 0, "09:00", "Meiji Shrine", "Harajuku", "sight", null, null),
                listOf(5, 2, 1, "12:30", "Lunch — ramen in Harajuku", "", "food", 1800.0, "JPY"),
                listOf(6, 2, 2, "16:00", "Shibuya Crossing + Hachiko", "", "sight", null, null),
                listOf(7, 3, 0, "09:30", "Train to Asakusa", "JR Yamanote line", "transit", 610.0, "JPY"),
                listOf(8, 3, 1, "10:00", "Senso-ji Temple", "Asakusa", "sight", null, null),
                listOf(9, 3, 2, "14:00", "teamLab Planets", "Odaiba", "sight", 3675.0, "JPY"),
                listOf(10, 3, 3, "18:30", "Dinner in Tsukishima", "", "food", 5200.0, "JPY"),
                listOf(11, 4, 0, "09:00", "Akihabara", "Electronics + anime district", "sight", null, null),
                listOf(12, 4, 1, "15:00", "Top up Suica card", "For transit", "errand", 3000.0, "JPY"),
                listOf(13, 7, 0, "10:00", "Shinjuku Gyoen", "Last morning in Tokyo", "sight", null, null),
                listOf(14, 8, 0, "09:00", "N'EX to Narita", "", "transit", 3070.0, "JPY"),
                listOf(15, 8, 1, "13:00", "Flight home", "", "transit", null, null),
                // v0.2.1 demo: an agent-booked moment with a full dossier (place/address/confirmation)
                listOf(16, 3, 4, "17:00", "Massage — Spa & Massage Asakusa", "60 min shiatsu · booked by agent", "errand", 9800.0, "JPY")
            )
            items.forEach { r ->
                db.execSQL(
                    "INSERT INTO items (id, dayId, sort, timeLocal, title, detail, kind, costLocal, ccy) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8])
                )
            }
            // Dossier fields for the demo massage booking (new columns are null for the rest)
            db.execSQL(
                "UPDATE items SET place = ?, address = ?, confirmation = ? WHERE id = ?",
                arrayOf(
                    "Spa & Massage Asakusa",
                    "2-6-4 Asakusa, Taito City, Tokyo",
                    "BK-8842",
                    16
                )
            )

            // FX used for seeding: 1 JPY ≈ 0.0256 ILS — flat rows: ts, note, category, amountHomeCcy(ILS), amountLocal(JPY)
            val txs = listOf(
                listOf("2026-09-20T14:10:00", "Hotel Shinjuku — 7 nights", "lodging", 2150.40, 84000.0),
                listOf("2026-09-20T11:30:00", "N'EX Narita → Shinjuku", "transit", 78.59, 3070.0),
                listOf("2026-09-20T20:00:00", "Dinner near Shinjuku Station", "food", 115.20, 4500.0)
            )
            txs.forEachIndexed { i, r ->
                db.execSQL(
                    "INSERT INTO transactions (id, tripId, ts, amountHomeCcy, amountLocal, ccy, category, note) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(i + 1, 1, r[0], r[3], r[4], "JPY", r[2], r[1])
                )
            }

            db.execSQL(
                "INSERT INTO documents (id, tripId, kind, filePath, expiresAt, metaJson) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf(1, 1, "passport", "on-device/passport.jpg", "2031-03-15", null)
            )

            // Demo AI daily summary for day 1 so the summary card is visible on first launch.
            db.execSQL(
                "INSERT INTO daily_notes (id, dayId, content, updatedAt) VALUES (?, ?, ?, ?)",
                arrayOf(
                    1, 1,
                    "Arrival day in Shinjuku. Land at Narita and take the N'EX (¥3,070) into the city, then check in at the hotel for 7 nights. Keep the evening light — an izakaya dinner near Shinjuku Station and an early night.",
                    "2026-08-24T20:00:00Z"
                )
            )
        }
    }
}
