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
 *  - `state`    : `pending` while the hint is still wanted in the editor,
 *                 `discarded` once the user tapped Discard on that line
 *
 * Ownership rule: a compile only ever touches `pending` hints. A `discarded`
 * record is kept as a tombstone, so a later run - even a clean one - never
 * re-injects a comment the user already threw away, and the editor never strips
 * a comment the user chose to keep. The comments belong to the user: Discard and
 * Save are the only things that remove them.
 *
 * The file lives at `Documents/PawnMC/compiler/injector_data.json` when shared storage
 * is mounted, and falls back to the app-private folder otherwise.
 */
object DiagnosticsStore {

    /** Hint state written to the JSON. */
    object State {
        /** Recorded by the last run that reported the line; hint should be shown. */
        const val PENDING = "pending"

        /** The user dismissed this hint; it is never printed on that line again. */
        const val DISCARDED = "discarded"
    }

    /** Records kept for one file; keyed by 1-based line. */
    data class Entry(
        val line: Int,
        val code: String,
        val kind: String,
        val message: String,
        val explainer: String,
        val at: Long,
        /** [State.PENDING] or [State.DISCARDED]. */
        val state: String = State.PENDING
    ) {
        /** True while the editor is still expected to show this hint. */
        val isPending: Boolean get() = state != State.DISCARDED
    }

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
                        at = row.optLong("at"),
                        // Older files have no state field: every record they carry was
                        // written to be shown, so `pending` is the honest default.
                        state = when (row.optString("state", State.PENDING)) {
                            State.DISCARDED -> State.DISCARDED
                            else -> State.PENDING
                        }
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
                        .put("state", entry.state)
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
     * Merges what the latest compiler run reported into the stored records.
     *
     * Only `pending` hints are replaced: a `discarded` tombstones survives every run
     * (clean or not) so a hint the user dismissed never comes back. When the run is
     * clean the pending records are dropped - the editor keeps whatever comment is
     * already in the buffer, because removing it is the user's call, not the
     * compiler's.
     */
    fun record(
        context: Context?,
        sourceFile: String,
        entries: List<Entry>
    ) {
        val file = fileFor(context)
        val store = load(file)
        val key = store.keyFor(sourceFile) ?: keyOf(sourceFile)
        val discarded = store[key]?.filterNot { it.isPending }.orEmpty()
        val pending = entries
            .filter { it.isPending }
            .sortedBy { it.line }
            .take(MAX_ENTRIES_PER_FILE)
        val merged = (pending + discarded)
            .distinctBy { it.line to it.state }
            .sortedBy { it.line }
            .take(MAX_ENTRIES_PER_FILE * 2)
        if (merged.isEmpty()) store.remove(key) else store[key] = merged.toMutableList()
        save(file, store)
    }

    /** Marks the hint on [sourceFile] at [line] as discarded; it is never shown again. */
    fun discardLine(context: Context?, sourceFile: String, line: Int) {
        mutate(context, sourceFile) { entries ->
            entries.map { entry ->
                if (entry.line == line) entry.copy(state = State.DISCARDED) else entry
            }
        }
    }

    /**
     * How many physical lines the injected comment on [line] occupies.
     *
     * Kept in memory only: the editor measures it when it injects or saves the hint
     * (a wrapped comment is several lines long) and Discard uses the same number to
     * remove exactly what was printed. One line is the correct default - an unwrapped
     * hint always sits on the line it explains.
     */
    fun setCommentSpan(sourceFile: String, line: Int, span: Int) {
        spans[Pair(keyOf(sourceFile), line)] = span.coerceAtLeast(1)
    }

    /** Comment span last reported for [line], or 1. */
    fun commentSpanFor(sourceFile: String, line: Int): Int =
        spans[Pair(keyOf(sourceFile), line)] ?: 1

    private val spans = java.util.concurrent.ConcurrentHashMap<Pair<String, Int>, Int>()

    /** Removes the entry of [sourceFile] at [line]; only used for retired tombstones. */
    fun dropLine(context: Context?, sourceFile: String, line: Int) {
        mutate(context, sourceFile) { entries -> entries.filterNot { it.line == line } }
    }

    /** Drops every stored entry for [sourceFile] (pending and discarded). */
    fun clear(context: Context?, sourceFile: String) {
        val file = fileFor(context)
        val store = load(file)
        val key = store.keyFor(sourceFile) ?: return
        if (store.remove(key) != null) save(file, store)
    }

    /** Every record stored for [sourceFile], or an empty list when there are none. */
    fun entriesFor(context: Context?, sourceFile: String): List<Entry> =
        load(fileFor(context)).matchesFor(sourceFile)

    /** Records the editor is still expected to print. */
    fun pendingEntriesFor(context: Context?, sourceFile: String): List<Entry> =
        entriesFor(context, sourceFile).filter { it.isPending }

    /** 1-based line numbers of [sourceFile] the last compile flagged, ascending. */
    fun damagedLines(context: Context?, sourceFile: String): List<Int> =
        pendingEntriesFor(context, sourceFile).map { it.line }.distinct()

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
