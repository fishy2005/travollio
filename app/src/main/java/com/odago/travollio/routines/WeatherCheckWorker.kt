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
import com.odago.travollio.data.SettingsPrefs
import com.odago.travollio.data.Weather
import java.time.Instant
import java.time.LocalDate

/**
 * Daily weather-aware rescheduling check (backend parity, on-phone): looks at
 * outdoor (sight) items with coordinates in the next 48h, pulls the live
 * forecast, and posts at most one notification suggesting an indoor same-day
 * swap when the weather is unfavorable. Silent without a weather key.
 */
class WeatherCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val key = SettingsPrefs.weatherApiKey(applicationContext)
        if (key.isBlank()) return Result.success()
        val db = AppDatabase.get(applicationContext)
        val trip = db.activeTrip() ?: return Result.success()
        val days = db.dayDao().daysForTripOnce(trip.id)
        val byId = days.associateBy { it.id }
        val today = LocalDate.now()
        val horizon = today.plusDays(2).toString()

        var message: String? = null
        val checked = ArrayList<String>()
        for (item in db.itemDao().itemsForTripOnce(trip.id)) {
            if (!Weather.isOutdoorKind(item.kind)) continue
            val date = byId[item.dayId]?.date ?: continue
            if (date < today.toString() || date > horizon) continue
            if (item.lat == null || item.lng == null) continue
            val days5 = Weather.forecast(item.lat, item.lng, key) ?: continue
            val hit = days5.firstOrNull { it.date == date } ?: continue
            checked.add(date)
            if (Weather.isUnfavorable(hit.condition, hit.tempC)) {
                val indoor = db.itemDao().itemsForTripOnce(trip.id)
                    .filter { it.dayId == item.dayId && Weather.isIndoorKind(it.kind) }
                    .firstOrNull()
                val swap = if (indoor != null) " Consider swapping '${item.title}' for '${indoor.title}'." else ""
                message = "Weather alert: ${hit.description} expected ${date} " +
                    "(${byId[item.dayId]?.city ?: trip.baseCity}).$swap"
                break
            }
        }

        if (message != null && notificationsAllowed()) {
            post(message)
        }
        db.routineStateDao().upsert(
            RoutineState(
                routineKey = "weather-check",
                lastRunTs = Instant.now().toString(),
                lastActionJson = if (message != null) "\"notified\"" else "\"none\"",
                stateJson = "{\"checkedDays\":${checked.distinct().size}}"
            )
        )
        return Result.success()
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun post(message: String) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "travollio-weather"
        if (nm.getNotificationChannel(channelId) == null) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Weather", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            System.currentTimeMillis().toInt(),
            NotificationCompat.Builder(applicationContext, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Travollio weather")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }
}
