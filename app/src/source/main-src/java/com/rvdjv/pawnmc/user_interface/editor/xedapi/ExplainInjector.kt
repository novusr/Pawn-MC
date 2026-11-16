package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import com.rvdjv.pawnmc.apps_data.data_compiler.DiagnosticsStore
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Injects the explainer hints recorded by the compiler run into the open editor.
 *
 * The compiler layer writes `Documents/PawnMC/compiler/injector_data.json` with the
 * file, the damaged line, the localized `[cause]` text and a `state`; this reader walks
 * that JSON and:
 *
 *  1. Appends a trailing `// [cause]: ...` comment to every `pending` line the compiler
 *     flagged, when the line is still present in the editor buffer. This runs right after
 *     the compile - the user sees the hint on the broken line while still looking at it,
 *     without leaving the editor.
 *  2. Leaves a comment that carries no pending record alone. A clean compile, a new log or
 *     a JSON that no longer lists the line never removes it: the comment belongs to the
 *     user now, and only [discard] (the Discard action) or a manual save takes it away.
 *  3. Never prints a comment on a line whose record was discarded. The tombstone stays in
 *     the JSON, so the hint cannot come back on the next run either.
 *
 * A plain comment was chosen over a shadow/gutter overlay on purpose: it needs no
 * renderer hooks and survives every theme.
 */
internal object ExplainInjector {

    /** How often one cycle waits before re-reading the JSON, in milliseconds. */
    const val CYCLE_INTERVAL_MS: Long = 6L * 60L * 1000L

    /** Matches a previously injected hint at the end of a line, / or // style. */
    private val HINT_TAIL = Regex("""\s*(?://|/\*)\s*\[[^\]]*]\s*:.*$""")

    /** Where an injected hint starts on a line. */
    private val HINT_START = Regex("""//\s*\[[^\]]*]\s*:""")

    /** True when the line already carries an injected hint. */
    internal fun hasHint(lineText: String): Boolean = HINT_TAIL.containsMatchIn(lineText)

    /** Strips any injected hint from [lineText]. */
    internal fun stripHint(lineText: String): String =
        lineText.replace(HINT_TAIL, "").trimEnd()

    private fun hintComment(explainer: String): String = "// $explainer"

    /**
     * Removes every printed hint whose record was discarded.
     *
     * Runs before each injection pass so a hint the user dismissed stays gone even when
     * the buffer is reloaded from disk or a stale copy of it is restored.
     */
    suspend fun stripDiscarded(editor: CodeEditor, filePath: String, context: Context) {
        val discarded = withContext(Dispatchers.IO) {
            DiagnosticsStore.entriesFor(context, filePath).filterNot { it.isPending }
        }
        if (discarded.isEmpty()) return
        withContext(Dispatchers.Main) {
            val document = editor.text
            var changed = false
            for (entry in discarded) {
                val index = entry.line - 1
                if (index !in 0 until document.lineCount) continue
                val text = document.getLine(index).toString()
                if (!HINT_START.containsMatchIn(text)) continue
                document.delete(index, stripHint(text).length, index, text.length)
                changed = true
            }
            if (changed) editor.invalidate()
        }
    }

    /**
     * One pass over the editor: reads the JSON off the IO dispatcher and appends the
     * pending hints that are not printed yet.
     *
     * Nothing is ever deleted here. Lines whose record disappeared simply keep the
     * comment they already have.
     */
    suspend fun apply(editor: CodeEditor, filePath: String, context: Context) {
        val entries = withContext(Dispatchers.IO) {
            DiagnosticsStore.pendingEntriesFor(context, filePath)
        }
        if (entries.isEmpty()) return
        withContext(Dispatchers.Main) {
            val document = editor.text
            var changed = false
            for (entry in entries) {
                val baseLine = anchorFor(document, entry) ?: continue
                val original = document.getLine(baseLine).toString()
                if (HINT_START.containsMatchIn(original)) continue
                if (original.isBlank()) continue
                document.insert(baseLine, original.length, " ${hintComment(entry.explainer)}")
                changed = true
            }
            if (changed) editor.invalidate()
        }
    }

    /**
     * Code line the hint of [entry] belongs to, or `null` when the line is gone.
     *
     * Earlier hints of the same file shift every following line downwards, and the JSON
     * keeps the line numbers the compiler printed, so the record index is only a starting
     * point. Walking the buffers above it and counting the hint comments that were already
     * printed there recovers the real offset: the hint then lands on the code line the
     * record was written for, or on nobody when that line already carries it.
     */
    private fun anchorFor(
        document: io.github.rosemoe.sora.text.Content,
        entry: DiagnosticsStore.Entry
    ): Int? {
        val target = entry.line - 1
        if (target !in 0 until document.lineCount) return null

        var offset = 0
        var probe = 0
        val limit = target.coerceAtMost(document.lineCount) - 1
        while (probe <= limit) {
            val text = document.getLine(probe).toString()
            if (HINT_START.containsMatchIn(text)) {
                when {
                    // A hint printed on its own line: it pushes the code below it down.
                    stripHint(text).isBlank() -> offset++
                    // A code line that already carries its hint: nothing left to print.
                    probe - offset >= target -> return null
                }
            }
            probe++
        }
        val resolved = (target + offset).coerceAtMost(document.lineCount - 1)
        val text = document.getLine(resolved).toString()
        if (HINT_START.containsMatchIn(text)) return null
        return resolved
    }

    /**
     * Discard action for the line under the cursor: removes the printed hint (the wrapped
     * continuation lines included), tombstones its record so no later run prints it again,
     * and drops the hint from the saved file.
     *
     * Returns true when a hint was actually removed.
     */
    suspend fun discard(
        editor: CodeEditor,
        filePath: String,
        context: Context,
        lineIndex: Int
    ): Boolean {
        val recordIndex = discardAt(editor, lineIndex) ?: return false
        editor.invalidate()

        // The hint lines that were removed are no longer in the buffer, so the record is
        // matched by distance to the anchor line instead of by text.
        withContext(Dispatchers.IO) {
            val entries = DiagnosticsStore.entriesFor(context, filePath)
            val pending = entries.filter { it.isPending }
            val closest = pending.minByOrNull { kotlin.math.abs(it.line - recordIndex) }
            if (closest != null) DiagnosticsStore.discardLine(context, filePath, closest.line)
            // Discard also reaches the saved file: after a reopen the comment must not be
            // printed back from disk.
            stripHintsFromDisk(filePath)
        }
        return true
    }

    /**
     * Removes the hint at [lineIndex] from the buffer and returns the 1-based number of
     * the line the hint was attached to, or `null` when there was no hint to remove.
     *
     * Runs on the caller's (main) thread: it touches the editor's document directly, so
     * wrapping it in `withContext(Dispatchers.Main)` would save nothing and only add the
     * nullable result of a suspended block to reason about.
     */
    private fun discardAt(editor: CodeEditor, lineIndex: Int): Int? {
        val document = editor.text
        if (lineIndex !in 0 until document.lineCount) return null
        // The cursor may sit on the code line, on the hint itself or on a wrapped
        // continuation of it: walk up to the line that starts the hint.
        var anchor = lineIndex
        while (anchor > 0 && !HINT_START.containsMatchIn(document.getLine(anchor).toString())) {
            anchor--
        }
        val lineText = document.getLine(anchor).toString()
        val match = HINT_START.find(lineText) ?: return null
        document.delete(
            anchor,
            match.range.first.coerceAtMost(lineText.trimEnd().length),
            anchor,
            lineText.length
        )
        // A wrapped hint continues on the following lines as plain `// ...` lines; they
        // belong to the same comment and go with it.
        while (anchor + 1 < document.lineCount) {
            val text = document.getLine(anchor + 1).toString()
            if (!text.trimStart().startsWith("//")) break
            document.delete(anchor + 1, 0, anchor + 1, text.length)
            document.delete(anchor, document.getLine(anchor).length, anchor + 1, 0)
        }
        return anchor + 1
    }

    /** Rewrites the file on disk without any injected hint comment. */
    suspend fun saveWithoutHints(filePath: String) = withContext(Dispatchers.IO) {
        stripHintsFromDisk(filePath)
    }

    private fun stripHintsFromDisk(filePath: String) {
        val file = File(filePath)
        if (!file.isFile || !file.canWrite()) return
        val original = runCatching { file.readText() }.getOrNull() ?: return
        val text = original.replace("\r\n", "\n")
        val cleaned = text.split('\n').joinToString("\n") { line -> stripHint(line) }
        if (cleaned != text) {
            // Keep a CRLF file as CRLF when that is what it used.
            val output = if (original.contains("\r\n")) cleaned.replace("\n", "\r\n") else cleaned
            runCatching { file.writeText(output) }
        }
    }
}
