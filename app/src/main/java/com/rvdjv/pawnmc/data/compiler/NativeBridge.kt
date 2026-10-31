package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.AppStorage
import com.rvdjv.pawnmc.data.library.LibraryStore
import java.io.File

/**
 * The one loaded native library and the compiler entry points it exposes.
 *
 * The per-architecture archives in `lib/<abi>/<abi>.zip` are unpacked into
 * `Documents/PawnMC/library/<abi>/`, so the compiler versions are no longer loaded as
 * two separate JNI libraries. Instead a single small library — [BRIDGE_LIBRARY] — is
 * loaded from `nativeLibraryDir` and it `dlopen`s the version the user picked from
 * the extracted folder:
 *
 * ```
 * Documents/PawnMC/library/<abi>/libpawnc3107.so    (pc_compile{}, wrappers)
 * Documents/PawnMC/library/<abi>/libpawnc31011.so   (pc_compile{}, wrappers)
 * ```
 *
 * Each version archive therefore holds a *dependent* library with no `JNI_OnLoad`
 * and no `Java_*` exports; the JNI surface lives here once ([compile],
 * [getOutput], [getErrors]) and is dispatched by the bridge to the active version.
 */
internal object NativeBridge {

    private const val TAG = "Compiler"

    /** Library loaded from `nativeLibraryDir`; it owns the JNI entry points. */
    const val BRIDGE_LIBRARY = "m"

    /** `lib<version>.so` inside `library/<abi>/`, and the version passed to the bridge. */
    private fun libraryFile(context: android.content.Context, version: String): File =
        File(LibraryStore.abiDirectory(context), "lib$version.so")

    /**
     * True when the selected [version] is unpacked for this device's ABI, i.e. the
     * compile can be routed to it without touching the APK's own libraries.
     */
    fun isReady(context: android.content.Context, version: String): Boolean =
        libraryFile(context, version).let { it.isFile && it.length() > 0L }

    /**
     * The ABI folder the compile will read from, or null when the app has no usable
     * storage root. This is the folder the UI reports, and the one the bridge dlopens.
     */
    fun libraryDirectory(context: android.content.Context): File? =
        runCatching { AppStorage.dataRoot(context) }
            .getOrNull()
            ?.let { LibraryStore.abiDirectory(context) }

    /**
     * Loads the bridge once, points it at the ABI folder and selects [version].
     *
     * Returns true when the version resolved and its engine entry points bound.
     */
    fun prepare(context: android.content.Context, version: String): Boolean {
        val directory = libraryDirectory(context) ?: return false
        if (!isReady(context, version)) return false

        val path = directory.absolutePath
        return try {
            if (!LOADED) {
                System.loadLibrary(BRIDGE_LIBRARY)
                LOADED = true
            }
            // The path is pushed on every call, not only on the first one: it is what
            // tells the bridge the storage root moved. The bridge then forces the next
            // `selectVersion` to `dlopen` again, so this is also the reload path.
            setLibraryDirectory(path)
            if (!selectVersion(version)) {
                android.util.Log.e(TAG, "$version did not resolve from $path")
                return false
            }
            ACTIVE_DIRECTORY = path
            ACTIVE_VERSION = version
            true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e(TAG, "Failed to prepare the native bridge for $version", e)
            false
        }
    }

    /** Folder and version currently `dlopen`ed, or null when nothing is loaded yet. */
    @Volatile
    private var ACTIVE_DIRECTORY: String? = null

    @Volatile
    private var ACTIVE_VERSION: String? = null

    /** True when [version] from the current ABI folder is already the active engine. */
    fun isActive(context: android.content.Context, version: String): Boolean =
        LOADED && ACTIVE_VERSION == version && ACTIVE_DIRECTORY == libraryDirectory(context)?.absolutePath

    /**
     * Library search folders in the order the bridge uses them. Kept in Kotlin too so
     * the compile log can say exactly where a version was looked for.
     */
    fun searchDirectories(context: android.content.Context): List<File> = buildList {
        libraryDirectory(context)?.let(::add)
        @Suppress("DEPRECATION")
        runCatching { File(context.applicationInfo.nativeLibraryDir) }.getOrNull()?.let(::add)
    }

    @Volatile
    private var LOADED = false

    /** Compiles with the arguments the runner built; `"Exit code: N\n<output>"`. */
    external fun compile(args: Array<String>): String

    /** Console output captured by the last compile. */
    external fun getOutput(): String

    /** Warnings and errors captured by the last compile. */
    external fun getErrors(): String

    /** Folder holding `libpawnc*.so`; empty means "use nativeLibraryDir only". */
    external fun setLibraryDirectory(path: String)

    /** Selects and dlopens [version] (`pawnc3107` / `pawnc31011`) from that folder. */
    external fun selectVersion(version: String): Boolean
}
