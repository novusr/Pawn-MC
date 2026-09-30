package com.rvdjv.pawnmc.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * Right-hand workspace panel, laid out like the Visual Studio Code explorer:
 * the opened editors first, then the folder tree of the current workspace, then
 * the workspace-wide search hits.
 */
@Composable
fun XedWorkspacePanel(
    session: XedWorkspaceViewModel,
    onOpenFile: (File) -> Unit,
    onCloseFile: (File) -> Unit,
    onSelectFile: (File) -> Unit,
    onOpenHit: (WorkspaceSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    val workspace = session.activeWorkspace

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
            subtitle = workspace?.root?.path ?: "Open a folder to browse it here"
        )

        if (workspace != null && workspace.documents.isNotEmpty()) {
            SectionLabel("Opened Editors")
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(workspace.documents.toList(), key = { it.file.absolutePath }) { document ->
                    OpenEditorRow(
                        name = document.name,
                        path = document.file.parent?.name.orEmpty(),
                        isActive = document.file.absolutePath == workspace.activePath,
                        isDirty = document.isDirty,
                        onSelect = { onSelectFile(document.file) },
                        onClose = { onCloseFile(document.file) }
                    )
                }
            }
        }

        if (workspace != null) {
            SectionLabel("Explorer")
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(flattenWorkspaceNodes(workspace), key = { it.key }) { entry ->
                    when {
                        entry.node.isDirectory -> FolderRow(
                            node = entry.node,
                            depth = entry.depth,
                            collapsed = !workspace.isExpanded(entry.node),
                            onToggle = { workspace.toggleFolder(entry.node) }
                        )
                        else -> FileRow(
                            node = entry.node,
                            depth = entry.depth,
                            isActive = entry.node.file.absolutePath == workspace.activePath,
                            isDirty = workspace.documentFor(entry.node.file)?.isDirty == true,
                            onOpen = { onOpenFile(entry.node.file) }
                        )
                    }
                }

                if (workspace.searchQuery.isNotEmpty()) {
                    item(key = "__search_results__") {
                        SectionLabel(
                            if (workspace.isSearching) "Searching..." else "Results (${workspace.searchResults.size})"
                        )
                    }
                    items(workspace.searchResults, key = { "${it.file.absolutePath}:${it.line}" }) { hit ->
                        SearchHitRow(hit = hit, onOpen = { onOpenHit(hit) })
                    }
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
                    .background(
                        if (isActive) {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .clickable { session.activateWorkspace(workspace) }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
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
private fun SectionHeader(title: String, subtitle: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
            .background(
                if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer
            )
            .clickable(onClick = onSelect)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Column(modifier = Modifier
            .weight(1f)
            .padding(start = 8.dp)) {
            Text(
                text = if (isDirty) "$name •" else name,
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
        IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
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
            .clickable(onClick = onToggle)
            .padding(start = (4 + depth * 12).dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
    ) {
        Icon(
            imageVector = if (collapsed) Icons.Filled.ChevronRight else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Icon(
            imageVector = if (collapsed) Icons.Filled.Folder else Icons.Filled.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(18.dp)
        )
        Text(
            text = node.file.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
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
            .background(if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onOpen)
            .padding(start = (22 + depth * 12).dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = node.file.name,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = if (isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun SearchHitRow(hit: WorkspaceSearchHit, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
