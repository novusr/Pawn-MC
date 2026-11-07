package com.pocketsamp.server.engine_handler

import android.util.Log
import com.pocketsamp.server.Locales
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Unpacks a `.tar.gz` / `.tar` stream into a directory.
 */
internal object ThirdUnpacker {

    private const val TAG = "Unpacker"

    private const val TAR_HEADER_SIZE = 512
    private const val TAR_MAGIC_OFFSET = 257
    private const val TAR_MAGIC_LENGTH = 5
    private val TAR_MAGICS = setOf("ustar")

    private fun t(key: String): String = Locales.text(Locales.current, key)

    /** Logs safely: [android.util.Log] is unmocked in JVM unit tests. */
    private fun logWarning(message: String) {
        runCatching { Log.w(TAG, message) }.onFailure { println("WARN/$TAG: $message") }
    }

    /** Unpacks [source] into [outputDir]; returns the number of files written. */
    fun extract(source: InputStream, outputDir: File, onProgress: (Long) -> Unit = {}): Int {
        var written = 0
        var processed = 0L

        source.use { raw ->
            val buffered = BufferedInputStream(raw, 64 * 1024)
            buffered.mark(2)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()
            val isGzip = b1 == 0x1f && b2 == 0x8b
            if (b1 == -1) {
                throw IllegalArgumentException(t("log.unpack.empty"))
            }
            if (!isGzip) {
                // Reject non-tar payloads early: a plain-text stream would
                // otherwise be treated as an empty archive instead of failing.
                buffered.mark(TAR_HEADER_SIZE)
                val header = ByteArray(TAR_HEADER_SIZE)
                var filled = 0
                while (filled < TAR_HEADER_SIZE) {
                    val read = buffered.read(header, filled, TAR_HEADER_SIZE - filled)
                    if (read == -1) break
                    filled += read
                }
                buffered.reset()
                val isTar = filled == TAR_HEADER_SIZE &&
                    String(header, TAR_MAGIC_OFFSET, TAR_MAGIC_LENGTH) in TAR_MAGICS
                if (!isTar) {
                    throw IllegalArgumentException(t("log.unpack.corrupt").replace("{reason}", "not a tar archive"))
                }
            }

            val tarStream: InputStream = if (isGzip) GZIPInputStream(buffered, 64 * 1024) else buffered
            try {
                TarArchiveInputStream(tarStream).use { tar ->
                    val buffer = ByteArray(64 * 1024)
                    var entry: TarArchiveEntry? = tar.nextEntry
                    while (entry != null) {
                        val name = entry.name.removePrefix("./")
                        if (name.isNotBlank()) {
                            val root = outputDir.canonicalFile
                            if (!root.exists() && !root.mkdirs()) error("Unable to create extraction directory")
                            val target = File(root, name).canonicalFile
                            val inside = target.path.startsWith(root.path + File.separator) ||
                                target.path == root.path
                            if (!inside) {
                                logWarning("Skipping entry outside destination: $name")
                            } else if (entry.isDirectory) {
                                target.mkdirs()
                            } else if (entry.isSymbolicLink) {
                                val linked = runCatching {
                                    java.nio.file.Files.deleteIfExists(target.toPath())
                                    java.nio.file.Files.createSymbolicLink(
                                        target.toPath(),
                                        java.nio.file.Paths.get(entry.linkName)
                                    )
                                }.isSuccess
                                if (!linked) {
                                    // Shared storage (FUSE) refuses symlink(2); leaving
                                    // nothing behind makes the loader report
                                    // "libstdc++.so.6: cannot open shared object file".
                                    // Copy the link target's bytes instead: a real file
                                    // always resolves where a dangling symlink would not.
                                    val copied = runCatching {
                                        val source = File(outputDir.canonicalFile, entry.linkName)
                                        val resolved = if (source.isFile) source else {
                                            File(outputDir.canonicalFile, entry.linkName.substringAfterLast('/'))
                                        }
                                        if (resolved.isFile) {
                                            target.parentFile?.mkdirs()
                                            resolved.copyTo(target, overwrite = true)
                                            true
                                        } else false
                                    }.getOrDefault(false)
                                    if (copied) {
                                        logWarning("Symlink materialized as copy: $name -> ${entry.linkName}")
                                    } else {
                                        logWarning("Symlink skipped: $name -> ${entry.linkName}")
                                    }
                                }
                            } else {
                                target.parentFile?.mkdirs()
                                val partial = File(target.parentFile, target.name + ".part")
                                FileOutputStream(partial).use { out ->
                                    var count: Int
                                    while (tar.read(buffer).also { count = it } != -1) {
                                        out.write(buffer, 0, count)
                                        processed += count
                                    }
                                }
                                if (!partial.renameTo(target)) {
                                    partial.delete()
                                    throw IllegalStateException("Unable to finalize extracted file ${target.name}")
                                }
                                if ((entry.mode and 0b001_001_001) != 0) {
                                    target.setExecutable(true, false)
                                }
                                written++
                            }
                        }
                        onProgress(processed)
                        entry = tar.nextEntry
                    }
                }
            } catch (eof: java.io.EOFException) {
                throw IllegalStateException(
                    t("log.unpack.incomplete").replace(
                        "{reason}",
                        eof.message ?: "unexpected end of stream"
                    ),
                    eof
                )
            } catch (e: java.io.IOException) {
                val raw = e.message.orEmpty()
                if (raw.contains("end of stream", ignoreCase = true) ||
                    raw.contains("truncated", ignoreCase = true)
                ) {
                    throw IllegalStateException(
                        t("log.unpack.incomplete").replace("{reason}", raw),
                        e
                    )
                }
                throw IllegalStateException(t("log.unpack.corrupt").replace("{reason}", raw), e)
            }
        }
        return written
    }
}
