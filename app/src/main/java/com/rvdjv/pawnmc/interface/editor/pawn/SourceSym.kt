package com.rvdjv.pawnmc.`interface`.editor.pawn

import com.rvdjv.pawnmc.data.pawn.PawnRegistry

internal data class PawnLocalFunction(
    val name: String,
    val description: String
)

/** Finds explicit function declarations in the current Pawn source file. */
internal object PawnSourceSymbols {

    private val declarationPattern = Regex(
        """\b(stock|public|forward|native)\s+((?:[A-Za-z_]\w*:)?[A-Za-z_]\w*)\s*\("""
    )

    fun functions(source: CharSequence): List<PawnLocalFunction> {
        val sourceText = source.toString()
        val searchableText = maskCommentsAndStrings(sourceText)
        val functions = mutableListOf<PawnLocalFunction>()

        for (match in declarationPattern.findAll(searchableText)) {
            val openParen = match.range.last
            val closeParen = matchingParen(searchableText, openParen) ?: continue
            val declarationEnd = searchableText.indexOfFirstNonWhitespace(closeParen + 1)
            if (declarationEnd !in searchableText.indices || searchableText[declarationEnd] !in ";{") {
                continue
            }

            val name = match.groupValues[2].substringAfterLast(':')
            if (PawnRegistry.containsSymbol(name)) continue

            val signature = sourceText.substring(match.range.first, closeParen + 1)
                .replace(Regex("\\s+"), " ")
                .trim()
            functions += PawnLocalFunction(name, "Declared in this file: $signature")
        }

        return functions.distinctBy { it.name.lowercase() }
    }

    private fun matchingParen(source: String, openParen: Int): Int? {
        var depth = 0
        for (index in openParen until source.length) {
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

    private fun String.indexOfFirstNonWhitespace(start: Int): Int {
        var index = start
        while (index < length && this[index].isWhitespace()) index++
        return index
    }

    private fun maskCommentsAndStrings(source: String): String {
        val masked = source.toCharArray()
        var index = 0
        var state = State.Code
        var quote = '\u0000'
        var escaped = false

        while (index < source.length) {
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

    private enum class State {
        Code,
        LineComment,
        BlockComment,
        String
    }
}