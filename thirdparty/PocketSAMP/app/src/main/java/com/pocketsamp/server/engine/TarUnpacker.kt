package com.pocketsamp.server.engine

import android.util.Log
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Unpacks a `.tar.gz` / `.tar` stream into a directory.
 *
 * Shared by [AssetExtractor] (bundled assets) and [MariaDbDownloader] (a bundle the
 * user downloads later), so both obey the same rules: directories are created,
 * symlinks are recreated without following escaping targets, the executable bit from
 * the tar header is applied, and a partially written file can never be mistaken for
 * a complete one because every entry is written to `.part` and renamed on success.
 */
internal object TarUnpacker {

    private const val TAG = "TarUnpacker"

    /**
     * @param source        raw archive bytes; gzip is detected from the magic number,
     *                      not from the file name (AAPT strips `.gz` at packaging).
     * @param outputDir     destination directory.
     * @param onProgress    called with the byte count processed so far.
     * @return the number of files written.
     */
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

            val tarStream: InputStream = if (isGzip) GZIPInputStream(buffered, 64 * 1024) else buffered
            TarArchiveInputStream(tarStream).use { tar ->
                val buffer = ByteArray(64 * 1024)
                var entry: TarArchiveEntry? = tar.nextEntry
                while (entry != null) {
                    val name = entry.name.removePrefix("./")
                    if (name.isNotBlank()) {
                        val target = File(outputDir, name).canonicalFile
                        // Refuse entries that would escape the destination.
                        val inside = target.path.startsWith(outputDir.canonicalPath + File.separator) ||
                            target.path == outputDir.canonicalPath
                        if (!inside) {
                            Log.w(TAG, "Skipping entry outside destination: $name")
                        } else if (entry.isDirectory) {
                            target.mkdirs()
                        } else if (entry.isSymbolicLink) {
                            runCatching {
                                java.nio.file.Files.deleteIfExists(target.toPath())
                                java.nio.file.Files.createSymbolicLink(
                                    target.toPath(),
                                    java.nio.file.Paths.get(entry.linkName)
                                )
                            }.onFailure { Log.w(TAG, "Symlink skipped: $name -> ${entry.linkName}") }
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
                            // Rename last: a killed download/extraction leaves the
                            // ".part" name behind, which the engine ignores.
                            if (!partial.renameTo(target)) {
                                target.delete()
                                partial.renameTo(target)
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
        }
        return written
    }
}
