package com.rvdjv.pawnmc.`interface`.editor.xedapi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** What the floating top-right tool panel does. */
enum class ToolPanelMode {
    /** Searches the whole workspace; results land in the explorer panel. */
    SearchWorkspace,

    /** Replaces inside the file currently open in the editor. */
    ReplacePerFile,

    /** Replaces inside every file of the workspace. */
    ReplaceAllFiles
}

/**
 * Floating panel anchored in the upper-right corner of the editor area, holding
 * the find/replace inputs of the workspace tools and an `X` to dismiss it.
 */
@Composable
fun ToolPanel(
    mode: ToolPanelMode,
    find: String,
    replace: String,
    isBusy: Boolean,
    onFindChange: (String) -> Unit,
    onReplaceChange: (String) -> Unit,
    onRun: () -> Unit,
    onRunSecondary: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = when (mode) {
        ToolPanelMode.SearchWorkspace -> "Search in Workspace"
        ToolPanelMode.ReplacePerFile -> "Replace Words per File"
        ToolPanelMode.ReplaceAllFiles -> "Replace Words All Files"
    }
    val primaryLabel = when (mode) {
        ToolPanelMode.SearchWorkspace -> "Search"
        ToolPanelMode.ReplacePerFile -> "Replace All in File"
        ToolPanelMode.ReplaceAllFiles -> "Replace in All Files"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("tool_panel_close")
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close panel")
                }
            }

            OutlinedTextField(
                value = find,
                onValueChange = onFindChange,
                label = { Text("Find") },
                singleLine = true,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth()
            )

            if (mode != ToolPanelMode.SearchWorkspace) {
                OutlinedTextField(
                    value = replace,
                    onValueChange = onReplaceChange,
                    label = { Text("Replace with") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                if (onRunSecondary != null) {
                    TextButton(onClick = onRunSecondary, enabled = !isBusy && find.isNotEmpty()) {
                        Text("Replace First")
                    }
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.width(72.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onRun,
                    enabled = !isBusy && find.isNotEmpty()
                ) {
                    Text(if (isBusy) "Working..." else primaryLabel)
                }
            }

            if (mode == ToolPanelMode.SearchWorkspace) {
                Text(
                    text = "Results are listed in the workspace panel.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
