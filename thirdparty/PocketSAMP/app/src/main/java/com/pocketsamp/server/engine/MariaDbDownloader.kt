package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Downloads, validates and activates a MariaDB bundle at runtime.
 *
 * The bundled `assets/mariadb_arm64.tar.gz` can be incomplete (for example only
 * `share/` data), and rebuilding MariaDB for Android is not something an app can do on
 * the device. This class lets the user point the app at a ready-made bundle instead.
 *
 * ## Where the bundle lands, and why it is not `filesDir`
 *
 * Since API 29 the kernel enforces W^X: `exec()` on a file under the app's data
 * directory fails with `EACCES`. Android lifts that restriction in exactly one case —
 * the file sits inside a **private** directory (`/data/data/<pkg>` or
 * `/data/user/<n>/<pkg>`) **and** the path mirrors the package name the way
 * `nativeLibraryDir` does. So a downloaded daemon is extracted to:
 *
 * ```
 * filesDir/mariadb-native/arm64-v8a/bin/mariadbd
 *                 ^ private path  ^ ABI mirror
 * ```
 *
 * and the engine prefers that copy when it exists (see
 * [MariaDbEngine.findMariadbdBinary]). Extracting straight into `filesDir/mariadb/`
 * would download successfully and then fail to start — the exact trap this design
 * avoids.
 *
 * A `.zip` bundle is also accepted when it contains a `lib/arm64-v8a/` entry, so a
 * prebuilt native-library package can be dropped in unchanged.
 */
class MariaDbDownloader(private val context: Context) {

    companion object {
        private const val TAG = "MariaDbDownloader"

        /** Directory that holds activated (executable) native copies. */
        fun nativeDir(context: Context): File =
            File(context.filesDir, "mariadb-native/arm64-v8a")

        /** Executable daemon resolved from a downloaded bundle, if any. */
        fun downloadedDaemon(context: Context): File? =
            File(nativeDir(context), "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }

        /** Data directory that belongs to the downloaded build (separate from assets). */
        fun downloadedDataDir(context: Context): File =
            File(context.filesDir, "mariadb/data")

        private const val CONNECT_TIMEOUT = 20_000
        private const val READ_TIMEOUT = 60_000
        private const val MAX_BUNDLE_BYTES = 512L * 1024 * 1024
        private const val USER_AGENT = "PocketSAMP-MariaDbBundle/1.0"
    }

    /** Outcome of one download attempt; [message] is console-ready text. */
    data class Result(val success: Boolean, val message: String)

    /**
     * Downloads [url], verifies it against [sha256] (hex, optional) and installs it.
     *
     * @param onProgress human-readable progress line (`Downloading 42%`).
     */
    suspend fun downloadAndInstall(
        url: String,
        sha256: String? = null,
        onProgress: (String) -> Unit = {}
    ): Result = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) {
            return@withContext Result(false, "Bundle URL must be https:// (refused: $url)")
        }
        if (!android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")) {
            return@withContext Result(
                false,
                "This device is ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}; the bundle is arm64-v8a only."
            )
        }

        val workDir = File(context.cacheDir, "mariadb-download").apply { mkdirs() }
        val partial = File(workDir, "bundle.download.part")

        try {
            onProgress("Connecting to bundle host...")
            val downloaded = fetch(url, partial) { percent ->
                onProgress("Downloading bundle... $percent%")
            }

            if (sha256.isNullOrBlank()) {
                onProgress("Warning: no SHA-256 given; the bundle is not integrity-checked.")
            } else {
                onProgress("Verifying SHA-256...")
                val actual = sha256Of(partial)
                if (!actual.equals(sha256.trim(), ignoreCase = true)) {
                    partial.delete()
                    return@withContext Result(
                        false,
                        "Checksum mismatch.\n  expected: ${sha256.trim()}\n  actual:   $actual"
                    )
                }
            }

            if (downloaded > MAX_BUNDLE_BYTES) {
                partial.delete()
                return@withContext Result(false, "Bundle is larger than 512 MB; refusing to install.")
            }

            install(partial, onProgress)
        } catch (e: Throwable) {
            Log.e(TAG, "Bundle download failed", e)
            partial.delete()
            Result(false, "Download failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Streams [url] into [target], resuming from the existing byte count so a dropped
     * connection continues instead of restarting a 40 MB transfer.
     */
    private fun fetch(url: String, target: File, onPercent: (Int) -> Unit): Long {
        val already = if (target.isFile) target.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/gzip, application/x-tar, application/zip, */*")
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }

        try {
            val code = connection.responseCode
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && already > 0
            if (code != HttpURLConnection.HTTP_OK && !resuming) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
                throw IllegalStateException("HTTP $code${if (detail.isNullOrBlank()) "" else ": $detail"}")
            }

            val total = connection.contentLengthLong + if (resuming) already else 0L
            val append = resuming
            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, append).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = if (append) already else 0L
                    var lastPercent = -1
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        copied += read
                        if (total > 0) {
                            val percent = (copied * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onPercent(percent)
                            }
                        }
                    }
                    out.fd.sync()
                    return copied
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Detects the archive kind by content (not extension), extracts into a staging
     * directory, checks for `bin/mariadbd`, then swaps the staging tree into place.
     *
     * Nothing replaces the working install until the new one is known to be complete,
     * so a corrupt bundle leaves the previous daemon untouched.
     */
    private fun install(archive: File, onProgress: (String) -> Unit): Result {
        val staging = File(context.cacheDir, "mariadb-staging").apply {
            deleteRecursively()
            mkdirs()
        }

        val isZip = isZip(archive)
        if (isZip) {
            onProgress("Extracting native-library zip...")
            val libRoot = File(staging, "jni")
            unzip(archive, libRoot)
            val abiDir = libRoot.walkTopDown().firstOrNull {
                it.isDirectory && it.name == "arm64-v8a"
            } ?: return Result(false, "Zip has no lib/arm64-v8a/ entries; that is the only layout accepted.")
            // A prebuilt zip carries libs at the ABI root; the daemon is optional in it,
            // but the share/ and lib/ trees around the daemon must be present.
            abiDir.copyRecursively(staging, overwrite = true)
        } else {
            onProgress("Extracting bundle...")
            FileInputStream(archive).use { TarUnpacker.extract(it, staging) }
        }

        val daemon = File(staging, "bin/mariadbd")
        if (!daemon.isFile || daemon.length() == 0L) {
            staging.deleteRecursively()
            return Result(
                false,
                "Bundle extracted but contains no bin/mariadbd. Expected bin/mariadbd, bin/mariadb-install-db, lib/ and share/mariadb/ for arm64-v8a."
            )
        }

        onProgress("Installing daemon...")
        daemon.setExecutable(true, false)
        File(staging, "bin/mariadb-install-db").let { if (it.isFile) it.setExecutable(true, false) }

        val destination = nativeDir(context)
        // Keep the old tree until the new one is fully moved in.
        val previous = File(context.filesDir, "mariadb-native/previous")
        previous.deleteRecursively()
        if (destination.exists() && !destination.renameTo(previous)) destination.deleteRecursively()
        destination.parentFile?.mkdirs()
        if (!staging.renameTo(destination)) {
            if (!staging.copyRecursively(destination, overwrite = true)) {
                return Result(false, "Could not move the extracted bundle into ${destination.absolutePath}.")
            }
            staging.deleteRecursively()
        }
        destination.walkTopDown()
            .filter { it.isFile && (it.parentFile?.name == "bin" || it.path.contains("/lib/")) }
            .forEach { it.setExecutable(true, false) }

        previous.deleteRecursively()
        return Result(
            true,
            "MariaDB bundle installed at ${destination.absolutePath}.\nRestart the server to start the database."
        )
    }

    private fun isZip(file: File): Boolean {
        FileInputStream(file).use { input ->
            val head = ByteArray(4)
            val read = input.read(head)
            return read == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
        }
    }

    private fun unzip(archive: File, destination: File) {
        java.util.zip.ZipInputStream(FileInputStream(archive)).use { zip ->
            while (zip.nextEntry.also { entry = it } != null) {
                val name = entry!!.name.trimStart('/', '.')
                val target = File(destination, name).canonicalFile
                // canonicalPath comparison needs the destination canonical too: the
                // cache dir resolves through /data/user/0, which is a different
                // string than filesDir on some Android builds.
                val root = destination.canonicalPath + File.separator
                if (target.path.startsWith(root)) {
                    if (entry!!.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        java.io.FileOutputStream(target).use { out -> zip.copyTo(out) }
                    }
                }
                zip.closeEntry()
            }
        }
    }

}
