package com.rvdjv.pawnmc.`interface`.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rvdjv.pawnmc.`interface`._Icons
import java.io.File
import kotlin.math.roundToInt
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
 *
 * ## Scrolling
 *
 * The [LazyColumn] has a bounded height inside the [Column], and each item exposes
 * its content type so the list can reuse compatible rows. Compiler output has a
 * bounded, independently touch-scrollable viewport; vertical and horizontal swipes
 * reveal the complete output without preventing the explorer itself from scrolling.
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
    onOpenFolder: () -> Unit = {},
    onHeaderDrag: (IntOffset) -> Unit = {},
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

    // Separate scroll states keep the explorer and compiler output independently
    // touch-scrollable, including logs wider than the floating panel.
    val explorerListState = rememberLazyListState()
    val outputScrollState = rememberScrollState()
    val outputHorizontalScrollState = rememberScrollState()

    // `documents` is a SnapshotStateList, so `toList()` would hand the list a fresh
    // instance on every recomposition and make it re-diff the rows. Keyed on the
    // size, the snapshot conversion happens only when a tab really opened or closed.
    val openDocuments: List<OpenDocument> = remember(workspace) {
        workspace?.documents?.toList().orEmpty()
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
            onClose = onClosePanel,
            // The folder picker lives in the panel itself, so a workspace folder can
            // be chosen from right where it is browsed. The folder is remembered
            // permanently, unlike the single temporary file the main screen picks.
            onOpenFolder = onOpenFolder,
            onDrag = onHeaderDrag
        )

        LazyColumn(
            state = explorerListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Captured into a local so the null check above survives the lambda: the
            // smart cast of a nullable local is not available inside a composable lambda.
            val current = workspace
            if (current != null && openDocuments.isNotEmpty()) {
                item(key = "__opened_editors__", contentType = "section-label") { SectionLabel("Opened Editors") }

                items(
                    items = openDocuments,
                    key = { doc: OpenDocument -> "open:${doc.file.absolutePath}" },
                    contentType = { "open-document" }
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

                item(key = "__explorer__", contentType = "section-label") { SectionLabel("Explorer") }
            }

            items(
                    items = flatNodes,
                    key = { entry: FlatNode -> "node:${entry.node.key}" },
                    // Lets the list reuse a row composable for a row of the same kind
                    // while scrolling, instead of rebuilding every row it draws.
                    contentType = { entry: FlatNode ->
                        if (entry.node.isDirectory) "folder" else "file"
                    }
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
                item(key = "__compiler_output__", contentType = "compiler-output") {
                    SectionLabel("Output")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .verticalScroll(outputScrollState)
                            .horizontalScroll(outputHorizontalScrollState)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = outputText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = false,
                        )
                    }
                }
            }
            if (current3 != null && current3.searchQuery.isNotEmpty()) {
                item(key = "__search_results__", contentType = "section-label") {
                    SectionLabel(
                        if (current3.isSearching) "Searching..." else "Results (${current3.searchResults.size})"
                    )
                }
                items(
                    items = current3.searchResults,
                    key = { hit: WorkspaceSearchHit -> "hit:${hit.file.absolutePath}:${hit.line}" },
                    contentType = { "search-hit" }
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
                    imageVector = _Icons.FolderRectOpen,
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
                text = "$remaining free",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    onClose: () -> Unit = {},
    onOpenFolder: () -> Unit = {},
    onDrag: (IntOffset) -> Unit = {}
) {
    val currentOnDrag = rememberUpdatedState(onDrag)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    currentOnDrag.value(
                        IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())
                    )
                }
            }
    ) {
        // Grab bar: makes the panel obviously draggable, the same cue the floating
        // compile button and the rectangle toolbar use.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 4.dp)
        ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
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
        IconButton(onClick = onOpenFolder, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = _Icons.FolderRectOpen,
                contentDescription = "Open workspace folder",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close workspace panel",
                modifier = Modifier.size(16.dp)
            )
        }
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
        imageVector = if (isInclude) _Icons.IncludeRect else _Icons.FileRect,
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
            imageVector = if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { rotationZ = if (collapsed) 0f else 90f }
        )
        Icon(
            imageVector = if (collapsed) _Icons.FolderRect else _Icons.FolderRectOpen,
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
