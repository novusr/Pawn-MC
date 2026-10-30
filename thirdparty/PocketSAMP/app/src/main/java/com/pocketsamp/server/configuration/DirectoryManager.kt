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

object DirectoryManager {
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
            val dir = File(customPath)
            if (dir.exists() && dir.isDirectory) {
                return dir
            }
        }
        val defaultDir = getDefaultServerDirectory()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun setServerDirectory(context: Context, directory: File): Boolean {
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.edit().putString(KEY_CUSTOM_SERVER_DIR, directory.absolutePath).commit()
    }

    fun resetToDefaultDirectory(context: Context): File {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CUSTOM_SERVER_DIR).commit()
        val defaultDir = getDefaultServerDirectory()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    fun isConfigPresent(context: Context): Boolean {
        val serverDir = getServerDirectory(context)
        return File(serverDir, "server.cfg").exists()
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
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
        val subPath = if (parts.size > 1) parts[1] else ""

        return if ("primary".equals(type, ignoreCase = true)) {
            val basePath = Environment.getExternalStorageDirectory().absolutePath
            if (subPath.isNotEmpty()) File(basePath, subPath) else File(basePath)
        } else {
            /*
             * Removable storage / SD Card: /storage/XXXX-XXXX/subPath
             */
            val extPath = "/storage/$type"
            if (subPath.isNotEmpty()) File(extPath, subPath) else File(extPath)
        }
    }
}
