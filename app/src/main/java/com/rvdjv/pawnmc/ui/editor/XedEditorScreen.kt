package com.rvdjv.pawnmc.ui.editor

import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.WrapText
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rvdjv.pawnmc.ui.PawnIcons
import com.rvdjv.pawnmc.ui.editor.pawn.PawnLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedEditorScreen(
    viewModel: XedEditorViewModel,
    onNavigateBack: () -> Unit,
    editorBackgroundColor: String? = null,
    onCompileRequest: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val appColorScheme = MaterialTheme.colorScheme
    val editorScheme = remember(appColorScheme, editorBackgroundColor) {
        buildEditorScheme(appColorScheme, editorBackgroundColor)
    }
    // Tracks which scheme instance is currently applied to the native CodeEditor so
    // `update` only re-tints when the theme actually changed. Deliberately a plain
    // holder (not snapshot state) because it is written during composition.
    val appliedScheme = remember { EditorSchemeHolder() }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var cursorLine by remember { mutableIntStateOf(1) }
    var cursorCol by remember { mutableIntStateOf(1) }

    var showExitDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplaceRow by remember { mutableStateOf(false) }

    var isWordWrap by remember { mutableStateOf(false) }
    var isReadOnly by remember { mutableStateOf(false) }
    var isLineNumbers by remember { mutableStateOf(true) }

    // ------------------------------------------------------------------
    // Workspace: several files/folders open at once, Visual Studio style.
    // ------------------------------------------------------------------
    val workspace = viewModel.workspace
    val scope = rememberCoroutineScope()

    var isExplorerVisible by remember { mutableStateOf(false) }
    var explorerWidth by remember { mutableFloatStateOf(320f) }
    var toolPanelMode by remember { mutableStateOf<XedToolPanelMode?>(null) }
    var toolFind by remember { mutableStateOf("") }
    var toolReplace by remember { mutableStateOf("") }
    var isToolBusy by remember { mutableStateOf(false) }
    // Bumped when a tool rewrote the file behind the editor, so the widget is
    // reloaded even though the document instance itself did not change.
    var editorReloadToken by remember { mutableIntStateOf(0) }
    // Tracks which buffer the native editor is showing, so switching workspace
    // files pushes new text only when the file really changed.
    val loadedBuffer = remember { LoadedBufferHolder() }

    val activeDocument = workspace.activeDocument
    val hasUnsavedChanges =
        if (workspace.isWorkspaceOpen) workspace.hasDirtyDocuments else viewModel.hasUnsavedChanges

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pickedUri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val folder = resolveTreeToFile(context, pickedUri)
            if (folder != null) {
                // Opening a folder replaces whatever single file was open before.
                workspace.openWorkspace(folder)
                isExplorerVisible = true
            } else {
                Toast.makeText(
                    context,
                    "That folder is not on local storage, Xed cannot browse it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(workspace.statusMessage) {
        workspace.statusMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            workspace.clearStatusMessage()
        }
    }

    /** Saves the file shown in the editor, in whichever mode is active. */
    val saveActiveFile: () -> Unit = {
        val document = workspace.activeDocument
        if (document != null) {
            scope.launch {
                workspace.saveDocument(document)
                workspace.refreshTree()
            }
        } else {
            viewModel.saveFile(editorRef?.text?.toString() ?: "")
        }
    }

    val openWorkspaceFile: (File) -> Unit = { file ->
        workspace.openFile(file)
        isExplorerVisible = true
    }

    /** Searches the whole workspace; hits are listed in the explorer panel. */
    val runWorkspaceSearch: () -> Unit = {
        isExplorerVisible = true
        workspace.search(toolFind)
    }

    /**
     * Replaces inside the file currently open.
     *
     * In single-file mode the editor's own searcher is used so the change lands in
     * the undo history; in workspace mode the file is rewritten on disk and the
     * editor buffer is refreshed from it.
     */
    val runReplacePerFile: (Boolean) -> Unit = { allOccurrences ->
        if (toolFind.isNotEmpty()) {
            val editor = editorRef
            val document = workspace.activeDocument
            if (document == null) {
                // Single-file mode: go through the editor searcher so the change
                // stays in the undo history.
                editor?.searcher?.apply {
                    search(toolFind, EditorSearcher.SearchOptions(false, false))
                    if (allOccurrences) replaceAll(toolReplace) else replaceThis(toolReplace)
                    stopSearch()
                }
            } else {
                isToolBusy = true
                scope.launch {
                    workspace.replaceInFile(document.file, toolFind, toolReplace, allOccurrences)
                    editorReloadToken++
                    isToolBusy = false
                }
            }
        }
    }

    /** Replaces inside every file of the workspace and reloads the open buffer. */
    val runReplaceAllFiles: () -> Unit = {
        if (toolFind.isNotEmpty()) {
            isToolBusy = true
            scope.launch {
                workspace.replaceInAllFiles(toolFind, toolReplace)
                workspace.refreshTree()
                editorReloadToken++
                isToolBusy = false
            }
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        val targetUri = uri ?: return@rememberLauncherForActivityResult
        val content = editorRef?.text?.toString() ?: return@rememberLauncherForActivityResult

        try {
            context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            Toast.makeText(
                context,
                "Saved as: ${targetUri.path ?: viewModel.fileName}",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Save As failed: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    BackHandler {
        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(viewModel.statusMessage) {
        viewModel.statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearStatusMessage()
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Unsaved Changes") },
            text = {
                Text(
                    if (workspace.isWorkspaceOpen) {
                        "Some files of this workspace have unsaved modifications. Do you want to save them before closing?"
                    } else {
                        "File '${viewModel.fileName}' has unsaved modifications. Do you want to save before closing?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (workspace.isWorkspaceOpen) {
                            scope.launch {
                                // Every open workspace, not just the one in front.
                                workspace.openWorkspaces.forEach { workspace.saveAll(it) }
                                showExitDialog = false
                                onNavigateBack()
                            }
                        } else {
                            val currentText = editorRef?.text?.toString() ?: ""
                            viewModel.saveFile(currentText) { success ->
                                showExitDialog = false
                                if (success) {
                                    onNavigateBack()
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("dialog_save_button")
                ) {
                    Text(if (workspace.isWorkspaceOpen) "Save All" else "Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showExitDialog = false
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("dialog_discard_button")
                    ) {
                        Text("Discard")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showExitDialog = false },
                        modifier = Modifier.testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    if (showJumpDialog) {
        var lineInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Jump to Line") },
            text = {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Line number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetLine = lineInput.toIntOrNull()
                        if (targetLine != null && targetLine > 0) {
                            editorRef?.jumpToLine(targetLine.coerceAtLeast(1) - 1)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        // The scaffold keeps the page tone so the brighter code area in the middle
        // reads as its own surface, with toolbar/status bars one step above it.
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = (activeDocument?.name ?: viewModel.fileName) +
                                if (hasUnsavedChanges) " *" else "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = activeDocument?.file?.path ?: viewModel.filePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (hasUnsavedChanges) {
                                showExitDialog = true
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editorRef?.undo()
                            canUndo = editorRef?.canUndo() == true
                            canRedo = editorRef?.canRedo() == true
                        },
                        enabled = canUndo,
                        modifier = Modifier.testTag("editor_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo"
                        )
                    }

                    IconButton(
                        onClick = {
                            editorRef?.redo()
                            canUndo = editorRef?.canUndo() == true
                            canRedo = editorRef?.canRedo() == true
                        },
                        enabled = canRedo,
                        modifier = Modifier.testTag("editor_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo"
                        )
                    }

                    // Search, Open Workspace, Save and Save As/Save All live on the
                    // floating rectangle toolbar above the compile button, so the top
                    // bar only keeps history and the overflow menu.
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(PawnIcons.Settings, contentDescription = "Editor options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Workspace Folder...") },
                                leadingIcon = {
                                    Icon(PawnIcons.Workspace, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    folderPickerLauncher.launch(buildOpenFolderIntent())
                                }
                            )
                            if (workspace.openWorkspaces.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (workspace.openWorkspaces.size >= MAX_OPEN_WORKSPACES) {
                                                "Close Workspace (max $MAX_OPEN_WORKSPACES reached)"
                                            } else {
                                                "Close Workspace"
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        val closing = workspace.activeWorkspace
                                        if (closing != null) {
                                            workspace.closeWorkspace(closing)
                                        }
                                        isExplorerVisible = workspace.isWorkspaceOpen
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Close All Workspaces") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        workspace.closeAllWorkspaces()
                                        isExplorerVisible = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Search in Workspace") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    toolPanelMode = XedToolPanelMode.SearchWorkspace
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Replace Words per File") },
                                leadingIcon = {
                                    Icon(Icons.Filled.FindReplace, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    toolPanelMode = XedToolPanelMode.ReplacePerFile
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Replace Words All Files") },
                                leadingIcon = {
                                    Icon(Icons.Filled.FindReplace, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    toolPanelMode = XedToolPanelMode.ReplaceAllFiles
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isExplorerVisible) "Hide Workspace Panel" else "Show Workspace Panel") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isExplorerVisible) Icons.Filled.ChevronRight else PawnIcons.PanelRect,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    isExplorerVisible = !isExplorerVisible
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = {
                                    Icon(Icons.Filled.SelectAll, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    editorRef?.selectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Jump to Line...") },
                                leadingIcon = {
                                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showJumpDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isWordWrap) "Disable Word Wrap" else "Enable Word Wrap") },
                                leadingIcon = {
                                    Icon(Icons.Filled.WrapText, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    isWordWrap = !isWordWrap
                                    editorRef?.isWordwrap = isWordWrap
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isReadOnly) "Enable Editing" else "Read-Only Mode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isReadOnly) Icons.Filled.EditNote else Icons.Filled.Visibility,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    isReadOnly = !isReadOnly
                                    editorRef?.editable = !isReadOnly
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isLineNumbers) Icons.Filled.FormatListNumbered else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    isLineNumbers = !isLineNumbers
                                    editorRef?.isLineNumberEnabled = isLineNumbers
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedVisibility(visible = showSearchBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { q ->
                                    searchQuery = q
                                    if (q.isNotEmpty()) {
                                        editorRef?.searcher?.search(
                                            q,
                                            EditorSearcher.SearchOptions(false, false)
                                        )
                                    } else {
                                        editorRef?.searcher?.stopSearch()
                                    }
                                },
                                label = { Text("Find in code") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    editorRef?.searcher?.gotoNext()
                                })
                            )
                            IconButton(onClick = { editorRef?.searcher?.gotoPrevious() }) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
                            }
                            IconButton(onClick = { editorRef?.searcher?.gotoNext() }) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
                            }
                            IconButton(onClick = {
                                showSearchBar = false
                                editorRef?.searcher?.stopSearch()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close search")
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            TextButton(onClick = { showReplaceRow = !showReplaceRow }) {
                                Text(if (showReplaceRow) "Hide Replace" else "Show Replace")
                            }
                        }

                        if (showReplaceRow) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it },
                                    label = { Text("Replace with") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(onClick = {
                                    editorRef?.searcher?.replaceThis(replaceQuery)
                                }) {
                                    Text("Replace")
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(onClick = {
                                    editorRef?.searcher?.replaceAll(replaceQuery)
                                }) {
                                    Text("All")
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Visual Studio Code style tab strip above the code canvas.
                XedTabStrip(
                    documents = workspace.documents,
                    activePath = workspace.activePath,
                    onSelectTab = { file -> workspace.setActive(file) },
                    onCloseTab = { file -> workspace.closeDocument(file) },
                    onNewTabClick = { isExplorerVisible = !isExplorerVisible },
                    onOpenFileClick = { folderPickerLauncher.launch(buildOpenFolderIntent()) }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                    viewModel.isLoading && !workspace.isWorkspaceOpen -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.loadError != null && !workspace.isWorkspaceOpen -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = viewModel.loadError ?: "Failed to open file",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.loadFile() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    else -> {
                        // Empty workspace: every tab was closed, so offer the two ways
                        // back into content instead of a blank canvas.
                        if (workspace.isWorkspaceOpen && workspace.documents.isEmpty()) {
                            EmptyWorkspaceView(
                                onBrowseExplorer = { isExplorerVisible = !isExplorerVisible },
                                onOpenFolder = { folderPickerLauncher.launch(buildOpenFolderIntent()) }
                            )
                        } else {
                        AndroidView(
                            factory = { ctx ->
                                XedCodeEditor(ctx).apply {
                                    typefaceText = Typeface.MONOSPACE
                                    isLineNumberEnabled = isLineNumbers
                                    isWordwrap = isWordWrap
                                    editable = !isReadOnly
                                    setTextSize(14f)
                                    colorScheme = editorScheme
                                    setEditorLanguage(PawnLanguage())
                                    setText(workspace.activeDocument?.content ?: viewModel.fileContent ?: "")
                                    // Reserve room for the " - <column>" suffix now
                                    // that the document is loaded; later edits are
                                    // cheap enough to re-measure on the fly.
                                    refreshColumnSuffixWidth()
                                    invalidate()

                                    subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                        canUndo = canUndo()
                                        canRedo = canRedo()
                                        // Longest line may have changed, so the
                                        // reserved column-label width is re-measured.
                                        refreshColumnSuffixWidth()
                                        val document = workspace.activeDocument
                                        if (document != null) {
                                            workspace.updateContent(document.file, text.toString())
                                            // The widget already shows this text, so the
                                            // reload watcher must not push it back and
                                            // reset the caret on every keystroke.
                                            loadedBuffer.content = text.toString()
                                        } else {
                                            viewModel.onContentChanged(text.toString())
                                        }
                                    }

                                    subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                                        cursorLine = cursor.leftLine + 1
                                        cursorCol = cursor.leftColumn + 1
                                    }

                                    editorRef = this
                                }
                                appliedScheme.current = editorScheme
                            },
                            update = { editor ->
                                editorRef = editor
                                // Re-tint when the app theme (and therefore the surface
                                // ramp) changes while the editor view is still alive.
                                if (appliedScheme.current !== editorScheme) {
                                    editor.colorScheme = editorScheme
                                    appliedScheme.current = editorScheme
                                }
                                // Push the buffer of the file the workspace panel
                                // activated, or of the single file opened from the
                                // browser. Only runs when the shown path or content
                                // really changed, so typing is never interrupted.
                                val document = workspace.activeDocument
                                val singlePath = if (workspace.isWorkspaceOpen) "" else viewModel.filePath
                                val singleContent = if (workspace.isWorkspaceOpen) "" else (viewModel.fileContent ?: "")
                                val targetPath = document?.file?.absolutePath ?: singlePath
                                val targetContent = document?.content ?: singleContent
                                if (loadedBuffer.needsReload(document, singlePath, singleContent, editorReloadToken)) {
                                    editor.setText(targetContent)
                                    loadedBuffer.store(document, targetPath, targetContent, editorReloadToken)
                                    cursorLine = 1
                                    cursorCol = 1
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }
                }

                // Floating explorer: it sits above the code canvas instead of being
                // welded to the right edge, matching the rest of the Xed surfaces.
                if (isExplorerVisible) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 10.dp, top = 10.dp)
                            .width(explorerWidth.dp)
                            .fillMaxHeight(0.86f)
                    ) {
                        XedWorkspacePanel(
                            session = workspace,
                            onOpenFile = openWorkspaceFile,
                            onCloseFile = { file -> workspace.closeDocument(file) },
                            onSelectFile = { file -> workspace.setActive(file) },
                            onOpenHit = { hit ->
                                workspace.openFile(hit.file)
                                // The buffer is filled asynchronously, so the jump has
                                // to wait for the reload to land in the widget.
                                editorRef?.postDelayed({
                                    editorRef?.jumpToLine((hit.line - 1).coerceAtLeast(0))
                                }, 250)
                            },
                            onClosePanel = { isExplorerVisible = false }
                        )
                    }
                }
                }
                }

                toolPanelMode?.let { mode ->
                    XedToolPanel(
                        mode = mode,
                        find = toolFind,
                        replace = toolReplace,
                        isBusy = isToolBusy,
                        onFindChange = { toolFind = it },
                        onReplaceChange = { toolReplace = it },
                        onRun = {
                            when (mode) {
                                XedToolPanelMode.SearchWorkspace -> runWorkspaceSearch()
                                XedToolPanelMode.ReplacePerFile -> runReplacePerFile(true)
                                XedToolPanelMode.ReplaceAllFiles -> runReplaceAllFiles()
                            }
                        },
                        onRunSecondary = if (mode == XedToolPanelMode.ReplacePerFile) {
                            { runReplacePerFile(false) }
                        } else {
                            null
                        },
                        onClose = { toolPanelMode = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

                // Workspace panel toggle: floats directly above the compile button and
                // opens or closes the explorer card.
                FloatingRoundAction(
                    size = 48.dp,
                    icon = PawnIcons.PanelRect,
                    contentDescription = if (isExplorerVisible) "Hide workspace panel" else "Show workspace panel",
                    testTag = "editor_explorer_toggle",
                    isActive = isExplorerVisible,
                    onClick = { isExplorerVisible = !isExplorerVisible }
                )

                // Rectangle-style floating toolbar, stacked vertically above the
                // workspace panel button and the compile button.
                FloatingRectToolbar(
                    hasUnsavedChanges = hasUnsavedChanges,
                    workspaceOpen = workspace.isWorkspaceOpen,
                    isSearchOpen = showSearchBar,
                    onSearchClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) editorRef?.searcher?.stopSearch()
                    },
                    onOpenWorkspaceClick = { folderPickerLauncher.launch(buildOpenFolderIntent()) },
                    onSaveClick = { saveActiveFile() },
                    onSaveAllClick = {
                        if (workspace.isWorkspaceOpen) {
                            scope.launch {
                                workspace.saveAll()
                                workspace.refreshTree()
                            }
                        } else {
                            saveAsLauncher.launch(viewModel.fileName)
                        }
                    }
                )

                FloatingCompileButton(
                    enabled = activeDocument != null || viewModel.filePath.isNotBlank(),
                    onClick = {
                        val n_target = activeDocument?.file?.path ?: viewModel.filePath
                        if (n_target.isBlank()) {
                            Toast.makeText(context, "Open a file first", Toast.LENGTH_SHORT).show()
                        } else {
                            // Unsaved buffers are written first so the compiler
                            // sees exactly what is on screen.
                            scope.launch {
                                workspace.openWorkspaces.forEach { n_ws ->
                                    workspace.saveAll(n_ws)
                                }
                                onCompileRequest(n_target)
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            QuickSymbolBar(
                onSymbolClick = { symbol ->
                    val editor = editorRef ?: return@QuickSymbolBar
                    editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, symbol)
                },
                onTabClick = ::insertIndentAtCursor
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasUnsavedChanges) {
                        "Modified"
                    } else {
                        val extension = activeDocument?.file?.extension?.uppercase()
                            ?: viewModel.file.extension.uppercase()
                        workspace.activeWorkspace?.name?.let { "$it - $extension" } ?: "Pawn ($extension)"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                // Compact caret readout: no padding around the numbers, no spacing
                // between them, so the pill covers as little of the editor as possible.
                Text(
                    text = "$cursorLine-$cursorCol",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 1.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef?.release()
            editorRef = null
        }
    }
}

/**
 * Shown when a workspace has no open document at all: either reopen the explorer or
 * pick another folder.
 */
@Composable
private fun EmptyWorkspaceView(
    onBrowseExplorer: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = PawnIcons.PanelRect,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No file open",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Browse the workspace panel or open another folder to start editing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onBrowseExplorer) {
                    Icon(PawnIcons.PanelRect, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Explorer")
                }
                FilledTonalButton(onClick = onOpenFolder) {
                    Icon(PawnIcons.FolderRect, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Folder")
                }
            }
        }
    }
}

/** Side of the editor the floating compile button rests on. */
private val FLOAT_MARGIN = 16.dp

/** Vertical gap between the stacked floating controls. */
private val FLOAT_GAP = 10.dp

/** Diameter of the floating compile button. */
private val COMPILE_BUTTON_SIZE = 58.dp

/** Diameter of the floating workspace panel button. */
private val PANEL_BUTTON_SIZE = 48.dp

/** Width of the rectangle-style floating toolbar. */
private val TOOLBAR_WIDTH = 52.dp

/** Height of the rectangle-style floating toolbar (drag handle + four entries). */
private val TOOLBAR_HEIGHT = 232.dp

/**
 * Rest position of a floating control, given the compile button it stacks above.
 *
 * All three floaters share this geometry: the panel button sits one gap above the
 * compile button, the rectangular toolbar one gap above that, and both are centred on
 * the compile button horizontally so the group reads as one column.
 */
private fun restOffsetAbove(
    controlWidth: Dp,
    controlHeight: Dp,
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density
): IntOffset = with(density) {
    val compilePx = COMPILE_BUTTON_SIZE.toPx()
    val panelPx = PANEL_BUTTON_SIZE.toPx()
    val controlWidthPx = controlWidth.toPx()
    val controlHeightPx = controlHeight.toPx()
    val marginPx = FLOAT_MARGIN.toPx()
    val gapPx = FLOAT_GAP.toPx()

    val x = (maxWidthPx - marginPx - controlWidthPx - (compilePx - controlWidthPx) / 2f)
        .coerceAtLeast(0f)
    val compileTop = maxHeightPx - marginPx - compilePx
    val y = (compileTop - gapPx - panelPx - gapPx - controlHeightPx).coerceAtLeast(0f)

    IntOffset(x.roundToInt(), y.roundToInt())
}

/**
 * Floating rectangle toolbar of the editor: Search, Open Workspace, Save File and
 * Save As / Save All, stacked top to bottom.
 *
 * The group is dragged anywhere inside the editor area with the same gesture physics
 * and boundary clamping as the compile button, and a plain tap on one of its entries
 * runs that action without starting a drag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingRectToolbar(
    hasUnsavedChanges: Boolean,
    workspaceOpen: Boolean,
    isSearchOpen: Boolean,
    onSearchClick: () -> Unit,
    onOpenWorkspaceClick: () -> Unit,
    onSaveClick: () -> Unit,
    onSaveAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val restOffset = restOffsetAbove(TOOLBAR_WIDTH, TOOLBAR_HEIGHT, maxWidthPx, maxHeightPx, density)
        val toolbarWidthPx = with(density) { TOOLBAR_WIDTH.toPx() }
        val toolbarHeightPx = with(density) { TOOLBAR_HEIGHT.toPx() }

        var draggedOffset by remember { mutableStateOf<IntOffset?>(null) }
        val offset = draggedOffset ?: restOffset

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { offset }
                .width(TOOLBAR_WIDTH)
                .height(TOOLBAR_HEIGHT)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedOffset ?: restOffset
                            val newX = (current.x + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - toolbarWidthPx).coerceAtLeast(0f))
                            val newY = (current.y + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - toolbarHeightPx).coerceAtLeast(0f))
                            draggedOffset = IntOffset(newX.roundToInt(), newY.roundToInt())
                        }
                    )
                }
                .testTag("editor_floating_toolbar")
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag indicator bar.
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(20.dp)
                        .height(3.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(2.dp)
                        )
                )

                ToolBarEntry(
                    icon = Icons.Filled.Search,
                    contentDescription = "Search",
                    testTag = "editor_floating_search",
                    isActive = isSearchOpen,
                    onClick = onSearchClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    icon = PawnIcons.FolderRect,
                    contentDescription = "Open workspace folder",
                    testTag = "editor_floating_open_workspace",
                    onClick = onOpenWorkspaceClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    icon = PawnIcons.Save,
                    contentDescription = "Save file",
                    testTag = "editor_floating_save",
                    isActive = hasUnsavedChanges,
                    onClick = onSaveClick
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    icon = if (workspaceOpen) PawnIcons.SaveAll else PawnIcons.Save,
                    contentDescription = if (workspaceOpen) "Save All" else "Save As",
                    testTag = "editor_floating_save_all",
                    onClick = onSaveAllClick
                )
            }
        }
    }
}

/** One entry of the floating rectangle toolbar. */
@Composable
private fun ToolBarEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(46.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * Floating round action, used by the workspace panel button that sits directly above
 * the compile button and opens or closes the explorer card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingRoundAction(
    size: Dp,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val restOffset = restOffsetAbove(size, size, maxWidthPx, maxHeightPx, density)
        val sizePx = with(density) { size.toPx() }

        var draggedOffset by remember { mutableStateOf<IntOffset?>(null) }
        val offset = draggedOffset ?: restOffset

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shadowElevation = 6.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .offset { offset }
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedOffset ?: restOffset
                            val newX = (current.x + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - sizePx).coerceAtLeast(0f))
                            val newY = (current.y + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - sizePx).coerceAtLeast(0f))
                            draggedOffset = IntOffset(newX.roundToInt(), newY.roundToInt())
                        }
                    )
                }
                .testTag(testTag)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Floating compile button of the editor.
 *
 * The button is dragged anywhere inside the editor area and stays where the user
 * put it, and a plain tap hands the currently active file to the compiler. The
 * offset is remembered in pixels, so it survives recomposition and theme changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingCompileButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val n_density = LocalDensity.current
    val n_diameter: Dp = COMPILE_BUTTON_SIZE

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val n_maxWidth = with(n_density) { maxWidth.toPx() }
        val n_maxHeight = with(n_density) { maxHeight.toPx() }
        val n_diameterPx = with(n_density) { n_diameter.toPx() }

        // `null` means "not dragged yet", so the button starts in the bottom end
        // corner and follows the panel whenever it has not been moved.
        var n_draggedOffset by remember { mutableStateOf<IntOffset?>(null) }
        val n_restOffset = IntOffset(
            x = (n_maxWidth - n_diameterPx - with(n_density) { 16.dp.toPx() })
                .coerceAtLeast(0f)
                .roundToInt(),
            y = (n_maxHeight - n_diameterPx - with(n_density) { 16.dp.toPx() })
                .coerceAtLeast(0f)
                .roundToInt()
        )
        val n_offset = n_draggedOffset ?: n_restOffset

        Surface(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            color = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            contentColor = if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { n_offset }
                .size(n_diameter)
                .pointerInput(enabled) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val n_current = n_draggedOffset ?: n_restOffset
                            val n_newX = (n_current.x + dragAmount.x)
                                .coerceIn(0f, (n_maxWidth - n_diameterPx).coerceAtLeast(0f))
                            val n_newY = (n_current.y + dragAmount.y)
                                .coerceIn(0f, (n_maxHeight - n_diameterPx).coerceAtLeast(0f))
                            n_draggedOffset = IntOffset(n_newX.roundToInt(), n_newY.roundToInt())
                        }
                    )
                }
                .testTag("editor_compile_fab")
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PawnIcons.WifiSignal,
                    contentDescription = "Compile active file",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/** Narrowest width the workspace panel may be dragged to. */
private const val MIN_EXPLORER_WIDTH = 200f

/** Widest width the workspace panel may be dragged to. */
private const val MAX_EXPLORER_WIDTH = 640f

/**
 * Visual Studio Code style tab strip above the code canvas.
 *
 * Each open document is a tab with a file-type icon, an unsaved dot and its own close
 * button; the active tab is drawn on the editor surface with the primary accent line
 * along its top edge. The last entry opens the explorer or a new file, so a tab strip
 * alone is enough to navigate the workspace.
 */
@Composable
private fun XedTabStrip(
    documents: List<OpenDocument>,
    activePath: String?,
    onSelectTab: (java.io.File) -> Unit,
    onCloseTab: (java.io.File) -> Unit,
    onNewTabClick: () -> Unit,
    onOpenFileClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TAB_STRIP_HEIGHT)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            documents.forEach { document ->
                val isActive = document.file.absolutePath == activePath
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isActive) {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                        .clickable { onSelectTab(document.file) }
                        .padding(start = 12.dp, end = 4.dp)
                        .testTag("editor_tab_${document.name}")
                ) {
                    // Signature accent line on top of the active tab.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            )
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(TAB_STRIP_HEIGHT)
                    ) {
                        Icon(
                            imageVector = if (document.file.extension.equals("inc", ignoreCase = true)) {
                                PawnIcons.IncludeRect
                            } else {
                                PawnIcons.FileRect
                            },
                            contentDescription = null,
                            tint = if (document.file.extension.equals("inc", ignoreCase = true)) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = document.name,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .widthIn(max = 140.dp)
                                .padding(start = 6.dp)
                        )
                        if (document.isDirty) {
                            Text(
                                text = "●",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { onCloseTab(document.file) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close ${document.name}",
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            IconButton(
                onClick = onNewTabClick,
                modifier = Modifier
                    .size(TAB_STRIP_HEIGHT)
                    .testTag("editor_tab_explorer_toggle")
            ) {
                Icon(
                    imageVector = PawnIcons.PanelRect,
                    contentDescription = "Toggle workspace panel",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onOpenFileClick,
                modifier = Modifier
                    .size(TAB_STRIP_HEIGHT)
                    .testTag("editor_tab_open_file")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Open file",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Compact height of the tab strip, matching the Visual Studio Code density. */
private val TAB_STRIP_HEIGHT = 38.dp

/** Remembers which buffer the native editor widget is currently showing. */
private class LoadedBufferHolder {
    var document: OpenDocument? = null
    var path: String? = null
    var content: String? = null
    var token: Int = 0

    /**
     * Whether the widget has to receive a new buffer.
     *
     * Both the file *and* the content are compared. The document object alone is not
     * enough: tapping a file in the workspace explorer swaps the active document, but
     * the buffer only arrives once the disk read finished, so a path-only or
     * identity-only check could latch onto the still-empty previous text and drop the
     * file the user just tapped.
     */
    fun needsReload(document: OpenDocument?, singleFilePath: String, singleFileContent: String, reloadToken: Int): Boolean {
        val targetPath = document?.file?.absolutePath ?: singleFilePath
        val targetContent = document?.content ?: singleFileContent
        return this.document !== document ||
            this.path != targetPath ||
            this.content != targetContent ||
            this.token != reloadToken
    }

    fun store(document: OpenDocument?, targetPath: String, targetContent: String, reloadToken: Int) {
        this.document = document
        this.path = targetPath
        this.content = targetContent
        this.token = reloadToken
    }
}

/**
 * Indents the line the caret currently sits on.
 *
 * The caret is read from the live editor at the moment of the tap instead of
 * from the cached `cursorLine`/`cursorCol` state, so the [TAB] key always acts
 * on the active selection even when the editor has not reported a selection
 * change yet (right after a paste, an undo, or when the editor is restored).
 *
 * The caret is the one the *user* placed, so the indent is inserted at the
 * caret rather than at the end of the line; that keeps [TAB] usable for
 * indenting a partially typed line. Sora advances the caret by itself after
 * `text.insert`, so no follow-up selection call is needed.
 */
private fun insertIndentAtCursor(editor: CodeEditor?) {
    val target = editor ?: return
    val line = target.cursor.leftLine
    val column = target.cursor.leftColumn
    val lineText = target.text.getLine(line)

    // Match the leading whitespace of the current line and add one level on top,
    // so consecutive [TAB] presses walk in cleanly instead of drifting right by
    // a fixed amount every time.
    val leading = lineText.take(column).indexOfFirst { !it.isWhitespace() }
        .let { if (it < 0) column else it }
    val baseIndent = lineText.take(leading)
    target.text.insert(line, column, baseIndent + "    ")
}

/** One key in the quick symbol row. */
@Composable
private fun QuickSymbolKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun QuickSymbolBar(
    onSymbolClick: (String) -> Unit,
    onTabClick: () -> Unit
) {
    // The [TAB] key is a structural key, not a character, so it is rendered as
    // its own button ahead of the symbol keys instead of being the last entry of
    // a horizontally scrolling row, where it was easy to miss.
    val symbols = listOf(
        "{", "}", "(", ")", "[", "]", ";", ":", ",", "\"", "'", "#", "=", ">", "<", "!", "&", "|"
    )
    val digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    val symbolScroll = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(symbolScroll)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickSymbolKey(
                    label = "TAB",
                    onClick = onTabClick,
                    modifier = Modifier.testTag("editor_tab_button")
                )

                symbols.forEach { sym ->
                    QuickSymbolKey(
                        label = sym,
                        onClick = { onSymbolClick(sym) },
                        modifier = if (sym == "{") Modifier.testTag("editor_symbol_brace_open") else Modifier
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                digits.forEach { d ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSymbolClick(d) }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = d,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Holds the last color scheme instance pushed into the native [CodeEditor]. */
private class EditorSchemeHolder {
    var current: EditorColorScheme? = null
}

/**
 * Builds the sora-editor scheme for the current theme.
 *
 * Darcula/Eclipse ship with their own hard-coded backgrounds, which used to make the
 * code area look like an unrelated white block inside the light theme. We keep the
 * syntax highlighting of the chosen scheme, but re-tint the surface family (code
 * background, current line, line-number panel, dividers, scrollbars) from the app
 * color scheme so the editor sits on the same elevation step as the surrounding UI.
 *
 * [customBackground] lets the user override the canvas with any `#RRGGBB` colour.
 * When it is set, the whole surface family is re-derived from that single colour so
 * the panel stays internally consistent (gutter, current line and dividers) instead
 * of clashing with a colour picked for the canvas alone.
 *
 * Every decision is driven by the canvas that will actually be painted rather than
 * by the app theme, so a user who pins a dark canvas while the app is in light mode
 * still gets a light-on-dark editor and vice versa.
 */
private fun buildEditorScheme(
    colorScheme: ColorScheme,
    customBackground: String? = null
): EditorColorScheme {
    val customArgb = customBackground?.let { parseHexColor(it) }

    // Re-derive the gutter and the accents from whichever canvas is in play, so
    // a custom colour produces a coherent editor instead of a mismatched one.
    val background: Int
    val lineNumberBackground: Int
    val outline: Int
    val faint: Int
    if (customArgb != null) {
        val onCustom = readableForegroundOn(customArgb)
        background = customArgb
        lineNumberBackground = blend(customArgb, onCustom, 0.06f)
        outline = blend(customArgb, onCustom, 0.22f)
        faint = blend(customArgb, onCustom, 0.10f)
    } else {
        background = colorScheme.surfaceContainerLowest.toArgb()
        lineNumberBackground = colorScheme.surfaceContainerLow.toArgb()
        outline = colorScheme.outlineVariant.toArgb()
        faint = colorScheme.onSurface.copy(alpha = 0.10f).toArgb()
    }

    // Pick the base scheme from the canvas rather than the app theme, so slots
    // the palette below does not explicitly set (selection, cursors, search
    // highlights) also follow the canvas.
    val darkCanvas = isDarkCanvas(background)
    val base: EditorColorScheme = if (darkCanvas) SchemeDarcula() else SchemeEclipse()

    applyVividSyntaxPalette(base, darkCanvas)

    base.setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
    base.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, lineNumberBackground)
    base.setColor(EditorColorScheme.LINE_NUMBER_PANEL, lineNumberBackground)
    base.setColor(EditorColorScheme.LINE_DIVIDER, outline)
    base.setColor(EditorColorScheme.BLOCK_LINE, outline)
    base.setColor(EditorColorScheme.CURRENT_LINE, faint)
    base.setColor(EditorColorScheme.SCROLL_BAR_TRACK, lineNumberBackground)
    base.setColor(EditorColorScheme.SCROLL_BAR_THUMB, outline)
    return base
}

/**
 * High-contrast, high-chroma syntax palette.
 *
 * Darcula and Eclipse ship muted token colours that are hard to tell apart at
 * a glance: `IDENTIFIER_NAME`, `TYPE` and `OPERATOR` in particular all sit
 * within a couple of shades of the plain text colour, so the editor reads as a
 * wall of grey. This palette replaces every token slot the Pawn analyzer uses
 * with a clearly separated hue, while keeping each hue on the correct side of
 * the background so contrast holds in both light and dark mode.
 *
 * The assignment is stable per token kind, so a given line never changes colour
 * between re-analyses (the analyzer runs asynchronously on every keystroke).
 */
private fun applyVividSyntaxPalette(scheme: EditorColorScheme, darkCanvas: Boolean) {
    val p = if (darkCanvas) DarkPalette else LightPalette

    scheme.setColor(EditorColorScheme.KEYWORD, p.keyword)
    scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, p.type)
    scheme.setColor(EditorColorScheme.LITERAL, p.literal)
    scheme.setColor(EditorColorScheme.FUNCTION_NAME, p.function)
    scheme.setColor(EditorColorScheme.OPERATOR, p.operator)
    scheme.setColor(EditorColorScheme.ANNOTATION, p.annotation)
    scheme.setColor(EditorColorScheme.COMMENT, p.comment)
    scheme.setColor(EditorColorScheme.LINE_NUMBER, p.lineNumber)
    // Plain identifiers (variables, labels) inherit the scheme's text colour, so
    // it has to be re-pointed at the canvas as well. Without this a custom dark
    // canvas under a light app theme would render every variable dark-on-dark.
    scheme.setColor(
        EditorColorScheme.TEXT_NORMAL,
        if (darkCanvas) 0xFFF2F5FA.toInt() else 0xFF111418.toInt()
    )
}

/**
 * Token colours for the dark canvas.
 *
 * Hues are chosen so adjacent kinds never share a family: keywords are amber,
 * types are cyan, literals are orange, functions are green, operators are
 * magenta, annotations are violet and comments are a desaturated slate.
 */
private data class SyntaxPalette(
    val keyword: Int,
    val type: Int,
    val literal: Int,
    val function: Int,
    val operator: Int,
    val annotation: Int,
    val comment: Int,
    val lineNumber: Int
)

private val DarkPalette = SyntaxPalette(
    keyword = 0xFFFFD866.toInt(),
    type = 0xFF6FE3F5.toInt(),
    literal = 0xFFFFB07C.toInt(),
    function = 0xFF7CFF9E.toInt(),
    operator = 0xFFE6A8FF.toInt(),
    annotation = 0xFFC9B6FF.toInt(),
    comment = 0xFF9AA7BD.toInt(),
    lineNumber = 0xFF7E8CA3.toInt()
)

/**
 * Token colours for the light canvas.
 *
 * The same hue assignment as [DarkPalette] but darkened so the contrast ratio
 * against a white canvas stays readable.
 */
private val LightPalette = SyntaxPalette(
    keyword = 0xFF9A3412.toInt(),
    type = 0xFF005E70.toInt(),
    literal = 0xFF9A3412.toInt(),
    function = 0xFF046C43.toInt(),
    operator = 0xFF6B21C8.toInt(),
    annotation = 0xFF4C1D95.toInt(),
    comment = 0xFF44546B.toInt(),
    lineNumber = 0xFF64748B.toInt()
)

/**
 * Parses a `#RRGGBB` string into an ARGB int, returning `null` for anything else.
 *
 * The stored preference is already validated by
 * [com.rvdjv.pawnmc.data.config.CompilerConfig.normalizeEditorBackgroundColor];
 * this is the second gate that keeps a malformed value from reaching the
 * editor scheme, and it never throws.
 */
private fun parseHexColor(hex: String): Int? {
    val digits = hex.trim().removePrefix("#")
    if (digits.length != 6) return null
    val value = digits.toIntOrNull(16) ?: return null
    return 0xFF000000.toInt() or value
}

/**
 * Picks black or white — whichever contrasts more with [background].
 *
 * Uses the ITU-R BT.601 luma approximation, which is the same weighting the
 * Material colour system uses for `onColor` decisions, so a custom background
 * the user picked for a dark project still gets light text.
 */
private fun readableForegroundOn(background: Int): Int {
    val r = (background shr 16) and 0xFF
    val g = (background shr 8) and 0xFF
    val b = background and 0xFF
    val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    return if (luma > 0.5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
}

/**
 * True when an opaque ARGB canvas is dark enough to need the light-on-dark
 * token colours.
 *
 * The same BT.601 luma threshold as [readableForegroundOn], so the token
 * palette and the automatically chosen foreground can never disagree.
 */
private fun isDarkCanvas(argb: Int): Boolean = readableForegroundOn(argb) == 0xFFFFFFFF.toInt()

/** Linearly mixes [amount] of [tint] into [base]; `amount` is clamped to 0..1. */
private fun blend(base: Int, tint: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun mix(shift: Int): Int {
        val b = (base shr shift) and 0xFF
        val c = (tint shr shift) and 0xFF
        return (b + (c - b) * t).toInt().coerceIn(0, 255)
    }
    return 0xFF000000.toInt() or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
}
