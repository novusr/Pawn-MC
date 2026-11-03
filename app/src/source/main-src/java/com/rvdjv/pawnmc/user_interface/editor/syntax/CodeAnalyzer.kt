package com.rvdjv.pawnmc.user_interface.editor.syntax

import com.rvdjv.pawnmc.apps-data.data_syntax.PawnRegistry
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** Analyzes source and publishes syntax styles. */
internal class CodeAnalyzer(
    private val onLocalFunctionsChanged: (List<LocalFunction>) -> Unit = {}
) : SimpleAnalyzeManager<Any?>() {

    /** Highlights tokens and collects local function declarations. */
    override fun analyze(text: StringBuilder, delegate: SimpleAnalyzeManager<Any?>.Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()
        val blockStack = ArrayDeque<Pair<Int, Int>>()
        val localFunctions = CodeSymbols.functions(text) { delegate.isCancelled() }.orEmpty()
        if (!delegate.isCancelled()) onLocalFunctionsChanged(localFunctions)
        val localFunctionNames = localFunctions.mapTo(hashSetOf()) { it.name }

        CodeLexer(text, delegate, localFunctionNames) { span ->
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

    /** Token types and their Sora styles. */
    private enum class SpanKind(val style: Long) {
        Keyword(TextStyle.makeStyle(EditorColorScheme.KEYWORD)),
        Type(TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)),
        Constant(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Function(TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)),
        LocalFunction(TextStyle.makeStyle(EditorColorScheme.ANNOTATION)),
        Identifier(TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)),
        Directive(TextStyle.makeStyle(EditorColorScheme.ANNOTATION)),
        String(TextStyle.makeStyle(EditorColorScheme.LITERAL)),
        Comment(TextStyle.makeStyle(EditorColorScheme.COMMENT)),
        Operator(TextStyle.makeStyle(EditorColorScheme.OPERATOR)),
        BraceOpen(TextStyle.makeStyle(EditorColorScheme.OPERATOR)),
        BraceClose(TextStyle.makeStyle(EditorColorScheme.OPERATOR));
    }

    private data class Span(val line: Int, val column: Int, val kind: SpanKind)

    /** Reads source text and emits styled tokens. */
    private class CodeLexer(
        private val text: CharSequence,
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
                    // Function names are highlighted; parentheses are not.
                    else -> skipPunctuation()
                }
            }
        }

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

        private fun skipLineComment() {
            emit(Span(line, column, SpanKind.Comment))
            while (hasMore() && text[index] != '\n' && text[index] != '\r') {
                index++
                column++
            }
        }

        private fun skipBlockComment() {
            emit(Span(line, column, SpanKind.Comment))
            index += 2
            column += 2
            while (hasMore()) {
                when (text[index]) {
                    '\r' -> {
                        index += if (peekChar(1) == '\n') 2 else 1
                        line++
                        column = 0
                        if (hasMore()) emit(Span(line, column, SpanKind.Comment))
                    }
                    '\n' -> {
                        index++
                        line++
                        column = 0
                        if (hasMore()) emit(Span(line, column, SpanKind.Comment))
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

        private fun emitDirective() {
            val startColumn = column
            val startIndex = index
            index++
            column++
            while (hasMore() && (text[index].isLetterOrDigit() || text[index] == '_')) {
                index++
                column++
            }
            // Columns are line-relative, so use the absolute source index.
            val word = text.substring(startIndex + 1, index)
            if (PawnRegistry.isDirective(word)) {
                emit(Span(line, startColumn, SpanKind.Directive))
            }
        }

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
            // Highlight an unfinished literal too.
            emit(Span(line, startColumn, SpanKind.String))
        }

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

        private fun emitIdentifier() {
            val startColumn = column
            val startIndex = index
            while (hasMore()) {
                val c = text[index]
                if (!(c.isLetterOrDigit() || c == '_' || c == ':')) break
                index++
                column++
                // Keep the tag separator with the identifier.
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

        /** Checks whether the identifier is followed by a call parenthesis. */
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

        /** Emits the longest operator starting at the cursor. */
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

        /** Highlights block-comment keywords. */
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

        private fun skipPunctuation() {
            index++
            column++
        }

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