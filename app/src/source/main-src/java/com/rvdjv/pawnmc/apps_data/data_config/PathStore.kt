package com.rvdjv.pawnmc.apps_data.data_config

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Reusable path persistence for PawnMC's two "remembered folder" surfaces.
 *
 * A single JSON file per surface lives under `Documents/PawnMC/` so the paths
 * survive an uninstall + reinstall of the app itself:
 *
 *  - `workspace/workspace.json`   — Xed Editor workspace folders and their tabs
 *  - `filemanager/filemanager.json` — PocketSAMP File Manager / server folder
 *
 * When a stored path no longer exists on disk it is silently dropped from the
 * file, which keeps the restore pass from burning IO on dead entries.
 */
object PathStore {

    /** Directory of the JSON file for [surface], created on write. */
    fun fileFor(context: Context, surface: String): File =
        File(File(AppStorage.dataRoot(context), surface), "$surface.json")

    /** Reads the saved string list for [surface]. */
    fun readPaths(context: Context, surface: String): List<String> {
        val file = fileFor(context, surface)
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

    /** Writes [paths] for [surface]; paths that no longer exist are dropped. */
    fun writePaths(context: Context, surface: String, paths: List<String>): List<String> {
        val survivors = paths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { File(it).exists() }

        val file = fileFor(context, surface)
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

    /** Restores [paths] but keeps only the ones that still exist on disk. */
    fun restoreExisting(paths: List<String>): List<String> =
        paths.filter { runCatching { File(it).exists() }.getOrDefault(false) }

    /**
     * Restores [paths], re-creating a recorded folder that vanished on disk.
     *
     * A remembered workspace folder is a user decision: if the storage hiccupped
     * or the app was reinstalled, silently pruning the entry made the workspace
     * disappear from the panel even though the mirror file still named it. The
     * folder is created again (empty when it cannot be recovered) so the restore
     * pass finds it and the panel repopulates as recorded.
     */
    fun restoreRecreating(paths: List<String>): List<String> =
        paths.map { path -> path.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .onEach { path ->
                val folder = File(path)
                if (!folder.isDirectory) {
                    runCatching { folder.mkdirs() }
                }
            }
            .filter { File(it).isDirectory }
}
