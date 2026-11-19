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
 * file, the damaged line and the localized `[cause]` text; this reader walks that
 * JSON and:
 *
 *  1. Appends a trailing `//// [cause]: ...` highlight comment to every line the last
 *     compile flagged, when the line is still present in the editor buffer. A line that
 *     already carries a hint is skipped, never overwritten - that keeps repeated
 *     passes and reload cycles free of doubled comments.
 *  2. Leaves a comment that carries no record alone. A clean compile, a new log or
 *     a JSON that no longer lists the line never removes it: the comment belongs to
 *     the user now, and only [discard] (the Discard action) or a manual save takes
 *     it away.
 *
 * There is no per-line discard state. [discard] removes every record the store
 * keeps for the file at once, so the very next injection pass finds nothing to
 * print and the editor settles quietly. The next compile writes a fresh JSON and
 * fresh hints appear again.
 *
 * A four-slash comment was chosen over a shadow/gutter overlay on purpose: it needs no
 * renderer hooks, survives every theme and the analyzer paints `////` in its own bright
 * highlight colour, so an injected hint reads differently from an ordinary `//` comment
 * the user wrote.
 */
internal object ExplainInjector {

    /** How often one cycle waits before re-reading the JSON, in milliseconds. */
    const val CYCLE_INTERVAL_MS: Long = 6L * 60L * 1000L

    /** Matches a previously injected hint at the end of a line, // or //// style. */
    private val HINT_TAIL = Regex("""\s*////?\s*\[[^\]]*]\s*:.*$""")

    /** Where an injected hint starts on a line. */
    private val HINT_START = Regex("""////\s*\[[^\]]*]\s*:""")

    /** A wrapped continuation line of a hint comment (`//// ...`, possibly indented). */
    private val HINT_CONTINUATION = Regex("""^\s*////""")

    /** True when the line already carries an injected hint. */
    internal fun hasHint(lineText: String): Boolean = HINT_TAIL.containsMatchIn(lineText)

    /** Strips any injected hint from [lineText]. */
    internal fun stripHint(lineText: String): String =
        lineText.replace(HINT_TAIL, "").trimEnd()

    private fun hintComment(explainer: String): String = "//// $explainer"

    /**
     * One pass over the editor: reads the JSON off the IO dispatcher and appends
     * the hints that are not printed yet.
     *
     * Nothing is ever deleted here. A line that already carries its hint is
     * skipped rather than overwritten, so running the pass twice - or after the
     * user saved the file with the comments in - never duplicates anything.
     */
    suspend fun apply(editor: CodeEditor, filePath: String, context: Context) {
        val entries = withContext(Dispatchers.IO) {
            DiagnosticsStore.entriesFor(context, filePath)
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
                // The column must never cross the line: sora's `Content.insert` past the text length when the line keeps a
                // separator slot (CRLF), and landing there silently puts the hint
                // outside the visible row - the hint then looks missing on lines
                // that end in `}` and friends. Clamping to the plain text length
                // keeps the comment glued to the code line it explains.
                val column = original.length
                    .coerceAtMost(document.getColumnCount(baseLine))
                // The comment must not look glued to the code it explains: when the
                // line does not already end in a space (for example `//// kode`),
                // one space goes in first, then the hint comment follows it.
                val needsSeparator = original.isNotEmpty() && !original.last().isWhitespace()
                val injected = (if (needsSeparator) " " else "") + hintComment(entry.explainer)
                document.insert(baseLine, column, injected)
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
     * continuation lines included), clears the whole stored record of the file - no
     * matter how many lines it lists - and drops the hint from the saved file.
     *
     * Returns true when a hint was actually removed.
     */
    suspend fun discard(
        editor: CodeEditor,
        filePath: String,
        context: Context,
        lineIndex: Int
    ): Boolean {
        val removedAny = discardAt(editor, lineIndex)
        editor.invalidate()

        withContext(Dispatchers.IO) {
            // One Discard cleans the entire file record: keeping a tombstone per
            // line was the old contract and it is gone. Whatever the store still
            // holds for this file goes away, so no pass re-prints anything until
            // the next compile writes a fresh JSON.
            DiagnosticsStore.clear(context, filePath)
            // Discard also reaches the saved file: after a reopen the comment must
            // not be printed back from disk.
            stripHintsFromDisk(filePath)
        }
        return removedAny
    }

    /**
     * Removes the hint at [lineIndex] from the buffer and returns whether anything
     * was removed.
     *
     * Runs on the caller's (main) thread: it touches the editor's document directly, so
     * wrapping it in `withContext(Dispatchers.Main)` would save nothing and only add the
     * nullable result of a suspended block to reason about.
     */
    private fun discardAt(editor: CodeEditor, lineIndex: Int): Boolean {
        val document = editor.text
        if (lineIndex !in 0 until document.lineCount) return false
        // The cursor may sit on the code line, on the hint itself or on a wrapped
        // continuation of it: walk up to the line that starts the hint.
        var anchor = lineIndex
        while (anchor > 0 && !HINT_START.containsMatchIn(document.getLine(anchor).toString())) {
            anchor--
        }
        val lineText = document.getLine(anchor).toString()
        val match = HINT_START.find(lineText) ?: return false
        document.delete(
            anchor,
            match.range.first.coerceAtMost(lineText.trimEnd().length),
            anchor,
            lineText.length
        )
        // A wrapped hint continues on the following lines as plain `//// ...` lines; they
        // belong to the same comment and go with it.
        while (anchor + 1 < document.lineCount) {
            val text = document.getLine(anchor + 1).toString()
            if (!HINT_CONTINUATION.containsMatchIn(text)) break
            document.delete(anchor + 1, 0, anchor + 1, text.length)
            document.delete(anchor, document.getLine(anchor).length, anchor + 1, 0)
        }
        return true
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
