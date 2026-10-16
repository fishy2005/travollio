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

/**
 * Price-watch check (backend parity, on-phone): fires at most one notification
 * per run. Alerts when a recorded observation hits its target (once per
 * observation), and nudges when watches go stale (>7 days without a check).
 * Observations come from the agent's `pricewatch_check` — there is no fake
 * provider API; a watch without observations never false-alerts.
 */
class PriceWatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = AppDatabase.get(applicationContext)
        val now = Instant.now().toString()
        var message: String? = null
        var hits = 0
        var stale = 0

        for (w in db.priceWatchDao().allOnce()) {
            val hit = w.lastObserved != null && w.lastObserved <= w.targetAmount
            if (hit && (w.lastAlertTs == null || (w.lastObservedTs != null && w.lastAlertTs < w.lastObservedTs))) {
                db.priceWatchDao().update(w.copy(lastAlertTs = now))
                hits++
                if (message == null) {
                    message = "Price drop: '${w.label}' at ${w.lastObserved} ${w.targetCcy} " +
                        "(target ${w.targetAmount})."
                }
            }
            val lastTouch = w.lastObservedTs ?: w.createdAt
            if (!hit && isOlderThanDays(lastTouch, 7)) stale++
        }
        if (message == null && stale > 0) {
            message = if (stale == 1) "1 price watch needs a fresh check — ask Pablo to run pricewatch_check."
            else "$stale price watches need fresh checks — ask Pablo to run pricewatch_check."
        }

        if (message != null && notificationsAllowed()) {
            post(message)
        }
        db.routineStateDao().upsert(
            RoutineState(
                routineKey = "price-watch",
                lastRunTs = now,
                lastActionJson = if (message != null) "\"notified\"" else "\"none\"",
                stateJson = "{\"hits\":$hits,\"stale\":$stale}"
            )
        )
        return Result.success()
    }

    private fun isOlderThanDays(iso: String, days: Long): Boolean = try {
        Instant.parse(iso).isBefore(Instant.now().minusSeconds(days * 86_400))
    } catch (_: Exception) {
        false
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun post(message: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "travollio-prices"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Prices", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Travollio prices")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
