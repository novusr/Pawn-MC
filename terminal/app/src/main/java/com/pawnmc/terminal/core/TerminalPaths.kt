package com.pawnmc.terminal.core

import android.content.Context
import java.io.File

/**
 * Every path the terminal feature writes to.
 *
 * All of it lives under the app's private files directory, which is the only place the
 * proot sandbox and the packaged native `lib*.so` files can be exec'd from on modern
 * Android. Nothing here is visible to the user through the file manager, which is
 * deliberate: the device storage they care about is bound into the container instead.
 */
internal object TerminalPaths {

    /** `files/` — parent of [localDir]; also the private dir proot binds into the container. */
    fun privateDir(context: Context): File = context.filesDir.parentFile ?: context.filesDir

    /** `files/local` — the "device" the container sees as its own root-adjacent storage. */
    fun localDir(context: Context): File = File(privateDir(context), "local").ensureDir()

    /** `files/local/bin` — the sandbox scripts plus the `pawncc` symlink. */
    fun binDir(context: Context): File = File(localDir(context), "bin").ensureDir()

    /** `files/local/lib` — scratch space exposed as LD_LIBRARY_PATH. */
    fun libDir(context: Context): File = File(localDir(context), "lib").ensureDir()

    /** `files/local/sandbox` — the extracted Ubuntu rootfs; proot's `-r` argument. */
    fun rootfsDir(context: Context): File = File(localDir(context), "sandbox").ensureDir()

    /** `files/local/home` — bound to `/home` (and `/root`) inside the container. */
    fun homeDir(context: Context): File = File(localDir(context), "home").ensureDir()

    /**
     * `files/local/sandbox/tmp` — bound to `/dev/shm`.
     *
     * Must be world writable (1777) or apt and Node refuse to run, so the mode is
     * applied by `sandbox.sh` rather than here, where Java cannot express it.
     */
    fun sharedMemoryDir(context: Context): File = File(rootfsDir(context), "tmp")

    /** `files/tmp` — the app-private cache proot unpacks archives through. */
    fun temporaryDir(context: Context): File =
        File(privateDir(context), "tmp").ensureDir()

    /** The staged rootfs archive, downloaded once and extracted on first launch. */
    fun rootfsArchive(context: Context): File = File(temporaryDir(context), "sandbox.tar.gz")

    /**
     * Marker that the rootfs has been extracted.
     *
     * The name is ugly on purpose — it is the contract `setup.sh` and [isInstalled]
     * share, and renaming it silently re-triggers a ~30 MB download.
     */
    fun setupMarker(context: Context): File =
        File(localDir(context), ".terminal_setup_ok_DO_NOT_REMOVE")

    /** The native library directory that holds `libproot.so`, `libloader.so`, `libtermux.so`. */
    fun nativeLibraryDir(context: Context): String = context.applicationInfo.nativeLibraryDir

    /**
     * True once the sandbox is extracted and ready to run.
     *
     * Mirrors `isTerminalInstalled()` from the Xed build: the marker alone is not
     * enough, because a user can wipe `sandbox/` while keeping the marker file.
     */
    fun isInstalled(context: Context): Boolean {
        if (!setupMarker(context).exists()) return false
        val rootfs = rootfsDir(context)
        val entries = rootfs.listFiles() ?: return false
        return entries.any { it.isDirectory && it.name !in NON_ROOTFS_ENTRIES }
    }

    /**
     * True when only `home/` and `tmp/` exist, meaning extraction produced nothing useful.
     * Kept separate from [isInstalled] so the UI can tell "never installed" from
     * "installed but broken".
     */
    fun isRootfsEmpty(context: Context): Boolean {
        val entries = rootfsDir(context).listFiles() ?: return true
        return entries.none { it.isDirectory && it.name !in NON_ROOTFS_ENTRIES }
    }

    /** Directories the sandbox adds itself and which therefore never prove a rootfs exists. */
    private val NON_ROOTFS_ENTRIES = setOf("home", "tmp")

    private fun File.ensureDir(): File = apply { if (!exists()) mkdirs() }
}
