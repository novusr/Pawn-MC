package com.pocketsamp.server.user_interface.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration.Reader
import com.pocketsamp.server.configuration._DirectoryManager
import com.pocketsamp.server.service_handler.Status
import com.pocketsamp.server.user_interface.theme.SampThemeColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class FileAction { CREATE_FILE, CREATE_FOLDER, RENAME, DELETE }
private data class ManagedFile(val file: File, val isDirectory: Boolean, val sizeBytes: Long)

/**
 * One visible line of the tree: the entry plus the two things the list needs to draw it.
 *
 * [depth] drives the hanging indent, [expanded] is only meaningful for folders.
 */
private data class FileRow(
    val entry: ManagedFile,
    val depth: Int,
    val expanded: Boolean
)

@Composable
fun _FileManager(status: Status, lang: Locales.Lang) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun t(key: String): String = Locales.text(lang, key)
    val serverActive = status != Status.STOPPED

    var root by remember { mutableStateOf(_DirectoryManager.getServerDirectory(context)) }
    var currentDirectory by remember(root.absolutePath) { mutableStateOf(root) }
    var entries by remember { mutableStateOf<List<ManagedFile>>(emptyList()) }
    /** Folders shown with their children; the root starts open. */
    var expandedPaths by remember(root.absolutePath) { mutableStateOf(setOf(root.absolutePath)) }
    /** A selected folder is also the destination for "new file" / "new folder". */
    var selectedFolder by remember(root.absolutePath) { mutableStateOf<File?>(null) }
    var loading by remember { mutableStateOf(false) }
    var directoryError by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var serverConfigExists by remember { mutableStateOf(false) }
    var refreshToken by remember { mutableStateOf(0) }
    var requestedInitialFolder by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<FileAction?>(null) }
    var selectedEntry by remember { mutableStateOf<File?>(null) }
    /** Folder a create action was started from, frozen when the dialog opens. */
    var createTarget by remember { mutableStateOf(root) }
    val selectedItem = selectedEntry?.let { selected ->
        entries.firstOrNull { it.file.absolutePath == selected.absolutePath }
    }
    var name by remember { mutableStateOf("") }
    val lifecycleOwner = context as? LifecycleOwner

    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) refreshToken++
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val directory = _DirectoryManager.parseTreeUriToFile(uri)
            if (directory == null || !directory.isDirectory) {
                operationError = t("files.folder.failed")
            } else if (serverActive) {
                operationError = t("files.folder.running")
            } else if (!_DirectoryManager.setServerDirectory(context, directory)) {
                operationError = t("files.operation.failed")
            } else {
                root = directory
                currentDirectory = directory
                expandedPaths = setOf(directory.absolutePath)
                selectedFolder = null
                operationError = null
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!_DirectoryManager.hasCustomServerDirectory(context) && !requestedInitialFolder) {
            requestedInitialFolder = true
            folderPicker.launch(null)
        }
    }

    LaunchedEffect(root.absolutePath, currentDirectory.absolutePath, refreshToken) {
        loading = true
        directoryError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val canonicalRoot = root.canonicalFile
                val canonicalCurrent = currentDirectory.canonicalFile
                require(isWithin(canonicalRoot, canonicalCurrent)) { t("files.outside.root") }
                require(canonicalCurrent.isDirectory && canonicalCurrent.canRead()) { t("files.read.denied") }
                serverConfigExists = File(canonicalRoot, "server.cfg").isFile
                canonicalCurrent.listFiles()
                    ?.filter { isWithin(canonicalRoot, it.canonicalFile) }
                    ?.map { ManagedFile(it, it.isDirectory, it.length()) }
                    ?.sortedWith(compareBy<ManagedFile> { !it.isDirectory }.thenBy { it.file.name.lowercase() })
                    ?: error(t("files.read.denied"))
            }
        }
        result.onSuccess {
            entries = it
            if (selectedEntry != null && it.none { entry -> entry.file.absolutePath == selectedEntry?.absolutePath }) {
                selectedEntry = null
            }
        }
            .onFailure {
                entries = emptyList()
                directoryError = if (it is SecurityException) {
                    t("files.permission.denied")
                } else {
                    it.message ?: t("files.read.failed")
                }
            }
        loading = false
    }

    fun openXed(item: File, workspace: Boolean) {
        val isValidItem = if (workspace) item.isDirectory else item.isFile
        if (!isValidItem) {
            operationError = t("files.item.unavailable")
            return
        }
        if (!workspace && item.extension.lowercase() !in EDITABLE_EXTENSIONS) {
            operationError = t("files.edit.unsupported")
            return
        }
        runCatching {
            context.startActivity(
                android.content.Intent().apply {
                    setClassName(
                        context.packageName,
                        "com.rvdjv.pawnmc.interface.main.MainActivity"
                    )
                    this.action = ACTION_OPEN_XED
                    putExtra(EXTRA_XED_PATH, item.absolutePath)
                    putExtra(EXTRA_XED_WORKSPACE, workspace)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            )
        }.onFailure {
            operationError = "${t("files.open.failed")}: ${it.localizedMessage.orEmpty()}"
        }
    }

    /**
     * Folder the next create action writes into.
     *
     * A selected folder wins - that is what makes tapping a nested folder and then "New
     * file" land inside it instead of silently going back to the root. Without a
     * selection the folder currently open is used.
     */
    fun createDestination(): File = createDestinationFor(root, currentDirectory, selectedFolder)

    fun startCreate(kind: FileAction) {
        createTarget = createDestination()
        name = ""
        action = kind
    }

    fun toggleFolder(folder: File) {
        val path = folder.absolutePath
        expandedPaths = if (path in expandedPaths) expandedPaths - path else expandedPaths + path
    }

    /**
     * Flattens the tree into the visible rows.
     *
     * Children are listed straight from the disk when a folder is expanded, so a folder
     * that changed since the last scan shows its current content without a full refresh.
     */
    fun buildRows(nodes: List<ManagedFile>, depth: Int): List<FileRow> {
        val rows = mutableListOf<FileRow>()
        nodes.forEach { node ->
            val isExpanded = node.isDirectory && node.file.absolutePath in expandedPaths
            rows += FileRow(node, depth, isExpanded)
            if (isExpanded) {
                val children = node.file.listFiles()
                    ?.filter { isWithin(root.canonicalFile, it.canonicalFile) }
                    ?.map { ManagedFile(it, it.isDirectory, it.length()) }
                    ?.sortedWith(compareBy<ManagedFile> { !it.isDirectory }.thenBy { it.file.name.lowercase() })
                    .orEmpty()
                rows += buildRows(children, depth + 1)
            }
        }
        return rows
    }

    val rows = remember(entries, expandedPaths, root.absolutePath) { buildRows(entries, 0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("files.title"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SampThemeColors.textPrimary)
                Text(
                    if (selectedFolder != null) selectedFolder!!.absolutePath else currentDirectory.absolutePath,
                    fontSize = 11.sp,
                    color = SampThemeColors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { refreshToken++ }) {
                Icon(Icons.Default.Refresh, contentDescription = t("files.refresh"), tint = SampThemeColors.accent)
            }
            IconButton(
                enabled = !serverActive,
                onClick = { folderPicker.launch(null) }
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = t("files.choose.root"), tint = SampThemeColors.accent)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SampThemeColors.card)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (serverConfigExists) t("files.server.ready") else t("files.server.missing"),
                    modifier = Modifier.weight(1f),
                    color = if (serverConfigExists) SampThemeColors.success else SampThemeColors.warn,
                    style = MaterialTheme.typography.bodySmall
                )
                if (!serverConfigExists) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val created = withContext(Dispatchers.IO) {
                                    runCatching { Reader.ensureDefaultConfig(root) }.isSuccess
                                }
                                if (created) {
                                    operationError = null
                                    refreshToken++
                                } else {
                                    operationError = t("files.create.failed")
                                }
                            }
                        }
                    ) {
                        Text(t("files.create.defaults"), fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = currentDirectory.absolutePath != root.absolutePath,
                onClick = {
                    currentDirectory.parentFile?.let { parent ->
                        if (isWithin(root.canonicalFile, parent.canonicalFile)) {
                            currentDirectory = parent
                            selectedFolder = null
                            selectedEntry = null
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(t("files.parent"), maxLines = 1)
            }
            OutlinedButton(onClick = { startCreate(FileAction.CREATE_FOLDER) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(t("files.new.folder"), maxLines = 1)
            }
            OutlinedButton(onClick = { startCreate(FileAction.CREATE_FILE) }, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(t("files.new.file"), maxLines = 1)
            }
        }

        Text(
            text = t("files.target") + ": " + createDestination().name,
            style = MaterialTheme.typography.labelSmall,
            color = SampThemeColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        operationError?.let { ErrorMessage(it) }
        directoryError?.let { ErrorMessage("${t("files.read.failed")}: $it") }

        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SampThemeColors.accent)
            }
            directoryError == null && entries.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(t("files.empty"), color = SampThemeColors.textSecondary)
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 60.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rows, key = { it.entry.file.absolutePath }) { row ->
                        val entry = row.entry
                        FileEntry(
                            entry = entry,
                            depth = row.depth,
                            expanded = row.expanded,
                            selected = selectedEntry?.absolutePath == entry.file.absolutePath,
                            folderSelected = entry.isDirectory &&
                                selectedFolder?.absolutePath == entry.file.absolutePath,
                            onSelect = {
                                selectedEntry = entry.file
                                // Selecting a folder also aims the create buttons at it.
                                selectedFolder = if (entry.isDirectory) entry.file else null
                            },
                            onToggle = {
                                if (entry.isDirectory) {
                                    toggleFolder(entry.file)
                                    selectedFolder = entry.file
                                }
                            },
                            onOpenWorkspace = { openXed(entry.file, workspace = true) },
                            onEdit = { openXed(entry.file, workspace = false) },
                            onRename = {
                                selectedEntry = entry.file
                                name = entry.file.name
                                action = FileAction.RENAME
                            },
                            onDelete = {
                                selectedEntry = entry.file
                                action = FileAction.DELETE
                            },
                            renameLabel = t("files.rename"),
                            deleteLabel = t("files.delete"),
                            editLabel = t("files.edit"),
                            workspaceLabel = t("files.open.workspace"),
                            folderLabel = t("files.folder"),
                            expandLabel = t("files.expand"),
                            collapseLabel = t("files.collapse")
                        )
                    }
                }
                selectedItem?.let { selected ->
                    val selectedFile = selected.file
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 58.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selected.isDirectory) {
                            OutlinedButton(
                                onClick = { currentDirectory = selectedFile; selectedEntry = null; selectedFolder = selectedFile },
                                modifier = Modifier.weight(1f)
                            ) { Text(t("files.browse"), maxLines = 1) }
                            OutlinedButton(
                                onClick = { openXed(selectedFile, workspace = true) },
                                modifier = Modifier.weight(1f)
                            ) { Text(t("files.open.workspace"), maxLines = 1) }
                        } else {
                            OutlinedButton(
                                onClick = { openXed(selectedFile, workspace = false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(t("files.edit"), maxLines = 1)
                            }
                        }
                        IconButton(
                            onClick = {
                                name = selectedFile.name
                                action = FileAction.RENAME
                            }
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, contentDescription = t("files.rename"))
                        }
                        IconButton(onClick = { action = FileAction.DELETE }) {
                            Icon(Icons.Default.Delete, contentDescription = t("files.delete"), tint = SampThemeColors.danger)
                        }
                    }
                }
            }
        }
    }

    val activeAction = action
    if (activeAction != null) {
        val deleting = activeAction == FileAction.DELETE
        AlertDialog(
            onDismissRequest = { action = null },
            title = {
                Text(
                    when (activeAction) {
                        FileAction.CREATE_FILE -> t("files.new.file")
                        FileAction.CREATE_FOLDER -> t("files.new.folder")
                        FileAction.RENAME -> t("files.rename")
                        FileAction.DELETE -> t("files.delete")
                    }
                )
            },
            text = {
                if (deleting) {
                    Text(t("files.delete.confirm").replace("{name}", selectedEntry?.name.orEmpty()))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeAction != FileAction.RENAME) {
                            Text(
                                text = "${t("files.target")}: ${createTarget.absolutePath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = SampThemeColors.textMuted
                            )
                        }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(t("files.name")) },
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = selectedEntry
                        val requestedName = name.trim()
                        val safeRoot = root
                        // Frozen when the dialog opened, so a refresh in the background
                        // cannot move a create action to a different folder mid-typing.
                        val newParent = createTarget
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val safeRootCanonical = safeRoot.canonicalFile
                                    if (activeAction == FileAction.DELETE) {
                                        require(target != null && isWithin(safeRootCanonical, target.canonicalFile)) { t("files.outside.root") }
                                        require(deleteEntry(target, safeRootCanonical)) { t("files.delete.failed") }
                                    } else {
                                        require(isSafeName(requestedName)) { t("files.invalid.name") }
                                        val parent = if (activeAction == FileAction.RENAME) {
                                            require(target != null && isWithin(safeRootCanonical, target.canonicalFile)) { t("files.outside.root") }
                                            target.parentFile ?: error(t("files.operation.failed"))
                                        } else {
                                            require(isWithin(safeRootCanonical, newParent.canonicalFile)) { t("files.outside.root") }
                                            require(newParent.isDirectory) { t("files.folder.failed") }
                                            newParent
                                        }
                                        val destination = File(parent, requestedName)
                                        require(isWithin(safeRootCanonical, destination.canonicalFile)) { t("files.outside.root") }
                                        when (activeAction) {
                                            FileAction.CREATE_FILE -> {
                                                require(!destination.exists()) { t("files.name.exists") }
                                                require(destination.createNewFile()) { t("files.create.failed") }
                                            }
                                            FileAction.CREATE_FOLDER -> {
                                                require(!destination.exists()) { t("files.name.exists") }
                                                require(destination.mkdir()) { t("files.create.failed") }
                                                // Open the new folder so its content is directly visible.
                                                expandedPaths = expandedPaths + parent.absolutePath
                                            }
                                            FileAction.RENAME -> {
                                                require(!destination.exists()) { t("files.name.exists") }
                                                require(target!!.renameTo(destination)) { t("files.rename.failed") }
                                            }
                                            FileAction.DELETE -> Unit
                                        }
                                    }
                                }
                            }
                            result.onSuccess {
                                operationError = null
                                refreshToken++
                            }.onFailure {
                                operationError = if (it is SecurityException) {
                                    t("files.permission.denied")
                                } else {
                                    it.message ?: t("files.operation.failed")
                                }
                            }
                            action = null
                        }
                    }
                ) { Text(if (deleting) t("files.delete") else t("files.save")) }
            },
            dismissButton = { TextButton(onClick = { action = null }) { Text(t("files.cancel")) } }
        )
    }
}

@Composable
private fun FileEntry(
    entry: ManagedFile,
    depth: Int,
    expanded: Boolean,
    selected: Boolean,
    folderSelected: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    renameLabel: String,
    deleteLabel: String,
    editLabel: String,
    workspaceLabel: String,
    folderLabel: String,
    expandLabel: String,
    collapseLabel: String
) {
    val file = entry.file
    var showActions by remember(file.absolutePath) { mutableStateOf(false) }
    val highlight = selected || folderSelected
    Card(colors = CardDefaults.cardColors(containerColor = SampThemeColors.card)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 16).dp)
                .clickable(onClick = onSelect)
                .background(if (highlight) SampThemeColors.accent.copy(alpha = 0.12f) else Color.Transparent)
                .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (entry.isDirectory) {
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandMore
                        else Icons.Default.ChevronRight,
                        contentDescription = if (expanded) collapseLabel else expandLabel,
                        tint = SampThemeColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(Modifier.size(26.dp))
            }
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = SampThemeColors.accent,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(file.name, color = SampThemeColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (entry.isDirectory) folderLabel else formatSize(entry.sizeBytes),
                    color = SampThemeColors.textMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Box {
                IconButton(onClick = { showActions = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = SampThemeColors.textSecondary)
                }
                DropdownMenu(
                    expanded = showActions,
                    onDismissRequest = { showActions = false }
                ) {
                    if (entry.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(if (expanded) collapseLabel else expandLabel) },
                            leadingIcon = {
                                Icon(
                                    if (expanded) Icons.Default.ExpandMore
                                    else Icons.Default.ChevronRight,
                                    contentDescription = null
                                )
                            },
                            onClick = { showActions = false; onToggle() }
                        )
                        DropdownMenuItem(
                            text = { Text(workspaceLabel) },
                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            onClick = { showActions = false; onOpenWorkspace() }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(editLabel) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showActions = false; onEdit() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(renameLabel) },
                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null) },
                        onClick = { showActions = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(deleteLabel) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { showActions = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorMessage(message: String) {
    Text(message, color = SampThemeColors.danger, style = MaterialTheme.typography.bodySmall)
}

/**
 * Resolves where "new file"/"new folder" should be created.
 *
 * The selected folder always wins over the folder currently open, because the user picked
 * it on purpose (usually a nested folder). A selection that is not a real folder inside
 * the workspace root - deleted meanwhile, or outside the root - is ignored, so the create
 * action falls back to the open folder instead of failing or escaping the tree.
 */
internal fun createDestinationFor(root: File, currentDirectory: File, selectedFolder: File?): File {
    if (selectedFolder == null || !selectedFolder.isDirectory) return currentDirectory
    return runCatching {
        if (isWithin(root.canonicalFile, selectedFolder.canonicalFile)) selectedFolder else currentDirectory
    }.getOrDefault(currentDirectory)
}

private fun isWithin(root: File, candidate: File): Boolean {
    val rootPath = root.canonicalPath
    val candidatePath = candidate.canonicalPath
    return candidatePath == rootPath || candidatePath.startsWith(rootPath + File.separator)
}

private fun isSafeName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') &&
        name.none { it.isISOControl() }

private fun deleteEntry(file: File, root: File): Boolean {
    if (!isWithin(root, file.canonicalFile)) return false
    if (!file.isDirectory || file.canonicalFile != file.absoluteFile) {
        return file.delete()
    }
    val children = file.listFiles() ?: return false
    for (child in children) {
        if (!deleteEntry(child, root)) return false
    }
    return file.delete()
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${bytes / (1024 * 1024)} MB"
}

private const val ACTION_OPEN_XED = "com.rvdjv.pawnmc.action.OPEN_XED_PATH"
private const val EXTRA_XED_PATH = "com.rvdjv.pawnmc.extra.XED_PATH"
private const val EXTRA_XED_WORKSPACE = "com.rvdjv.pawnmc.extra.XED_WORKSPACE"
private val EDITABLE_EXTENSIONS = setOf(
    "pwn", "pawn", "inc", "p", "txt", "cfg", "ini", "json", "sql", "log", "xml", "yml", "yaml"
)
