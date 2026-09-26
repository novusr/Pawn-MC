package com.rvdjv.pawnmc.ui.editor.pawn

import android.os.Bundle
import com.rvdjv.pawnmc.data.pawn.PawnItemKind
import com.rvdjv.pawnmc.data.pawn.PawnLanguageRegistry
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
 */
class PawnLanguage : Language {

    private val analyzeManager = PawnAnalyzeManager()
    private val symbolPairs = SymbolPairMatch.DefaultSymbolPairs()

    override fun getAnalyzeManager(): AnalyzeManager = analyzeManager

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch) || ch == '#' || ch == ':'
        }

        if (prefix.isBlank()) return
        publisher.checkCancelled()

        val matchingItems = PawnLanguageRegistry.getCompletions(prefix)
        for (item in matchingItems) {
            publisher.checkCancelled()
            val itemKind = when (item.kind) {
                PawnItemKind.KEYWORD -> CompletionItemKind.Keyword
                PawnItemKind.DIRECTIVE -> CompletionItemKind.Snippet
                PawnItemKind.TYPE -> CompletionItemKind.TypeParameter
                PawnItemKind.CONSTANT -> CompletionItemKind.Constant
                PawnItemKind.FUNCTION -> CompletionItemKind.Function
                PawnItemKind.CALLBACK -> CompletionItemKind.Interface
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
