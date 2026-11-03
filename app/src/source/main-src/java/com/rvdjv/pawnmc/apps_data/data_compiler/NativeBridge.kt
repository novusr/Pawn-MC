package com.rvdjv.pawnmc.apps_data.data_compiler

import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import com.rvdjv.pawnmc.apps_data.data_library.LibraryStore
import java.io.File

/**
 * The one loaded native library and the compiler entry points it exposes.
 *
 * The per-architecture archives in app assets are unpacked into
 * `Documents/PawnMC/library/<abi>/`. The bridge itself is loaded from
 * `nativeLibraryDir`; the selected compiler is staged from Documents into app-private
 * code cache because shared storage may be mounted `noexec`:
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
     * True when the selected [version] is a valid ELF for this device ABI.
     */
    fun isReady(context: android.content.Context, version: String): Boolean =
        libraryFile(context, version).let {
            LibraryStore.hasExpectedElfAbi(it, LibraryStore.deviceAbi())
        }

    /** Explains why a compiler file in Documents cannot be loaded. */
    fun readinessError(context: android.content.Context, version: String): String {
        val abi = LibraryStore.deviceAbi()
        val file = libraryFile(context, version)
        if (!file.isFile) return "Missing ${file.absolutePath} (expected ABI $abi)."
        val actual = LibraryStore.elfDescription(file)
        val expected = LibraryStore.expectedElfDescription(abi)
        return if (!LibraryStore.hasExpectedElfAbi(file, abi)) {
            "Invalid compiler library ${file.absolutePath}: found $actual; expected $expected for ABI $abi."
        } else {
            val loadError = LAST_PREPARATION_ERROR ?: if (LOADED) getLoadError() else ""
            if (loadError.isNotBlank()) {
                "The file matches $expected, but compiler preparation failed: $loadError"
            } else {
                "The file matches $expected, but it could not be staged or loaded. Check the compiler log for details."
            }
        }
    }

    /**
     * The ABI folder holding the source compiler libraries, or null when the app has no
     * usable storage root. This is the folder the UI reports.
     */
    fun libraryDirectory(context: android.content.Context): File? =
        runCatching { AppStorage.dataRoot(context) }
            .getOrNull()
            ?.let { LibraryStore.abiDirectory(context) }

    /**
     * Loads the bridge once, stages the Documents library into app-private cache,
     * and selects [version] from that cache.
     *
     * Returns true when the version resolved and its engine entry points bound.
     */
    fun prepare(context: android.content.Context, version: String): Boolean {
        LAST_PREPARATION_ERROR = null
        val directory = libraryDirectory(context) ?: run {
            LAST_PREPARATION_ERROR = "Compiler storage folder is unavailable."
            return false
        }
        if (!isReady(context, version)) {
            LAST_PREPARATION_ERROR = readinessError(context, version)
            return false
        }

        val source = libraryFile(context, version)
        val staged = try {
            LibraryStore.stageLibraryForLoading(
                source,
                File(context.codeCacheDir, "pawnmc-libraries"),
                LibraryStore.deviceAbi()
            )
        } catch (e: java.io.IOException) {
            LAST_PREPARATION_ERROR = e.message ?: "Could not copy the compiler into app-private code cache."
            android.util.Log.e(TAG, "Failed to stage ${source.absolutePath} for Android's linker", e)
            return false
        } catch (e: IllegalArgumentException) {
            LAST_PREPARATION_ERROR = e.message ?: "Compiler ELF validation failed."
            android.util.Log.e(TAG, "Refusing to stage an invalid compiler library ${source.absolutePath}", e)
            return false
        } catch (e: java.security.NoSuchAlgorithmException) {
            LAST_PREPARATION_ERROR = e.message ?: "SHA-256 is unavailable."
            android.util.Log.e(TAG, "SHA-256 is unavailable; cannot safely stage ${source.absolutePath}", e)
            return false
        }
        val loadDirectory = staged.parentFile ?: run {
            LAST_PREPARATION_ERROR = "Staged compiler has no parent directory."
            return false
        }
        val sourcePath = directory.absolutePath
        return try {
            if (!LOADED) {
                System.loadLibrary(BRIDGE_LIBRARY)
                LOADED = true
            }
            setLibraryDirectory(loadDirectory.absolutePath)
            if (!selectVersion(version)) {
                val linkerError = getLoadError()
                LAST_PREPARATION_ERROR = linkerError
                android.util.Log.e(TAG, "$version did not resolve from ${staged.absolutePath}: $linkerError")
                return false
            }
            ACTIVE_DIRECTORY = sourcePath
            ACTIVE_VERSION = version
            ACTIVE_SOURCE_LENGTH = source.length()
            ACTIVE_SOURCE_MODIFIED = source.lastModified()
            true
        } catch (e: UnsatisfiedLinkError) {
            LAST_PREPARATION_ERROR = e.message ?: "Native bridge or compiler symbol could not be linked."
            android.util.Log.e(TAG, "Failed to prepare the native bridge for $version", e)
            false
        }
    }

    /** Folder and version currently `dlopen`ed, or null when nothing is loaded yet. */
    @Volatile
    private var ACTIVE_DIRECTORY: String? = null

    @Volatile
    private var ACTIVE_VERSION: String? = null

    @Volatile
    private var ACTIVE_SOURCE_LENGTH = -1L

    @Volatile
    private var ACTIVE_SOURCE_MODIFIED = -1L

    @Volatile
    private var LAST_PREPARATION_ERROR: String? = null

    /** True when [version] from the current ABI folder is already the active engine. */
    fun isActive(context: android.content.Context, version: String): Boolean {
        val source = libraryFile(context, version)
        return LOADED &&
            ACTIVE_VERSION == version &&
            ACTIVE_DIRECTORY == libraryDirectory(context)?.absolutePath &&
            source.isFile &&
            ACTIVE_SOURCE_LENGTH == source.length() &&
            ACTIVE_SOURCE_MODIFIED == source.lastModified()
    }

    @Volatile
    private var LOADED = false

    /** Compiles with the arguments the runner built; `"Exit code: N\n<output>"`. */
    external fun compile(args: Array<String>): String

    /** Console output captured by the last compile. */
    external fun getOutput(): String

    /** Warnings and errors captured by the last compile. */
    external fun getErrors(): String

    /** Sets the app-private folder holding the staged `libpawnc*.so` file. */
    external fun setLibraryDirectory(path: String)

    /** Selects and loads [version] (`pawnc3107` / `pawnc31011`) from that folder. */
    external fun selectVersion(version: String): Boolean

    /** Last dynamic-linker error, or an empty string after a successful load. */
    external fun getLoadError(): String
}
