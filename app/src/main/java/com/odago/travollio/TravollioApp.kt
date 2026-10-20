package com.odago.travollio

import android.app.Application
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.odago.travollio.data.AgentConfig
import com.odago.travollio.routines.BudgetPaceWorker
import com.odago.travollio.routines.PriceWatchWorker
import com.odago.travollio.routines.WeatherCheckWorker
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TravollioApp : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        cleanupLegacyLogs()
        // Restore the user's AI provider/key/model choice (falls back to the baked DeepSeek).
        AgentConfig.load(this)
        // pdfbox-android needs its resource loader initialised before the first PD
        // Document is opened; otherwise PDF attachment text extraction silently fails.
        runCatching { com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(this) }
        scheduleRoutines()
    }

    /**
     * Crash-only logger: capture any uncaught exception to a single user-visible file
     * (Downloads/Travollio_crash.log) + logcat tag "TravollioCrash". No routine/success
     * logging anywhere else in the app. The default handler still runs afterwards.
     */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "=== $stamp thread=${thread.name} ===\n$sw\n"
                Log.e("TravollioCrash", entry)
                appendVisibleCrash(entry)
            } catch (_: Exception) {
                // never let the logger itself crash
            } finally {
                prev?.uncaughtException(thread, throwable)
            }
        }
    }

    /** One-time cleanup of legacy log files from the verbose-logging era. */
    private fun cleanupLegacyLogs() {
        try {
            getExternalFilesDir(null)?.let { dir ->
                java.io.File(dir, "crash_log.txt").takeIf { it.exists() }?.delete()
            }
        } catch (_: Exception) {}
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val resolver = contentResolver
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf("Travollio_diagnostics.log"),
                null
            )?.use { c ->
                while (c.moveToNext()) {
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    )
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        } catch (_: Exception) {}
    }

    /** Append a crash to Downloads via MediaStore so it is readable on a modern phone. */
    private fun appendVisibleCrash(entry: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = contentResolver
        val name = "Travollio_crash.log"
        var uri: android.net.Uri? = null
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    )
                }
            }
            if (uri == null) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            }
            uri?.let {
                resolver.openOutputStream(it, "wa")?.use { s -> s.write(entry.toByteArray()) }
            }
        } catch (_: Exception) {
            // keep the crash logger non-fatal
        }
    }

    private fun scheduleRoutines() {
        val constraints = Constraints.Builder().build()
        val wm = WorkManager.getInstance(this)

        // One-shot on install so the first routine check runs immediately.
        val once = OneTimeWorkRequestBuilder<BudgetPaceWorker>()
            .setConstraints(constraints)
            .build()
        wm.enqueueUniqueWork(
            "budget-pace-once",
            ExistingWorkPolicy.REPLACE,
            once
        )

        // Daily periodic heartbeat (spec: budget pace check daily 20:00 + document expiry).
        // v0.1 uses a 24h periodic window; exact-time scheduling lands with the Phase C scheduler work.
        val daily = PeriodicWorkRequestBuilder<BudgetPaceWorker>(24, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            "daily-routines",
            ExistingPeriodicWorkPolicy.KEEP,
            daily
        )

        // Weather-aware rescheduling check (12h window; silent without a key).
        val weather = PeriodicWorkRequestBuilder<WeatherCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            "weather-check",
            ExistingPeriodicWorkPolicy.KEEP,
            weather
        )

        // Price-watch check (6h window per spec).
        val prices = PeriodicWorkRequestBuilder<PriceWatchWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            "price-watch",
            ExistingPeriodicWorkPolicy.KEEP,
            prices
        )

        // Home-screen widget refresh (countdown changes at midnight; item/budget on writes too).
        val widget = PeriodicWorkRequestBuilder<com.odago.travollio.widget.WidgetWorker>(3, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        wm.enqueueUniquePeriodicWork(
            "widget-refresh-periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            widget
        )
    }
}
