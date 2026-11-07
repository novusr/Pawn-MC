package com.pocketsamp.server.configuration

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.pocketsamp.server.engine_handler.ThirdRuntime
import java.io.File

object _DirectoryManager {
    private const val PREF_NAME = "pocketsamp_prefs"
    private const val KEY_CUSTOM_SERVER_DIR = "custom_server_directory"

    fun getDefaultServerDirectory(): File {
        val extStorage = Environment.getExternalStorageDirectory()
        return if (extStorage != null) {
            File(extStorage, "PocketSAMP")
        } else {
            File("/storage/emulated/0/PocketSAMP")
        }
    }

    /** `Documents/PawnMC`, the app's own folder on shared storage. */
    fun getDocumentsPawnMcDir(): File {
        val extStorage = Environment.getExternalStorageDirectory()
            ?: return File("/storage/emulated/0/Documents/PawnMC")
        return File(File(extStorage, Environment.DIRECTORY_DOCUMENTS), "PawnMC")
    }

    fun getServerDirectory(context: Context): File {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_SERVER_DIR, null)
        if (!customPath.isNullOrBlank()) {
            val custom = File(customPath)
            // A stale custom path (folder deleted, SD card pulled) costs nothing
            // but a probe; drop it from both stores so the next launch goes
            // straight to a valid folder instead of failing silently.
            if (custom.isDirectory) return custom
            prefs.edit().remove(KEY_CUSTOM_SERVER_DIR).apply()
            com.rvdjv.pawnmc.apps_data.data_config.PathStore
                .writePaths(getDocumentsPawnMcDir(), "filemanager", emptyList())
        }
        // Fall back to the reusable JSON mirror first (it survives an uninstall),
        // then to the extracted r_samp template folder.
        runCatching {
            com.rvdjv.pawnmc.apps_data.data_config.PathStore
                .readPaths(getDocumentsPawnMcDir(), "filemanager")
        }.getOrNull()?.let { stored ->
            val alive = stored.filter { File(it).isDirectory }
            if (alive.size != stored.size) {
                // The mirror only keeps paths that still open; a dead entry costs
                // nothing but a failed File(...) probe, so prune it on sight.
                com.rvdjv.pawnmc.apps_data.data_config.PathStore
                    .writePaths(getDocumentsPawnMcDir(), "filemanager", alive)
            }
            alive.firstOrNull()?.let { return File(it) }
        }
        return getTemplateDirectory()
    }

    /**
     * `Documents/PawnMC/storage/template/`, where the extracted r_samp.tar.gz
     * base project lives. When no folder has been picked yet the File Manager
     * lands here instead of creating an empty default elsewhere.
     */
    fun getTemplateDirectory(): File {
        val dir = File(File(getDocumentsPawnMcDir(), "storage"), "template")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun hasCustomServerDirectory(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val customPath = prefs.getString(KEY_CUSTOM_SERVER_DIR, null)
        return !customPath.isNullOrBlank() && File(customPath).let { it.exists() && it.isDirectory }
    }

    fun setServerDirectory(context: Context, directory: File): Boolean {
        if (!hasStoragePermission(context) || !directory.exists() || !directory.isDirectory || !directory.canRead()) return false
        val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return false
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val stored = prefs.edit().putString(KEY_CUSTOM_SERVER_DIR, canonical.absolutePath).commit()
        // Mirror the folder into the reusable JSON store so a reinstall keeps the
        // same File Manager target; dead paths are dropped from the mirror.
        com.rvdjv.pawnmc.apps_data.data_config.PathStore
            .writePaths(getDocumentsPawnMcDir(), "filemanager", listOf(canonical.absolutePath))
        return stored
    }

    fun resetToDefaultDirectory(context: Context): File {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CUSTOM_SERVER_DIR).commit()
        return getTemplateDirectory()
    }

    fun isConfigPresent(context: Context): Boolean {
        val serverDir = getServerDirectory(context)
        return File(serverDir, "server.cfg").exists()
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun getManageStorageIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
            } catch (e: Exception) {
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    /** Converts a DocumentTree Uri from OpenDocumentTree() into a real File path. */
    fun parseTreeUriToFile(uri: Uri): File? {
        val docId = try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (e: Exception) {
            null
        } ?: return null

        val parts = docId.split(":")
        if (parts.isEmpty()) return null

        val type = parts[0]
        val subPath = docId.substringAfter(':', "")
        if (subPath.split('/').any { it == ".." || it == "." }) return null

        if (type.equals("primary", ignoreCase = true) &&
            (subPath.isBlank() || subPath == "Download" || subPath == "Downloads")
        ) return null
        val resolved = if ("primary".equals(type, ignoreCase = true)) {
            val basePath = Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isNotEmpty()) File(basePath, subPath) else File(basePath)
        } else {
            /*
             * Removable storage / SD Card: /storage/XXXX-XXXX/subPath
             */
            val extPath = "/storage/$type"
            if (subPath.isNotEmpty()) File(extPath, subPath) else File(extPath)
        }
        return runCatching { resolved.canonicalFile }.getOrNull()?.takeIf { it.isDirectory && it.canRead() }
    }
}
