package com.pocketsamp.server.configuration

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reusable path persistence for the PocketSAMP File Manager.
 *
 * The selected server folder is mirrored into
 * `Documents/PawnMC/filemanager/filemanager.json` so the choice survives an
 * uninstall + reinstall of the app itself. Paths that no longer exist on disk
 * are dropped from the file on every read, which keeps the restore pass from
 * burning IO on dead entries.
 *
 * Self-contained on purpose: the PocketSAMP module does not link PawnMC's app
 * code, so this mirrors the same file format without referencing it.
 */
internal object _PathStore {

    /** Reads the saved string list from [surfaceFile]'s parent directory. */
    fun readPaths(baseDir: File, surface: String): List<String> {
        val file = File(File(baseDir, surface), "$surface.json")
        if (!file.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(file.readText())
            root.optJSONArray("paths")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val value = array.optString(index, "").trim()
                    value.takeIf { it.isNotEmpty() }
                }
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Writes [paths] under [baseDir]; paths that no longer exist are dropped. */
    fun writePaths(baseDir: File, surface: String, paths: List<String>): List<String> {
        val survivors = paths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { runCatching { File(it).exists() }.getOrDefault(false) }

        val file = File(File(baseDir, surface), "$surface.json")
        runCatching {
            file.parentFile?.mkdirs()
            val root = JSONObject()
            root.put("schema", 1)
            root.put("updated", System.currentTimeMillis())
            val array = JSONArray()
            survivors.forEach { array.put(it) }
            root.put("paths", array)
            file.writeText(root.toString(2))
        }
        return survivors
    }
}
