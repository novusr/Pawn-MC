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
 *  - `explainer`: the localized `[cause]` text rendered for that message,
 *                 including the `%s` fill from the quoted symbol
 *  - `at`       : wall-clock stamp of the last compile that produced it
 *
 * There is no per-line state any more. The whole store for a file is one of
 * two things:
 *
 *  - *present*  : the last compile flagged lines in it, the editor prints the
 *                 hints that are not on the buffer yet.
 *  - *cleared*  : the user tapped Discard once, so the file's record is removed
 *                 entirely. Nothing is re-injected into that file until the
 *                 next compile writes a fresh record - the user's "no" lasts
 *                 exactly until they ask the compiler for new hints.
 *
 * Saving the file is the other reset: whatever hint text was already injected
 * lives in the source now, and the next compile re-reads the saved code and
 * writes a fresh JSON from scratch.
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

    /**
     * Hard limit of [Entry]s per file, errors and warnings share the budget. Raised
     * from 6 to 11: shorter scripts can flag more lines than the old budget covered,
     * and the editor's injection pass is cheap enough to print eleven hints.
     */
    const val MAX_ENTRIES_PER_FILE: Int = 11

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
            for (entry in entries.sortedBy { it.line }) {
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
     * Replaces whatever the previous run stored for [sourceFile] with [entries].
     *
     * A compile never merges with an older record: the JSON is the record of the
     * *latest* run, and a clean run stores nothing. No tombstone is kept - once
     * the user discarded the hints, the next compile that flags lines again
     * writes a fresh record and the hints are printed once more.
     */
    fun record(
        context: Context?,
        sourceFile: String,
        entries: List<Entry>
    ) {
        val file = fileFor(context)
        val store = load(file)
        val key = store.keyFor(sourceFile) ?: keyOf(sourceFile)
        val fresh = entries
            .sortedBy { it.line }
            .take(MAX_ENTRIES_PER_FILE)
        if (fresh.isEmpty()) store.remove(key) else store[key] = fresh.toMutableList()
        save(file, store)
    }

    /** Removes the entry of [sourceFile] at [line]. */
    fun dropLine(context: Context?, sourceFile: String, line: Int) {
        mutate(context, sourceFile) { entries -> entries.filterNot { it.line == line } }
    }

    /** Drops every stored entry for [sourceFile]; this is the whole Discard action. */
    fun clear(context: Context?, sourceFile: String) {
        val file = fileFor(context)
        val store = load(file)
        val key = store.keyFor(sourceFile) ?: return
        if (store.remove(key) != null) save(file, store)
    }

    /** Every record stored for [sourceFile], or an empty list when there are none. */
    fun entriesFor(context: Context?, sourceFile: String): List<Entry> =
        load(fileFor(context)).matchesFor(sourceFile)

    /** 1-based line numbers of [sourceFile] the last compile flagged, ascending. */
    fun damagedLines(context: Context?, sourceFile: String): List<Int> =
        entriesFor(context, sourceFile).map { it.line }.distinct()

    /** Every file path that currently has damaged (error/warning/fatal) lines stored. */
    fun damagedFiles(context: Context?): List<String> =
        load(fileFor(context)).keys.toList()

    /** Applies [transform] to the records of [sourceFile] and writes the store back. */
    private fun mutate(
        context: Context?,
        sourceFile: String,
        transform: (List<Entry>) -> List<Entry>
    ) {
        val file = fileFor(context)
        val store = load(file)
        val key = store.keyFor(sourceFile) ?: keyOf(sourceFile)
        val current = store[key] ?: return
        val next = transform(current)
        if (next.isEmpty()) store.remove(key) else store[key] = next.sortedBy { it.line }.toMutableList()
        save(file, store)
    }

    /** Stored key that belongs to [sourceFile], or null when the file has no record yet. */
    private fun Map<String, MutableList<Entry>>.keyFor(sourceFile: String): String? {
        val normalized = keyOf(sourceFile)
        if (containsKey(normalized)) return normalized
        val lower = normalized.lowercase()
        return entries.firstOrNull { (key, _) ->
            key.equals(normalized, ignoreCase = true) || lower.endsWith("/" + key.lowercase())
        }?.key
    }

    /**
     * Compiler paths may be relative (`gamemodes/x.pwn`) while the editor holds
     * the absolute one; a key matches when it equals the path or ends with the
     * relative tail of it.
     */
    private fun Map<String, MutableList<Entry>>.matchesFor(sourceFile: String): List<Entry> {
        val key = keyFor(sourceFile) ?: return emptyList()
        return this[key].orEmpty().sortedBy { it.line }
    }
}
