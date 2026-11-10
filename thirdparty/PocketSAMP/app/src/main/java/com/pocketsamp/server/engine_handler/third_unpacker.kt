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
        // Symlinks whose target had not been written yet when the entry was
        // reached; resolved in a second pass after the main loop completes.
        val pendingLinks = mutableListOf<Pair<String, String>>()

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
                                /*
                                 * Shared storage (FUSE) refuses symlink(2), and a
                                 * dangling copy is what later shows up as
                                 * "libstdc++.so.6: cannot open shared object file".
                                 * So do not try the symlink at all here: always
                                 * materialize the link as a byte-for-byte copy of
                                 * its target. Relative link names (../lib64/x style)
                                 * resolve against the link's own directory first,
                                 * then against the extraction root; absolute names
                                 * and same-name lookups fill in the rest.
                                 */
                                val linkName = entry.linkName
                                val candidates = buildList {
                                    if (File(linkName).isAbsolute) {
                                        add(File(linkName))
                                    } else {
                                        add(File(target.parentFile, linkName))
                                        add(File(outputDir.canonicalFile, linkName))
                                    }
                                    add(File(outputDir.canonicalFile, File(linkName).name))
                                }
                                val resolved = candidates.firstOrNull { it.isFile }
                                if (resolved != null) {
                                    target.parentFile?.mkdirs()
                                    resolved.copyTo(target, overwrite = true)
                                    written++
                                } else {
                                    // Real archives never dangle; a miss means an
                                    // entry ordering issue that the second pass at
                                    // the end of extraction sweeps up.
                                    pendingLinks.add(name to linkName)
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
                                // renameTo refuses when the destination exists
                                // (FUSE is the usual offender) — clear the old
                                // copy first, and if rename still fails, fall
                                // back to a plain copy so the file lands anyway.
                                if (target.exists() && !target.delete()) {
                                    partial.delete()
                                    throw IllegalStateException("Unable to finalize extracted file ${target.name}")
                                }
                                val renamed = partial.renameTo(target)
                                val copied = !renamed && runCatching {
                                    partial.copyTo(target, overwrite = true)
                                }.isSuccess
                                if (!renamed && !copied) {
                                    partial.delete()
                                    throw IllegalStateException("Unable to finalize extracted file ${target.name}")
                                }
                                if (partial.exists()) partial.delete()
                                if ((entry.mode and 0b001_001_001) != 0) {
                                    target.setExecutable(true, false)
                                }
                                written++
                            }
                        }
                        onProgress(processed)
                        entry = tar.nextEntry
                    }

                    /*
                     * Second pass: materialize the symlinks that were deferred
                     * because their target had not been unpacked yet. Now that
                     * every regular entry is on disk the same candidate walk
                     * almost always finds the real file, closing the hole that
                     * left loader warnings like "libstdc++.so.6: cannot open
                     * shared object file" scattered across a fresh extraction.
                     */
                    for ((linkPath, linkTarget) in pendingLinks) {
                        val linkRoot = outputDir.canonicalFile
                        val linkFile = File(linkRoot, linkPath.removePrefix("./")).canonicalFile
                        if (!linkFile.path.startsWith(linkRoot.path + File.separator) && linkFile.path != linkRoot.path) continue
                        val candidates = buildList {
                            if (File(linkTarget).isAbsolute) {
                                add(File(linkTarget))
                            } else {
                                add(File(linkFile.parentFile, linkTarget))
                                add(File(linkRoot, linkTarget))
                            }
                            add(File(linkRoot, File(linkTarget).name))
                        }
                        val resolved = candidates.firstOrNull { it.isFile }
                        if (resolved != null) {
                            linkFile.parentFile?.mkdirs()
                            resolved.copyTo(linkFile, overwrite = true)
                            written++
                            logWarning("Deferred symlink materialized: $linkPath -> $linkTarget")
                        } else {
                            logWarning("Symlink skipped: $linkPath -> $linkTarget")
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
