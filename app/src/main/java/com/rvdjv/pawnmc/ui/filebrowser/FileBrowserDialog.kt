package com.rvdjv.pawnmc.ui.filebrowser

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File
import java.text.DecimalFormat

enum class FileBrowserMode { FILE, FOLDER }

data class FileEntry(val file: File, val isParent: Boolean = false)

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    val idx = digitGroups.coerceAtMost(units.size - 1)
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, idx.toDouble())) + " " + units[idx]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    mode: FileBrowserMode,
    fileExtensions: Set<String> = setOf("pwn", "p", "inc"),
    onFileSelected: (String) -> Unit,
    onFolderSelected: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = remember { CompilerConfig.getInstance(context) }
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    fun listEntries(dir: File): List<FileEntry> {
        val result = mutableListOf<FileEntry>()
        if (dir.absolutePath.length > rootPath.length && dir.absolutePath.startsWith(rootPath)) {
            val parent = dir.parentFile
            if (parent != null && parent.canRead()) {
                result.add(FileEntry(parent, isParent = true))
            }
        }
        val files = dir.listFiles() ?: emptyArray()
        val filtered = files
            .filter { !it.name.startsWith(".") }
            .filter { file ->
                if (file.isDirectory) true
                else if (mode == FileBrowserMode.FILE) file.extension.lowercase() in fileExtensions
                else false
            }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
        filtered.forEach { result.add(FileEntry(it, isParent = false)) }
        return result
    }

    fun buildBreadcrumbs(dir: File): List<File> {
        val crumbs = mutableListOf<File>()
        var current: File? = dir
        while (current != null) {
            val isRoot = current.absolutePath == rootPath
            val isOutsideRoot = !current.absolutePath.startsWith(rootPath)
            crumbs.add(0, current)
            if (isRoot || isOutsideRoot || current.parentFile == null) break
            current = current.parentFile
        }
        return crumbs
    }

    var currentDir by remember {
        val startPath = config.lastOpenedDirPath ?: rootPath
        var start = File(startPath)
        if (!start.exists() || !start.isDirectory) start = File(rootPath)
        mutableStateOf(start)
    }
    var entries by remember { mutableStateOf(listEntries(currentDir)) }
    var breadcrumbs by remember { mutableStateOf(buildBreadcrumbs(currentDir)) }

    fun navigateTo(dir: File) {
        currentDir = dir
        config.lastOpenedDirPath = dir.absolutePath
        entries = listEntries(dir)
        breadcrumbs = buildBreadcrumbs(dir)
    }

    fun navigateUp(): Boolean {
        if (currentDir.absolutePath.length > rootPath.length && currentDir.absolutePath.startsWith(rootPath)) {
            val parent = currentDir.parentFile
            if (parent != null && parent.canRead()) {
                navigateTo(parent)
                return true
            }
        }
        return false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(if (mode == FileBrowserMode.FILE) "Select File" else "Select Folder") },
                    navigationIcon = {
                        IconButton(onClick = { if (!navigateUp()) onDismiss() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(breadcrumbs) { index, dir ->
                        val isLast = index == breadcrumbs.lastIndex
                        val label = if (dir.absolutePath == rootPath) "Internal Storage" else dir.name
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                color = if (isLast) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable(enabled = !isLast) { navigateTo(dir) }
                            )
                            if (!isLast) {
                                Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                HorizontalDivider()

                val hasAnyEntries = entries.any { !it.isParent }
                if (!hasAnyEntries) {
                    val hasAnyFilesAtAll = (currentDir.listFiles()?.isNotEmpty()) == true
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (hasAnyFilesAtAll) Icons.Filled.InsertDriveFile else Icons.Filled.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (hasAnyFilesAtAll) "No Matching Files" else "Empty Folder",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = if (hasAnyFilesAtAll) "There are no files with the required extension."
                                   else "There are no files in this folder.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(entries) { entry ->
                            FileEntryRow(entry) {
                                if (entry.file.isDirectory) {
                                    navigateTo(entry.file)
                                } else if (mode == FileBrowserMode.FILE) {
                                    onFileSelected(entry.file.absolutePath)
                                }
                            }
                        }
                    }
                }

                if (mode == FileBrowserMode.FOLDER) {
                    Button(
                        onClick = { onFolderSelected(currentDir.absolutePath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Select This Folder")
                    }
                }
            }
        }
    }
}

@Composable
private fun FileEntryRow(entry: FileEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when {
            entry.isParent -> Icons.Filled.ArrowUpward
            entry.file.isDirectory -> Icons.Filled.FolderOpen
            else -> Icons.Filled.InsertDriveFile
        }
        val iconTint = if (entry.file.isDirectory && !entry.isParent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        val bgTint = if (entry.file.isDirectory && !entry.isParent) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

        Surface(shape = RoundedCornerShape(8.dp), color = bgTint, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconTint)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (entry.isParent) ".." else entry.file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!entry.isParent) {
                val infoText = if (entry.file.isDirectory) {
                    "${entry.file.listFiles()?.size ?: 0} items"
                } else {
                    formatFileSize(entry.file.length())
                }
                Text(
                    text = infoText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!entry.isParent && entry.file.isDirectory) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
