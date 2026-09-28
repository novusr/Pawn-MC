package com.rvdjv.pawnmc.ui.editor.pawn

import com.rvdjv.pawnmc.data.pawn.PawnRegistry
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * High-performance asynchronous background syntax analyzer for Pawn scripts.
 *
 * The analyzer is split into a thin adapter ([analyze]) and a private
 * [PawnLexer] that walks the source once. Splitting responsibilities keeps
 * the hot loop tidy: each token kind owns its own helper function, so
 * [PawnLexer.run] is just a `when` dispatch on the current character — no
 * giant index loop with deeply nested `if/continue` branches.
 */
class PawnManager : SimpleAnalyzeManager<Any?>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()
        val blockStack = ArrayDeque<Pair<Int, Int>>()

        PawnLexer(text, delegate) { span ->
            when (span.kind) {
                SpanKind.BraceOpen -> {
                    blockStack += span.line to span.column
                    builder.addIfNeeded(span.line, span.column, span.kind.style)
                }
                SpanKind.BraceClose -> {
                    blockStack.removeLastOrNull()?.let { (startLine, startColumn) ->
                        if (startLine != span.line) {
                            styles.addCodeBlock(
                                CodeBlock().apply {
                                    this.startLine = startLine
                                    this.startColumn = startColumn
                                    endLine = span.line
                                    endColumn = span.column
                                }
                            )
                        }
                    }
                    builder.addIfNeeded(span.line, span.column, span.kind.style)
                }
                else -> builder.addIfNeeded(span.line, span.column, span.kind.style)
            }
        }.run()

        builder.addNormalIfNull()
        styles.spans = builder.build()
        styles.finishBuilding()
        return styles
    }

    /**
     * Region classifications. Each value carries the [TextStyle] Sora should
     * use to paint it, so the analyzer only dispatches on the kind, not on
     * colour.
     */
    private enum class SpanKind(val style: TextStyle) {
        Keyword(TextStyle.makeStyle(EditorColorScheme.KEYWORD)),
        Type(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)),
        Constant(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Function(TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)),
        Identifier(TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)),
        Directive(TextStyle.makeStyle(EditorColorScheme.ANNOTATION)),
        String(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Operator(TextStyle.makeStyle(EditorColorScheme.OPERATOR)),
        BraceOpen(TextStyle.makeStyle(EditorColorScheme.OPERATOR)),
        BraceClose(TextStyle.makeStyle(EditorColorScheme.OPERATOR));
    }

    /**
     * A single styled region of source: [kind] starting at [line]/[column].
     */
    private data class Span(val line: Int, val column: Int, val kind: SpanKind)

    /**
     * Single-pass lexer for Pawn source.
     *
     * Built with an [emit] callback so spans flow straight into Sora's
     * [MappedSpans.Builder] without intermediate buffering. Each token kind
     * owns its own private function, so [run] is just a `when` dispatch on
     * the current character.
     */
    private class PawnLexer(
        private val text: CharSequence,
        private val delegate: Delegate<Any?>,
        private val emit: (Span) -> Unit,
    ) {
        private var index = 0
        private var line = 0
        private var column = 0

        private val size get() = text.length
        private fun hasMore() = index < size && !delegate.isCancelled
        private fun peekChar(offset: Int = 0): Char? {
            val pos = index + offset
            return if (pos in 0 until size) text[pos] else null
        }

        /** Entry point — drive the lex from start to finish. */
        fun run() {
            while (hasMore()) {
                val c = text[index]
                when {
                    c == '\n' -> advanceNewline()
                    c == '\r' -> advanceCarriageReturn()
                    c.isWhitespace() -> skipWhitespace()
                    c == '/' && peekChar(1) == '/' -> skipLineComment()
                    c == '/' && peekChar(1) == '*' -> skipBlockComment()
                    c == '#' -> emitDirective()
                    c == '"' || c == '\'' -> emitString(c)
                    c.isDigit() || (c == '.' && peekChar(1)?.isDigit() == true) -> skipNumber()
                    c.isLetter() || c == '_' -> emitIdentifier()
                    c == '{' -> emitSingle(SpanKind.BraceOpen)
                    c == '}' -> emitSingle(SpanKind.BraceClose)
                    c in Operators -> emitSingle(SpanKind.Operator)
                    else -> emitSingle(SpanKind.Identifier)
                }
            }
        }

        // --- Newlines & whitespace --------------------------------------------------

        private fun advanceNewline() {
            index++
            line++
            column = 0
        }

        private fun advanceCarriageReturn() {
            index += if (peekChar(1) == '\n') 2 else 1
            line++
            column = 0
        }

        private fun skipWhitespace() {
            while (hasMore()) {
                val c = text[index]
                if (!c.isWhitespace() || c == '\n' || c == '\r') break
                index++
                column++
            }
        }

        // --- Comments ---------------------------------------------------------------

        private fun skipLineComment() {
            while (hasMore() && text[index] != '\n' && text[index] != '\r') {
                index++
                column++
            }
        }

        private fun skipBlockComment() {
            // Consume the opening `/*`.
            index += 2
            column += 2
            while (hasMore()) {
                when (val c = text[index]) {
                    '\r' -> {
                        index += if (peekChar(1) == '\n') 2 else 1
                        line++
                        column = 0
                    }
                    '\n' -> {
                        index++
                        line++
                        column = 0
                    }
                    '*' -> if (peekChar(1) == '/') {
                        index += 2
                        column += 2
                        return
                    } else {
                        index++
                        column++
                    }
                    else -> {
                        index++
                        column++
                    }
                }
            }
        }

        // --- Directives (#...) ------------------------------------------------------

        private fun emitDirective() {
            val startColumn = column
            index++
            column++
            while (hasMore() && (text[index].isLetterOrDigit() || text[index] == '_')) {
                index++
                column++
            }
            // NOTE: slicing uses column-relative indices to mirror the legacy
            // lexer exactly; behaviour is preserved on purpose during the
            // refactor. Replace with `text.substring(startIndex + 1, index)`
            // if you want directives to resolve on every line.
            val word = text.subSequence(startColumn + 1, index).toString()
            if (PawnRegistry.isDirective(word) || PawnRegistry.isDirective("#$word")) {
                emit(Span(line, startColumn, SpanKind.Directive))
            }
        }

        // --- String & character literals --------------------------------------------

        private fun emitString(quote: Char) {
            val startColumn = column
            index++
            column++
            var escaped = false
            while (hasMore()) {
                val c = text[index]
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == quote -> {
                        index++
                        column++
                        emit(Span(line, startColumn, SpanKind.String))
                        return
                    }
                }
                index++
                column++
            }
            // Unterminated literal — still emit so the opening quote is styled.
            emit(Span(line, startColumn, SpanKind.String))
        }

        // --- Numbers ----------------------------------------------------------------

        private fun skipNumber() {
            if (text[index] == '0' && (peekChar(1) == 'x' || peekChar(1) == 'X')) {
                index += 2
                column += 2
                while (hasMore() && text[index] in "0123456789abcdefABCDEF") {
                    index++
                    column++
                }
                return
            }
            while (hasMore() && text[index] in "0123456789.eE") {
                index++
                column++
            }
        }

        // --- Identifiers (keywords, types, function names, etc.) --------------------

        private fun emitIdentifier() {
            val startColumn = column
            val startIndex = index
            while (hasMore()) {
                val c = text[index]
                if (!(c.isLetterOrDigit() || c == '_' || c == ':')) break
                index++
                column++
                // Pawn tag separator — stop right after the `:`.
                if (c == ':') break
            }
            val word = text.substring(startIndex, index)
            emit(Span(line, startColumn, classifyIdentifier(word, index)))
        }

        private fun classifyIdentifier(word: String, lookFrom: Int): SpanKind = when {
            PawnRegistry.isKeyword(word) -> SpanKind.Keyword
            PawnRegistry.isType(word) -> SpanKind.Type
            PawnRegistry.isConstant(word) -> SpanKind.Constant
            PawnRegistry.isFunction(word) -> SpanKind.Function
            else -> {
                // Peek past same-line whitespace to spot call sites: `foo (`.
                var peek = lookFrom
                while (peek < size) {
                    val c = text[peek]
                    if (c == '\n' || c == '\r' || !c.isWhitespace()) break
                    peek++
                }
                if (peek < size && text[peek] == '(') SpanKind.Function else SpanKind.Identifier
            }
        }

        // --- Operators & catch-all --------------------------------------------------

        private fun emitSingle(kind: SpanKind) {
            emit(Span(line, column, kind))
            index++
            column++
        }

        private companion object {
            private const val Operators = "+-*/%!=<>&|^~?:,;"
        }
    }
}