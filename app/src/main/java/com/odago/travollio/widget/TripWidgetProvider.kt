package com.odago.travollio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.odago.travollio.MainActivity
import com.odago.travollio.R
import com.odago.travollio.data.AppDatabase
import com.odago.travollio.data.Currencies
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Home-screen live widget: trip countdown + next moment + budget. */
class TripWidgetProvider : android.appwidget.AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    override fun onEnabled(context: Context) = refresh(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) = Unit

    companion object {
        /** MainActivity intent extra: which bottom tab to land on (widget deep-links). */
        const val EXTRA_TAB = "extra_tab"
        const val TAB_ASSISTANT = 3
        const val TAB_TODAY = 0

        fun refresh(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniqueWork(
                "widget-refresh",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetWorker>()
                    .setInitialDelay(java.time.Duration.ofSeconds(1))
                    .build()
            )
        }
    }
}

/** Builds and pushes widget RemoteViews from Room (background worker, no main-thread DB). */
class WidgetWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val trip = db.activeTrip()
        val views = android.widget.RemoteViews(
            applicationContext.packageName,
            if (trip == null) R.layout.trip_widget_empty else R.layout.trip_widget
        )

        if (trip == null) {
            views.setOnClickPendingIntent(R.id.widget_root, openApp(TripWidgetProvider.TAB_TODAY))
            push(views)
            return Result.success()
        }

        val today = LocalDate.now()
        val start = runCatching { LocalDate.parse(trip.startDate) }.getOrNull()
        val end = runCatching { LocalDate.parse(trip.endDate) }.getOrNull()
        val days = db.dayDao().daysForTripOnce(trip.id)
        val dayIdx = days.indexOfFirst { it.date == today.toString() }

        val title = trip.name
        val subtitle = when {
            start != null && today.isBefore(start) ->
                "in ${ChronoUnit.DAYS.between(today, start)} days"
            end != null && today.isAfter(end) -> "trip ended"
            dayIdx >= 0 -> "Day ${dayIdx + 1} of ${days.size} · ${days[dayIdx].city}"
            else -> trip.baseCity
        }
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_subtitle, subtitle.uppercase())

        // Next moment today (first not-done, else "all done").
        val todayItems = (days.firstOrNull { it.date == today.toString() } ?: days.firstOrNull())
            ?.let { d -> db.itemDao().itemsForTripOnce(trip.id).filter { it.dayId == d.id }.sortedBy { it.sort } }
            ?: emptyList()
        val upcoming = todayItems.firstOrNull { it.doneAt == null }
        if (upcoming != null) {
            views.setTextViewText(
                R.id.widget_next,
                (upcoming.timeLocal?.let { "$it · " } ?: "") + upcoming.title
            )
        } else {
            views.setTextViewText(R.id.widget_next, "All checked off for today")
        }

        // Budget line.
        val spent = db.transactionDao().forTripOnce(trip.id).sumOf { it.amountHomeCcy }
        views.setTextViewText(
            R.id.widget_budget,
            if (trip.budgetCapHomeCcy > 0)
                "${Currencies.symbol(trip.homeCcy)}${"%,.0f".format(spent)} of " +
                    "${Currencies.symbol(trip.homeCcy)}${"%,.0f".format(trip.budgetCapHomeCcy)} spent"
            else "${Currencies.symbol(trip.homeCcy)}${"%,.0f".format(spent)} spent"
        )

        views.setOnClickPendingIntent(R.id.widget_root, openApp(TripWidgetProvider.TAB_TODAY))
        views.setOnClickPendingIntent(R.id.widget_ask, openApp(TripWidgetProvider.TAB_ASSISTANT, "ask_pablo"))
        push(views)
        return Result.success()
    }

    private fun openApp(tab: Int, action: String = "open"): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            putExtra(TripWidgetProvider.EXTRA_TAB, tab)
            putExtra("widget_action", action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val code = if (tab == TripWidgetProvider.TAB_ASSISTANT) 201 else 200
        return PendingIntent.getActivity(
            applicationContext, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun push(views: android.widget.RemoteViews) {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val provider = java.lang.Class.forName(TripWidgetProvider::class.java.name)
        manager.getAppWidgetIds(android.content.ComponentName(applicationContext, provider))?.forEach {
            manager.updateAppWidget(it, views)
        }
    }
}
