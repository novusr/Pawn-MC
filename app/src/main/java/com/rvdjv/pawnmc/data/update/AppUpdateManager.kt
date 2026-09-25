package com.rvdjv.pawnmc.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.rvdjv.pawnmc.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max

private const val GITHUB_LATEST_RELEASE_URL = "https://api.github.com/repos/novusr/Pawn-MC/releases/latest"

data class GitHubRelease(
    val version: String,
    val apkUrl: String?,
    val releaseUrl: String
)

enum class UpdateStatus {
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    ERROR
}

data class UpdateCheckResult(
    val currentVersion: String,
    val latestVersion: String?,
    val status: UpdateStatus,
    val release: GitHubRelease?
)

class AppUpdateManager(private val context: Context) {
    private val versionFile = File(context.filesDir, ".pawnmc_version.txt")

    fun getCurrentVersion(): String = BuildConfig.VERSION_NAME.trim()

    fun storeCurrentVersion(version: String = getCurrentVersion()) {
        versionFile.writeText(version.trim())
    }

    fun ensureVersionFileWritten() {
        storeCurrentVersion(getCurrentVersion())
    }

    fun readStoredVersion(): String? {
        if (!versionFile.exists()) return null
        val stored = versionFile.readText().trim()
        return stored.ifEmpty { null }
    }

    fun checkForUpdate(): UpdateCheckResult {
        val currentVersion = getCurrentVersion()
        val storedVersion = readStoredVersion() ?: currentVersion
        storeCurrentVersion(currentVersion)

        val release = fetchLatestRelease() ?: return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = null,
            status = UpdateStatus.ERROR,
            release = null
        )

        val comparison = compareVersions(storedVersion, release.version)
        val installComparison = compareVersions(currentVersion, release.version)
        val status = when {
            installComparison < 0 -> UpdateStatus.UPDATE_AVAILABLE
            comparison < 0 -> UpdateStatus.UPDATE_AVAILABLE
            else -> UpdateStatus.UP_TO_DATE
        }

        return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = release.version,
            status = status,
            release = release
        )
    }

    fun fetchLatestRelease(): GitHubRelease? {
        val url = URL(GITHUB_LATEST_RELEASE_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "PawnMC-Android/${BuildConfig.VERSION_NAME}")
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val responseBody = connection.inputStream.bufferedReader().readText()
            val releaseJson = JSONObject(responseBody)
            val version = releaseJson.optString("tag_name", "").trim()
            if (version.isBlank()) {
                return null
            }

            val releaseUrl = releaseJson.optString("html_url", "https://github.com/novusr/Pawn-MC/releases/latest")
            var apkUrl: String? = null
            val assets = releaseJson.optJSONArray("assets")
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val filename = asset.optString("name", "").lowercase(Locale.US)
                    if (filename.endsWith(".apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            GitHubRelease(
                version = version,
                apkUrl = apkUrl ?: releaseUrl,
                releaseUrl = releaseUrl
            )
        } finally {
            connection.disconnect()
        }
    }

    fun downloadLatestApk(release: GitHubRelease): File {
        val apkUrl = release.apkUrl ?: throw IllegalStateException("No APK asset found for latest release.")
        val targetFile = File(context.cacheDir, "pawnmc-latest.apk")
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val url = URL(apkUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } finally {
            connection.disconnect()
        }
    }

    fun buildInstallIntent(apkFile: File): Intent {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canRequestUnknownSources(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()
    }

    fun buildUnknownSourcesIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        @JvmStatic
        fun compareVersions(currentVersion: String, targetVersion: String): Int {
            val currentParts = parseVersionParts(currentVersion)
            val targetParts = parseVersionParts(targetVersion)
            val maxSize = max(currentParts.size, targetParts.size)

            for (index in 0 until maxSize) {
                val currentPart = currentParts.getOrElse(index) { 0 }
                val targetPart = targetParts.getOrElse(index) { 0 }
                if (currentPart != targetPart) {
                    return currentPart.compareTo(targetPart)
                }
            }

            val currentSuffix = prereleaseSuffix(currentVersion)
            val targetSuffix = prereleaseSuffix(targetVersion)

            if (currentSuffix.isEmpty() && targetSuffix.isEmpty()) return 0
            if (currentSuffix.isEmpty()) return 1
            if (targetSuffix.isEmpty()) return -1
            return currentSuffix.compareTo(targetSuffix)
        }

        private fun parseVersionParts(value: String): List<Int> {
            val cleaned = value.trim().removePrefix("v")
            val matches = Regex("\\d+").findAll(cleaned).map { it.value.toInt() }.toList()
            return if (matches.isEmpty()) listOf(0) else matches
        }

        private fun prereleaseSuffix(value: String): String {
            val cleaned = value.trim().removePrefix("v")
            val symbolIndex = cleaned.indexOfAny(charArrayOf('-', '+', '_'))
            return if (symbolIndex == -1) "" else cleaned.substring(symbolIndex + 1).trim()
        }
    }
}
