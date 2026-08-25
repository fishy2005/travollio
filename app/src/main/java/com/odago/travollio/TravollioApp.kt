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
import com.odago.travollio.routines.BudgetPaceWorker
import java.io.File
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
        scheduleRoutines()
    }

    /**
     * Debug aid: capture any uncaught exception to a file on device so a crash that can't be
     * reproduced here is self-documenting. Logs to getExternalFilesDir(null)/crash_log.txt and
     * logcat tag "TravollioCrash". The default handler still runs afterwards.
     */
    private fun installCrashLogger() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null)
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val entry = "=== $stamp thread=${thread.name} ===\n$sw\n"
                Log.e("TravollioCrash", entry)
                if (dir != null) {
                    File(dir, "crash_log.txt").appendText(entry)
                }
                // Also write where a phone user can actually open it — getExternalFilesDir is
                // hidden from file managers on Android 11+. Works until the process dies.
                appendVisibleCrash(entry)
            } catch (_: Exception) {
                // never let the logger itself crash
            } finally {
                prev?.uncaughtException(thread, throwable)
            }
        }
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
    }
}
