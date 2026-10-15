package com.rvdjv.pawnmc.data.config

import android.content.Context
import java.io.File

/**
 * Every path PawnMC keeps for the user, in one place.
 *
 * The app used to hide all of its state inside `filesDir`, which Android deletes on
 * uninstall and which no file manager can reach. That is fine for machine state (the
 * extracted Ubuntu rootfs, the native scratch files) but wrong for anything the user is
 * meant to own: the starter workspace, the settings mirror, generated artefacts.
 *
 * Storage is therefore split in two:
 *
 *  - [privateRoot] — `files/.pawnmc`, app-private. Survives an in-place APK update, is
 *    deleted by an uninstall. Everything that is derived can be rebuilt from goes here.
 *  - [dataRoot] — the app's own directory on *shared* storage,
 *    `<external-files>/PawnMC/`. External files are deleted by an uninstall just like
 *    private data, but they are visible to every file manager and to a PC over USB, so a
 *    script, an `.amx` or a `.json` written here stays editable outside PawnMC.
 *
 * `getExternalFilesDir` needs no permission on any supported API level, which is what
 * makes it the safe default: when it is unavailable (storage unmounted, restricted
 * profile) every helper falls back to the private root instead of failing, so the app
 * keeps working with a less visible location rather than not working at all.
 */
internal object AppStorage {

    /** Folder name of the app directory on shared storage. */
    const val PUBLIC_DIR_NAME: String = "PawnMC"

    /**
     * Test seam for the public Documents root.
     *
     * `Environment.getExternalStoragePublicDirectory` is a static Android call a JVM
     * unit test cannot redirect, so the resolution goes through this indirection and
     * a test points it at a temp folder. Production code never touches it: `null`
     * means "use the real Environment".
     */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var documentsRootOverride: (() -> File?)? = null

    /**
     * `Documents/PawnMC` on shared storage.
     *
     * The one location that survives an uninstall: `Android/data/<pkg>/` is wiped by
     * the installer, but the public `Documents` directory is user data the system
     * never removes. The settings JSON mirror lives here, so a manual reinstall
     * finds every previous setting on its first launch and the user (or a file
     * manager, or a PC over USB) can inspect and hand-edit the file between
     * installs. Writing here requires the all-files permission the app already
     * requests on the main screen; when it is not granted, or the storage is
     * unmounted, callers fall back to the app-scoped root instead of failing.
     */
    fun documentsRoot(context: Context): File? {
        val documents = documentsRootOverride?.invoke()
            ?: if (hasPublicWriteAccess()) {
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
            } else {
                null
            } ?: return null
        // `PawnMC` is appended after the override, so the override answers with the
        // raw Documents directory exactly like the real Environment call does.
        val root = java.io.File(documents, PUBLIC_DIR_NAME)
        return runCatching { root.ensureDir() }.getOrNull()?.takeIf { it.isDirectory }
    }

    /** True when the app may write anywhere on shared storage right now. */
    private fun hasPublicWriteAccess(): Boolean =
        publicWriteAccessOverride?.invoke() ?: realPublicWriteAccess()

    /** Test seam; `null` means "ask the real Android APIs". */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var publicWriteAccessOverride: (() -> Boolean)? = null

    private fun realPublicWriteAccess(): Boolean = when {
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R ->
            android.os.Environment.isExternalStorageManager()
        else -> android.os.Environment.getExternalStorageState() ==
            android.os.Environment.MEDIA_MOUNTED
    }

    /**
     * `files/.pawnmc` — the hidden, app-private root.
     *
     * This is the location the update manager's version marker and the JSON settings
     * mirror have always used; both now come from here instead of repeating the path.
     */
    fun privateRoot(context: Context): File =
        File(context.filesDir, ".pawnmc").ensureDir()

    /**
     * The app directory the user can browse: `<external-files>/PawnMC`.
     *
     * Falls back to [privateRoot] when external storage is not mounted (or the device
     * has none), so a caller never has to handle a null root.
     */
    fun dataRoot(context: Context): File {
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return if (external != null) File(external, PUBLIC_DIR_NAME).ensureDir() else privateRoot(context)
    }

    /** True when [dataRoot] really points at shared storage and not at the private fallback. */
    fun isDataRootPublic(context: Context): Boolean =
        runCatching { context.getExternalFilesDir(null) }.getOrNull() != null

    /**
     * Default folder for the bundled starter workspace, `PawnMC/workspace`.
     *
     * Kept under the app directory rather than in `files/TMP` so the example script can be
     * opened, edited and copied by any other tool on the device, and so deleting it is a
     * user action instead of a side effect of clearing app data.
     */
    fun workspaceRoot(context: Context): File = File(dataRoot(context), "workspace").ensureDir()

    /**
     * Folder holding the user-visible settings mirror.
     *
     * Primary: `Documents/PawnMC/config` — survives an uninstall, so the JSON is
     * there when a fresh install looks for it. Fallback: `PawnMC/config` under the
     * app-scoped external dir, then the private root, matching the chain the other
     * helpers use. The chosen location is written *last* below so the caller can
     * log which one is in effect.
     */
    fun configRoot(context: Context): File {
        documentsRoot(context)?.let { return File(it, "config").ensureDir() }
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        val base = if (external != null) File(external, PUBLIC_DIR_NAME) else privateRoot(context)
        return File(base, "config").ensureDir()
    }

    /**
     * Folder holding the unified compile-log SQLite database, `PawnMC/logs`.
     *
     * One single `compile_logs.db` in `Android/data/<pkg>/files/PawnMC/logs/`
     * replaces the per-source `.log` files the compiler used to scatter next to
     * every script: one open handle for the whole app, one row (cell) per
     * compile, and the whole history browsable from any file manager or a PC.
     */
    fun logsRoot(context: Context): File = File(dataRoot(context), "logs").ensureDir()

    private fun File.ensureDir(): File = apply { if (!exists()) mkdirs() }
}
