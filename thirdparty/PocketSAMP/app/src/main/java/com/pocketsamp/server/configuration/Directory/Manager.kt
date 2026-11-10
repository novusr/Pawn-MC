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

    /**
     * `Documents/Pawn-MC/PocketSAMP`, the app's own folder on shared storage.
     *
     * Nested under a named product folder so nothing else litters the user's
     * Documents root; the old `Documents/PawnMC` spot migrates automatically.
     */
    fun getDocumentsPawnMcDir(): File {
        val legacy = legacyDocumentsPawnMcDir()
        val target = documentsPawnMcDir()
        if (legacy.isDirectory && !target.isDirectory) {
            runCatching {
                if (!legacy.renameTo(target)) {
                    legacy.copyRecursively(target, overwrite = true)
                    legacy.deleteRecursively()
                }
            }
        }
        return target
    }

    /** The current documents location, `Documents/Pawn-MC/PocketSAMP`. */
    fun documentsPawnMcDir(): File {
        val extStorage = Environment.getExternalStorageDirectory()
            ?: return File("/storage/emulated/0/Documents/Pawn-MC/PocketSAMP")
        return File(File(extStorage, Environment.DIRECTORY_DOCUMENTS), "Pawn-MC/PocketSAMP")
    }

    /** The pre-rename location, kept only for the one-time migration above. */
    private fun legacyDocumentsPawnMcDir(): File {
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
            _PathStore.writePaths(getDocumentsPawnMcDir(), "filemanager", emptyList())
        }
        // Fall back to the reusable JSON mirror first (it survives an uninstall),
        // then to the extracted r_samp template folder.
        val stored = _PathStore.readPaths(getDocumentsPawnMcDir(), "filemanager")
        if (stored.isNotEmpty()) {
            // The mirror only keeps paths that still open; a dead entry costs
            // nothing but a failed File(...) probe, so prune it on sight.
            val alive = stored.filter { path -> runCatching { File(path).isDirectory }.getOrDefault(false) }
            if (alive.size != stored.size) {
                _PathStore.writePaths(getDocumentsPawnMcDir(), "filemanager", alive)
            }
            alive.firstOrNull()?.let { return File(it) }
        }
        // No folder has ever been picked: activate the template folder as the
        // working directory instead of returning it as an anonymous fallback.
        // Registering it here means the File Manager opens with the template
        // already active (browsable, writable, launchable) and the picker only
        // appears when the user wants a different folder.
        val template = getTemplateDirectory()
        if (template.isDirectory && template.canRead()) {
            runCatching { setServerDirectory(context, template) }
        }
        return template
    }

    /**
     * `Downloads/.Pawn-MC/template/`, where the extracted r_samp.tar.gz base
     * project lives.
     *
     * Moved out of `Documents/PawnMC/storage/template/`: several system folder
     * pickers refuse to open (or return an empty listing for) app-created folders
     * under Documents, which left the File Manager staring at an empty view even
     * though the files were there. Downloads is browsable by every picker, and the
     * dot-prefix keeps the folder out of the way of the user's own files.
     */
    fun getTemplateDirectory(): File {
        val legacy = legacyTemplateDirectory()
        val target = templateDirectory()
        // One-time move of an old Documents extraction so nothing is left behind.
        if (legacy.isDirectory && !target.isDirectory) {
            runCatching {
                if (!legacy.renameTo(target)) {
                    legacy.copyRecursively(target, overwrite = true)
                    legacy.deleteRecursively()
                }
            }
        }
        if (!target.exists()) target.mkdirs()
        return target
    }

    /** The current template location, `Downloads/.Pawn-MC/template`. */
    fun templateDirectory(): File {
        val extStorage = Environment.getExternalStorageDirectory()
            ?: return File("/storage/emulated/0/Download/.Pawn-MC/template")
        return File(File(extStorage, Environment.DIRECTORY_DOWNLOADS), ".Pawn-MC/template")
    }

    /** The pre-move template location, kept only for the migration above. */
    private fun legacyTemplateDirectory(): File =
        File(File(getDocumentsPawnMcDir(), "storage"), "template")

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
        _PathStore.writePaths(getDocumentsPawnMcDir(), "filemanager", listOf(canonical.absolutePath))
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

        /*
         * Every primary-storage location is accepted, including the storage root
         * itself and Downloads. The old code returned null for `primary:` with a
         * blank/Download sub-path, which is exactly what made "storage/ to
         * Downloads" picks flicker between "invalid path" and success depending
         * on which tree entry the user happened to tap.
         */
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
