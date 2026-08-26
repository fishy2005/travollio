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
 * refuses the upgrade. The update feed is HTTPS; cleartext is permitted app-wide
 * only so user-configured local LLM endpoints (LM Studio / Ollama over LAN/tailscale)
 * work — the feed itself never uses it.
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

    /** Fetch the latest published release. Distinguishes "current" from "unreachable".
     *
     *  The GitHub API is rich but rate-limited (60 req/hr per IP — and mobile/CGNAT IPs
     *  are shared, so unauthenticated calls routinely hit HTTP 403). We therefore fall
     *  back to the GitHub **web** "latest" redirect (`github.com/...releases/latest`,
     *  NOT api.github.com) when the API fails, so a rate-limit never hides an update. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        var apiFailed = false
        val apiInfo = try {
            fetchFromApi()
        } catch (e: Exception) {
            apiFailed = true
            null
        }
        if (apiInfo != null) {
            if (apiInfo.versionCode > currentVersionCode) CheckResult.Newer(apiInfo)
            else CheckResult.Current(apiInfo)
        } else {
            val webInfo = runCatching { fetchFromWebRedirect() }.getOrNull()
            when {
                webInfo != null && webInfo.versionCode > currentVersionCode -> CheckResult.Newer(webInfo)
                webInfo != null -> CheckResult.Current(webInfo)
                apiFailed -> CheckResult.Unreachable("Couldn't reach the update feed — check your connection and try again.")
                else -> CheckResult.UpToDate
            }
        }
    }

    /** Query the GitHub Releases API. Returns null on 404 (no release published yet);
     *  throws on any other non-success (rate limit 403, server 5xx, ...). */
    private fun fetchFromApi(): UpdateInfo? {
        val conn = (URL(feedUrl()).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Travollio")
        }
        try {
            return when (conn.responseCode) {
                200 -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                404 -> null
                else -> error("GitHub returned HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            conn.disconnect()
            throw e
        }
    }

    /** Follow the `github.com/{owner}/{repo}/releases/latest` redirect to the newest
     *  release tag. The web host isn't subject to api.github.com's rate limit, so this
     *  keeps the update discoverable even when the API is temporarily exhausted. */
    private fun fetchFromWebRedirect(): UpdateInfo? {
        val base = BuildConfig.GH_API_BASE.replace("api.github.com", "github.com").trimEnd('/')
        val conn = (URL("$base/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Travollio")
            setRequestProperty("Accept", "text/html")
        }
        try {
            val code = conn.responseCode
            if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return null
            val loc = conn.getHeaderField("Location") ?: return null
            val tag = extractTag(loc) ?: return null
            return buildUpdateInfoFromTag(tag)
        } finally {
            conn.disconnect()
        }
    }

    /** The last "tag/.../...." path segment of a release redirect Location header. */
    private fun extractTag(loc: String): String? {
        val idx = loc.lastIndexOf("tag/")
        if (idx < 0) return null
        val tag = loc.substring(idx + 4).trim().trimEnd('/')
        return tag.takeIf { it.isNotBlank() }
    }

    /** Build an update handle from just a tag (web fallback path) — the APK asset on a
     *  release is always `Travollio-v{versionName}.apk` (see scripts/release.sh). */
    private fun buildUpdateInfoFromTag(tag: String): UpdateInfo? {
        val (code, name) = parseVersion(tag) ?: return null
        val apkUrl = "https://github.com/${BuildConfig.GH_OWNER}/${BuildConfig.GH_REPO}/releases/download/$tag/Travollio-v${name}.apk"
        return UpdateInfo(versionCode = code, versionName = name, apkUrl = apkUrl, notes = "", publishedAt = "", mandatory = false)
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
