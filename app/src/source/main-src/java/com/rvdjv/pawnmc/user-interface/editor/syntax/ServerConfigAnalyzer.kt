package com.rvdjv.pawnmc.`user-interface`.editor.syntax

import com.rvdjv.pawnmc.apps-data.data_syntax.ServerConfig
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Highlights an SA-MP `server.cfg`.
 *
 * The language is line oriented and has no blocks, no escapes worth tracking and no
 * multi-line constructs, so this analyzer never needs the Pawn lexer: each line is split
 * into comment, directive and value spans.
 */
internal class ServerConfigAnalyzer : SimpleAnalyzeManager<Any?>() {

    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<Any?>.Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()

        var lineStart = 0
        var line = 0
        while (lineStart <= text.length) {
            if (delegate.isCancelled()) break
            val lineEnd = text.indexOfAny(NEWLINES, lineStart).let {
                if (it < 0) text.length else it
            }
            analyzeLine(text, lineStart, lineEnd, line, builder)
            if (lineEnd >= text.length) break
            // Skip a CRLF pair as one line break.
            lineStart = if (text[lineEnd] == '\r' && lineEnd + 1 < text.length && text[lineEnd + 1] == '\n') {
                lineEnd + 2
            } else {
                lineEnd + 1
            }
            line++
        }

        builder.addNormalIfNull()
        styles.spans = builder.build()
        styles.finishBuilding()
        return styles
    }

    private fun analyzeLine(
        text: CharSequence,
        start: Int,
        end: Int,
        line: Int,
        builder: MappedSpans.Builder
    ) {
        var index = start
        // Leading whitespace is not part of any token.
        while (index < end && (text[index] == ' ' || text[index] == '\t')) index++
        if (index >= end) return

        // A full-line comment (either Sa-MP comment syntax).
        if (text[index] == ';' || (text[index] == '/' && index + 1 < end && text[index + 1] == '/')) {
            builder.addIfNeeded(line, index - start, SpanKind.Comment.style)
            return
        }

        // The key runs to the first separator; anything before it is the directive.
        val keyEnd = run {
            var cursor = index
            while (cursor < end && text[cursor] != ' ' && text[cursor] != '\t' && text[cursor] != '=') cursor++
            cursor
        }
        val key = text.subSequence(index, keyEnd).toString()
        if (ServerConfig.isDirective(key)) {
            builder.addIfNeeded(line, index - start, SpanKind.Keyword.style)
        }
        index = keyEnd

        // The value: skip separators, then split into quoted strings and bare words.
        while (index < end) {
            when {
                text[index] == ';' || (text[index] == '/' && index + 1 < end && text[index + 1] == '/') -> {
                    builder.addIfNeeded(line, index - start, SpanKind.Comment.style)
                    return
                }
                text[index] == '"' -> {
                    val column = index - start
                    index++
                    while (index < end && text[index] != '"') index++
                    if (index < end) index++
                    builder.addIfNeeded(line, column, SpanKind.String.style)
                }
                text[index] == ' ' || text[index] == '\t' || text[index] == '=' -> index++
                text[index].isDigit() -> {
                    val column = index - start
                    while (index < end && (text[index].isDigit() || text[index] == '.' || text[index] == '-')) index++
                    builder.addIfNeeded(line, column, SpanKind.Constant.style)
                }
                else -> {
                    val column = index - start
                    while (index < end && text[index] != ' ' && text[index] != '\t') {
                        // Values such as `192.168.1.1:7777` keep the whole token.
                        if (text[index] == ';') break
                        if (text[index] == '/' && index + 1 < end && text[index + 1] == '/') break
                        index++
                    }
                    builder.addIfNeeded(line, column, SpanKind.Value.style)
                }
            }
        }
    }

    private enum class SpanKind(val style: Long) {
        Keyword(TextStyle.makeStyle(EditorColorScheme.KEYWORD)),
        Value(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)),
        String(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Constant(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Comment(TextStyle.makeStyle(EditorColorScheme.COMMENT));
    }

    private companion object {
        val NEWLINES = charArrayOf('\n', '\r')
    }
}
