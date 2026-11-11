package com.pocketsamp.server.engine_handler

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Direct archive extraction for the PocketSAMP File Manager.
 *
 * Formats covered:
 *  - `.zip`        via commons-compress [ZipFile]
 *  - `.tar`        via commons-compress [TarArchiveInputStream]
 *  - `.tar.gz/.tgz` via the gzip compressor wrapped around the tar stream
 *  - `.rar`        via junrar (RAR4); RAR5 falls back to SevenZipJBinding
 *  - `.7z`         via SevenZipJBinding (also handles modern RAR5 archives)
 *
 * Every write is confined to the destination folder the caller passes; entries
 * carrying `..` or absolute components are skipped rather than followed, so a
 * crafted archive cannot escape the server folder.
 */
internal object ArchiveExtractor {

    /** The three destinations the File Manager offers. */
    enum class Method { NEW_FOLDER, ARCHIVE_FOLDER, DIRECT }

    /** Extensions this extractor can handle, without the dot. */
    val SUPPORTED_EXTENSIONS = setOf("zip", "tar", "gz", "tgz", "rar", "7z")

    /** True when [file] looks like an archive the extractor can open. */
    fun isSupported(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".zip") ||
            name.endsWith(".tar") ||
            name.endsWith(".tar.gz") ||
            name.endsWith(".tgz") ||
            name.endsWith(".rar") ||
            name.endsWith(".7z") ||
            name.endsWith(".gz")
    }

    /** Destination folder for [archive] under [method]. */
    fun destinationFor(archive: File, method: Method): File = when (method) {
        Method.DIRECT -> archive.parentFile ?: File(".")
        Method.ARCHIVE_FOLDER -> File(archive.parentFile ?: File("."), archive.nameWithoutExtension)
        Method.NEW_FOLDER -> File(archive.parentFile ?: File("."), archive.nameWithoutExtension + "_extracted")
    }

    /**
     * Extracts [archive] into [destination]. Returns the number of entries
     * written; throws on the first failure, leaving whatever landed behind.
     */
    fun extract(archive: File, destination: File): Int {
        require(archive.isFile && archive.canRead()) { "Archive is not readable: ${archive.name}" }
        require(destination.isDirectory || destination.mkdirs()) { "Cannot create destination: $destination" }
        val destinationCanonical = destination.canonicalFile
        return when {
            archive.name.lowercase().endsWith(".zip") -> extractZip(archive, destinationCanonical)
            archive.name.lowercase().endsWith(".tar") -> extractTar(archive, destinationCanonical, gzip = false)
            archive.name.lowercase().endsWith(".tar.gz") ||
                archive.name.lowercase().endsWith(".tgz") -> extractTar(archive, destinationCanonical, gzip = true)
            archive.name.lowercase().endsWith(".gz") -> extractGzSingle(archive, destinationCanonical)
            archive.name.lowercase().endsWith(".rar") -> extractRar(archive, destinationCanonical)
            archive.name.lowercase().endsWith(".7z") -> extractSevenZip(archive, destinationCanonical)
            else -> error("Unsupported archive format: ${archive.name}")
        }
    }

    // ------------------------------------------------------------------
    // Shared write helper
    // ------------------------------------------------------------------

    /** Sanitized relative path, or null when the entry must be skipped. */
    private fun safeRelativePath(entryName: String): String? {
        val normalized = entryName.replace('\\', '/')
        val parts = normalized.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty()) return null
        if (parts.any { it == ".." || it.endsWith(':') || it.isBlank() && normalized.startsWith("/") }) return null
        if (normalized.startsWith("/")) return null
        return parts.joinToString("/")
    }

    /** Ensures the parent of a sanitized target stays inside [destination]. */
    private fun ensureWithin(destination: File, relative: String): File {
        val target = File(destination, relative)
        val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: target
        require(targetCanonical.path.startsWith(destination.path + File.separator) ||
            targetCanonical.path == destination.path) { "Entry escapes destination: $relative" }
        return target
    }

    /** Makes sure a directory entry exists, and marks it executable-safe. */
    private fun ensureDirectory(destination: File, relativeDir: String) {
        val target = ensureWithin(destination, relativeDir.trimEnd('/'))
        if (!target.isDirectory && !target.mkdirs()) error("Cannot create folder: ${target.name}")
    }

    /** Writes [input] into the sanitized target path. */
    private fun writeFile(destination: File, relative: String, input: BufferedInputStream) {
        val parts = relative.split('/')
        if (parts.size > 1) ensureDirectory(destination, parts.dropLast(1).joinToString("/"))
        val target = ensureWithin(destination, relative)
        if (relative.endsWith("/")) {
            if (!target.isDirectory && !target.mkdirs()) error("Cannot create folder: ${target.name}")
            return
        }
        if (target.isDirectory) error("File conflicts with a folder: ${target.name}")
        target.parentFile?.let { parent -> if (!parent.isDirectory && !parent.mkdirs()) error("Cannot create folder: $parent") }
        FileOutputStream(target).use { output -> input.copyTo(output) }
    }

    // ------------------------------------------------------------------
    // .zip
    // ------------------------------------------------------------------

    private fun extractZip(archive: File, destination: File): Int {
        var count = 0
        ZipFile(archive).use { zip ->
            val entries = zip.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipArchiveEntry
                val relative = safeRelativePath(entry.name) ?: continue
                if (entry.isDirectory) {
                    ensureDirectory(destination, relative)
                    continue
                }
                zip.getInputStream(entry).use { stream ->
                    writeFile(destination, relative, BufferedInputStream(stream))
                }
                count++
            }
        }
        return count
    }

    // ------------------------------------------------------------------
    // .tar / .tar.gz
    // ------------------------------------------------------------------

    private fun extractTar(archive: File, destination: File, gzip: Boolean): Int {
        var count = 0
        FileInputStream(archive).use { raw ->
            val source = if (gzip) {
                BufferedInputStream(GzipCompressorInputStream(raw))
            } else {
                BufferedInputStream(raw)
            }
            TarArchiveInputStream(source).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val relative = safeRelativePath(entry.name) ?: continue
                    if (entry.isDirectory) {
                        ensureDirectory(destination, relative)
                        continue
                    }
                    writeFile(destination, relative, BufferedInputStream(tar))
                    count++
                }
            }
        }
        return count
    }

    /** A bare `.gz` that is not `.tar.gz`: single compressed file, name minus `.gz`. */
    private fun extractGzSingle(archive: File, destination: File): Int {
        val baseName = archive.name.removeSuffix(".gz")
        FileInputStream(archive).use { raw ->
            GzipCompressorInputStream(raw).use { gzip ->
                writeFile(destination, safeRelativePath(baseName) ?: return 0, BufferedInputStream(gzip))
            }
        }
        return 1
    }

    // ------------------------------------------------------------------
    // .rar
    // ------------------------------------------------------------------

    private fun extractRar(archive: File, destination: File): Int {
        var count = 0
        Archive(archive).use { rar ->
            // junrar reads RAR4 natively; a RAR5 signature is handed to the
            // 7-Zip engine, which understands both.
            val rar5 = isRar5Signature(archive)
            if (rar5) return extractSevenZip(archive, destination)
            val headers = rar.fileHeaders
            for (header in headers) {
                if (header.isDirectory) {
                    safeRelativePath(header.fileNameString)?.let { ensureDirectory(destination, it) }
                    continue
                }
                val relative = safeRelativePath(header.fileNameString) ?: continue
                val target = ensureWithin(destination, relative)
                target.parentFile?.let { parent -> if (!parent.isDirectory && !parent.mkdirs()) error("Cannot create folder: $parent") }
                FileOutputStream(target).use { output -> rar.extractFile(header, output) }
                count++
            }
        }
        return count
    }

    /** RAR5 starts with `Rar!\x1A\x07\x01\x00`; RAR4 ends `\x1A\x07\x00`. */
    private fun isRar5Signature(archive: File): Boolean =
        runCatching {
            FileInputStream(archive).use { input ->
                val signature = ByteArray(8)
                val read = input.read(signature)
                read >= 8 && signature[7].toInt() == 0x00 && signature[6].toInt() == 0x01
            }
        }.getOrDefault(false)

    // ------------------------------------------------------------------
    // .7z (and RAR5 via the same engine)
    // ------------------------------------------------------------------

    private fun extractSevenZip(archive: File, destination: File): Int {
        RandomAccessFile(archive, "r").use { handle ->
            val inArchive = runCatching {
                SevenZip.openInArchive(null, RandomAccessFileInStream(handle))
            }.getOrElse { throw IllegalStateException("Cannot open archive: ${it.message}") }
            try {
                val itemCount = inArchive.getNumberOfItems()
                val indices = IntArray(itemCount) { it }
                val callback = SevenZipCallback(inArchive, destination)
                // extract() is void; per-item failures surface through
                // setOperationResult throwing inside the callback.
                inArchive.extract(indices, false, callback)
                return callback.written
            } catch (e: SevenZipException) {
                throw IllegalStateException("7z error: ${e.message}", e)
            } finally {
                runCatching { inArchive.close() }
            }
        }
    }

    /** Streams every requested item into the sanitized destination tree. */
    private class SevenZipCallback(
        private val inArchive: IInArchive,
        private val destination: File
    ) : net.sf.sevenzipjbinding.IArchiveExtractCallback {

        var written: Int = 0
            private set

        // IProgress: native engine only needs the counters; progress reporting
        // beyond the extracted-item count is not worth the plumbing here.
        override fun setTotal(total: Long) = Unit

        override fun setCompleted(complete: Long) = Unit

        override fun getStream(index: Int, askExtractMode: ExtractAskMode): ISequentialOutStream? {
            if (askExtractMode != ExtractAskMode.EXTRACT) return null
            val path = inArchive.getProperty(index, PropID.PATH) as? String ?: ""
            val isDirectory = (inArchive.getProperty(index, PropID.IS_FOLDER) as? Boolean) == true
            val sanitized = safeRelativePath(path) ?: return null
            if (isDirectory || path.endsWith("/")) {
                ensureDirectory(destination, sanitized)
                return null
            }
            val target = ensureWithin(destination, sanitized)
            target.parentFile?.let { parent -> if (!parent.isDirectory && !parent.mkdirs()) error("Cannot create folder: $parent") }
            // Start fresh: the stream replaces any earlier partial file.
            FileOutputStream(target, false).use { output -> output.flush() }
            return ISequentialOutStream { data ->
                FileOutputStream(target, true).use { output -> output.write(data) }
                data.size
            }
        }

        override fun prepareOperation(askExtractMode: ExtractAskMode) = Unit

        override fun setOperationResult(operationResultOk: ExtractOperationResult) {
            if (operationResultOk != ExtractOperationResult.OK) {
                throw SevenZipException("Item failed: $operationResultOk")
            }
            written++
        }
    }
}
