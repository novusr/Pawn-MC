package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import com.rvdjv.pawnmc.apps_data.data_compiler.DiagnosticsStore
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Injects the explainer hints recorded by the compiler run into the open editor.
 *
 * The compiler layer writes `Documents/PawnMC/compiler/injector_data.json` with the
 * file, the damaged line and the localized `[explain]` text; this reader walks
 * that JSON every cycle and:
 *
 *  1. Appends a trailing `// [explain]: ...` comment to each line the compiler
 *     flagged, when the line is still present in the editor buffer.
 *  2. Removes any trailing `[explain]` comment whose stored line no longer
 *     exists, so a fixed file cleans itself up.
 *
 * A plain comment was chosen over a shadow/gutter overlay on purpose: it needs
 * no renderer hooks and survives every theme.
 */
internal object ExplainInjector {

    /** How long one cycle waits before re-reading the JSON, in milliseconds. */
    const val CYCLE_INTERVAL_MS: Long = 6L * 60L * 1000L

    /** Matches a previously injected hint at the end of a line, / or // style. */
    private val HINT_TAIL = Regex("""\s*(?://|/\*)\s*\[[^\]]*]\s*:.*$""")

    /** True when the line already carries an injected hint. */
    internal fun hasHint(lineText: String): Boolean = HINT_TAIL.containsMatchIn(lineText)

    /** Strips any injected hint from [lineText]. */
    internal fun stripHint(lineText: String): String =
        lineText.replace(HINT_TAIL, "").trimEnd()

    /**
     * One pass over the editor: reads the JSON off the IO dispatcher, applies
     * the matching hints inside the editor's own lock and reports the lines it
     * removed because their text is gone.
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
                val index = entry.line - 1
                if (index < 0 || index >= document.lineCount) {
                    // The line the compiler flagged no longer exists in the
                    // buffer: retire its record so the JSON stays honest.
                    withContext(Dispatchers.IO) {
                        DiagnosticsStore.dropLine(context, filePath, entry.line)
                    }
                    continue
                }
                val original = document.getLine(index).toString().trimEnd()
                if (hasHint(original)) {
                    // Drop the stale hint first; a fresh one lands below.
                    val base = stripHint(original)
                    document.delete(index, base.length, index, original.length)
                    changed = true
                    if (base.isBlank()) continue
                    document.insert(index, base.length, " // ${entry.explainer}")
                } else if (original.isNotBlank()) {
                    document.insert(index, original.length, " // ${entry.explainer}")
                    changed = true
                }
            }
            if (changed) editor.invalidate()
        }
    }
}
