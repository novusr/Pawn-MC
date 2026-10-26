package com.rvdjv.pawnmc.`interface`.editor.portal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders the small markdown subset the `docs` page uses.
 *
 * This is not a general parser. It understands headings, fenced code blocks,
 * bullets, `**bold**` and `` `code` `` - the only constructs the shipped
 * documentation contains. Pulling a markdown library in for that would cost more APK
 * than the whole portal.
 */
@Composable
internal fun MarkdownOutput(markdown: String) {
    var inCodeBlock = false
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        markdown.lineSequence().forEach { line ->
            when {
                line.trimStart().startsWith("```") -> inCodeBlock = !inCodeBlock
                line.isBlank() -> Spacer(Modifier.height(3.dp))
                inCodeBlock -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        line,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall
                            .copy(fontFamily = FontFamily.Monospace)
                    )
                }
                line.startsWith("#") -> Text(
                    text = line.trimStart('#', '),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                line.startsWith("- ") || line.startsWith("* ") -> Text(
                    text = "- ${line.drop(2)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                else -> Text(
                    text = markdownInline(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** Expands the inline spans of one markdown line. */
@Composable
private fun markdownInline(line: String) = buildAnnotatedString {
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
        val style = if (value.startsWith("**")) {
            SpanStyle(fontWeight = FontWeight.Bold)
        } else {
            SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        pushStyle(style)
        append(content)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
