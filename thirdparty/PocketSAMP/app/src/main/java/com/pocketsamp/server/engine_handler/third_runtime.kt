package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration._DirectoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Fetches the runtime archives from GitHub into `Documents/PawnMC/storage/`.
 */
class ThirdRuntime(private val context: Context) {

    private fun t(key: String): String = Locales.text(Locales.current, key)

    companion object {
        private const val TAG = "ThirdRuntime"

        /** Raw GitHub prefix for the runtime archives. */
        private const val REPO_BASE =
            "https://raw.githubusercontent.com/novusr/Pawn-MC/main/thirdparty/PocketSAMP/runtime"

        private const val CONNECT_TIMEOUT = 20_000
        private const val READ_TIMEOUT = 60_000
        private const val MAX_ATTEMPTS = 3

        /** Sub-directory of Documents/PawnMC that holds the runtime archives. */
        fun storageDir(context: Context): File =
            File(_DirectoryManager.getDocumentsPawnMcDir(), "storage")

        fun archiveFile(context: Context, name: String): File =
            File(storageDir(context), "$name.tar.gz")

        /** True when every archive is present and non-empty. */
        fun isComplete(context: Context): Boolean =
            ARCHIVES.all { archiveFile(context, it).let { f -> f.isFile && f.length() > 0 } }

        /** Archives fetched on first run. */
        private val ARCHIVES = listOf("rootfs_i386", "samp_base", "mariadb_i386")
    }

    /** Downloads any archive that is missing; true when all exist afterwards. */
    suspend fun sync(onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (!_DirectoryManager.hasStoragePermission(context)) {
            Log.i(TAG, "Storage permission missing; skipping the runtime archive download.")
            return@withContext false
        }

        val dir = storageDir(context).apply { mkdirs() }
        if (!dir.isDirectory) {
            Log.w(TAG, "Cannot create ${dir.absolutePath}")
            return@withContext false
        }

        for (name in ARCHIVES) {
            val target = archiveFile(context, name)
            if (target.isFile && target.length() > 0) continue
            onProgress(t("log.dl.downloading").replace("{name}", name))
            if (!download(name, target)) {
                Log.w(TAG, "Could not download $name")
                return@withContext false
            }
        }
        onProgress(t("log.dl.ready"))
        true
    }

    /** Streams one archive, resuming and retrying on a short body. */
    private fun download(name: String, target: File): Boolean {
        val partial = File(target.parentFile, "${target.name}.part")
        var attempt = 1
        while (attempt <= MAX_ATTEMPTS) {
            try {
                fetch("$REPO_BASE/$name.tar.gz", partial)
                if (!partial.isFile || partial.length() == 0L) return false
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) return false
                Log.i(TAG, "Downloaded $name (${target.length()} bytes)")
                return true
            } catch (e: Throwable) {
                Log.w(TAG, "Attempt $attempt for $name failed: ${e.message}")
                if (attempt == MAX_ATTEMPTS) {
                    partial.delete()
                    return false
                }
                attempt++
            }
        }
        return false
    }

    private fun fetch(url: String, target: File) {
        val already = if (target.isFile) target.length() else 0L
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PawnMC-PocketSAMP")
            setRequestProperty("Accept-Encoding", "identity")
            if (already > 0) setRequestProperty("Range", "bytes=$already-")
        }
        try {
            val code = connection.responseCode
            val resuming = code == HttpURLConnection.HTTP_PARTIAL && already > 0
            if (code != HttpURLConnection.HTTP_OK && !resuming) {
                throw IllegalStateException("HTTP $code for $url")
            }
            val append = resuming
            if (already > 0 && !resuming) target.delete()
            val expected = connection.contentLengthLong
            var written = if (append) already else 0L
            connection.inputStream.use { input ->
                java.io.FileOutputStream(target, append).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                        written += read
                    }
                    out.fd.sync()
                }
            }
            if (expected > 0 && written - (if (append) already else 0L) < expected) {
                throw java.io.EOFException("truncated: ${written - (if (append) already else 0L)} of $expected bytes")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** SHA-256 of [file], for callers that want to pin a download. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
