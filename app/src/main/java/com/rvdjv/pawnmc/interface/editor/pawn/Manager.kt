package com.rvdjv.pawnmc.`interface`.editor.pawn

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
 *
 * Highlighting is not list-only. Registry lookups (keywords, directives,
 * types, constants, known natives and forwards) are the first pass, but a
 * contextual rule runs afterwards: any identifier directly followed by `(`
 * is a function call site, so `halodunia()` paints the name as a function
 * while the `()` themselves stay uncoloured. Operators are matched as whole
 * tokens from [PawnRegistry.LONG_OPERATORS], so `>>>=` is a single span
 * instead of three.
 */
class PawnManager : SimpleAnalyzeManager<Any?>() {

    /**
     * Tokenizes [text] and paints the result.
     *
     * The delegate parameter is written with the outer type arguments, because
     * `Delegate` is an *inner* class of the generic manager: Kotlin resolves
     * `SimpleAnalyzeManager<Any?>.Delegate<Any?>` to the `Delegate<V>` the abstract
     * `analyze` declares, which is what makes the override match.
     */
    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<Any?>.Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()
        val blockStack = ArrayDeque<Pair<Int, Int>>()
        val localFunctions = PawnSourceSymbols.functions(text).mapTo(hashSetOf()) { it.name }

        PawnLexer(text, delegate, localFunctions) { span ->
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
     * Region classifications. Each value carries the style id Sora should paint it
     * with (`TextStyle.makeStyle` returns a `long` in this Sora version), so the
     * analyzer only dispatches on the kind, not on colour.
     */
    private enum class SpanKind(val style: Long) {
        Keyword(TextStyle.makeStyle(EditorColorScheme.KEYWORD)),
        Type(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)),
        Constant(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Function(TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)),
        LocalFunction(TextStyle.makeStyle(EditorColorScheme.ANNOTATION)),
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
        // `Delegate` is an inner class of the generic manager, so the outer type
        // arguments have to be supplied on the reference itself, and the inner
        // argument is fixed by `SimpleAnalyzeManager<Any?>`.
        private val delegate: SimpleAnalyzeManager<Any?>.Delegate<Any?>,
        private val localFunctions: Set<String>,
        private val emit: (Span) -> Unit,
    ) {
        private var index = 0
        private var line = 0
        private var column = 0

        private val size get() = text.length
        private fun hasMore() = index < size && !delegate.isCancelled()
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
                    c == '*' && tryEmitBlockCommentKeyword() -> Unit
                    c == '{' -> emitSingle(SpanKind.BraceOpen)
                    c == '}' -> emitSingle(SpanKind.BraceClose)
                    c in PawnRegistry.OPERATOR_HEADS -> emitOperator()
                    // Parentheses and any other punctuation carry no colour:
                    // the caller name before `(` is what gets highlighted.
                    else -> skipPunctuation()
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
            val startIndex = index
            index++
            column++
            while (hasMore() && (text[index].isLetterOrDigit() || text[index] == '_')) {
                index++
                column++
            }
            // Slice by absolute offset: the column only tracks the start of the
            // current line, so a column-relative slice would read the wrong
            // characters on every line but the first.
            val word = text.substring(startIndex + 1, index)
            if (PawnRegistry.isDirective(word)) {
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
            word in localFunctions -> SpanKind.LocalFunction
            PawnRegistry.isFunction(word) -> SpanKind.Function
            isCallSite(lookFrom) -> SpanKind.Function
            else -> SpanKind.Identifier
        }

        /**
         * True when the identifier starting at [from] is immediately used as a
         * call, i.e. followed by `()` with nothing but whitespace in between.
         *
         * This is what makes an undeclared name such as `halodunia()` render as
         * a function, which is the whole point of the call-site rule: the
         * parenthesised argument list itself stays unstyled.
         */
        private fun isCallSite(from: Int): Boolean {
            var peek = from
            while (peek < size) {
                val c = text[peek]
                if (c == '\n' || c == '\r') return false
                if (!c.isWhitespace()) return c == '('
                peek++
            }
            return false
        }

        // --- Operators & catch-all --------------------------------------------------

        /**
         * Consume the longest operator starting at the cursor.
         *
         * Falling back to a single character keeps unknown punctuation styled as
         * an operator instead of leaking an unstyled gap into the buffer.
         */
        private fun emitOperator() {
            val startColumn = column
            var length = 1
            for (candidate in PawnRegistry.LONG_OPERATORS) {
                if (matchesAt(index, candidate)) {
                    length = candidate.length
                    break
                }
            }
            index += length
            column += length
            emit(Span(line, startColumn, SpanKind.Operator))
        }

        private fun matchesAt(start: Int, token: String): Boolean {
            if (start + token.length > size) return false
            for (i in token.indices) {
                if (text[start + i] != token[i]) return false
            }
            return true
        }

        /**
         * Pawn block comments are opened with a lone `*` on its own line, but
         * the language spells the delimiters `*begin`, `*end` and `*then` in
         * its keyword table, so the registry entries would otherwise be dead
         * weight. Match them here and emit a single keyword span.
         */
        private fun tryEmitBlockCommentKeyword(): Boolean {
            for (keyword in BlockCommentKeywords) {
                if (!matchesAt(index, keyword)) continue
                val startColumn = column
                index += keyword.length
                column += keyword.length
                emit(Span(line, startColumn, SpanKind.Keyword))
                return true
            }
            return false
        }

        /** Advance over a character that carries no styling of its own. */
        private fun skipPunctuation() {
            index++
            column++
        }

        /** Emit a single-character token such as `{` or `}`. */
        private fun emitSingle(kind: SpanKind) {
            emit(Span(line, column, kind))
            index++
            column++
        }

        private companion object {
            private val BlockCommentKeywords = listOf("*begin", "*end", "*then")
        }
    }
}