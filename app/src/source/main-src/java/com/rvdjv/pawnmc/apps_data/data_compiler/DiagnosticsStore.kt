package com.rvdjv.pawnmc.apps_data.data_compiler

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent explainer log for the xed editor.
 *
 * Every compiler run that produces `error`/`warning`/`fatal` lines records a
 * diagnosis here, keyed by file path (a file may have several lines, so the file
 * is a key, never a value). Each line entry keeps:
 *
 *  - `line`     : 1-based source line the compiler reported
 *  - `code`     : the message number ("150"), zero-padded form kept too
 *  - `marker`   : which record type ("error" / "warning" / "fatal")
 *  - `message`  : the raw compiler text of that line
 *  - `explainer`: the localized `[explain]` text rendered for that message,
 *                 including the `%s` fill from the quoted symbol
 *  - `at`       : wall-clock stamp of the last compile that produced it
 *
 * The file lives at `Documents/PawnMC/compiler/injector_data.json` when shared storage
 * is mounted, and falls back to the app-private folder otherwise.
 */
object DiagnosticsStore {

    /** Records kept for one file; keyed by 1-based line. */
    data class Entry(
        val line: Int,
        val code: String,
        val kind: String,
        val message: String,
        val explainer: String,
        val at: Long
    )

    private const val FILE_NAME = "injector_data.json"

    /** Hard limit of [Entry]s per file, errors and warnings share the budget. */
    const val MAX_ENTRIES_PER_FILE: Int = 6

    private val lock = Any()

    /** Resolves `Documents/PawnMC/compiler/injector_data.json` or the private fallback. */
    fun fileFor(context: Context?): File {
        val root = context?.let { candidate ->
            runCatching {
                com.rvdjv.pawnmc.apps_data.data_config.AppStorage.dataRoot(candidate)
            }.getOrNull()
        } ?: context?.getExternalFilesDir(null) ?: context?.filesDir ?: File(".")
        return File(File(root, "compiler"), FILE_NAME)
    }

    /** Normalizes a path to the key form stored in the JSON. */
    internal fun keyOf(path: String): String = File(path).absolutePath.replace('\\', '/')

    /** Reads the whole store: absolute file path -> line entries, ordered by line. */
    fun load(file: File): MutableMap<String, MutableList<Entry>> {
        val result = linkedMapOf<String, MutableList<Entry>>()
        val json = runCatching {
            if (!file.isFile) return result
            JSONArray(file.readText())
        }.getOrNull() ?: return result
        for (i in 0 until json.length()) {
            val fileEntry = json.optJSONObject(i) ?: continue
            val path = fileEntry.optString("file")
            if (path.isBlank()) continue
            val lines = result.getOrPut(path) { mutableListOf() }
            val rows = fileEntry.optJSONArray("diagnostics") ?: continue
            for (j in 0 until rows.length()) {
                val row = rows.optJSONObject(j) ?: continue
                val line = row.optInt("line")
                if (line <= 0) continue
                lines.add(
                    Entry(
                        line = line,
                        code = row.optString("code"),
                        kind = row.optString("kind", "error"),
                        message = row.optString("message"),
                        explainer = row.optString("explainer"),
                        at = row.optLong("at")
                    )
                )
            }
            lines.sortBy { it.line }
        }
        return result
    }

    /** Writes [store] back to [file], one object per file key. */
    fun save(file: File, store: Map<String, List<Entry>>) {
        val json = JSONArray()
        for ((path, entries) in store) {
            if (entries.isEmpty()) continue
            val fileEntry = JSONObject()
            fileEntry.put("file", path)
            val rows = JSONArray()
            for (entry in entries.sortedBy { it.line }.take(MAX_ENTRIES_PER_FILE)) {
                rows.put(
                    JSONObject()
                        .put("line", entry.line)
                        .put("code", entry.code)
                        .put("kind", entry.kind)
                        .put("message", entry.message)
                        .put("explainer", entry.explainer)
                        .put("at", entry.at)
                )
            }
            fileEntry.put("diagnostics", rows)
            json.put(fileEntry)
        }
        synchronized(lock) {
            file.parentFile?.let { parent ->
                if (!parent.isDirectory && !parent.mkdirs()) return
            }
            runCatching { file.writeText(json.toString(2)) }
        }
    }

    /**
     * Replaces the entries of [sourceFile] with what the latest compiler run
     * reported. A clean run (no diagnostics) wipes the file key entirely, so the
     * next editor cycle finds nothing to inject.
     */
    fun record(
        context: Context?,
        sourceFile: String,
        entries: List<Entry>
    ) {
        val file = fileFor(context)
        val store = load(file)
        val key = keyOf(sourceFile)
        val trimmed = entries
            .sortedBy { it.line }
            .take(MAX_ENTRIES_PER_FILE)
        if (trimmed.isEmpty()) store.remove(key) else store[key] = trimmed.toMutableList()
        save(file, store)
    }

    /** Removes the entry of [sourceFile] at [line], used when the line vanished. */
    fun dropLine(context: Context?, sourceFile: String, line: Int) {
        val file = fileFor(context)
        val store = load(file)
        val normalized = keyOf(sourceFile)
        // Resolve the same way [entriesFor] does, so a relative compiler key is
        // retired even though the editor passes the absolute path.
        val entries = store.matchesFor(sourceFile)
        if (entries.isEmpty()) return
        val key = store.keys.first { stored ->
            stored.equals(normalized, ignoreCase = true) ||
                normalized.lowercase().endsWith("/" + stored.lowercase())
        }
        val next = entries.filterNot { it.line == line }
        if (next.isEmpty()) store.remove(key) else store[key] = next.toMutableList()
        save(file, store)
    }

    /** Drops every stored entry for [sourceFile]; called after a clean compile. */
    fun clear(context: Context?, sourceFile: String) {
        val file = fileFor(context)
        val store = load(file)
        if (store.remove(keyOf(sourceFile)) != null) save(file, store)
    }

    /** Entries stored for [sourceFile], or an empty list when there are none. */
    fun entriesFor(context: Context?, sourceFile: String): List<Entry> =
        load(fileFor(context)).matchesFor(sourceFile)

    /** 1-based line numbers of [sourceFile] the last compile flagged, ascending. */
    fun damagedLines(context: Context?, sourceFile: String): List<Int> =
        entriesFor(context, sourceFile).map { it.line }.distinct()

    /**
     * Compiler paths may be relative (`gamemodes/x.pwn`) while the editor holds
     * the absolute one; a key matches when it equals the path or ends with the
     * relative tail of it.
     */
    private fun Map<String, MutableList<Entry>>.matchesFor(sourceFile: String): List<Entry> {
        val normalized = keyOf(sourceFile)
        val direct = this[normalized]
        if (direct != null) return direct.sortedBy { it.line }
        val lower = normalized.lowercase()
        for ((key, entries) in this) {
            if (key.equals(normalized, ignoreCase = true)) return entries.sortedBy { it.line }
            if (lower.endsWith("/" + key.lowercase())) return entries.sortedBy { it.line }
        }
        return emptyList()
    }
}
