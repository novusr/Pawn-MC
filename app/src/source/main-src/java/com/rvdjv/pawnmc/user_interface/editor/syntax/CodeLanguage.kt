package com.rvdjv.pawnmc.user_interface.editor.syntax

import android.os.Bundle
import com.rvdjv.pawnmc.apps_data.data_syntax._item
import com.rvdjv.pawnmc.apps_data.data_syntax.PawnItemKind
import com.rvdjv.pawnmc.apps_data.data_syntax.PawnRegistry
import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
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

/** Syntax highlighting and completion for the editor. */
class CodeLanguage(
    private val appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
) : Language {

    private companion object {
        const val COMPLETION_IDLE_DELAY_MS = 2_500L
        const val COMPLETION_CANCEL_CHECK_MS = 50L
    }

    @Volatile
    private var localFunctions: List<LocalFunction> = emptyList()
    private val codeAnalyzer = CodeAnalyzer { localFunctions = it }
    private val symbolPairs = SymbolPairMatch.DefaultSymbolPairs()

    override fun getAnalyzeManager(): AnalyzeManager = codeAnalyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val operatorPrefix = operatorPrefixAt(content, position)
        if (operatorPrefix != null) {
            waitForTypingPause(publisher)
            if (!matchesAtCursor(content, position, operatorPrefix)) return
            publish(publisher, operatorPrefix, localFunctions)
            return
        }

        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch) || ch == '#' || ch == ':'
        }

        if (prefix.isBlank()) return
        waitForTypingPause(publisher)
        if (!matchesAtCursor(content, position, prefix)) return
        publish(publisher, prefix, localFunctions)
    }

    private fun matchesAtCursor(
        content: ContentReference,
        position: CharPosition,
        expectedPrefix: String
    ): Boolean {
        if (position.line !in 0 until content.getLineCount()) return false
        val line = content.getLine(position.line)
        if (position.column !in 0..line.length) return false
        val actualPrefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch) || ch == '#' || ch == ':'
        }
        return actualPrefix == expectedPrefix && actualPrefix.isNotBlank()
    }

    private fun waitForTypingPause(publisher: CompletionPublisher) {
        // Sora runs completion off the UI thread; frequent cancellation checks discard
        // this request promptly when another keystroke arrives.
        val deadline = System.nanoTime() + COMPLETION_IDLE_DELAY_MS * 1_000_000L
        while (true) {
            publisher.checkCancelled()
            val remainingNanos = deadline - System.nanoTime()
            if (remainingNanos <= 0L) return
            val sleepMillis = ((remainingNanos + 999_999L) / 1_000_000L)
                .coerceAtMost(COMPLETION_CANCEL_CHECK_MS)
            try {
                Thread.sleep(sleepMillis)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                publisher.checkCancelled()
                return
            }
        }
    }

    /** Returns the operator token ending at the cursor, if any. */
    private fun operatorPrefixAt(content: ContentReference, position: CharPosition): String? {
        if (position.line !in 0 until content.getLineCount()) return null
        val line = content.getLine(position.line)
        if (position.column !in 0..line.length) return null
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
        localFunctions: List<LocalFunction>
    ) {
        val localFunctionDescription = when (appLanguage) {
            CompilerConfig.AppLanguage.ID -> "Dideklarasikan di berkas ini"
            CompilerConfig.AppLanguage.ES -> "Declarada en este archivo"
            CompilerConfig.AppLanguage.RU -> "Объявлено в этом файле"
            CompilerConfig.AppLanguage.TL -> "Idineklara sa file na ito"
            CompilerConfig.AppLanguage.EN -> "Declared in this file"
        }
        val matchingItems = PawnRegistry.getCompletions(prefix, appLanguage) + localFunctions
            .filter { it.name.startsWith(prefix, ignoreCase = true) }
            .map { _item(it.name, "$localFunctionDescription: ${it.description.substringBefore("(").substringAfter(": ").trim()}", PawnItemKind.FUNCTION) }
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
        if (line !in 0 until content.getLineCount()) return 0
        val lineStr = content.getLine(line)
        if (column !in 0..lineStr.length) return 0
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
        codeAnalyzer.destroy()
        localFunctions = emptyList()
    }
}
