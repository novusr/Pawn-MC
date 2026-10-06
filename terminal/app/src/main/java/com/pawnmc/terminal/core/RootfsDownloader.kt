package com.pawnmc.terminal.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Progress of a [RootfsDownloader.download] run. */
internal data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val fileName: String,
) {
    /** `0f..1f`, or null while the server has not sent a `Content-Length` yet. */
    val fraction: Float?
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/**
 * Fetches the Ubuntu rootfs exactly once.
 *
 * The project deliberately avoids OkHttp here: this is one flat GET with a progress
 * callback, and the app already depends on `kotlinx-coroutines` and nothing else on the
 * network side. The incoming bytes are streamed straight to disk, so a 30 MB image
 * never becomes a 30 MB `ByteArray`.
 */
internal object RootfsDownloader {

    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val BUFFER_SIZE = 64 * 1024

    /**
     * Downloads [url] into [destination], reporting progress on every buffer.
     *
     * A partial file is removed on failure so a retry cannot resume from a truncated
     * archive, which would fail later inside `tar` with a much less obvious error.
     */
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (DownloadProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, "${destination.name}.part")

        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            var expectedBytes = -1L
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IOException("Rootfs download failed with HTTP $responseCode")
                }

                val totalBytes = connection.contentLengthLong
                expectedBytes = totalBytes
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            onProgress(
                                DownloadProgress(
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    fileName = destination.name,
                                )
                            )
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (partial.length() == 0L) {
                throw IOException("Rootfs download returned an empty archive")
            }
            // If the server declared a length, do not stage a truncated response.
            if (expectedBytes > 0 && partial.length() != expectedBytes) {
                throw IOException(
                    "Rootfs download was truncated: received ${partial.length()} of $expectedBytes bytes"
                )
            }

            // Move only after a complete download, so the destination file's existence is
            // proof that the archive transfer finished.
            if (destination.exists()) destination.delete()
            if (!partial.renameTo(destination)) {
                partial.copyTo(destination, overwrite = true)
                partial.delete()
            }
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    /**
     * Installs the rootfs archive if it is not already staged.
     *
     * @return true when a download happened, false when the archive was already present.
     */
    suspend fun ensureStaged(
        context: Context,
        onProgress: (DownloadProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val archive = TerminalPaths.rootfsArchive(context)
        if (archive.exists() && archive.length() > 0) return@withContext false

        val url = RootfsSources.forDevice()
            ?: throw IOException("No Ubuntu rootfs is published for this device's CPU")

        download(url, archive, onProgress)
        true
    }
}
