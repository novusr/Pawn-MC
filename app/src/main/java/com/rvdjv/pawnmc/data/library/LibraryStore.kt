package com.rvdjv.pawnmc.data.library

import android.util.Log
import com.rvdjv.pawnmc.data.config.AppStorage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * User-visible native libraries, `Documents/PawnMC/library/<abi>/`.
 *
 * The APK carries per-ABI archives in assets rather than raw compiler libraries under
 * `lib/<abi>/...so`. This object unpacks the matching archive on first use:
 *
 * ```
 * Documents/PawnMC/library/arm64-v8a/libpawnc3107.so
 * Documents/PawnMC/library/arm64-v8a/libpawnc31011.so
 * ```
 *
 * The ZIP asset and extracted folder are keyed by the first ABI Android reports that
 * PawnMC supports. Extracted ELF headers are checked too, so a stale ARM64 file cannot
 * be mistaken for an x86_64 compiler just because its file size matches.
 *
 * The extraction is idempotent and cheap to re-check: when every library the
 * archive lists is already present in the ABI folder with its recorded size and ABI, the
 * archive is not opened again. Deleting a file under `library/<abi>/` makes the
 * next launch unpack that ABI again, which is what makes the folder a usable
 * recovery point.
 */
object LibraryStore {

    private const val TAG = "LibraryStore"

    /** Folder name under `Documents/PawnMC` that holds the unpacked libraries. */
    const val LIBRARY_DIR_NAME = "library"

    /** ABI used when the device reports none that the APK ships. */
    private const val FALLBACK_ABI = "arm64-v8a"

    /** ABIs for which PawnMC builds compiler libraries. */
    private val ABI_MACHINES = mapOf(
        "armeabi-v7a" to (1 to 40),
        "x86_64" to (2 to 62),
        "arm64-v8a" to (2 to 183)
    )

    /** Test seam: replace the ABI list reported by [android.os.Build]. */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var abiOverride: (() -> Array<String>)? = null

    /** The ABI folder this install unpacks into, e.g. `arm64-v8a`. */
    fun deviceAbi(): String {
        val reported = abiOverride?.invoke() ?: android.os.Build.SUPPORTED_ABIS
        return reported.firstOrNull { it in ABI_MACHINES } ?: FALLBACK_ABI
    }

    /** `Documents/PawnMC/library`. */
    fun libraryRoot(context: android.content.Context): File =
        File(AppStorage.dataRoot(context), LIBRARY_DIR_NAME)

    /** `Documents/PawnMC/library/<abi>`, the folder the native loader reads. */
    fun abiDirectory(context: android.content.Context, abi: String = deviceAbi()): File =
        File(libraryRoot(context), abi)

    /** Absolute path of the ABI folder passed to the native bridge. */
    fun abiDirectoryPath(context: android.content.Context, abi: String = deviceAbi()): String =
        abiDirectory(context, abi).absolutePath

    /**
     * Asset path of the archive for [abi], e.g. `arm64-v8a.zip`.
     *
     * The archives are produced by CI (`ci.yml`, "Pack compiler libraries into per-ABI
     * archives") into `app/src/main/assets/`, because the compiler `.so` files only exist
     * as CMake build output. `assets/` is used rather than `lib/<abi>/` so the archive is
     * not itself subject to the native-library merge/packaging rules, and so it survives
     * as a plain file regardless of `extractNativeLibs`.
     */
    fun archiveAssetName(abi: String = deviceAbi()): String = "$abi.zip"

    /** Reads the archive as a stream; null when the APK has no archive for this ABI. */
    private fun openArchive(context: android.content.Context, abi: String): InputStream? =
        runCatching { context.assets.open(archiveAssetName(abi)) }
            .onFailure { Log.i(TAG, "No asset ${archiveAssetName(abi)}: ${it.message}") }
            .getOrNull()

    /** One entry of the per-ABI archive. */
    private data class Entry(val name: String, val size: Long)

    /**
     * Unpacks the [abi] archive into `library/<abi>/` unless it is already there.
     *
     * Returns true when the ABI folder holds both compiler versions afterwards.
     * A missing archive is a failure because native compiler libraries are intentionally
     * loaded from this user-visible folder, not from the APK.
     */
    fun ensureExtracted(context: android.content.Context, abi: String = deviceAbi()): Boolean {
        val target = abiDirectory(context, abi)

        // Opening the asset is the only way to learn whether the archive exists; it is
        // closed again straight away so the real pass below re-reads it.
        val probe = openArchive(context, abi) ?: run {
            Log.e(TAG, "No compiler archive ${archiveAssetName(abi)} in APK assets.")
            return false
        }
        val entries = try {
            listArchive(probe)
        } catch (error: Exception) {
            // Report the corrupt bundle and keep the compiler unavailable until repaired.
            Log.e(TAG, "Cannot read ${archiveAssetName(abi)} (corrupt archive?)", error)
            return false
        } finally {
            runCatching { probe.close() }
        }

        if (entries.none { it.name == "libpawnc3107.so" } ||
            entries.none { it.name == "libpawnc31011.so" }
        ) {
            Log.e(TAG, "${archiveAssetName(abi)} does not contain both compiler libraries.")
            return false
        }
        if (isComplete(target, entries)) {
            Log.i(TAG, "$abi libraries already present in ${target.absolutePath}")
            return true
        }

        return runCatching {
            target.mkdirs()
            openArchive(context, abi)?.use { unpack(it, target, entries) }
                ?: throw java.io.FileNotFoundException(archiveAssetName(abi))
            isComplete(target, entries)
        }.onFailure { error ->
            Log.e(TAG, "Failed to extract ${archiveAssetName(abi)}", error)
        }.getOrDefault(false).also { complete ->
            Log.i(TAG, "Extracted ${archiveAssetName(abi)} into ${target.absolutePath}: $complete")
        }
    }

    /** True when every archived library has the expected size and ABI. */
    private fun isComplete(directory: File, entries: List<Entry>): Boolean {
        if (!directory.isDirectory) return false
        return entries.all { entry ->
            val file = File(directory, entry.name)
            file.isFile &&
                file.length() == entry.size &&
                hasExpectedElfAbi(file, directory.name)
        }
    }

    internal fun elfDescription(file: File): String {
        val (elfClass, machine) = readElfHeader(file) ?: return "invalid or unreadable ELF"
        val machineName = when (machine) {
            3 -> "EM_386"
            40 -> "EM_ARM"
            62 -> "EM_X86_64"
            183 -> "EM_AARCH64"
            else -> "ELF machine $machine"
        }
        return "ELF${if (elfClass == 1) 32 else 64} $machineName ($machine)"
    }

    internal fun expectedElfDescription(abi: String): String {
        val (elfClass, machine) = ABI_MACHINES[abi] ?: return "supported ABI"
        val machineName = when (machine) {
            40 -> "EM_ARM"
            62 -> "EM_X86_64"
            183 -> "EM_AARCH64"
            else -> "ELF machine $machine"
        }
        return "ELF${if (elfClass == 1) 32 else 64} $machineName ($machine)"
    }

    private fun readElfHeader(file: File): Pair<Int, Int>? {
        if (!file.isFile || file.length() < 20L) return null
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) return null
                val elfClass = input.readUnsignedByte()
                val dataEncoding = input.readUnsignedByte()
                if (elfClass !in 1..2 || dataEncoding !in 1..2) return null
                input.seek(0x12)
                val first = input.readUnsignedByte()
                val second = input.readUnsignedByte()
                val machine = if (dataEncoding == 2) (first shl 8) or second else first or (second shl 8)
                elfClass to machine
            }
        }.getOrNull()
    }

    internal fun hasExpectedElfAbi(file: File, abi: String): Boolean {
        val expected = ABI_MACHINES[abi] ?: return false
        if (!file.isFile || file.length() < 20L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { input ->
                val magic = ByteArray(4)
                input.readFully(magic)
                if (!magic.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46))) return false
                val elfClass = input.readUnsignedByte()
                val dataEncoding = input.readUnsignedByte()
                if (elfClass != expected.first || dataEncoding !in 1..2) return false
                input.seek(0x12)
                val first = input.readUnsignedByte()
                val second = input.readUnsignedByte()
                val machine = if (dataEncoding == 2) (first shl 8) or second else first or (second shl 8)
                machine == expected.second
            }
        }.getOrDefault(false)
    }

    /**
     * Copies a validated Documents library into app-private code cache for Android's
     * linker. Shared Documents storage is commonly mounted noexec, so ELF files there
     * cannot reliably be mapped executable even when their architecture is correct.
     * The Documents copy remains the source of truth; the content-addressed cache is
     * recreated from it whenever the bytes change or the cache is cleared.
     */
    internal fun stageLibraryForLoading(source: File, cacheRoot: File, abi: String): File {
        require(hasExpectedElfAbi(source, abi)) {
            "Cannot stage ${source.absolutePath}: expected ${expectedElfDescription(abi)}."
        }
        if (!cacheRoot.isDirectory && !cacheRoot.mkdirs()) {
            throw java.io.IOException("Could not create native library cache ${cacheRoot.absolutePath}")
        }

        val temporary = File(cacheRoot, ".${source.name}.${System.nanoTime()}.tmp")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            source.inputStream().buffered().use { input ->
                FileOutputStream(temporary).buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            if (!hasExpectedElfAbi(temporary, abi)) {
                throw java.io.IOException("Staged compiler ${source.name} failed ELF validation.")
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val directory = File(cacheRoot, "$abi-$hash")
            val staged = File(directory, source.name)
            if (staged.isFile && staged.length() == temporary.length()) {
                if (sha256(staged) == hash) {
                    staged.setReadable(true, true)
                    staged.setExecutable(true, true)
                    return staged
                }
            }
            if (!directory.isDirectory && !directory.mkdirs()) {
                throw java.io.IOException("Could not create native library cache ${directory.absolutePath}")
            }
            if (staged.exists() && !staged.delete()) {
                throw java.io.IOException("Could not replace cached compiler ${staged.absolutePath}")
            }
            if (!temporary.renameTo(staged)) {
                throw java.io.IOException("Could not install cached compiler ${staged.absolutePath}")
            }
            staged.setReadable(true, true)
            staged.setExecutable(true, true)
            return staged
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun listArchive(source: InputStream): List<Entry> {
        val result = mutableListOf<Entry>()
        ZipInputStream(source.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                // Only flat library file names: no paths, nothing that escapes the folder.
                val name = entry.name
                if (!entry.isDirectory && name == File(name).name && name.endsWith(".so")) {
                    result += Entry(name, entry.size.coerceAtLeast(0L))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return result
    }

    private fun unpack(source: InputStream, target: File, expected: List<Entry>) {
        val allowed = expected.associateBy { it.name }
        ZipInputStream(source.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && allowed.containsKey(name)) {
                    val destination = File(target, name)
                    val staged = File(target, ".$name.tmp")
                    FileOutputStream(staged).use { output -> zip.copyTo(output) }
                    if (!hasExpectedElfAbi(staged, target.name)) {
                        staged.delete()
                        throw IllegalStateException("$name in ${archiveAssetName(target.name)} has the wrong ELF architecture.")
                    }
                    if (destination.exists() && !destination.delete()) {
                        staged.delete()
                        throw IllegalStateException("Could not replace ${destination.absolutePath}")
                    }
                    if (!staged.renameTo(destination)) {
                        staged.delete()
                        throw IllegalStateException("Could not install ${destination.absolutePath}")
                    }
                    destination.setReadable(true, false)
                    destination.setExecutable(true, false)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
