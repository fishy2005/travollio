package com.odago.travollio.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY id")
    fun all(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY id")
    fun allOnce(): List<Trip>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun tripOnce(id: Long): Trip?

    @Insert
    suspend fun insert(trip: Trip): Long

    @Update
    suspend fun update(trip: Trip)

    @Delete
    suspend fun delete(trip: Trip)
}

@Dao
interface DayDao {
    @Query("SELECT * FROM days WHERE tripId = :tripId ORDER BY date")
    fun daysForTrip(tripId: Long): Flow<List<Day>>

    @Query("SELECT * FROM days WHERE tripId = :tripId ORDER BY date")
    fun daysForTripOnce(tripId: Long): List<Day>

    @Insert
    suspend fun insertAll(days: List<Day>)

    @Insert
    suspend fun insert(day: Day): Long

    @Update
    suspend fun update(day: Day)

    @Delete
    suspend fun delete(day: Day)

    @Query("SELECT * FROM days WHERE id = :id LIMIT 1")
    suspend fun dayOnce(id: Long): Day?
}

@Dao
interface ItemDao {
    @Query(
        "SELECT i.* FROM items i JOIN days d ON i.dayId = d.id " +
            "WHERE d.tripId = :tripId ORDER BY d.date, i.sort, i.timeLocal"
    )
    fun itemsForTrip(tripId: Long): Flow<List<Item>>

    @Query(
        "SELECT i.* FROM items i JOIN days d ON i.dayId = d.id " +
            "WHERE d.tripId = :tripId ORDER BY d.date, i.sort, i.timeLocal"
    )
    suspend fun itemsForTripOnce(tripId: Long): List<Item>

    @Query("SELECT * FROM items WHERE dayId = :dayId ORDER BY sort, timeLocal")
    fun itemsForDay(dayId: Long): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun itemOnce(id: Long): Item?

    @Insert
    suspend fun insert(item: Item): Long

    /** Full-entity update — the agent edits by fetch → copy → update. */
    @Update
    suspend fun update(item: Item)

    /** Move an item to another day; sort is set by the caller (append = maxSort+1). */
    @Query("UPDATE items SET dayId = :dayId, sort = :sort WHERE id = :id")
    suspend fun moveItem(id: Long, dayId: Long, sort: Int)

    /** Re-sort a whole day in place so the UI order stays stable after add/move. */
    @Query("SELECT COALESCE(MAX(sort), -1) FROM items WHERE dayId = :dayId")
    suspend fun maxSortForDay(dayId: Long): Int

    @Delete
    suspend fun delete(item: Item)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE tripId = :tripId ORDER BY ts DESC, id DESC")
    fun forTrip(tripId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE tripId = :tripId ORDER BY ts DESC, id DESC")
    fun forTripOnce(tripId: Long): List<Transaction>

    @Insert
    suspend fun insertAll(list: List<Transaction>)

    @Insert
    suspend fun insert(tx: Transaction): Long
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY expiresAt")
    fun all(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY expiresAt")
    fun allOnce(): List<Document>

    @Insert
    suspend fun insert(doc: Document)

    @Update
    suspend fun update(doc: Document)

    @Delete
    suspend fun delete(doc: Document)

    @Query("DELETE FROM documents WHERE tripId = :tripId")
    suspend fun deleteAll(tripId: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY ts, id")
    fun all(): Flow<List<ChatMessage>>

    @Insert
    suspend fun insert(msg: ChatMessage)

    /** "Restart the agent": wipe the conversation so the next send has no prior chat context. */
    @Query("DELETE FROM chat_messages")
    suspend fun clear()
}

@Dao
interface DailyNoteDao {
    /** One AI note per day (dayId is unique). */
    @Query("SELECT * FROM daily_notes WHERE dayId = :dayId LIMIT 1")
    fun forDay(dayId: Long): Flow<DailyNote?>

    /** All notes for a trip (join via days). */
    @Query("SELECT n.* FROM daily_notes n JOIN days d ON n.dayId = d.id WHERE d.tripId = :tripId")
    fun forTrip(tripId: Long): Flow<List<DailyNote>>

    @Query("SELECT * FROM daily_notes WHERE dayId = :dayId LIMIT 1")
    suspend fun forDayOnce(dayId: Long): DailyNote?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DailyNote): Long
}

@Dao
interface RoutineStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(state: RoutineState)

    @Query("SELECT stateJson FROM routine_state WHERE routineKey = :key LIMIT 1")
    suspend fun stateOnce(key: String): String?

    @Query("SELECT stateJson FROM routine_state WHERE routineKey = :key LIMIT 1")
    fun stateFlow(key: String): Flow<String?>

    /** Persist the id of the active trip (null clears it). Stored as the stateJson string. */
    @Query("INSERT OR REPLACE INTO routine_state (routineKey, stateJson) VALUES ('active_trip_id', :state)")
    suspend fun setActiveTripId(state: String?)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments ORDER BY createdAt")
    fun all(): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE itemId = :itemId ORDER BY createdAt")
    fun forItem(itemId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
    suspend fun once(id: Long): Attachment?

    @Insert
    suspend fun insert(a: Attachment): Long

    /** File an attachment against an item (agent linking). */
    @Query("UPDATE attachments SET itemId = :itemId WHERE id IN (:ids)")
    suspend fun link(itemId: Long, ids: List<Long>)
}
