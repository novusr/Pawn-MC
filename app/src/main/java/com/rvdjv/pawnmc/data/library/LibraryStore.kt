package com.rvdjv.pawnmc.data.library

import android.util.Log
import com.rvdjv.pawnmc.data.config.AppStorage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * User-visible native libraries, `Documents/PawnMC/library/<abi>/`.
 *
 * The APK no longer ships the per-architecture native code as raw
 * `lib/<abi>/...so`. Each ABI folder in the APK carries a single archive,
 * `lib/<abi>/<abi>.zip`, and this object unpacks it on first use:
 *
 * ```
 * Documents/PawnMC/library/arm64-v8a/libpawnc3107.so
 * Documents/PawnMC/library/arm64-v8a/libpawnc31011.so
 * Documents/PawnMC/library/arm64-v8a/libandroidx.graphics.path.so
 * ```
 *
 * Both the ZIP in the APK and the extracted folder are keyed by the device ABI, so
 * `arm64-v8a.zip` holds the `arm64-v8a` libraries and is unpacked into
 * `library/arm64-v8a/`; no mixed-ABI directory can be produced.
 *
 * The extraction is idempotent and cheap to re-check: when every library the
 * archive lists is already present in the ABI folder with its recorded size, the
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

    /** Device ABIs in preference order, most preferred first. */
    private val ABI_PREFERENCE = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    /** Test seam: replace the ABI list reported by [android.os.Build]. */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var abiOverride: (() -> Array<String>)? = null

    /** The ABI folder this install unpacks into, e.g. `arm64-v8a`. */
    fun deviceAbi(): String {
        val reported = abiOverride?.invoke() ?: android.os.Build.SUPPORTED_ABIS
        return reported.firstOrNull { it in ABI_PREFERENCE } ?: FALLBACK_ABI
    }

    /** `Documents/PawnMC/library`. */
    fun libraryRoot(context: android.content.Context): File =
        File(AppStorage.dataRoot(context), LIBRARY_DIR_NAME)

    /** `Documents/PawnMC/library/<abi>`, the folder the native loader reads. */
    fun abiDirectory(context: android.content.Context, abi: String = deviceAbi()): File =
        File(libraryRoot(context), abi)

    /** Absolute path used by the native bridge when nothing is extracted yet. */
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
     * Returns true when the ABI folder holds every library afterwards — including
     * the case where nothing had to be done. A missing archive also returns true:
     * the files the user has are then the whole truth, and the loader falls back to
     * `nativeLibraryDir` for anything this folder does not provide.
     */
    fun ensureExtracted(context: android.content.Context, abi: String = deviceAbi()): Boolean {
        val target = abiDirectory(context, abi)

        // Opening the asset is the only way to learn whether the archive exists; it is
        // closed again straight away so the real pass below re-reads it.
        val probe = openArchive(context, abi) ?: run {
            Log.i(TAG, "No $abi archive in assets; using nativeLibraryDir only.")
            return true
        }
        val entries = try {
            listArchive(probe)
        } catch (error: Exception) {
            // A truncated/corrupt asset must not wedge the app: report and carry on, the
            // loader falls back to nativeLibraryDir.
            Log.e(TAG, "Cannot read ${archiveAssetName(abi)} (corrupt archive?)", error)
            return false
        } finally {
            runCatching { probe.close() }
        }

        if (entries.isEmpty()) {
            Log.w(TAG, "${archiveAssetName(abi)} lists no libraries; nothing to extract.")
            return true
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

    /** True when [directory] already holds every [entries] file at its recorded size. */
    private fun isComplete(directory: File, entries: List<Entry>): Boolean {
        if (!directory.isDirectory) return false
        return entries.all { entry ->
            val file = File(directory, entry.name)
            file.isFile && file.length() == entry.size
        }
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
                    FileOutputStream(destination).use { output -> zip.copyTo(output) }
                    destination.setReadable(true, false)
                    destination.setExecutable(true, false)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
