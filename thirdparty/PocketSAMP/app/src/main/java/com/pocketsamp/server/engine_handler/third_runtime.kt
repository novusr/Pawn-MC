package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration._DirectoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private data class RuntimeArchive(
    val fileName: String,
    val repositoryPath: String,
    val sha256: String
)

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
        private val syncMutex = Mutex()

        const val ROOTFS_ARCHIVE = "r_rootfs_i386.tar.gz"
        const val SAMP_ARCHIVE = "r_samp.tar.gz"
        const val MARIADB_ARCHIVE = "r_mariadb.tar.gz"
        const val ECOSYSTEM_ARCHIVE = "r_ecosystem.tar.gz"

        private val ARCHIVES = listOf(
            RuntimeArchive(
                ROOTFS_ARCHIVE,
                "rootfs/$ROOTFS_ARCHIVE",
                "182853cee480da1ce80be21c30a487cdca86c32f1eb5882ac08795eee6fad525"
            ),
            RuntimeArchive(
                SAMP_ARCHIVE,
                "samp/$SAMP_ARCHIVE",
                "e28471239a531d5c1a40699f2a95a40129b8d087ef1c28d1c06b47ae4eb6f1b5"
            ),
            RuntimeArchive(
                MARIADB_ARCHIVE,
                "mariadb/$MARIADB_ARCHIVE",
                "e4a2b4e50830b8917af6cac7ba0d5cad17098cd9d776618068ae6448ff5d13a5"
            ),
            RuntimeArchive(
                ECOSYSTEM_ARCHIVE,
                "ecosystem/$ECOSYSTEM_ARCHIVE",
                "f44f9886197c7e672bebe13b549db451408b625c1dfd49f328627a1b2cb668f8"
            )
        )

        /** Sub-directory of Documents/PawnMC that holds the runtime archives. */
        fun storageDir(context: Context): File =
            File(_DirectoryManager.getDocumentsPawnMcDir(), "storage")

        fun archiveFile(context: Context, fileName: String): File =
            File(storageDir(context), fileName)

        /** Verifies that every downloaded archive matches its expected digest. */
        suspend fun isComplete(context: Context): Boolean = withContext(Dispatchers.IO) {
            ARCHIVES.all { archive ->
                archiveFile(context, archive.fileName).let { file ->
                    file.isFile && file.length() > 0L && matchesChecksum(file, archive)
                }
            }
        }

        private fun matchesChecksum(file: File, archive: RuntimeArchive): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            return try {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
                }
                digest.digest().joinToString("") { "%02x".format(it) } == archive.sha256
            } catch (e: IOException) {
                Log.w(TAG, "Could not verify ${archive.fileName}", e)
                false
            }
        }
    }

    /** Downloads any archive that is missing; true when all exist afterwards. */
    suspend fun sync(onProgress: (String) -> Unit = {}): Boolean = syncMutex.withLock {
        withContext(Dispatchers.IO) { syncArchives(onProgress) }
    }

    private fun syncArchives(onProgress: (String) -> Unit): Boolean {
        if (!_DirectoryManager.hasStoragePermission(context)) {
            Log.i(TAG, "Storage permission missing; skipping the runtime archive download.")
            return false
        }

        val dir = storageDir(context).apply { mkdirs() }
        if (!dir.isDirectory) {
            Log.w(TAG, "Cannot create ${dir.absolutePath}")
            return false
        }

        for (archive in ARCHIVES) {
            val target = archiveFile(context, archive.fileName)
            if (target.isFile && target.length() > 0L && matchesChecksum(target, archive)) continue
            if (target.exists()) {
                Log.w(TAG, "Removing invalid cached archive ${archive.fileName}")
                if (!target.delete()) {
                    Log.w(TAG, "Could not remove invalid archive ${target.absolutePath}")
                    return false
                }
            }
            onProgress(t("log.dl.downloading").replace("{name}", archive.fileName))
            if (!download(archive, target)) {
                Log.w(TAG, "Could not download ${archive.fileName}")
                return false
            }
        }
        onProgress(t("log.dl.ready"))
        return true
    }

    /** Streams one archive, resuming and retrying on a short body. */
    private fun download(archive: RuntimeArchive, target: File): Boolean {
        val partial = File(target.parentFile, "${target.name}.part")
        var attempt = 1
        while (attempt <= MAX_ATTEMPTS) {
            try {
                fetch("$REPO_BASE/${archive.repositoryPath}", partial)
                if (!partial.isFile || partial.length() == 0L) throw IOException("Empty download for ${archive.fileName}")
                if (!matchesChecksum(partial, archive)) {
                    partial.delete()
                    throw IOException("SHA-256 verification failed for ${archive.fileName}")
                }
                if (target.exists() && !target.delete()) throw IOException("Could not replace ${archive.fileName}")
                if (!partial.renameTo(target)) throw IOException("Could not finalize ${archive.fileName}")
                Log.i(TAG, "Downloaded ${archive.fileName} (${target.length()} bytes)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Attempt $attempt for ${archive.fileName} failed: ${e.message}")
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
