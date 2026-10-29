package com.rvdjv.pawnmc.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment

import android.provider.Settings
import androidx.core.content.FileProvider
import com.rvdjv.pawnmc.BuildConfig
import com.rvdjv.pawnmc.data.config.AppStorage
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.max

private const val GITHUB_OWNER = "novusr"
private const val GITHUB_REPO = "Pawn-MC"
private const val GITHUB_API_PATH = "/releases/latest"
private const val GITHUB_LATEST_RELEASE_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
private const val GITHUB_API_RELEASES_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO$GITHUB_API_PATH"


private const val APK_FILE_EXTENSION = ".apk"
private const val ZIP_FILE_EXTENSION = ".zip"
private const val MAX_UPDATE_ENTRY_BYTES = 512L * 1024L * 1024L
private const val MAX_UPDATE_ARCHIVE_BYTES = 1024L * 1024L * 1024L
private const val MAX_UPDATE_DOWNLOAD_BYTES = MAX_UPDATE_ARCHIVE_BYTES
private const val PACKAGE_ARCHIVE_MIME_TYPE = "application/vnd.android.package-archive"
private const val LATEST_APK_FILE_NAME = "pawnmc-latest.apk"
private const val UNKNOWN_APP_SOURCES_PACKAGE_PREFIX = "package:"
private const val LEGACY_PACKAGE_SUFFIX = ".legacy"
private const val LEGACY_VERSION_SUFFIX = "-legacy"
private const val LEGACY_ASSET_NAME = "app-android-7-8-debug.zip"
private const val MODERN_ASSET_NAME = "app-android-8-15-debug.zip"

private const val _MC_LATEST = GITHUB_API_RELEASES_URL

data class GitHubRelease(
    val version: String,
    val legacyArchiveUrl: String?,
    val modernArchiveUrl: String?,
    val releaseUrl: String,
    val bodyMarkdown: String = ""
) {
    /** The package common to older releases, when one was published. */
    val apkUrl: String?
        get() = legacyArchiveUrl.takeIf { it?.endsWith(APK_FILE_EXTENSION, ignoreCase = true) == true }
            ?: modernArchiveUrl?.takeIf { it.endsWith(APK_FILE_EXTENSION, ignoreCase = true) }
    fun archiveUrl(isLegacyBuild: Boolean): String? = if (isLegacyBuild) {
        legacyArchiveUrl ?: modernArchiveUrl
    } else {
        modernArchiveUrl ?: legacyArchiveUrl
    }
}

internal fun parseGitHubRelease(releaseJson: JSONObject): GitHubRelease {
    val version = releaseJson.optString("tag_name", "").trim()
    val releaseUrl = releaseJson.optString(
        "html_url",
        GITHUB_LATEST_RELEASE_URL
    )

    var legacyArchiveUrl: String? = null
    var modernArchiveUrl: String? = null
    var fallbackApkUrl: String? = null
    val assets = releaseJson.optJSONArray("assets")
    if (assets != null) {
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            val filename = asset.optString("name", "").lowercase(Locale.US)
            val downloadUrl = asset.optString("browser_download_url", "").takeIf { it.isNotBlank() }
            when {
                filename == LEGACY_ASSET_NAME ||
                    (filename.startsWith("pawnmc-") && filename.endsWith(ZIP_FILE_EXTENSION)) -> {
                    if (legacyArchiveUrl == null) legacyArchiveUrl = downloadUrl
                }
                filename == MODERN_ASSET_NAME -> modernArchiveUrl = downloadUrl
                filename.endsWith(APK_FILE_EXTENSION) &&
                    !filename.endsWith("-7-8.apk") &&
                    !filename.endsWith("-8-15.apk") && fallbackApkUrl == null -> fallbackApkUrl = downloadUrl
            }
        }
    }

    return GitHubRelease(
        version = version,
        legacyArchiveUrl = legacyArchiveUrl ?: fallbackApkUrl,
        modernArchiveUrl = modernArchiveUrl ?: legacyArchiveUrl ?: fallbackApkUrl,
        releaseUrl = releaseUrl,
        bodyMarkdown = releaseJson.optString("body", "").trim()
    )
}

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

class UpdateManager(private val context: Context) {
    private val hiddenBaseDir = hiddenBaseDir(context)
    private val versionFile = File(hiddenBaseDir, ".pawnmc_version.txt")

    companion object {
        /**
         * The app's hidden data directory, `files/.pawnmc`.
         *
         * It lives under `filesDir`, which Android preserves across an in-place APK
         * update and which is separate from SharedPreferences, so the version marker
         * survives even when the prefs are wiped by a manual reinstall. The path itself
         * is owned by [com.rvdjv.pawnmc.data.config.AppStorage]; this accessor only keeps
         * the name the rest of the compiler layer already calls.
         */
        fun hiddenBaseDir(context: Context): File =
            com.rvdjv.pawnmc.data.config.AppStorage.privateRoot(context)

        @Volatile
        private var didWriteVersionInSession: Boolean = false

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

    fun getCurrentVersion(): String = BuildConfig.VERSION_NAME.trim().removeSuffix(LEGACY_VERSION_SUFFIX)

    fun storeCurrentVersion(version: String = getCurrentVersion()) {
        val trimmedVersion = version.trim()
        if (trimmedVersion.isBlank()) return

        val existingVersion = readStoredVersion()
        if (existingVersion == trimmedVersion) {
            didWriteVersionInSession = true
            return
        }

        runCatching {
            hiddenBaseDir.mkdirs()
            versionFile.writeText(trimmedVersion)
            didWriteVersionInSession = true
        }
    }

    fun ensureVersionFileWritten() {
        if (didWriteVersionInSession) return
        val currentVersion = getCurrentVersion().trim()
        if (currentVersion.isBlank()) return

        val existingVersion = readStoredVersion()
        if (existingVersion == currentVersion) {
            didWriteVersionInSession = true
            return
        }

        storeCurrentVersion(currentVersion)
    }

    fun readStoredVersion(): String? {
        if (!versionFile.exists()) return null
        val stored = versionFile.readText().trim()
        return stored.ifEmpty { null }
    }

    fun checkForUpdate(): UpdateCheckResult {
        val currentVersion = getCurrentVersion()
        val storedVersion = readStoredVersion()
            ?.removeSuffix(LEGACY_VERSION_SUFFIX)
            ?.takeIf { it.isNotBlank() }
            ?: currentVersion
        ensureVersionFileWritten()

        val release = fetchLatestRelease() ?: return UpdateCheckResult(
            currentVersion = currentVersion,
            latestVersion = null,
            status = UpdateStatus.ERROR,
            release = null
        )

        val comparison = compareVersions(storedVersion, release.version)
        val installComparison = compareVersions(currentVersion, release.version)
        val latestVersionIsNewer = installComparison < 0 || (isLegacyBuild() && comparison < 0)
        val isLegacy = isLegacyBuild()
        val hasPackage = latestVersionIsNewer && release.archiveUrl(isLegacy) != null
        val status = when {
            (installComparison < 0 || (isLegacy && comparison < 0)) && hasPackage -> UpdateStatus.UPDATE_AVAILABLE
            latestVersionIsNewer -> UpdateStatus.ERROR
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
        val url = URL(_MC_LATEST)
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

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(responseBody)
            val version = releaseJson.optString("tag_name", "").trim()
            if (version.isBlank()) {
                return null
            }

            parseGitHubRelease(releaseJson)
        } finally {
            connection.disconnect()
        }
    }

    /** Whether this installation uses the Android 7/API 24 legacy package. */
    fun isLegacyBuild(): Boolean = context.packageName.endsWith(LEGACY_PACKAGE_SUFFIX)

    /** The archive published for this exact flavor; never silently install the other flavor. */
    fun selectedArchiveUrl(release: GitHubRelease): String? = release.archiveUrl(isLegacyBuild())

    /** Download the selected ZIP (or legacy APK fallback) into Documents/Pawn-MC. */
    fun downloadLatestApk(release: GitHubRelease): File {
        val archiveUrl = selectedArchiveUrl(release)
            ?: throw IllegalStateException("No update package is available for this PawnMC version.")
        val updateDir = AppStorage.documentsRoot(context)
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                throw IllegalStateException("Grant PawnMC All files access, then retry downloading to Documents/Pawn-MC.")
            } else {
                throw IllegalStateException("Cannot access Documents/Pawn-MC on shared storage.")
            }
        if (!updateDir.isDirectory && !updateDir.mkdirs()) {
            throw IllegalStateException("Unable to create Documents/Pawn-MC.")
        }
        val isZip = archiveUrl.substringBefore('?').endsWith(ZIP_FILE_EXTENSION, ignoreCase = true)
        val assetName = archiveUrl.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: if (isZip) "pawnmc-${release.version}-update.zip" else LATEST_APK_FILE_NAME
        require(assetName.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid update package filename." }
        val safeVersion = release.version.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val legacyBuild = isLegacyBuild()
        val packageLabel = if (legacyBuild) "android-7-8" else "android-8-15"
        val releasePackageName = "app-$packageLabel-debug.zip"
        val isLegacySharedPackage = legacyBuild && assetName.startsWith("pawnmc-")
        require(!isZip || isLegacySharedPackage || assetName.equals(releasePackageName, ignoreCase = true)) {
            "Release archive does not match the installed PawnMC Android flavor."
        }
        val safeAssetName = if (isZip) assetName else "pawnmc-$safeVersion-$packageLabel-update.apk"
        val targetFile = File(updateDir, safeAssetName)
        val originalApks = updateDir.walkTopDown()
            .filter {
                it.isFile && it.extension.equals("apk", ignoreCase = true) && it.canonicalFile != targetFile.canonicalFile
            }
            .associate { it.canonicalPath to it.lastModified() }
        val targetFileExisted = targetFile.isFile
        val partialFile = File(updateDir, "${targetFile.name}.download")
        require(!partialFile.exists() || partialFile.delete()) {
            "Unable to discard the incomplete previous download."
        }

        val url = URL(archiveUrl)
        val allowedHosts = setOf("github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
        require(url.protocol.equals("https", ignoreCase = true) && url.host.lowercase(Locale.US) in allowedHosts) {
            "Update packages must use a secure GitHub HTTPS URL."
        }
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Download failed: HTTP ${connection.responseCode}")
            }

            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_UPDATE_DOWNLOAD_BYTES) {
                throw IllegalStateException("Update package exceeds the download size limit.")
            }
            connection.inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloadedBytes += count
                        if (downloadedBytes > MAX_UPDATE_DOWNLOAD_BYTES) {
                            throw IllegalStateException("Update package exceeds the download size limit.")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(partialFile.isFile && partialFile.length() > 0L) {
                "The downloaded update package is empty."
            }
            if (targetFileExisted) {
                val stagedUpdate = File(updateDir, "${targetFile.nameWithoutExtension}-new.apk")
                require(!stagedUpdate.exists() && partialFile.renameTo(stagedUpdate)) {
                    "Unable to stage the replacement update package safely."
                }
                require(targetFile.delete()) {
                    "Unable to safely replace the previous update package."
                }
                if (!stagedUpdate.renameTo(targetFile)) {
                    stagedUpdate.copyTo(targetFile, overwrite = false)
                    stagedUpdate.delete()
                }
            } else if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile, overwrite = false)
                partialFile.delete()
            }
            require(targetFile.isFile && targetFile.length() > 0L) {
                "The downloaded update package is empty."
            }
            val retainedApks = updateDir.walkTopDown()
                .filter {
                    it.isFile && it.extension.equals("apk", ignoreCase = true) && it.canonicalFile != targetFile.canonicalFile
                }
                .associate { it.canonicalPath to it.lastModified() }
            if (originalApks.any { (path, modified) -> retainedApks[path] != modified }) {
                throw IllegalStateException("Download attempted to modify an existing APK; it was rejected.")
            }
            targetFile
        } finally {
            connection.disconnect()
            if (partialFile.exists()) partialFile.delete()
        }
    }

    /** Extract the selected release archive and return its APK without deleting old files. */
    fun extractUpdateApk(archiveFile: File): File {
        if (archiveFile.extension.equals("apk", ignoreCase = true)) {
            val apkParent = archiveFile.canonicalFile.parentFile
            require(apkParent == AppStorage.documentsRoot(context)?.canonicalFile) {
                "Update APK must be located directly inside Documents/Pawn-MC."
            }
            return validateUpdateApk(archiveFile)
        }
        if (!archiveFile.extension.equals("zip", ignoreCase = true)) {
            throw IllegalArgumentException("Unsupported update package: ${archiveFile.name}")
        }

        val updateDir = archiveFile.parentFile
            ?: throw IllegalStateException("Update package has no parent directory.")
        val canonicalUpdateDir = updateDir.canonicalFile
        require(
            archiveFile.canonicalFile.parentFile == canonicalUpdateDir &&
                canonicalUpdateDir == AppStorage.documentsRoot(context)?.canonicalFile
        ) {
            "Update archive must be located directly inside Documents/Pawn-MC."
        }
        val extractDir = File(
            updateDir,
            "pawnmc-update-${archiveFile.nameWithoutExtension}-${System.currentTimeMillis()}"
        )
        if (!extractDir.mkdirs() && !extractDir.isDirectory) {
            throw IllegalStateException("Unable to create update extraction directory.")
        }

        val root = extractDir.canonicalFile
        val apkBefore = updateDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .associate { it.canonicalPath to it.lastModified() }
        val uncompressedSizes = mutableMapOf<String, Long>()
        var extractedApk: File? = null
        var extractedBytes = 0L
        java.util.zip.ZipInputStream(archiveFile.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val output = File(extractDir, entry.name).canonicalFile
                if (!output.toPath().startsWith(root.toPath())) {
                    throw IllegalArgumentException("Unsafe path in update archive.")
                }
                if (entry.isDirectory) {
                    if (!output.isDirectory && !output.mkdirs()) {
                        throw IllegalStateException("Unable to extract update archive.")
                    }
                } else {
                    output.parentFile?.let { parent ->
                        if (!parent.isDirectory && !parent.mkdirs()) {
                            throw IllegalStateException("Unable to extract update archive.")
                        }
                    }
                    output.outputStream().use { destination ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalForEntry = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            totalForEntry += count
                            extractedBytes += count
                            uncompressedSizes[output.canonicalPath] = totalForEntry
                            if (totalForEntry > MAX_UPDATE_ENTRY_BYTES || extractedBytes > MAX_UPDATE_ARCHIVE_BYTES) {
                                throw IllegalArgumentException("Update archive exceeds the safe extraction size limit.")
                            }
                            destination.write(buffer, 0, count)
                        }
                    }
                    if (output.extension.equals("apk", ignoreCase = true)) {
                        if (extractedApk != null) {
                            throw IllegalArgumentException("Update archive contains multiple APK files.")
                        }
                        extractedApk = output
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val apk = extractedApk?.takeIf { it.isFile && it.length() > 0L }
            ?: throw IllegalArgumentException("Update archive does not contain a valid APK.")
        validateUpdateApk(apk)
        val apkAfter = updateDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .associate { it.canonicalPath to it.lastModified() }
        if (apkBefore.any { (path, modified) -> apkAfter[path] != modified }) {
            throw IllegalArgumentException("Update archive modified an APK outside its extraction folder.")
        }
        val totalInstalledApkBytes = apkAfter.keys.sumOf { path -> uncompressedSizes[path] ?: File(path).length() }
        if (totalInstalledApkBytes > MAX_UPDATE_ARCHIVE_BYTES) {
            throw IllegalArgumentException("Update APK files exceed the safe total size limit.")
        }
        return apk
    }

    /** Validate the exact package selected by the installed Android flavor. */
    private fun validateUpdateApk(apk: File): File {
        require(apk.isFile && apk.length() > 0L && apk.extension.equals("apk", ignoreCase = true)) {
            "Update APK is missing or invalid."
        }
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.PackageInfoFlags.of(
                    android.content.pm.PackageManager.GET_META_DATA.toLong()
                )
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                android.content.pm.PackageManager.GET_META_DATA
            )
        }
        val isExpectedPackage = packageInfo?.packageName == context.packageName
        require(isExpectedPackage) {
            "Update APK does not match this PawnMC app package."
        }
        return apk
    }

    fun buildInstallIntent(apkFile: File): Intent {
        require(apkFile.isFile && apkFile.length() > 0L) { "Update APK is missing or empty." }
        require(apkFile.extension.equals("apk", ignoreCase = true)) { "Update file must be an APK." }
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, PACKAGE_ARCHIVE_MIME_TYPE)
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
            Uri.parse("${UNKNOWN_APP_SOURCES_PACKAGE_PREFIX}${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

}
