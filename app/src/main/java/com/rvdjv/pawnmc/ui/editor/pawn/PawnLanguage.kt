package com.rvdjv.pawnmc.ui.editor.pawn

import android.os.Bundle
import com.rvdjv.pawnmc.data.pawn._item
import com.rvdjv.pawnmc.data.pawn.PawnItemKind
import com.rvdjv.pawnmc.data.pawn.PawnRegistry
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.completion.CompletionHelper
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.SimpleCompletionItem
import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch

/**
 * Official Pawn language implementation for the internal Sora/Xed editor engine.
 *
 * Autocompletion is served from [PawnRegistry], which now also carries the
 * operator table and the include-derived natives and forwards, so the popup
 * and the syntax highlighter always agree on what the language contains.
 */
class PawnLanguage : Language {

    private val analyzeManager = PawnManager()
    private val symbolPairs = SymbolPairMatch.DefaultSymbolPairs()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val source = (0 until content.getLineCount()).joinToString("\n") { content.getLine(it) }
        val localFunctions = PawnSourceSymbols.functions(source)
        val operatorPrefix = operatorPrefixAt(content, position)
        if (operatorPrefix != null) {
            publisher.checkCancelled()
            publish(publisher, operatorPrefix, localFunctions)
            return
        }

        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch) || ch == '#' || ch == ':'
        }

        if (prefix.isBlank()) return
        publisher.checkCancelled()
        publish(publisher, prefix, localFunctions)
    }

    /**
     * The operator token the cursor sits at the end of, or `null` when the
     * position is not on an operator.
     *
     * [CompletionHelper.computePrefix] only walks identifier characters, so
     * without this the operator entries in the registry would never be
     * reachable from the popup. The scan stops as soon as an identifier
     * character is reached so that `a+` keeps offering symbols for `a`.
     */
    private fun operatorPrefixAt(content: ContentReference, position: CharPosition): String? {
        val line = content.getLine(position.line)
        var start = position.column
        while (start > 0 && line[start - 1] in PawnRegistry.OPERATOR_HEADS) {
            start--
        }
        if (start == position.column) return null
        if (start > 0 && Character.isJavaIdentifierPart(line[start - 1])) return null
        val candidate = line.substring(start, position.column)
        return if (PawnRegistry.isOperator(candidate)) candidate else null
    }

    private fun publish(
        publisher: CompletionPublisher,
        prefix: String,
        localFunctions: List<PawnLocalFunction>
    ) {
        val matchingItems = PawnRegistry.getCompletions(prefix) + localFunctions
            .filter { it.name.startsWith(prefix, ignoreCase = true) }
            .map { _item(it.name, it.description, PawnItemKind.FUNCTION) }
        for (item in matchingItems) {
            publisher.checkCancelled()
            val itemKind = when (item.kind) {
                PawnItemKind.KEYWORD -> CompletionItemKind.Keyword
                PawnItemKind.DIRECTIVE -> CompletionItemKind.Snippet
                PawnItemKind.TYPE -> CompletionItemKind.TypeParameter
                PawnItemKind.CONSTANT -> CompletionItemKind.Constant
                PawnItemKind.FUNCTION -> CompletionItemKind.Function
                PawnItemKind.CALLBACK -> CompletionItemKind.Interface
                PawnItemKind.OPERATOR -> CompletionItemKind.Keyword
            }

            val completion = SimpleCompletionItem(
                item.name,
                item.description,
                prefix.length,
                item.name
            )
            completion.kind(itemKind)
            publisher.addItem(completion)
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val lineStr = content.getLine(line)
        var count = 0
        for (i in 0 until column.coerceAtMost(lineStr.length)) {
            val c = lineStr[i]
            if (c == '{') count += 4
            else if (c == '}') count -= 4
        }
        return count.coerceAtLeast(0)
    }

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    override fun destroy() {
    }
}
