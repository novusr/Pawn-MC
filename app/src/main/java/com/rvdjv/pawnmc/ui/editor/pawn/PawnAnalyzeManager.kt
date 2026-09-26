package com.rvdjv.pawnmc.ui.editor.pawn

import com.rvdjv.pawnmc.data.pawn.PawnLanguageRegistry
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.util.Stack

/**
 * High-performance asynchronous background syntax analyzer for Pawn scripts.
 */
class PawnAnalyzeManager : SimpleAnalyzeManager<Any?>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Any?>): Styles {
        val styles = Styles()
        val builder = MappedSpans.Builder()
        val blockStack = Stack<Pair<Int, Int>>()

        var inBlockComment = false
        var currentLine = 0
        var col = 0
        val len = text.length
        var i = 0

        while (i < len && !delegate.isCancelled) {
            val c = text[i]

            if (c == '\r') {
                if (i + 1 < len && text[i + 1] == '\n') {
                    i += 2
                } else {
                    i++
                }
                currentLine++
                col = 0
                continue
            } else if (c == '\n') {
                i++
                currentLine++
                col = 0
                continue
            }

            if (inBlockComment) {
                while (i < len) {
                    val ch = text[i]
                    if (ch == '\r' || ch == '\n') break
                    if (ch == '*' && i + 1 < len && text[i + 1] == '/') {
                        i += 2
                        col += 2
                        inBlockComment = false
                        break
                    }
                    i++
                    col++
                }
                continue
            }

            if (c.isWhitespace()) {
                while (i < len && text[i].isWhitespace() && text[i] != '\r' && text[i] != '\n') {
                    i++
                    col++
                }
                continue
            }

            if (c == '/' && i + 1 < len) {
                val next = text[i + 1]
                if (next == '/') {
                    while (i < len && text[i] != '\r' && text[i] != '\n') {
                        i++
                        col++
                    }
                    continue
                } else if (next == '*') {
                    inBlockComment = true
                    i += 2
                    col += 2
                    continue
                }
            }

            if (c == '#') {
                val startCol = col
                i++
                col++
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_')) {
                    i++
                    col++
                }
                val word = text.subSequence(startCol + 1, i).toString()
                if (PawnLanguageRegistry.isDirective(word) || PawnLanguageRegistry.isDirective("#$word")) {
                    builder.addIfNeeded(currentLine, startCol, TextStyle.makeStyle(EditorColorScheme.ANNOTATION))
                }
                continue
            }

            if (c == '"') {
                builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                i++
                col++
                var escaped = false
                while (i < len) {
                    val ch = text[i]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        i++
                        col++
                        break
                    }
                    i++
                    col++
                }
                continue
            }

            if (c == '\'') {
                builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.LITERAL))
                i++
                col++
                var escaped = false
                while (i < len) {
                    val ch = text[i]
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '\'') {
                        i++
                        col++
                        break
                    }
                    i++
                    col++
                }
                continue
            }

            if (c.isDigit() || (c == '.' && i + 1 < len && text[i + 1].isDigit())) {
                if (c == '0' && i + 1 < len && (text[i + 1] == 'x' || text[i + 1] == 'X')) {
                    i += 2
                    col += 2
                    while (i < len && (text[i].isDigit() || text[i] in 'a'..'f' || text[i] in 'A'..'F')) {
                        i++
                        col++
                    }
                } else {
                    while (i < len && (text[i].isDigit() || text[i] == '.' || text[i] == 'e' || text[i] == 'E')) {
                        i++
                        col++
                    }
                }
                continue
            }

            if (c.isLetter() || c == '_') {
                val startCol = col
                val startIdx = i
                while (i < len && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == ':')) {
                    val ch = text[i]
                    i++
                    col++
                    if (ch == ':') break
                }
                val word = text.subSequence(startIdx, i).toString()

                val style = when {
                    PawnLanguageRegistry.isKeyword(word) -> TextStyle.makeStyle(EditorColorScheme.KEYWORD)
                    PawnLanguageRegistry.isType(word) -> TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_NAME)
                    PawnLanguageRegistry.isConstant(word) -> TextStyle.makeStyle(EditorColorScheme.LITERAL)
                    PawnLanguageRegistry.isFunction(word) -> TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
                    else -> {
                        var peekIdx = i
                        while (peekIdx < len && text[peekIdx].isWhitespace() && text[peekIdx] != '\r' && text[peekIdx] != '\n') {
                            peekIdx++
                        }
                        if (peekIdx < len && text[peekIdx] == '(') {
                            TextStyle.makeStyle(EditorColorScheme.FUNCTION_NAME)
                        } else {
                            TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
                        }
                    }
                }

                builder.addIfNeeded(currentLine, startCol, style)
                continue
            }

            if (c == '{') {
                blockStack.push(currentLine to col)
                builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
                i++
                col++
                continue
            } else if (c == '}') {
                if (blockStack.isNotEmpty()) {
                    val (startL, startC) = blockStack.pop()
                    if (startL != currentLine) {
                        val block = CodeBlock().apply {
                            startLine = startL
                            startColumn = startC
                            endLine = currentLine
                            endColumn = col
                        }
                        styles.addCodeBlock(block)
                    }
                }
                builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
                i++
                col++
                continue
            }

            if (c in "+-*/%!=<>&|^~?:,;") {
                builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.OPERATOR))
                i++
                col++
                continue
            }

            builder.addIfNeeded(currentLine, col, TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL))
            i++
            col++
        }

        builder.addNormalIfNull()
        styles.spans = builder.build()
        styles.finishBuilding()
        return styles
    }
}
