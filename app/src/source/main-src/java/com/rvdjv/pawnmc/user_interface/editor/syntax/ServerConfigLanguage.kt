package com.rvdjv.pawnmc.user_interface.editor.syntax

import android.os.Bundle
import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import com.rvdjv.pawnmc.apps_data.data_syntax.ServerConfig
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
 * Syntax highlighting and completion for an SA-MP `server.cfg`.
 *
 * The vocabulary lives in [ServerConfig] so the directive list stays in one place.
 */
class ServerConfigLanguage(
    private val appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
) : Language {

    private companion object {
        const val COMPLETION_IDLE_DELAY_MS = 2_500L
        const val COMPLETION_CANCEL_CHECK_MS = 50L
    }

    private val analyzer = ServerConfigAnalyzer()
    private val symbolPairs = SymbolPairMatch.DefaultSymbolPairs()

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun getInterruptionLevel(): Int = Language.INTERRUPTION_LEVEL_SLIGHT

    override fun requireAutoComplete(
        content: ContentReference,
        position: CharPosition,
        publisher: CompletionPublisher,
        extraArguments: Bundle
    ) {
        val prefix = CompletionHelper.computePrefix(content, position) { ch ->
            Character.isJavaIdentifierPart(ch)
        }
        if (prefix.isBlank()) return
        // `hostname` and `hostname = ` are both completion points; the second one only
        // inserts the directive name, which is what a key already in a line wants.
        val line = if (position.line in 0 until content.getLineCount()) {
            content.getLine(position.line).toString()
        } else {
            null
        }
        val inValue = line != null && ServerConfig.directiveAt(line, position.column) != null

        waitForTypingPause(publisher)
        publish(publisher, prefix, inValue)
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

    private fun publish(publisher: CompletionPublisher, prefix: String, inValue: Boolean) {
        val matches = ServerConfig.completions(prefix, appLanguage)
        for (directive in matches) {
            publisher.checkCancelled()
            val usage = ServerConfig.usageFor(directive)
            val description = ServerConfig.descriptionFor(directive, appLanguage)
            // Inside a value only the directive name is inserted; outside one the value
            // hint follows it, so a half-written key becomes a complete line.
            val insertion = if (inValue) directive else usage
            val completion = SimpleCompletionItem(directive, "$usage — $description", prefix.length, insertion)
            completion.kind(CompletionItemKind.Property)
            publisher.addItem(completion)
        }
    }

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int = 0

    override fun useTab(): Boolean = false

    override fun getFormatter(): Formatter = EmptyLanguage.EmptyFormatter.INSTANCE

    override fun getSymbolPairs(): SymbolPairMatch = symbolPairs

    override fun getNewlineHandlers(): Array<NewlineHandler>? = null

    override fun destroy() {
        analyzer.destroy()
    }
}
