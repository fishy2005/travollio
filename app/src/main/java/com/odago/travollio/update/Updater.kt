package com.odago.travollio.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.odago.travollio.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app update engine (settings tab).
 *
 * How it works: the source of truth for "the latest build" is a GitHub **release**
 * (hosted via the GitHub Releases API). We publish each signed APK as a release
 * asset tagged `v{versionCode}-{versionName}` (e.g. `v4-0.3.2`), then the app:
 *
 *   1. GET `{api}/repos/{owner}/{repo}/releases/latest`  → newest release metadata.
 *   2. Parse versionCode/versionName from the tag, find the `.apk` asset URL.
 *   3. If `versionCode` > our own `BuildConfig.VERSION_CODE`, offer the update.
 *   4. Download the APK to `cacheDir/updates/`, then hand it to Android's package
 *      installer via FileProvider.
 *
 * The APK we install is signed with the SAME certificate as the installed app
 * (see the `signingConfigs.release` block in build.gradle.kts), otherwise Android
 * refuses the upgrade. HTTPS only — `usesCleartextTraffic` stays off.
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
    val publishedAt: String,
    val mandatory: Boolean,
)

/** Outcome of a feed check — lets the UI distinguish "you're current" from a real failure. */
sealed interface CheckResult {
    /** A release exists and is newer than the installed build. */
    data class Newer(val info: UpdateInfo) : CheckResult
    /** A release exists but is the same or older — already up to date. */
    data class Current(val info: UpdateInfo) : CheckResult
    /** No newer build is published (feed empty / 404 / no .apk asset). */
    data object UpToDate : CheckResult
    /** Couldn't reach the feed at all (offline, DNS, server error). */
    data class Unreachable(val reason: String?) : CheckResult
}

object Updater {

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** "v{versionCode}-{versionName}", e.g. v4-0.3.2 — the GitHub release tag convention. */
    val feedTagPrefix: String get() = "v${currentVersionCode}-${currentVersionName}"

    private fun feedUrl(): String =
        "${BuildConfig.GH_API_BASE}/repos/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}/releases/latest"

    /** Fetch the latest published release. Distinguishes "current" from "unreachable". */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val conn = try {
            (URL(feedUrl()).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Travollio")
            }
        } catch (e: Exception) {
            return@withContext CheckResult.Unreachable(e.message)
        }
        try {
            val code = conn.responseCode
            if (code != 200) {
                // 404 → no release published yet: treat as "nothing newer", not an error.
                return@withContext CheckResult.UpToDate
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val info = parseRelease(body) ?: return@withContext CheckResult.UpToDate
            when {
                info.versionCode > currentVersionCode -> CheckResult.Newer(info)
                else -> CheckResult.Current(info)
            }
        } catch (e: Exception) {
            CheckResult.Unreachable(e.message)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseRelease(body: String): UpdateInfo? {
        val o = JSONObject(body)
        val tag = o.optString("tag_name", "")
        val parsed = parseVersion(tag) ?: return null
        val (code, name) = parsed
        val assets = o.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url")
                break
            }
        }
        val url = apkUrl ?: return null
        return UpdateInfo(
            versionCode = code,
            versionName = name,
            apkUrl = url,
            notes = o.optString("body", "")
                .trim()
                .removePrefix("notes:").trim()
                .replace("\\n", "\n"),
            publishedAt = o.optString("published_at", ""),
            mandatory = o.optString("body", "").contains("[mandatory]"),
        )
    }

    /** Tag convention `v{versionCode}-{versionName}` → Pair(versionCode, versionName). */
    internal fun parseVersion(tag: String): Pair<Int, String>? {
        val t = tag.removePrefix("v")
        val dash = t.indexOf('-')
        if (dash <= 0) return null
        val code = t.substring(0, dash).toIntOrNull() ?: return null
        val name = t.substring(dash + 1).trim()
        if (name.isBlank()) return null
        return code to name
    }

    fun isNewer(remote: Int): Boolean = remote > currentVersionCode

    /**
     * Download the APK to cacheDir/updates/ and return the local file.
     * `onProgress` is 0..100 (best-effort; the server may not send Content-Length).
     */
    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { runCatching { it.delete() } }
            val out = File(dir, "travollio-v${info.versionName}-${info.versionCode}.apk")
            val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Travollio")
                if (conn.responseCode != 200) error("Download failed (HTTP ${conn.responseCode})")
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) onProgress(((done * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            out
        }

    /** Whether Android lets us kick off an APK install (i.e. unknown-sources allowed). */
    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 26) context.packageManager.canRequestPackageInstalls() else true

    /** Send the user to enable "install unknown apps" for this app. */
    fun requestInstallPermission(context: Context) {
        runCatching {
            val intent = Intent(
                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Launch the system package installer for the downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        }
        context.startActivity(intent)
    }
}
