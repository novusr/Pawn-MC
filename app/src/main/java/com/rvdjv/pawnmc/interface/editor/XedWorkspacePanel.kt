package com.rvdjv.pawnmc.`interface`.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvdjv.pawnmc.`interface`.PawnIcons
import java.io.File

/** Minimum height of a touch target in the panel, per the accessibility guidance. */
private val PanelRowMinHeight = 44.dp

/**
 * Floating workspace panel: the opened editors, the folder tree of the active
 * workspace and the workspace-wide search hits, laid out like the Visual Studio Code
 * explorer.
 *
 * Every list now lives in **one** [LazyColumn]. The panel used to render the opened
 * editors and the explorer as two independent scrolling columns that competed for the
 * same vertical gesture: the inner list swallowed the drag, so taps on files and
 * folders never reached their `clickable` and the explorer looked frozen.
 *
 * The tree is remembered against [Workspace.tree] and [Workspace.collapsedPaths]
 * rather than being rebuilt inside the item loop. `flattenWorkspaceNodes` is a plain
 * function, so Compose cannot observe the snapshot reads inside it; without the
 * `remember` the flat list was computed once and folder toggles never recomposed it.
 */
@Composable
fun XedWorkspacePanel(
    session: XedWorkspaceViewModel,
    outputText: String = "",
    onOpenFile: (File) -> Unit,
    onCloseFile: (File) -> Unit,
    onSelectFile: (File) -> Unit,
    onOpenHit: (WorkspaceSearchHit) -> Unit,
    onClosePanel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val workspace = session.activeWorkspace
    val tree = workspace?.tree
    val collapsedPaths = workspace?.collapsedPaths

    // Tracked explicitly so both a fresh scan and a folder toggle invalidate the
    // flattened list together with the rows rendered from it.
    val flatNodes: List<FlatNode> = remember(tree, collapsedPaths, workspace) {
        if (workspace == null) emptyList() else flattenWorkspaceNodes(workspace)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        if (session.openWorkspaces.isNotEmpty()) {
            WorkspaceSwitcherRow(session = session)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        SectionHeader(
            title = (workspace?.name ?: "No workspace").uppercase(),
            subtitle = workspace?.root?.path ?: "Open a folder to browse it here",
            onClose = onClosePanel
        )

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // Captured into a local so the null check above survives the lambda: the
            // smart cast of a nullable local is not available inside a composable lambda.
            val current = workspace
            if (current != null && current.documents.isNotEmpty()) {
                item(key = "__opened_editors__") { SectionLabel("Opened Editors") }

                items(
                    items = current.documents.toList(),
                    key = { doc: OpenDocument -> "open:${doc.file.absolutePath}" }
                ) { document ->
                    OpenEditorRow(
                        name = document.name,
                        path = document.file.parentFile?.name.orEmpty(),
                        isActive = document.file.absolutePath == current.activePath,
                        isDirty = document.isDirty,
                        onSelect = { onSelectFile(document.file) },
                        onClose = { onCloseFile(document.file) }
                    )
                }

                item(key = "__explorer__") { SectionLabel("Explorer") }
            }

            items(
                    items = flatNodes,
                    key = { entry: FlatNode -> "node:${entry.node.key}" }
                ) { entry ->
                val node = entry.node
                val current2 = workspace
                when {
                    node.isDirectory -> FolderRow(
                        node = node,
                        depth = entry.depth,
                        collapsed = current2?.isExpanded(node) == false,
                        onToggle = { current2?.toggleFolder(node) }
                    )
                    else -> FileRow(
                        node = node,
                        depth = entry.depth,
                        isActive = node.file.absolutePath == current2?.activePath,
                        isDirty = current2?.documentFor(node.file)?.isDirty == true,
                        onOpen = { onOpenFile(node.file) }
                    )
                }
            }

            val current3 = workspace
            if (outputText.isNotBlank()) {
                item(key = "__compiler_output__") {
                    SectionLabel("Output")
                    Text(
                        text = outputText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            if (current3 != null && current3.searchQuery.isNotEmpty()) {
                item(key = "__search_results__") {
                    SectionLabel(
                        if (current3.isSearching) "Searching..." else "Results (${current3.searchResults.size})"
                    )
                }
                items(
                    items = current3.searchResults,
                    key = { hit: WorkspaceSearchHit -> "hit:${hit.file.absolutePath}:${hit.line}" }
                ) { hit ->
                    SearchHitRow(hit = hit, onOpen = { onOpenHit(hit) })
                }
            }
        }
    }
}

/**
 * Row of the open workspaces, Visual Studio style: the one in front is marked,
 * tapping another one switches the panel and the editor to it, and the `X`
 * closes that workspace.
 */
@Composable
private fun WorkspaceSwitcherRow(session: XedWorkspaceViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        session.openWorkspaces.forEach { workspace ->
            val isActive = workspace.root.absolutePath == session.activeWorkspace?.root?.absolutePath
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PanelRowMinHeight)
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { session.activateWorkspace(workspace) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = PawnIcons.FolderRectOpen,
                    contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 6.dp)
                )
                IconButton(
                    onClick = { session.closeWorkspace(workspace) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close ${workspace.name}",
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        if (session.openWorkspaces.size < MAX_OPEN_WORKSPACES) {
            val remaining = MAX_OPEN_WORKSPACES - session.openWorkspaces.size
            Text(
                text = "$remaining slot(s) free",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?, onClose: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close workspace panel",
                modifier = Modifier.size(16.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 2.dp)
    )
}

/**
 * Document glyph of a panel row.
 *
 * Include files carry the `+` sheet and every other document the `{}` sheet, so a
 * Pawn source and an `#include` are told apart without reading the file name.
 */
@Composable
private fun documentIcon(extension: String, modifier: Modifier = Modifier) {
    val isInclude = extension.equals("inc", ignoreCase = true)
    Icon(
        imageVector = if (isInclude) PawnIcons.IncludeRect else PawnIcons.FileRect,
        contentDescription = null,
        tint = if (isInclude) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        modifier = modifier.size(18.dp)
    )
}

@Composable
private fun OpenEditorRow(
    name: String,
    path: String,
    isActive: Boolean,
    isDirty: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PanelRowMinHeight)
            .background(
                color = if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp)
    ) {
        documentIcon(name.substringAfterLast('.', ""))

        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (path.isNotEmpty()) {
                Text(
                    text = path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isDirty) {
            Text(
                text = "●",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close $name",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FolderRow(node: WorkspaceNode, depth: Int, collapsed: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PanelRowMinHeight)
            .clickable(onClick = onToggle)
            .padding(start = (4 + depth * 12).dp, end = 8.dp)
    ) {
        // The chevron rotates instead of swapping glyphs, so whether a directory is
        // open or closed is readable at a glance even at 18dp.
        Icon(
            imageVector = if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (collapsed) 0f else 90f }
        )
        Icon(
            imageVector = if (collapsed) PawnIcons.FolderRect else PawnIcons.FolderRectOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
        )
        Text(
            text = node.file.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun FileRow(
    node: WorkspaceNode,
    depth: Int,
    isActive: Boolean,
    isDirty: Boolean,
    onOpen: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PanelRowMinHeight)
            .background(
                color = if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent
            )
            .clickable(onClick = onOpen)
            .padding(start = (26 + depth * 12).dp, end = 8.dp)
    ) {
        documentIcon(node.file.extension)

        Text(
            text = node.file.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )

        if (isDirty) {
            Text(
                text = "●",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SearchHitRow(hit: WorkspaceSearchHit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PanelRowMinHeight)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = "${hit.file.name}:${hit.line}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = hit.preview,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One tree entry paired with its indent depth. */
private data class FlatNode(val node: WorkspaceNode, val depth: Int)

/**
 * Turns the recursive [WorkspaceNode] tree into the flat, depth-annotated list a
 * [LazyColumn] can render, skipping the children of collapsed folders.
 *
 * Must be called from a `remember` keyed on [Workspace.tree] and
 * [Workspace.collapsedPaths]: the snapshot reads happen inside this plain function,
 * so Compose cannot observe them and would otherwise reuse a stale list.
 */
private fun flattenWorkspaceNodes(workspace: Workspace): List<FlatNode> {
    val output = mutableListOf<FlatNode>()

    fun walk(nodes: List<WorkspaceNode>, depth: Int) {
        nodes.forEach { node ->
            output += FlatNode(node, depth)
            if (node.isDirectory && workspace.isExpanded(node)) {
                walk(node.children, depth + 1)
            }
        }
    }

    walk(workspace.tree, 0)
    return output
}
