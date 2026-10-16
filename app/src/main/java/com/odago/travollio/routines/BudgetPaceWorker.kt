package com.odago.travollio.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.odago.travollio.MainActivity
import com.odago.travollio.data.AppDatabase
import com.odago.travollio.data.RoutineState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Deterministic routine (spec v0.2): budget pace check + document expiry, pure math over Room data.
 * Runs daily via WorkManager; posts at most one notification per run when something needs attention.
 */
class BudgetPaceWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val trip = db.activeTrip() ?: return Result.success()
        val txs = db.transactionDao().forTripOnce(trip.id)
        val docs = db.documentDao().forTripOnce(trip.id)
        val ccy = trip.homeCcy.ifBlank { "ILS" }
        val sym = currencySymbol(ccy)

        val spent = txs.sumOf { it.amountHomeCcy }
        val cap = trip.budgetCapHomeCcy
        val today = LocalDate.now()
        // Defensive: seeded dates are always parseable, but a future user-edited trip
        // must never crash the worker. Unparseable dates → skip that trip's checks.
        val start = parseDate(trip.startDate)
        val end = parseDate(trip.endDate)

        var message: String? = null

        // 1) Budget pace (only meaningful during the trip window, and only when cap > 0)
        if (start != null && end != null && cap > 0 && !today.isBefore(start) && !today.isAfter(end)) {
            val totalDays = ChronoUnit.DAYS.between(start, end).toInt() + 1
            val elapsed = (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceAtLeast(1)
            val projected = spent / (elapsed.toDouble() / totalDays)
            if (projected > cap * 1.05) {
                message = "Budget pace: on track to spend ~$sym${"%,.0f".format(projected)} vs your $sym${"%,.0f".format(cap)} cap. Time to trim."
            } else if (spent / cap > 0.9 && today.isAfter(end.minusDays(1))) {
                message = "Budget: you've used ${((spent / cap) * 100).toInt()}% of your cap with the trip still running."
            }
        }

        // 2) Document expiry — T-30/T-7 pre-trip alerts (spec routine #2)
        for (d in docs.filter { it.expiresAt != null }) {
            val exp = parseDate(d.expiresAt!!) ?: continue
            val daysLeft = ChronoUnit.DAYS.between(today, exp).toInt()
            when {
                daysLeft < 0 -> message = "Document alert: your ${d.kind} expired ${-daysLeft} days ago."
                daysLeft <= 30 && (daysLeft == 30 || daysLeft == 7 || daysLeft in 1..6) ->
                    message = "Document alert: ${d.kind} expires in $daysLeft days (${exp.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))})."
            }
        }

        if (message != null && notificationsAllowed()) {
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "travollio-routines"
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Routines", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                applicationContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            nm.notify(
                System.currentTimeMillis().toInt(),
                NotificationCompat.Builder(applicationContext, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Travollio routine")
                    .setContentText(message)
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            )
        }

        // Heartbeat memory (spec: RoutineState)
        db.routineStateDao().upsert(
            RoutineState(
                routineKey = "budget-pace",
                lastRunTs = Instant.now().toString(),
                lastActionJson = if (message != null) "\"notified\"" else "\"none\"",
                stateJson = "{\"spent\":$spent,\"cap\":$cap}"
            )
        )
        return Result.success()
    }

    /** API 33+ requires the POST_NOTIFICATIONS runtime permission; without it, skip posting. */
    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun parseDate(s: String?): LocalDate? = try {
        s?.let { LocalDate.parse(it) }
    } catch (_: Exception) {
        null
    }

    private fun currencySymbol(ccy: String): String = when (ccy.uppercase()) {
        "ILS", "NIS" -> "₪"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY", "CNY" -> "¥"
        "INR" -> "₹"
        else -> "$ccy "
    }
}
