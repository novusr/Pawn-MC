package com.rvdjv.pawnmc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks for app updates via the GitHub Releases API.
 *
 * - Only checks when network is available
 * - Fails silently on any error (timeout, parse error, no network)
 * - Supports "skip this version" via SharedPreferences
 */
class UpdateChecker(private val context: Context) {

    data class UpdateInfo(
        val versionName: String,
        val downloadUrl: String,
        val changelog: String
    )

    companion object {
        private const val GITHUB_API_URL =
            "https://api.github.com/repos/novusr/Pawn-MC/releases/latest"
        private const val PREFS_NAME = "update_checker"
        private const val KEY_SKIPPED_VERSION = "skipped_update_version"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 10_000
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check for a newer release on GitHub.
     * Returns [UpdateInfo] if a newer version exists, null otherwise.
     * Never throws — all errors are caught and result in null.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) return@withContext null

            val json = fetchLatestRelease() ?: return@withContext null
            val tagName = json.optString("tag_name", "").removePrefix("v")
            if (tagName.isEmpty()) return@withContext null

            val currentVersion = getCurrentVersion()
            if (!isNewerVersion(remote = tagName, current = currentVersion)) {
                return@withContext null
            }

            val downloadUrl = findApkDownloadUrl(json) ?: return@withContext null
            val changelog = json.optString("body", "").ifEmpty {
                "A new version is available."
            }

            UpdateInfo(
                versionName = tagName,
                downloadUrl = downloadUrl,
                changelog = changelog
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getSkippedVersion(): String? = prefs.getString(KEY_SKIPPED_VERSION, null)

    fun setSkippedVersion(version: String) {
        prefs.edit().putString(KEY_SKIPPED_VERSION, version).apply()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchLatestRelease(): JSONObject? {
        val url = URL(GITHUB_API_URL)
        val connection = url.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

    /**
     * Compare two semver strings (e.g. "1.2.8" vs "1.3.0").
     * Returns true if [remote] is strictly newer than [current].
     */
    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    /**
     * Find the first .apk asset download URL from the release JSON.
     */
    private fun findApkDownloadUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk", ignoreCase = true)) {
                return asset.optString("browser_download_url", "").ifEmpty { null }
            }
        }
        return null
    }
}
