package com.odago.travollio.data

import com.odago.travollio.data.Document
import com.odago.travollio.data.Day
import com.odago.travollio.data.Item
import com.odago.travollio.data.ChatMessage
import com.odago.travollio.data.Transaction
import com.odago.travollio.data.Trip
import com.odago.travollio.data.DailyNote

/** One combined snapshot of everything the UI renders. */
data class AppState(
    val trip: Trip? = null,
    val days: List<Day> = emptyList(),
    val items: List<Item> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val documents: List<Document> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val notes: List<DailyNote> = emptyList(),
    val availableTrips: List<Trip> = emptyList(),
    val packing: List<PackingItem> = emptyList()
) {
    companion object {
        fun empty() = AppState()
    }

    val spentHomeCcy: Double get() = transactions.sumOf { it.amountHomeCcy }
    val spentLocal: Double get() = transactions.sumOf { it.amountLocal ?: 0.0 }
    val localCcy: String get() = trip?.localCcy ?: "JPY"
}
