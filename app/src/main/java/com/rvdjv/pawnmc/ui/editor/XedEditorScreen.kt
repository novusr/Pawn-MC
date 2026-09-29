package com.rvdjv.pawnmc.ui.editor

import android.graphics.Typeface
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XedEditorScreen(
    viewModel: XedEditorViewModel,
    onNavigateBack: () -> Unit,
    editorBackgroundColor: String? = null
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
        // The scaffold keeps the page tone so the brighter code area in the middle
        // reads as its own surface, with toolbar/status bars one step above it.
        containerColor = MaterialTheme.colorScheme.background,
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
                            imageVector = PawnIcons.Save,
                            contentDescription = "Save file",
                            tint = if (viewModel.hasUnsavedChanges) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = {
                            saveAsLauncher.launch(viewModel.fileName)
                        },
                        modifier = Modifier.testTag("editor_save_as_button")
                    ) {
                        Text("Save As")
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
                                    colorScheme = editorScheme
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
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
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
        if (darkCanvas) 0xFFE6E9EF.toInt() else 0xFF1A1C1E.toInt()
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
    keyword = 0xFFFFC66D.toInt(),
    type = 0xFF4DD0E1.toInt(),
    literal = 0xFFFFAB70.toInt(),
    function = 0xFF69F0AE.toInt(),
    operator = 0xFFCE93D8.toInt(),
    annotation = 0xFFB39DFF.toInt(),
    comment = 0xFF7E8AA0.toInt(),
    lineNumber = 0xFF5C6B80.toInt()
)

/**
 * Token colours for the light canvas.
 *
 * The same hue assignment as [DarkPalette] but darkened so the contrast ratio
 * against a white canvas stays readable.
 */
private val LightPalette = SyntaxPalette(
    keyword = 0xFFB45309.toInt(),
    type = 0xFF00697A.toInt(),
    literal = 0xFFC2410C.toInt(),
    function = 0xFF15803D.toInt(),
    operator = 0xFF7E22CE.toInt(),
    annotation = 0xFF5B21B6.toInt(),
    comment = 0xFF5A6B7F.toInt(),
    lineNumber = 0xFF8A97A8.toInt()
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
