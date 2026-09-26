package com.rvdjv.pawnmc.ui.editor

import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rvdjv.pawnmc.ui.editor.pawn.PawnLanguage
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedEditorScreen(
    viewModel: XedEditorViewModel,
    onNavigateBack: () -> Unit,
    darkTheme: Boolean
) {
    val context = LocalContext.current
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

    BackHandler {
        if (viewModel.hasUnsavedChanges) {
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
                Text("File '${viewModel.fileName}' has unsaved modifications. Do you want to save before closing?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentText = editorRef?.text?.toString() ?: ""
                        viewModel.saveFile(currentText) { success ->
                            showExitDialog = false
                            if (success) {
                                onNavigateBack()
                            }
                        }
                    },
                    modifier = Modifier.testTag("dialog_save_button")
                ) {
                    Text("Save")
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
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = viewModel.fileName + if (viewModel.hasUnsavedChanges) " *" else "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.filePath,
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
                            if (viewModel.hasUnsavedChanges) {
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

                    IconButton(
                        onClick = {
                            showSearchBar = !showSearchBar
                            if (!showSearchBar) {
                                editorRef?.searcher?.stopSearch()
                            }
                        },
                        modifier = Modifier.testTag("editor_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search"
                        )
                    }

                    IconButton(
                        onClick = {
                            val text = editorRef?.text?.toString() ?: ""
                            viewModel.saveFile(text)
                        },
                        enabled = !viewModel.isSaving,
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Save file",
                            tint = if (viewModel.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                onClick = {
                                    showMenu = false
                                    editorRef?.selectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Jump to Line...") },
                                onClick = {
                                    showMenu = false
                                    showJumpDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isWordWrap) "Disable Word Wrap" else "Enable Word Wrap") },
                                onClick = {
                                    showMenu = false
                                    isWordWrap = !isWordWrap
                                    editorRef?.isWordwrap = isWordWrap
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isReadOnly) "Enable Editing" else "Read-Only Mode") },
                                onClick = {
                                    showMenu = false
                                    isReadOnly = !isReadOnly
                                    editorRef?.editable = !isReadOnly
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLineNumbers) "Hide Line Numbers" else "Show Line Numbers") },
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
                    containerColor = MaterialTheme.colorScheme.surface
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
                    color = MaterialTheme.colorScheme.surfaceVariant,
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
                when {
                    viewModel.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    viewModel.loadError != null -> {
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
                        AndroidView(
                            factory = { ctx ->
                                CodeEditor(ctx).apply {
                                    typefaceText = Typeface.MONOSPACE
                                    isLineNumberEnabled = isLineNumbers
                                    isWordwrap = isWordWrap
                                    editable = !isReadOnly
                                    setTextSize(14f)
                                    colorScheme = if (darkTheme) SchemeDarcula() else SchemeEclipse()
                                    setEditorLanguage(PawnLanguage())
                                    setText(viewModel.fileContent ?: "")

                                    subscribeEvent(ContentChangeEvent::class.java) { _, _ ->
                                        canUndo = canUndo()
                                        canRedo = canRedo()
                                        viewModel.onContentChanged(text.toString())
                                    }

                                    subscribeEvent(SelectionChangeEvent::class.java) { _, _ ->
                                        cursorLine = cursor.leftLine + 1
                                        cursorCol = cursor.leftColumn + 1
                                    }

                                    editorRef = this
                                }
                            },
                            update = { editor ->
                                editorRef = editor
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            QuickSymbolBar(
                onSymbolClick = { symbol ->
                    val editor = editorRef ?: return@QuickSymbolBar
                    if (symbol == "TAB") {
                        editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, "    ")
                    } else {
                        editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, symbol)
                    }
                }
            )

            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (viewModel.hasUnsavedChanges) "Modified" else "Pawn (${viewModel.file.extension.uppercase()})",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (viewModel.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ln $cursorLine, Col $cursorCol",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun QuickSymbolBar(onSymbolClick: (String) -> Unit) {
    val symbols = listOf(
        "{", "}", "(", ")", "[", "]", ";", ":", ",", "\"", "'", "#", "=", ">", "<", "!", "&", "|", "TAB"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            symbols.forEach { sym ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onSymbolClick(sym) }
                ) {
                    Text(
                        text = sym,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
