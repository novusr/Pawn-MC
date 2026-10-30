package com.rvdjv.pawnmc.data.config

import android.content.Context
import java.io.File

/**
 * Every path PawnMC keeps for the user, in one place.
 */
internal object AppStorage {

    /** Folder name of the app directory on shared storage. */
    const val PUBLIC_DIR_NAME: String = "PawnMC"

    /** Test seam for the public Documents root; `null` means the real Environment. */
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var documentsRootOverride: (() -> File?)? = null

    /** `Documents/PawnMC` on shared storage, the one location that survives an uninstall. */
    fun documentsRoot(context: Context): File? {
        val documents = documentsRootOverride?.invoke()
            ?: if (hasPublicWriteAccess()) {
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOCUMENTS
                )
            } else {
                null
            } ?: return null
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

    /** `files/.pawnmc`, the hidden app-private root. */
    fun privateRoot(context: Context): File =
        File(context.filesDir, ".pawnmc").ensureDir()

    /** The app directory the user can browse: `Documents/PawnMC`. */
    fun dataRoot(context: Context): File {
        documentsRoot(context)?.let { return it }
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        return if (external != null) File(external, PUBLIC_DIR_NAME).ensureDir() else privateRoot(context)
    }

    /** True when [dataRoot] points at the public Documents tree. */
    fun isDataRootPublic(context: Context): Boolean = documentsRoot(context) != null

    /** Default folder for the bundled starter workspace, `PawnMC/workspace`. */
    fun workspaceRoot(context: Context): File = File(dataRoot(context), "workspace").ensureDir()

    /** One-time migration of the user tree from the old app-scoped location. */
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

    /** Folder holding the user-visible settings mirror, `PawnMC/config`. */
    fun configRoot(context: Context): File {
        documentsRoot(context)?.let { return File(it, "config").ensureDir() }
        val external = runCatching { context.getExternalFilesDir(null) }.getOrNull()
        val base = if (external != null) File(external, PUBLIC_DIR_NAME) else privateRoot(context)
        return File(base, "config").ensureDir()
    }

    /** Folder holding the unified compile-log SQLite database, `PawnMC/logs`. */
    fun logsRoot(context: Context): File = File(dataRoot(context), "logs").ensureDir()

    private fun File.ensureDir(): File = apply { if (!exists()) mkdirs() }
}
