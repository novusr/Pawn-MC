package com.rvdjv.pawnmc.`user-interface`.editor.syntax

import com.rvdjv.pawnmc.apps-data.data_syntax.PawnRegistry

internal data class LocalFunction(
    val name: String,
    val description: String
)

/** Finds local function declarations and excludes registry entries. */
internal object CodeSymbols {

    private val declarationPattern = Regex(
        """\b(stock|public|forward|native)\s+((?:[A-Za-z_]\w*:)?[A-Za-z_]\w*)\s*\("""
    )

    fun functions(source: CharSequence): List<LocalFunction> {
        return functions(source) { false }.orEmpty()
    }

    fun functions(source: CharSequence, isCancelled: () -> Boolean): List<LocalFunction>? {
        if (isCancelled()) return null
        val sourceText = source.toString()
        val searchableText = maskCommentsAndStrings(sourceText, isCancelled) ?: return null
        val functions = mutableListOf<LocalFunction>()

        for (match in declarationPattern.findAll(searchableText)) {
            if (isCancelled()) return null
            val openParen = match.range.last
            val closeParen = matchingParen(searchableText, openParen, isCancelled)
            if (isCancelled()) return null
            if (closeParen == null) continue
            val declarationEnd = searchableText.indexOfFirstNonWhitespace(closeParen + 1, isCancelled)
                ?: return null
            if (declarationEnd !in searchableText.indices || searchableText[declarationEnd] !in ";{") {
                continue
            }

            val name = match.groupValues[2].substringAfterLast(':')
            if (PawnRegistry.containsSymbol(name)) continue

            val signature = sourceText.substring(match.range.first, closeParen + 1)
                .replace(Regex("\\s+"), " ")
                .trim()
            functions += LocalFunction(name, "Declared in this file: $signature")
        }

        return functions.distinctBy { it.name.lowercase() }
    }

    private fun matchingParen(
        source: String,
        openParen: Int,
        isCancelled: () -> Boolean
    ): Int? {
        var depth = 0
        for (index in openParen until source.length) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0 && isCancelled()) return null
            when (source[index]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun String.indexOfFirstNonWhitespace(
        start: Int,
        isCancelled: () -> Boolean
    ): Int? {
        var index = start
        while (index < length && this[index].isWhitespace()) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0 && isCancelled()) return null
            index++
        }
        return index
    }

    private fun maskCommentsAndStrings(source: String, isCancelled: () -> Boolean): String? {
        val masked = source.toCharArray()
        var index = 0
        var state = State.Code
        var quote = '\u0000'
        var escaped = false

        while (index < source.length) {
            if (index % CANCELLATION_CHECK_INTERVAL == 0 && isCancelled()) return null
            val current = source[index]
            val next = source.getOrNull(index + 1)
            when (state) {
                State.Code -> when {
                    current == '/' && next == '/' -> {
                        masked[index] = ' '
                        masked[index + 1] = ' '
                        index += 2
                        state = State.LineComment
                        continue
                    }
                    current == '/' && next == '*' -> {
                        masked[index] = ' '
                        masked[index + 1] = ' '
                        index += 2
                        state = State.BlockComment
                        continue
                    }
                    current == '"' || current == '\'' -> {
                        masked[index] = ' '
                        quote = current
                        escaped = false
                        state = State.String
                    }
                }
                State.LineComment -> {
                    if (current == '\n' || current == '\r') {
                        state = State.Code
                    } else {
                        masked[index] = ' '
                    }
                }
                State.BlockComment -> {
                    if (current == '*' && next == '/') {
                        masked[index] = ' '
                        masked[index + 1] = ' '
                        index += 2
                        state = State.Code
                        continue
                    }
                    if (current != '\n' && current != '\r') masked[index] = ' '
                }
                State.String -> {
                    if (current != '\n' && current != '\r') masked[index] = ' '
                    when {
                        escaped -> escaped = false
                        current == '\\' -> escaped = true
                        current == quote -> state = State.Code
                    }
                }
            }
            index++
        }

        return masked.concatToString()
    }

    private const val CANCELLATION_CHECK_INTERVAL = 1024

    private enum class State {
        Code,
        LineComment,
        BlockComment,
        String
    }
}