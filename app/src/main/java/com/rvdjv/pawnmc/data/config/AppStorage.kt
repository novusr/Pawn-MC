package com.rvdjv.pawnmc.data.config

import android.content.Context
import java.io.File

/**
 * Every path PawnMC keeps for the user, in one place.
 *
 * The app used to hide all of its state inside `filesDir`, which Android deletes on
 * uninstall and which no file manager can reach. That is fine for machine state (the
 * native scratch files, caches) but wrong for anything the user is
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
     * The app directory the user can browse: `Documents/PawnMC`.
     *
     * Everything user-owned moved here: `Android/data/<pkg>/` is wiped by an
     * uninstall, while the public Documents tree is user data the system keeps, and
     * it is reachable from any file manager and a PC over USB. Requires the
     * all-files permission the app already asks for on the main screen; when it is
     * unavailable, callers fall back to the app-scoped external dir and then to
     * [privateRoot], so a restricted profile degrades to less-visible storage
     * rather than to a crash.
     */
    fun dataRoot(context: Context): File {
        documentsRoot(context)?.let { return it }
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return if (external != null) File(external, PUBLIC_DIR_NAME).ensureDir() else privateRoot(context)
    }

    /** True when [dataRoot] points at the public Documents tree. */
    fun isDataRootPublic(context: Context): Boolean = documentsRoot(context) != null

    /**
     * Default folder for the bundled starter workspace, `PawnMC/workspace`.
     *
     * Kept under the app directory rather than in `files/TMP` so the example script can be
     * opened, edited and copied by any other tool on the device, and so deleting it is a
     * user action instead of a side effect of clearing app data.
     */
    fun workspaceRoot(context: Context): File = File(dataRoot(context), "workspace").ensureDir()

    /**
     * One-time migration of the user tree from the old app-scoped location.
     *
     * `Android/data/<pkg>/files/PawnMC` is wiped on uninstall, so a user who upgrades
     * to the Documents-based layout loses nothing only if the old tree is copied over
     * before anything else writes to the new one. Runs once per process launch from
     * `MainActivity`; an existing migration marker makes it a no-op, and any copy
     * error is swallowed so a broken card slot never blocks the app.
     */
    fun migrateLegacyDataRoot(context: Context) {
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull() ?: return
        val legacy = File(external, PUBLIC_DIR_NAME)
        if (!legacy.isDirectory) return
        val target = dataRoot(context)
        if (target == legacy || !target.isDirectory) return
        val marker = File(target, ".migrated-from-android-data")
        if (marker.isFile) return

        runCatching {
            legacy.walkTopDown()
                .filter { it.isFile }
                .forEach { source ->
                    val destination = File(target, source.relativeTo(legacy).path)
                    if (!destination.isFile || destination.length() < source.length()) {
                        destination.parentFile?.mkdirs()
                        source.copyTo(destination, overwrite = true)
                    }
                }
            marker.writeText("migrated ${System.currentTimeMillis()}")
        }
    }

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
