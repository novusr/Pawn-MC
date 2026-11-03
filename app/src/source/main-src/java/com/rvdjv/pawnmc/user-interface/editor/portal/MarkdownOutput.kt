package com.rvdjv.pawnmc.`user-interface`.editor.portal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvdjv.pawnmc.`user-interface`.theme.status_success

/**
 * Renders the small markdown subset the `docs` page uses.
 *
 * The look follows PawnMC rather than a terminal: headings are real headings (with a rule
 * under `#`), bullets keep a hanging indent so wrapped text lines up, and code blocks are
 * one rounded card instead of a stack of one-line boxes. Everything is built from the
 * Material colour scheme so the portal stays correct in both themes.
 */
@Composable
internal fun MarkdownOutput(markdown: String) {
    // Consecutive code lines belong to one fenced block; collecting them first turns the
    // block into a single surface with one background instead of a ladder of strips.
    val blocks = rememberMarkdownBlocks(markdown)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    Column {
                        Text(
                            text = markdownInline(block.text, bold = true),
                            style = if (block.level == 1) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.titleSmall
                            },
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (block.level == 1) 2.dp else 4.dp)
                        )
                        if (block.level == 1) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(1.dp),
                                modifier = Modifier.fillMaxWidth().height(1.dp)
                            ) {}
                        }
                    }
                }

                is MarkdownBlock.Bullet -> Row(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .width(12.dp)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = markdownInline(block.text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    )
                }

                is MarkdownBlock.Code -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                is MarkdownBlock.Text -> Text(
                    text = markdownInline(block.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                is MarkdownBlock.Spacer -> Spacer(Modifier.height(4.dp))
            }
        }
    }
}

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
    data class Text(val text: String) : MarkdownBlock
    data object Spacer : MarkdownBlock
}

/**
 * Splits [markdown] into renderable blocks, merging fenced code runs into one block.
 *
 * Kept out of the composable so the parsing rules are testable on the JVM.
 */
internal fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val codeLines = mutableListOf<String>()
    var codeLanguage = ""
    var inCodeBlock = false

    fun flushCode() {
        if (codeLines.isNotEmpty()) {
            blocks += MarkdownBlock.Code(codeLanguage, codeLines.joinToString("\n"))
            codeLines.clear()
        }
        codeLanguage = ""
    }

    markdown.lineSequence().forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                if (inCodeBlock) {
                    flushCode()
                    inCodeBlock = false
                } else {
                    inCodeBlock = true
                    codeLanguage = trimmed.removePrefix("```").trim()
                }
            }
            inCodeBlock -> codeLines += line
            line.isBlank() -> if (blocks.lastOrNull() !is MarkdownBlock.Spacer) {
                blocks += MarkdownBlock.Spacer
            }
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 3)
                blocks += MarkdownBlock.Heading(level, trimmed.drop(level).trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks += MarkdownBlock.Bullet(trimmed.drop(2).trim())
            }
            else -> blocks += MarkdownBlock.Text(line.trim())
        }
    }
    flushCode()
    return blocks
}

@Composable
private fun rememberMarkdownBlocks(markdown: String): List<MarkdownBlock> =
    remember(markdown) { parseMarkdownBlocks(markdown) }

/**
 * Expands the inline spans of one markdown line: `**bold**`, `` `code` ``, and the two
 * status markers `__success` / `__error` that the docs page can use inside backticks.
 *
 * Status markers render as a tinted word instead of the literal token, so raw markup never
 * leaks into the transcript when the copy grows a status. Unknown backtick spans stay as
 * a monospace code token.
 */
@Composable
private fun markdownInline(line: String, bold: Boolean = false): AnnotatedString {
    val primary = MaterialTheme.colorScheme.primary
    val success = status_success
    val errorColor = MaterialTheme.colorScheme.error
    val monoSurface = MaterialTheme.colorScheme.surfaceContainerHighest
    return buildAnnotatedString {
        if (bold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        val token = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
        var cursor = 0
        token.findAll(line).forEach { match ->
            append(line.substring(cursor, match.range.first))
            val value = match.value
            val content = if (value.startsWith("**")) {
                value.removeSurrounding("**")
            } else {
                value.removeSurrounding("`")
            }
            val style = when {
                value.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold)
                content == "__success" -> SpanStyle(color = success, fontWeight = FontWeight.SemiBold)
                content == "__error" -> SpanStyle(
                    color = errorColor,
                    fontWeight = FontWeight.SemiBold
                )
                else -> SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = primary,
                    background = monoSurface.copy(alpha = 0.35f)
                )
            }
            pushStyle(style)
            append(if (content.startsWith("__")) content.removePrefix("__") else content)
            pop()
            cursor = match.range.last + 1
        }
        if (cursor < line.length) append(line.substring(cursor))
        if (bold) pop()
    }
}
