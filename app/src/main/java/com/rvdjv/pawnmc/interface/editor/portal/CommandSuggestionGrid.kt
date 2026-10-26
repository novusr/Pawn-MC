package com.rvdjv.pawnmc.`interface`.editor.portal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The 3x3 command grid above the portal input.
 *
 * Laid out as a column of fixed-width rows rather than a `LazyVerticalGrid`: the
 * portal card is already a scroll container, nine cells need no recycling, and a
 * nested lazy grid inside a `verticalScroll` parent throws on unbounded height.
 *
 * A partly filled last row keeps the same cell width, so one or two matches do not
 * stretch across the card.
 */
@Composable
internal fun CommandSuggestionGrid(
    cells: List<String>,
    onCellClick: (String) -> Unit
) {
    val rows = cells
        .chunked(PortalCommandParser.SUGGESTION_COLUMNS)
        .take(PortalCommandParser.SUGGESTION_ROWS)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { rowCells ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowCells.forEach { cell ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCellClick(cell) }
                    ) {
                        Text(
                            text = cell,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall
                                .copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                        )
                    }
                }
                repeat(PortalCommandParser.SUGGESTION_COLUMNS - rowCells.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
