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

    /** Folder holding the user-visible settings mirror, `PawnMC/config`. */
    fun configRoot(context: Context): File = File(dataRoot(context), "config").ensureDir()

    private fun File.ensureDir(): File = apply { if (!exists()) mkdirs() }
}
