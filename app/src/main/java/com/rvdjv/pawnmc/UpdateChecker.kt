package com.rvdjv.pawnmc

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class UpdateChecker(private val context: Context) {

    data class UpdateInfo(
        val versionName: String,
        val downloadUrl: String,
        val changelog: String
    )

    data class ManifestResult(
        val updateInfo: UpdateInfo?,
        val forceUpdate: Boolean
    )

    companion object {
        private const val PREFS_NAME = "update_checker"
        private const val KEY_SKIPPED_VERSION = "skipped_update_version"
        private const val FETCH_INTERVAL_SECONDS = 3600L // 1 hour
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkManifest(): ManifestResult? = withContext(Dispatchers.IO) {
        try {
            if (!isNetworkAvailable()) return@withContext null

            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(FETCH_INTERVAL_SECONDS)
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)

            remoteConfig.fetchAndActivate().await()

            val currentVersion = getCurrentVersion()

            // Check for force update
            val minSupported = remoteConfig.getString("min_supported_version").ifEmpty { "0.0.0" }
            val forceUpdate = isNewerVersion(remote = minSupported, current = currentVersion)

            // Check for regular update
            val latestVersion = remoteConfig.getString("latest_version")
            var updateInfo: UpdateInfo? = null
            if (latestVersion.isNotEmpty() && isNewerVersion(remote = latestVersion, current = currentVersion)) {
                val downloadUrl = remoteConfig.getString("download_url")
                if (downloadUrl.isNotEmpty()) {
                    val changelog = remoteConfig.getString("changelog").ifEmpty {
                        "A new version is available."
                    }
                    updateInfo = UpdateInfo(
                        versionName = latestVersion,
                        downloadUrl = downloadUrl,
                        changelog = changelog
                    )
                }
            }

            ManifestResult(
                updateInfo = updateInfo,
                forceUpdate = forceUpdate
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

    private fun getCurrentVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
    }

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
}
