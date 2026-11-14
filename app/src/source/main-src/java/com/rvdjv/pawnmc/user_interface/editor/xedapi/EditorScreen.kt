package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rvdjv.pawnmc.apps_data.data_compiler.Compiler
import com.rvdjv.pawnmc.apps_data.data_compiler.DiagnosticsStore
import com.rvdjv.pawnmc.user_interface._Icons
import com.rvdjv.pawnmc.apps_data.data_config.AppLocalization
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.user_interface.editor.workspace.MAX_OPEN_WORKSPACES
import com.rvdjv.pawnmc.user_interface.editor.workspace.OpenDocument
import com.rvdjv.pawnmc.user_interface.editor.workspace.WorkspaceSession
import com.rvdjv.pawnmc.user_interface.editor.workspace.WorkspaceSearchHit
import com.rvdjv.pawnmc.user_interface.editor.workspace.buildFolderPickerIntent
import com.rvdjv.pawnmc.user_interface.editor.workspace.buildFilePickerIntent
import com.rvdjv.pawnmc.user_interface.editor.workspace.resolveDocumentToFile
import com.rvdjv.pawnmc.user_interface.editor.workspace.resolveTreeToFile
import com.rvdjv.pawnmc.user_interface.editor.syntax.CodeLanguages
import com.rvdjv.pawnmc.user_interface.editor.workspace.WorkspacePanel
import com.rvdjv.pawnmc.user_interface.editor.portal.DeveloperPortal
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onNavigateBack: () -> Unit,
    editorBackgroundColor: String? = null,
    compileOutput: String = "",
    isCompiling: Boolean = false,
    onCompileRequest: (String) -> Unit = {}
) {
    val context = LocalContext.current
    // Shared across screens so the localisation asset is read and parsed only once
    // per process; the main screen also warms it up in the background.
    val localizer = remember(context) { AppLocalization.shared(context) }
    val appLanguage = remember(context) { ContentConfig.getInstance(context).n_app_language }
    val appColorScheme = MaterialTheme.colorScheme
    // Auto-close rule from settings, re-read when the screen re-enters so a
    // change in Settings is picked up without touching the widget internals.
    /** Live toggle for the auto-close behaviour (drives the editor directly). */
    var editorAutoCloseEnabled by remember {
        mutableStateOf(ContentConfig.getInstance(context).n_editor_auto_close != ContentConfig.EditorAutoClose.SMART)
    }
    // The stored mode stays available for a user who picked "Smart pair" in
    // Settings; the toggle switches between that mode and fully-off.
    val storedAutoCloseMode by remember {
        mutableStateOf(ContentConfig.getInstance(context).n_editor_auto_close)
    }
    val editorScheme = remember(appColorScheme, editorBackgroundColor) {
        buildEditorScheme(appColorScheme, editorBackgroundColor)
    }
    // Tracks which scheme instance is currently applied to the native CodeEditor so
    // `update` only re-tints when the theme actually changed. Deliberately a plain
    // holder (not snapshot state) because it is written during composition.
    val appliedScheme = remember { EditorSchemeHolder() }
    // Same idea for the language: switching files re-installs the highlighter only when
    // the extension actually changed, so typing never re-tokenises the document.
    val appliedLanguage = remember { LanguageHolder() }
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }

    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    // Both readouts are 0-based exactly as Sora reports them; the status bar adds
    // the one-based offset when it renders. Keeping the raw value here is what
    // stopped the "Line 4 -> 5" drift: the old code stored `leftLine + 1` and the
    // status bar's formatter added its own +1 on top of it.
    var cursorLine by remember { mutableIntStateOf(0) }
    var cursorCol by remember { mutableIntStateOf(0) }

    var showExitDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("Server.pwn") }
    var newFileParent by remember { mutableStateOf<File?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("gamemodes") }
    var showSearchBar by remember { mutableStateOf(false) }
    var showJumpDialog by remember { mutableStateOf(false) }

    fun openNewFileDialog(directory: File) {
        newFileParent = directory
        newFileName = "Server.pwn"
        showNewFileDialog = true
    }

    fun openNewFolderDialog(directory: File) {
        newFileParent = directory
        newFolderName = "gamemodes"
        showNewFolderDialog = true
    }

    fun dismissAutoCompletion() {
        hideAutoCompletion(editorRef)
    }

    var showAboutDialog by remember { mutableStateOf(false) }
    var showHashDialog by remember { mutableStateOf(false) }
    var showHashAlgorithmMenu by remember { mutableStateOf(false) }
    var hashAlgorithm by remember { mutableStateOf("SHA-256") }
    var hashText by remember { mutableStateOf("") }
    var hashResult by remember { mutableStateOf<String?>(null) }
    var hashError by remember { mutableStateOf<String?>(null) }
    var hashTextMode by remember { mutableStateOf(false) }
    var hashFileUri by remember { mutableStateOf<Uri?>(null) }
    var hashFileName by remember { mutableStateOf<String?>(null) }
    var isHashing by remember { mutableStateOf(false) }
    var noticeMessage by remember { mutableStateOf<String?>(null) }
    var isQuickSymbolBarVisible by remember { mutableStateOf(true) }

    // ------------------------------------------------------------------
    // Workspace: several files/folders open at once, Visual Studio style.
    // Declared before the explain-sync loop because the loop reads the
    // active document path from it.
    // ------------------------------------------------------------------
    val workspace = viewModel.workspace
    val scope = rememberCoroutineScope()

    /** How often the explainer JSON is re-read for hint injection. */
    val explainCycleMs = remember { ExplainInjector.CYCLE_INTERVAL_MS }

    /** Bumped whenever a new cycle should start right away (open, or compile done). */
    var explainCycleTick by remember { mutableIntStateOf(0) }

    /**
     * Applies the explain hints for the file now shown in the editor, without
     * waiting for the next periodic cycle.
     */
    fun refreshExplainHints() {
        val editor = editorRef ?: return
        val path = if (workspace.isWorkspaceOpen) {
            workspace.activeDocument?.file?.absolutePath
        } else {
            viewModel.filePath.takeIf { it.isNotBlank() }
        } ?: return
        scope.launch { ExplainInjector.apply(editor, path, context) }
    }

    /**
     * Explainer sync loop: every six minutes - and once right after the screen
     * opens - the compiled diagnostics JSON is read and the `[explain]` comments
     * are re-applied to the lines it still covers. Ticks from the compile path
     * run a refresh right away, so the comments appear while the user is still
     * looking at the code, not only after leaving and re-entering the editor.
     */
    LaunchedEffect(explainCycleTick) {
        if (explainCycleTick > 0) {
            refreshExplainHints()
        }
        while (true) {
            delay(explainCycleMs)
            editorRef?.let { editor ->
                val path = if (workspace.isWorkspaceOpen) {
                    workspace.activeDocument?.file?.absolutePath
                } else {
                    viewModel.filePath.takeIf { it.isNotBlank() }
                }
                if (path != null) {
                    ExplainInjector.apply(editor, path, context)
                }
            }
        }
    }

    // Start the first cycle immediately when the editor appears.
    LaunchedEffect(Unit) { explainCycleTick++ }

    /**
     * Master toggle for the whole floating-button stack.
     *
     * Off hides every floating control — Developer Portal, orientation,
     * workspace panel, the rectangle toolbar, the compile button — leaving only
     * the code canvas. Toggled from the overflow menu ("Hide floating buttons"),
     * because a clean reading surface was the one thing the editor could not
     * offer while four buttons floated over it.
     */
    var floatingButtonsHidden by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showReplaceRow by remember { mutableStateOf(false) }

    var isWordWrap by remember { mutableStateOf(false) }
    var isReadOnly by remember { mutableStateOf(false) }
    var isLineNumbers by remember { mutableStateOf(true) }

    // ------------------------------------------------------------------
    // Workspace: declared above, before the explain-sync loop; the scope
    // is declared next to it because the instant hint refresh launches
    // through it.
    // ------------------------------------------------------------------
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, workspace) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                workspace.openWorkspaces.forEach { openWorkspace ->
                    workspace.refreshTree(openWorkspace)
                }
                // A folder that was deleted or unmounted while the app was in the
                // background is dropped here, and the remembered list follows, so a
                // dead path is not offered again on the next launch.
                workspace.closeMissingWorkspaces()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var isExplorerVisible by remember { mutableStateOf(false) }
    var explorerOffset by remember { mutableStateOf(IntOffset.Zero) }
    var isPawnTerminalVisible by remember { mutableStateOf(false) }
    // Landscape toggle: `true` forces landscape, `false` releases the lock back to
    // whatever orientation the activity reports by default (portrait).
    var isLandscapeForced by remember { mutableStateOf(false) }
    var pawnConsoleVersion by remember {
        mutableStateOf(ContentConfig.CompilerVersion.V3107)
    }
    var explorerWidth by remember { mutableFloatStateOf(320f) }
    var toolPanelMode by remember { mutableStateOf<ToolPanelMode?>(null) }
    var toolFind by remember { mutableStateOf("") }
    var toolReplace by remember { mutableStateOf("") }
    var isToolBusy by remember { mutableStateOf(false) }
    // Bumped when a tool rewrote the file behind the editor, so the widget is
    // reloaded even though the document instance itself did not change.
    var editorReloadToken by remember { mutableIntStateOf(0) }
    // Tracks which buffer the native editor is showing, so switching workspace
    // files pushes new text only when the file really changed.
    val loadedBuffer = remember { LoadedBufferHolder() }

    // The active file decides the language: Pawn scripts keep the Pawn highlighter,
    // while an SA-MP `server.cfg` switches to the config highlighter and completion.
    val activeDocument = workspace.activeDocument
    val activeFilePath = activeDocument?.file?.absolutePath
        ?: if (workspace.isWorkspaceOpen) null else viewModel.filePath
    val codeLanguage = remember(activeFilePath, appLanguage) {
        CodeLanguages.forFile(activeFilePath?.let(::File), appLanguage)
    }

    // Journal: which document is in front, so a crash inside the editor names its file.
    // Runs on every tab switch, which is exactly the granularity a crash timeline needs.
    LaunchedEffect(activeFilePath) {
        if (!activeFilePath.isNullOrBlank()) {
            com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal.record(
                context,
                "editor",
                "file ${File(activeFilePath).name}"
            )
        }
    }

    val hasUnsavedChanges =
        if (workspace.isWorkspaceOpen) {
            workspace.hasDirtyDocuments
        } else {
            !viewModel.isTemporaryFile && viewModel.hasUnsavedChanges
        }
    val displayFileName = if (workspace.isWorkspaceOpen) {
        activeDocument?.name ?: "No file open"
    } else {
        viewModel.fileName + if (viewModel.isTemporaryFile) " (internal temporary)" else ""
    }
    val displayFilePath = if (workspace.isWorkspaceOpen) {
        activeDocument?.file?.path ?: workspace.activeWorkspace?.root?.path.orEmpty()
    } else {
        viewModel.filePath
    }
    val workspaceHasNoActiveFile = workspace.isWorkspaceOpen && activeDocument == null

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
                    "That folder is not on local storage, the editor cannot browse it",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val pawnFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val pickedUri = result.data?.data ?: return@rememberLauncherForActivityResult
        scope.launch {
            val file = resolveDocumentToFile(context, pickedUri)
            when {
                file == null -> Toast.makeText(
                    context, "Choose a Pawn file on local primary storage", Toast.LENGTH_LONG
                ).show()
                file.extension.lowercase() !in EDITOR_OPENABLE_EXTENSIONS -> Toast.makeText(
                    context, "Only .pawn, .pwn, .p, .inc, .txt, .log and .cfg files are supported", Toast.LENGTH_LONG
                ).show()
                else -> {
                    workspace.registerExternalFile(file)
                    if (workspace.activeDocument?.file == file) isExplorerVisible = true
                    else Toast.makeText(context, "Could not open file (workspace limit reached)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(workspace.statusMessage) {
        workspace.statusMessage?.let { message ->
            noticeMessage = message
            workspace.clearStatusMessage()
        }
    }

    LaunchedEffect(compileOutput, isCompiling) {
        if (isCompiling || compileOutput.isNotBlank()) isExplorerVisible = true
    }

    /** Saves the file shown in the editor, in whichever mode is active. */
    val saveActiveFile: () -> Unit = {
        val document = workspace.activeDocument
        if (document != null) {
            scope.launch {
                val saved = workspace.saveDocument(document)
                workspace.refreshTree()
                // The reopen list follows the save, so an editor left right after a
                // save reopens exactly what was stored.
                workspace.persistOpenTabs()
                if (saved) {
                    Toast.makeText(context, "Saved ${document.name}", Toast.LENGTH_SHORT).show()
                }
            }
        } else if (viewModel.isTemporaryFile) {
            Toast.makeText(
                context,
                "The internal temporary file cannot be saved. Use Save As to create a Pawn file.",
                Toast.LENGTH_SHORT
            ).show()
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

    /** Replaces inside the file currently open. */
    val runReplacePerFile: (Boolean) -> Unit = { allOccurrences ->
        if (toolFind.isNotEmpty()) {
            val editor = editorRef
            val document = workspace.activeDocument
            if (document == null) {
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
        val content = editorRef?.text?.toString()
        if (content == null) {
            Toast.makeText(context, "Save As failed: editor is not ready", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        try {
            context.contentResolver.takePersistableUriPermission(
                targetUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Save As failed: ${e.localizedMessage ?: "Unknown error"}",
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }

        val displayName = try {
            context.contentResolver.query(
                targetUri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            } ?: targetUri.lastPathSegment?.substringAfterLast('/') ?: viewModel.fileName
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Save As failed: ${e.localizedMessage ?: "Unable to read the selected filename"}",
                Toast.LENGTH_SHORT
            ).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.saveAs(targetUri, content, displayName) { saved ->
            if (saved) {
                Toast.makeText(context, "Saved as: $displayName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val hashFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        hashFileUri = uri
        hashFileName = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment
        hashResult = null
        hashError = null
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = {
                Text(if (appLanguage == ContentConfig.AppLanguage.ID) "File Kode Baru" else "New Code File")
            },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    label = {
                        Text(if (appLanguage == ContentConfig.AppLanguage.ID) "Nama file" else "File name")
                    },
                    placeholder = { Text("Server.pwn") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFileName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (workspace.createNewPawnFile(newFileName, newFileParent) != null) {
                                showNewFileDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text(if (appLanguage == ContentConfig.AppLanguage.ID) "Folder Baru" else "New Folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = {
                        Text(if (appLanguage == ContentConfig.AppLanguage.ID) "Nama folder" else "Folder name")
                    },
                    placeholder = { Text("gamemodes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newFolderName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (workspace.createNewFolder(newFolderName, newFileParent) != null) {
                                showNewFolderDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    BackHandler {
        // A running compile must not be abandoned mid-flight: back is blocked
        // until the job finishes, matching the disabled back button in the bar.
        if (isCompiling) return@BackHandler
        if (hasUnsavedChanges) {
            showExitDialog = true
        } else {
            // Remember what was open before leaving, so the next entry into the
            // editor reopens the same tabs instead of an empty session.
            workspace.persistOpenTabs()
            onNavigateBack()
        }
    }

    LaunchedEffect(viewModel.statusMessage) {
        viewModel.statusMessage?.let { msg ->
            noticeMessage = msg
            viewModel.clearStatusMessage()
        }
    }

    noticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { noticeMessage = null },
            title = { Text(when (appLanguage) {
                ContentConfig.AppLanguage.ID -> "Informasi"
                ContentConfig.AppLanguage.ES -> "Informacion"
                ContentConfig.AppLanguage.RU -> "informatsiya"
                ContentConfig.AppLanguage.TL -> "Abiso"
                ContentConfig.AppLanguage.EN -> "Notice"
            }) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { noticeMessage = null }) {
                    Text(when (appLanguage) {
                        ContentConfig.AppLanguage.ID -> "Tutup"
                        ContentConfig.AppLanguage.ES -> "Cerrar"
                        ContentConfig.AppLanguage.RU -> "Zakryt"
                        ContentConfig.AppLanguage.TL -> "Isara"
                        ContentConfig.AppLanguage.EN -> "Dismiss"
                    })
                }
            }
        )
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
                                var allSaved = true
                                workspace.openWorkspaces.forEach { target ->
                                    val dirtyCount = target.documents.count { it.isDirty }
                                    if (workspace.saveAll(target) != dirtyCount) allSaved = false
                                }
                                showExitDialog = false
                                if (allSaved) {
                                    workspace.openWorkspaces.forEach { openWorkspace ->
                                        openWorkspace.documents.forEach { document -> workspace.openFile(document.file, openWorkspace) }
                                    }
                                    // The tab list is written after saving so a reopen
                                    // matches what was just flushed to disk.
                                    workspace.persistOpenTabs()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Some files could not be saved. Review the unsaved tabs and retry.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            val currentText = editorRef?.text?.toString() ?: ""
                            viewModel.saveFile(currentText) { success ->
                                showExitDialog = false
                                if (success) {
                                    viewModel.loadFile()
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
                            scope.launch {
                                // Discard rolls every dirty buffer back to what is
                                // already on disk, so the file keeps its original
                                // content; only Save commits the edits.
                                workspace.openWorkspaces.forEach { target ->
                                    target.documents.filter { it.isDirty }.forEach { document ->
                                        runCatching {
                                            document.updateContent(document.file.readText())
                                            document.markSaved()
                                        }
                                    }
                                }
                                if (!viewModel.isTemporaryFile && viewModel.hasUnsavedChanges) {
                                    viewModel.loadFile()
                                }
                                workspace.persistOpenTabs()
                                onNavigateBack()
                            }
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
        // Opening the dialog processes `injector_data.json`: the damaged lines of the
        // file now open become the rotating shadow hints of the line input.
        val jumpTargetPath = if (workspace.isWorkspaceOpen) {
            workspace.activeDocument?.file?.absolutePath
        } else {
            viewModel.filePath.takeIf { it.isNotBlank() }
        }
        val damagedLines = remember(jumpTargetPath) {
            jumpTargetPath?.let { DiagnosticsStore.damagedLines(context, it) }.orEmpty()
        }
        // When no damaged line exists the placeholder stays the plain localized
        // "line number" label; otherwise it cycles random damaged lines while
        // the user keeps the field idle.
        var shadowLine by remember(damagedLines) {
            mutableStateOf(damagedLines.randomOrNull())
        }
        LaunchedEffect(damagedLines) {
            while (damagedLines.size > 1) {
                delay(4_000L)
                // Pick a different line than the one showing, so the rotation
                // is always visible to the user.
                val next = damagedLines.filter { it != shadowLine }.randomOrNull()
                if (next != null) shadowLine = next
            }
        }
        var lineInput by remember { mutableStateOf("") }
        var columnInput by remember { mutableStateOf("") }
        var showColumnInput by remember { mutableStateOf(false) }
        val linePlaceholder = when (shadowLine) {
            null -> localizer.get("editor.jump.shadow.line", appLanguage, "line number")
            else -> "$shadowLine ${localizer.get("editor.jump.shadow.damaged", appLanguage, "damaged line")}"
        }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = {
                Text(if (appLanguage == ContentConfig.AppLanguage.ID) "Lompat ke Posisi" else "Jump to Position")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lineInput,
                        onValueChange = { lineInput = it.filter { ch -> ch.isDigit() } },
                        label = {
                            Text(if (appLanguage == ContentConfig.AppLanguage.ID) "Nomor baris" else "Line number")
                        },
                        placeholder = {
                            Text(
                                linePlaceholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    if (damagedLines.isNotEmpty()) {
                        // One-tap fill: tapping a damaged line drops it into the
                        // input so pressing Go jumps straight to it.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            damagedLines.take(6).forEach { damaged ->
                                AssistChip(
                                    onClick = { lineInput = damaged.toString() },
                                    label = { Text(damaged.toString()) }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            if (showColumnInput) columnInput = ""
                            showColumnInput = !showColumnInput
                        }
                    ) {
                        Text(
                            if (showColumnInput) {
                                if (appLanguage == ContentConfig.AppLanguage.ID) "Sembunyikan Kolom" else "Hide Column"
                            } else {
                                if (appLanguage == ContentConfig.AppLanguage.ID) "Tampilkan Kolom" else "Show Column"
                            }
                        )
                    }
                    if (showColumnInput) {
                        OutlinedTextField(
                            value = columnInput,
                            onValueChange = { columnInput = it.filter { ch -> ch.isDigit() } },
                            label = {
                                Text(
                                    if (appLanguage == ContentConfig.AppLanguage.ID) "Kolom (opsional)" else "Column (optional)"
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetLine = lineInput.toIntOrNull()
                        if (targetLine != null && targetLine > 0) {
                            editorRef?.let { editor ->
                                val line = (targetLine - 1).coerceIn(0, editor.text.lineCount - 1)
                                val lineLength = editor.text.getLine(line).length
                                val column = resolveJumpColumn(
                                    columnInput.takeIf { showColumnInput },
                                    lineLength
                                )
                                editor.jumpToLine(line)
                                editor.setSelection(line, column)
                            }
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

    if (showAboutDialog) {
        EditorAboutDialog(
            localizer = localizer,
            language = appLanguage,
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showHashDialog) {
        AlertDialog(
            onDismissRequest = { showHashDialog = false },
            title = { Text("Hash Generator") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!hashTextMode) {
                            FilledTonalButton(onClick = { hashTextMode = false; hashResult = null; hashError = null }) {
                                Text("Current file")
                            }
                        } else {
                            TextButton(onClick = { hashTextMode = false; hashResult = null; hashError = null }) {
                                Text("Current file")
                            }
                        }
                        if (hashTextMode) {
                            FilledTonalButton(onClick = { hashTextMode = true; hashResult = null; hashError = null }) {
                                Text("Text")
                            }
                        } else {
                            TextButton(onClick = { hashTextMode = true; hashResult = null; hashError = null }) {
                                Text("Text")
                            }
                        }
                        Box {
                            TextButton(onClick = { showHashAlgorithmMenu = true }) {
                                Text(hashAlgorithm)
                            }
                            DropdownMenu(
                                expanded = showHashAlgorithmMenu,
                                onDismissRequest = { showHashAlgorithmMenu = false }
                            ) {
                                listOf("MD5", "SHA-1", "SHA-256", "SHA-512").forEach { algorithm ->
                                    DropdownMenuItem(
                                        text = { Text(algorithm) },
                                        onClick = {
                                            hashAlgorithm = algorithm
                                            hashResult = null
                                            hashError = null
                                            showHashAlgorithmMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (hashTextMode) {
                        OutlinedTextField(
                            value = hashText,
                            onValueChange = { hashText = it; hashResult = null; hashError = null },
                            label = { Text("Text to hash") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = hashFileName
                                    ?: activeDocument?.file?.name
                                    ?: viewModel.fileName.ifBlank { "No file open" },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { hashFilePickerLauncher.launch(buildFilePickerIntent()) }
                            ) {
                                Icon(Icons.Filled.FolderOpen, contentDescription = null)
                                Text("Choose file")
                            }
                        }
                    }
                    hashError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    hashResult?.let { result ->
                        Text(
                            text = result,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("$hashAlgorithm hash", result))
                        }) {
                            Text("Copy")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isHashing,
                    onClick = {
                    val selectedUri = if (hashTextMode) null else hashFileUri
                    val editorBytes = when {
                        hashTextMode -> hashText.toByteArray(Charsets.UTF_8)
                        selectedUri == null -> editorRef?.text?.toString()?.toByteArray(Charsets.UTF_8)
                        else -> null
                    }
                    if (!hashTextMode && selectedUri == null && editorBytes == null) {
                        hashError = "Open a file first"
                    } else {
                        isHashing = true
                        scope.launch {
                            runCatching {
                                val digest = java.security.MessageDigest.getInstance(hashAlgorithm)
                                if (selectedUri != null) {
                                    withContext(Dispatchers.IO) {
                                        val input = context.contentResolver.openInputStream(selectedUri)
                                            ?: error("Unable to open selected file")
                                        input.use { stream ->
                                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                            while (true) {
                                                val count = stream.read(buffer)
                                                if (count < 0) break
                                                digest.update(buffer, 0, count)
                                            }
                                        }
                                        digest.digest()
                                    }
                                } else {
                                    digest.digest(editorBytes ?: error("Open a file first"))
                                }.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                            }.onSuccess {
                                hashResult = it
                                hashError = null
                            }.onFailure {
                                hashError = it.localizedMessage ?: "Unable to generate hash"
                            }
                            isHashing = false
                        }
                    }
                }) {
                    Text(if (isHashing) "Generating..." else "Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHashDialog = false }) { Text("Close") }
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
                            text = displayFileName + if (hasUnsavedChanges) " *" else "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = displayFilePath,
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
                            dismissAutoCompletion()
                            if (isCompiling) {
                                Toast.makeText(
                                    context,
                                    "Compilation in progress, please wait",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@IconButton
                            }
                            if (hasUnsavedChanges) {
                                showExitDialog = true
                            } else {
                                workspace.persistOpenTabs()
                                onNavigateBack()
                            }
                        },
                        enabled = !isCompiling,
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
                            dismissAutoCompletion()
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
                            dismissAutoCompletion()
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
                        IconButton(onClick = {
                            dismissAutoCompletion()
                            showMenu = true
                        }) {
                            Icon(Icons.Filled.HourglassEmpty, contentDescription = "Editor options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Workspace Folder...") },
                                leadingIcon = {
                                    Icon(_Icons.Workspace, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    folderPickerLauncher.launch(buildFolderPickerIntent())
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
                                        dismissAutoCompletion()
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
                                        dismissAutoCompletion()
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
                                    dismissAutoCompletion()
                                    showMenu = false
                                    toolPanelMode = ToolPanelMode.SearchWorkspace
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Replace Words per File") },
                                leadingIcon = {
                                    Icon(Icons.Filled.FindReplace, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    toolPanelMode = ToolPanelMode.ReplacePerFile
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Replace Words All Files") },
                                leadingIcon = {
                                    Icon(Icons.Filled.FindReplace, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    toolPanelMode = ToolPanelMode.ReplaceAllFiles
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hash Generator") },
                                leadingIcon = {
                                    Icon(Icons.Filled.PrecisionManufacturing, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    hashResult = null
                                    hashError = null
                                    hashFileUri = null
                                    hashFileName = null
                                    showHashDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isExplorerVisible) "Hide Workspace Panel" else "Show Workspace Panel") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isExplorerVisible) Icons.Filled.ChevronRight else Icons.Filled.Explore,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    isExplorerVisible = !isExplorerVisible
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (floatingButtonsHidden) {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) "Tampilkan tombol mengambang" else "Show floating buttons"
                                        } else {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) "Sembunyikan tombol mengambang" else "Hide floating buttons"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (floatingButtonsHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    floatingButtonsHidden = !floatingButtonsHidden
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Select All") },
                                leadingIcon = {
                                    Icon(Icons.Filled.SelectAll, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    editorRef?.selectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Jump to Line...") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    showJumpDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (editorAutoCloseEnabled) {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) "Matikan tutup otomatis" else "Disable auto-close brackets"
                                        } else {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) "Nyalakan tutup otomatis" else "Enable auto-close brackets"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (editorAutoCloseEnabled) Icons.Filled.Code else Icons.Filled.Block,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    editorAutoCloseEnabled = !editorAutoCloseEnabled
                                    ContentConfig.getInstance(context).n_editor_auto_close =
                                        if (editorAutoCloseEnabled) {
                                            ContentConfig.EditorAutoClose.PAIR_ALWAYS
                                        } else {
                                            ContentConfig.EditorAutoClose.SMART
                                        }
                                    Toast.makeText(
                                        context,
                                        if (editorAutoCloseEnabled) {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) {
                                                "Tutup otomatis dinyalakan"
                                            } else {
                                                "Auto-close brackets enabled"
                                            }
                                        } else {
                                            if (appLanguage == ContentConfig.AppLanguage.ID) {
                                                "Tutup otomatis dimatikan"
                                            } else {
                                                "Auto-close brackets disabled"
                                            }
                                        },
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Jump to Matching Bracket") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Code, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
                                    showMenu = false
                                    editorRef?.let { editor ->
                                        val match = findMatchingBracketPosition(editor)
                                        if (match != null) {
                                            editor.jumpToLine(match.first)
                                            editor.setSelection(match.first, match.second)
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isWordWrap) "Disable Word Wrap" else "Enable Word Wrap") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = null)
                                },
                                onClick = {
                                    dismissAutoCompletion()
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
                                    dismissAutoCompletion()
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
                                    dismissAutoCompletion()
                                    showMenu = false
                                    isLineNumbers = !isLineNumbers
                                    editorRef?.isLineNumberEnabled = isLineNumbers
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        localizer.get(
                                            if (isQuickSymbolBarVisible) "editor.quickkeys.hide" else "editor.quickkeys.show",
                                            appLanguage,
                                            if (isQuickSymbolBarVisible) "Hide Quick Keys" else "Show Quick Keys"
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Keyboard, contentDescription = null)
                                },
                                onClick = {
                                    isQuickSymbolBarVisible = !isQuickSymbolBarVisible
                                    showMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(localizer.get("editor.about.menu", appLanguage, "About PawnMC Editor"))
                                },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
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
                .imePadding()
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

            // The editor body: tab strip, canvas, right-edge sidebar, and every floating
            // control in one Box so they share one coordinate space.
            //
            // This Box is the overlay layer, not just a canvas host. The floating stack
            // used to live in a second sibling Box below this one; because both had
            // `weight(1f)` they split the height, which put the compile button and the
            // workspace toggle in a band underneath the canvas - the "wall" that stopped
            // them reaching the top of the editor. They belong here, over the code, where
            // they can float anywhere in the body.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                // Visual Studio Code style tab strip above the code canvas.
                EditorTabStrip(
                    documents = workspace.documents,
                    activePath = workspace.activePath,
                    onSelectTab = { file ->
                        dismissAutoCompletion()
                        workspace.openFile(file)
                    },
                    onCloseTab = { file ->
                        dismissAutoCompletion()
                        workspace.closeDocument(file)
                    },
                    onNewTabClick = {
                        dismissAutoCompletion()
                        isExplorerVisible = !isExplorerVisible
                    },
                    onOpenFileClick = {
                        dismissAutoCompletion()
                        folderPickerLauncher.launch(buildFolderPickerIntent())
                    },
                    onOpenPawnFileClick = {
                        dismissAutoCompletion()
                        pawnFilePickerLauncher.launch(buildFilePickerIntent())
                    },
                    onCreateFileClick = {
                        dismissAutoCompletion()
                        openNewFileDialog(workspace.activeWorkspace?.root ?: context.filesDir)
                    }
                )

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // The code canvas. The explorer overlay is anchored to this Box, so a
                    // dragged panel cannot drift over the tab strip or the sidebar.
                    Box(modifier = Modifier.fillMaxSize()) {
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
                        // An empty workspace must not fall through to a stale single-file
                        // buffer such as TMP/blank.pwn; offer workspace navigation instead.
                        if (workspaceHasNoActiveFile) {
                            EmptyWorkspaceView(
                                onBrowseExplorer = { isExplorerVisible = !isExplorerVisible },
                                onOpenFolder = { folderPickerLauncher.launch(buildFolderPickerIntent()) }
                            )
                        } else {
                        // Explicit type argument: without it Kotlin cannot infer the view type of the
// factory and the update block is reported as untyped.
                        AndroidView<XedCodeEditor>(
                            factory = { ctx: Context ->
                                // The last expression must be the editor itself: an
                                // `apply` block returns Unit, which would break the
                                // factory's return type.
                                val created = XedCodeEditor(ctx)
                                created.apply {
                                    typefaceText = Typeface.MONOSPACE
                                    isLineNumberEnabled = isLineNumbers
                                    isWordwrap = isWordWrap
                                    editable = !isReadOnly
                                    this.autoCloseMode = when {
                                        !editorAutoCloseEnabled -> ContentConfig.EditorAutoClose.SMART
                                        storedAutoCloseMode != ContentConfig.EditorAutoClose.SMART -> storedAutoCloseMode
                                        else -> ContentConfig.EditorAutoClose.PAIR_ALWAYS
                                    }
                                    setTextSize(14f)
                                    // Same crash guard as the update pass: the very
                                    // first scheme install can race the editor's lazy
                                    // internals on a re-created composition.
                                    runCatching { setColorScheme(editorScheme) }
                                    setEditorLanguage(codeLanguage)
                                    setText(if (workspace.isWorkspaceOpen) workspace.activeDocument?.content.orEmpty() else viewModel.fileContent.orEmpty())
                                    // Reserve room for the "-<column>" suffix now
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
                                        // No +1 here: the status bar applies the
                                        // single one-based conversion itself.
                                        cursorLine = cursor.leftLine
                                        cursorCol = cursor.leftColumn
                                        refreshColumnSuffixWidth()
                                    }

                                    editorRef = created
                                }
                                created
                            },
                            update = { editor: XedCodeEditor ->
                                editorRef = editor
                                // A `server.cfg` keeps the Pawn grammar out of the way: the
                                // widget must re-analyse with the config language as soon as a
                                // workspace file with another extension is activated.
                                if (appliedLanguage.current !== codeLanguage) {
                                    editor.setEditorLanguage(codeLanguage)
                                    appliedLanguage.current = codeLanguage
                                    // setEditorLanguage resets the symbol-pair
                                    // matcher underneath; re-assert the toggle so
                                    // the auto-close state survives the swap.
                                    editor.autoCloseMode = editor.autoCloseMode
                                }
                                // Re-tint when the app theme (and therefore the surface
                                // ramp) changes while the editor view is still alive.
                                if (appliedScheme.current !== editorScheme) {
                                    // A theme swap lands on the same frame the sora
                                    // internals can still be re-creating their render
                                    // queue (an `addLast` on a not-yet-created list);
                                    // dropping one repaint is invisible, a crash is not.
                                    runCatching { editor.setColorScheme(editorScheme) }
                                    appliedScheme.current = editorScheme
                                }
                                // The toggle decides live: off means never insert a
                                // closer, on means the stored mode (or "Always pair"
                                // when the stored mode is the off-flavoured SMART).
                                editor.autoCloseMode = when {
                                    !editorAutoCloseEnabled -> ContentConfig.EditorAutoClose.SMART
                                    storedAutoCloseMode != ContentConfig.EditorAutoClose.SMART -> storedAutoCloseMode
                                    else -> ContentConfig.EditorAutoClose.PAIR_ALWAYS
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
                                    cursorLine = 0
                                    cursorCol = 0
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        }
                    }

                // Floating explorer: like every other floating surface it is a free-floating
                // card that the user drags anywhere inside the editor canvas, not a
                // docked sidebar welded to one edge. It lives inside the canvas Box on
                // purpose: `fillMaxSize` there means "the code area", while in the Row it
                // would claim the sidebar's width and push the strip off screen.
                if (isExplorerVisible) {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val panelWidth = explorerWidth.dp
                            .coerceAtMost(maxWidth - (EXPLORER_EDGE_MARGIN * 2))
                        // The card is anchored to the top start, so the drag range is the
                        // leftover room: horizontally minus the panel width, vertically
                        // minus the height the card itself occupies.
                        val maxOffsetX = with(density) {
                            (maxWidth - panelWidth - EXPLORER_EDGE_MARGIN).toPx()
                                .coerceAtLeast(0f).roundToInt()
                        }
                        val maxOffsetY = with(density) {
                            (maxHeight - maxHeight * EXPLORER_HEIGHT_FRACTION - EXPLORER_EDGE_MARGIN)
                                .toPx().coerceAtLeast(0f).roundToInt()
                        }
                        val imeBottom = WindowInsets.ime.getBottom(density)
                        val isKeyboardVisible = imeBottom > 0
                        val clampedExplorerOffset = IntOffset(
                            explorerOffset.x.coerceIn(0, maxOffsetX),
                            explorerOffset.y.coerceIn(0, maxOffsetY)
                        )
                        val controlsRightX = with(density) { (maxWidth - 68.dp).toPx().roundToInt().coerceAtLeast(0) }
                        val controlsLeftX = with(density) { 12.dp.toPx().roundToInt() }
                        val controlY = with(density) { (maxHeight - imeBottom.toDp() - 150.dp).toPx().roundToInt().coerceAtLeast(0) }
                        val panelWidthPx = with(density) { panelWidth.toPx().roundToInt() }
                        val overlapsRightControls = isKeyboardVisible &&
                            clampedExplorerOffset.x + panelWidthPx > controlsRightX &&
                            clampedExplorerOffset.y + with(density) { (maxHeight.roundToPx() * EXPLORER_HEIGHT_FRACTION).roundToInt() } > controlY
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 10.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset {
                                    val safeOffset = IntOffset(
                                        clampedExplorerOffset.x.coerceIn(0, maxOffsetX),
                                        clampedExplorerOffset.y.coerceIn(0, maxOffsetY)
                                    )
                                    if (!overlapsRightControls) safeOffset
                                    else {
                                        val shift = with(density) { 64.dp.roundToPx() }
                                        val leftCandidate = (safeOffset.x - shift).coerceIn(0, maxOffsetX)
                                        val rightCandidate = (safeOffset.x + shift).coerceIn(0, maxOffsetX)
                                        val leftCanMove = leftCandidate < safeOffset.x
                                        val rightCanMove = rightCandidate > safeOffset.x
                                        val nextX = when {
                                            leftCanMove -> leftCandidate
                                            rightCanMove -> rightCandidate
                                            else -> safeOffset.x
                                        }
                                        IntOffset(nextX, safeOffset.y)
                                    }
                                }
                                .padding(start = EXPLORER_EDGE_MARGIN, top = EXPLORER_EDGE_MARGIN)
                                .width(panelWidth)
                                .fillMaxHeight(EXPLORER_HEIGHT_FRACTION)
                                .zIndex(2f)
                        ) {
                            WorkspacePanel(
                                session = workspace,
                                outputText = compileOutput,
                                isCompiling = isCompiling,
                                onOpenFile = { file ->
                                    dismissAutoCompletion()
                                    openWorkspaceFile(file)
                                },
                                onCloseFile = { file ->
                                    dismissAutoCompletion()
                                    workspace.closeDocument(file)
                                },
                                onSelectFile = { file ->
                                    dismissAutoCompletion()
                                    workspace.openFile(file)
                                },
                                onOpenHit = { hit ->
                                    dismissAutoCompletion()
                                    workspace.openFile(hit.file)
                                    // The buffer is filled asynchronously, so the jump has
                                    // to wait for the reload to land in the widget.
                                    editorRef?.postDelayed({
                                        editorRef?.jumpToLine((hit.line - 1).coerceAtLeast(0))
                                    }, 250)
                                },
                                onClosePanel = { isExplorerVisible = false },
                                onOpenFolder = {
                                    dismissAutoCompletion()
                                    folderPickerLauncher.launch(buildFolderPickerIntent())
                                },
                                onCreateFileAt = { directory ->
                                    dismissAutoCompletion()
                                    openNewFileDialog(directory)
                                },
                                onCreateFolderAt = { directory ->
                                    dismissAutoCompletion()
                                    openNewFolderDialog(directory)
                                },
                                onBeforeAction = ::dismissAutoCompletion,
                                onHeaderDrag = { delta ->
                                    explorerOffset = IntOffset(
                                        (clampedExplorerOffset.x + delta.x).coerceIn(0, maxOffsetX),
                                        (clampedExplorerOffset.y + delta.y).coerceIn(0, maxOffsetY)
                                    )
                                }
                            )
                        }
                    }
                }

                }

                // Scroll sidebar, docked to the right edge of the editor body.
                //
                // A trailing sibling of the canvas inside the Row, so it owns its own
                // strip of width instead of painting over the last characters of a line.
                // The Row is the canvas row, not the whole body: the tab strip above keeps
                // the full width, and the floating buttons still get the body to move in.
                EditorScrollSidebar(
                    editor = editorRef,
                    activeDocument = activeDocument,
                    singleFileContent = viewModel.fileContent,
                    onBeforeSeek = ::dismissAutoCompletion,
                    modifier = Modifier.fillMaxHeight()
                )
                }
            }

            // The floating stack, back inside the editor body.
            //
            // This used to be a second, separate Box under the canvas. Both carried
            // `weight(1f)`, so the two split the available height: the canvas got the top
            // half and the compile button, workspace toggle and toolbar were marooned in
            // the bottom half with no way to reach the top of the editor. Living here, in
            // the same Box as the canvas, they float over the code again.
            Box(modifier = Modifier.fillMaxSize()) {
                toolPanelMode?.let { mode ->
                    ToolPanel(
                        mode = mode,
                        find = toolFind,
                        replace = toolReplace,
                        isBusy = isToolBusy,
                        onFindChange = { toolFind = it },
                        onReplaceChange = { toolReplace = it },
                        onRun = {
                            dismissAutoCompletion()
                            when (mode) {
                                ToolPanelMode.SearchWorkspace -> runWorkspaceSearch()
                                ToolPanelMode.ReplacePerFile -> runReplacePerFile(true)
                                ToolPanelMode.ReplaceAllFiles -> runReplaceAllFiles()
                            }
                        },
                        onRunSecondary = if (mode == ToolPanelMode.ReplacePerFile) {
                            {
                                dismissAutoCompletion()
                                runReplacePerFile(false)
                            }
                        } else {
                            null
                        },
                        onClose = {
                            dismissAutoCompletion()
                            toolPanelMode = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

            // Master gate: when hidden, the whole floating stack (Developer Portal,
            // orientation, workspace toggle, rectangle toolbar, compile button)
            // disappears and the canvas is left clean.
            if (!floatingButtonsHidden) {
            FloatingRoundAction(
                size = PANEL_BUTTON_SIZE,
                icon = Icons.Filled.Extension,
                contentDescription = "Open the Developer Portal",
                    testTag = "editor_terminal_toggle",
                    tint = FLOAT_TINT_PORTAL,
                    isActive = isPawnTerminalVisible,
                    reservedBelow = TOOLBAR_HEIGHT + FLOAT_GAP + PANEL_BUTTON_SIZE + FLOAT_GAP,
                    bubbleIndex = 0,
                    editor = editorRef,
                    onClick = { isPawnTerminalVisible = !isPawnTerminalVisible }
                )

                // Portrait / Landscape toggle: a floating round action at the left side of
                // the canvas, so it never stacks on top of the right column of buttons.
                // It flips the activity orientation lock and the icon reflects the mode
                // so the current state is obvious even without reading the label.
                FloatingRoundAction(
                    size = PANEL_BUTTON_SIZE,
                    icon = _Icons.Scarf,
                    contentDescription = if (isLandscapeForced) "Switch to Portrait" else "Switch to Landscape",
                    testTag = "editor_orientation_toggle",
                    tint = FLOAT_TINT_ORIENTATION,
                    isActive = isLandscapeForced,
                    alignRight = false,
                    bubbleIndex = 1,
                    editor = editorRef,
                    onClick = {
                        val next = !isLandscapeForced
                        isLandscapeForced = next
                        (context as? Activity)?.requestedOrientation = if (next) {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                )

                // Keep the workspace toggle above the toolbar so it remains reachable
                // while the explorer panel is open.
                FloatingRoundAction(
                    size = PANEL_BUTTON_SIZE,
                    icon = Icons.Filled.Explore,
                    contentDescription = if (isExplorerVisible) "Hide workspace panel" else "Show workspace panel",
                    testTag = "editor_explorer_toggle",
                    tint = FLOAT_TINT_NEUTRAL,
                    isActive = isExplorerVisible,
                    reservedBelow = TOOLBAR_HEIGHT + FLOAT_GAP,
                    bubbleIndex = 2,
                    editor = editorRef,
                    onClick = { isExplorerVisible = !isExplorerVisible }
                )

                // Rectangle-style toolbar remains directly above the compile button.
                FloatingRectToolbar(
                    hasUnsavedChanges = hasUnsavedChanges,
                    workspaceOpen = workspace.isWorkspaceOpen,
                    saveEnabled = workspace.isWorkspaceOpen || !viewModel.isTemporaryFile,
                    isSearchOpen = showSearchBar,
                    onBeforeAction = ::dismissAutoCompletion,
                    onSearchClick = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) editorRef?.searcher?.stopSearch()
                    },
                    onOpenWorkspaceClick = { folderPickerLauncher.launch(buildFolderPickerIntent()) },
                    onSaveClick = { saveActiveFile() },
                    onSaveAllClick = {
                        if (workspace.isWorkspaceOpen) {
                            scope.launch {
                                var allSaved = true
                                workspace.openWorkspaces.forEach { openWorkspace ->
                                    val dirtyCount = openWorkspace.documents.count { it.isDirty }
                                    if (workspace.saveAll(openWorkspace) != dirtyCount) allSaved = false
                                }
                                workspace.refreshTree()
                                if (!allSaved) {
                                    Toast.makeText(
                                        context,
                                        "Some files could not be saved.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            saveAsLauncher.launch(viewModel.fileName)
                        }
                    }
                )



                /**
                 * Quick-compile hotspot at the bottom of the floating stack.
                 *
                 * It was removed by mistake together with the download action: they
                 * were different buttons and this one is the fastest path to a build.
                 * The toolbar and the menu can compile too, but this is one tap and
                 * always where the thumb already is.
                 */
                FloatingCompileButton(
                    enabled = if (workspace.isWorkspaceOpen) {
                        activeDocument != null
                    } else {
                        viewModel.filePath.isNotBlank()
                    },
                    onClick = {
                        dismissAutoCompletion()
                        val target = if (workspace.isWorkspaceOpen) {
                            activeDocument?.file?.path.orEmpty()
                        } else {
                            viewModel.filePath
                        }
                        if (target.isBlank()) {
                            Toast.makeText(context, "Open a file first", Toast.LENGTH_SHORT).show()
                        } else {
                            /*
                             * Unsaved buffers are written first so the compiler
                             * sees exactly what is on screen.
                             */
                            scope.launch {
                                var allSaved = true
                                if (activeDocument == null && viewModel.hasUnsavedChanges) {
                                    allSaved = viewModel.saveFileSync(editorRef?.text?.toString() ?: "")
                                }
                                workspace.openWorkspaces.forEach { openWorkspace ->
                                    val dirty = openWorkspace.documents.count { it.isDirty }
                                    if (workspace.saveAll(openWorkspace) != dirty) allSaved = false
                                }
                                if (allSaved) {
                                    onCompileRequest(target)
                                    isExplorerVisible = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Compilation cancelled because a file could not be saved.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }



                if (isPawnTerminalVisible) {
                    val terminalSource = if (workspace.isWorkspaceOpen) {
                        activeDocument?.file
                    } else {
                        viewModel.filePath.takeIf { it.isNotBlank() }?.let { File(it) }
                    }
                    DeveloperPortal(
                        context = context,
                        language = appLanguage,
                        activeSource = terminalSource,
                        workspaceRoot = workspace.activeWorkspace?.root ?: terminalSource?.parentFile,
                        selectedVersion = pawnConsoleVersion,
                        onVersionChange = { pawnConsoleVersion = it },
                        onClose = { isPawnTerminalVisible = false },
                        onCompile = { source, manualOptions, version ->
                            var allSaved = true
                            if (workspace.isWorkspaceOpen) {
                                workspace.openWorkspaces.forEach { openWorkspace ->
                                    val dirtyCount = openWorkspace.documents.count { it.isDirty }
                                    if (workspace.saveAll(openWorkspace) != dirtyCount) allSaved = false
                                }
                            } else if (viewModel.hasUnsavedChanges) {
                                allSaved = viewModel.saveFileSync(editorRef?.text?.toString() ?: "")
                            }

                            if (!allSaved) {
                                "Compilation cancelled because a file could not be saved."
                            } else {
                                val explained = withContext(Dispatchers.IO) {
                                    val result = Compiler.compileManual(
                                        sourceFile = source.absolutePath,
                                        options = manualOptions,
                                        version = version
                                    )
                                    Compiler.explainCompilerOutput(
                                        rawText = result.second,
                                        cacheDir = context.cacheDir,
                                        language = appLanguage
                                    ) + "\nExit code: ${result.first}"
                                }
                                // The compile above refreshed the diagnostics
                                // JSON; apply the hints right away so the
                                // comments show up while the user is still
                                // looking at the code, not only after leaving
                                // and reopening the editor.
                                refreshExplainHints()
                                explained
                            }
                        }
                    )
                }
            }
            }
            }

            if (isQuickSymbolBarVisible) {
                QuickSymbolBar(
                    onSymbolClick = { symbol ->
                        dismissAutoCompletion()
                        val editor = editorRef
                        if (editor != null && symbol.length == 1) {
                            val cursor = editor.cursor
                            val nextChar = runCatching {
                                editor.text.getLine(cursor.leftLine).getOrNull(cursor.leftColumn)
                            }.getOrNull()
                            if (symbol[0] in setOf(')', '}', ']') && nextChar == symbol[0]) {
                                editor.setSelection(cursor.leftLine, cursor.leftColumn + 1)
                            } else {
                                editor.text.insert(cursor.leftLine, cursor.leftColumn, symbol)
                            }
                        }
                    },
                    onTabClick = { applyTabAction(editorRef) }
                )
            }

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
                    text = cursorPositionLabel(cursorLine, cursorCol),
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
    }

    DisposableEffect(Unit) {
        onDispose {
            editorRef?.release()
            editorRef = null
        }
    }
}

private data class EditorAboutItem(
    val key: String,
    val title: String,
    val description: String
)

/** Plain holder for the installed editor language; written during composition. */
private class LanguageHolder {
    var current: io.github.rosemoe.sora.lang.Language? = null
}

@Composable
private fun EditorAboutDialog(
    localizer: AppLocalization,
    language: ContentConfig.AppLanguage,
    onDismiss: () -> Unit
) {
    val items = listOf(
        EditorAboutItem("editor.about.back", "Back", "Return to the file selection and compiler screen."),
        EditorAboutItem("editor.about.undo", "Undo", "Undo the most recent edit."),
        EditorAboutItem("editor.about.redo", "Redo", "Restore the most recently undone edit."),
        EditorAboutItem("editor.about.options", "Editor options", "Open workspace, search and replace tools, view controls, quick keys, and this About page."),
        EditorAboutItem("editor.about.open_workspace", "Open Workspace Folder", "Choose a local folder and browse its files in the workspace panel."),
        EditorAboutItem("editor.about.close_workspace", "Close Workspace", "Close the active workspace while keeping other open workspaces available."),
        EditorAboutItem("editor.about.close_all", "Close All Workspaces", "Close every workspace currently open in the editor."),
        EditorAboutItem("editor.about.search_workspace", "Search in Workspace", "Find text across files in the active workspace."),
        EditorAboutItem("editor.about.replace_file", "Replace Words per File", "Replace matches in the current file, one occurrence or all at once."),
        EditorAboutItem("editor.about.replace_workspace", "Replace Words All Files", "Replace matching text across the workspace files."),
        EditorAboutItem("editor.about.workspace_panel", "Show or Hide Workspace Panel", "Toggle the panel containing open files, folders, and search results."),
        EditorAboutItem("editor.about.select_all", "Select All", "Select all text in the current file."),
        EditorAboutItem("editor.about.jump", "Jump to Line", "Move the cursor directly to a line number."),
        EditorAboutItem("editor.about.wrap", "Word Wrap", "Turn line wrapping on or off."),
        EditorAboutItem("editor.about.read_only", "Read-Only Mode", "Prevent edits until editing is enabled again."),
        EditorAboutItem("editor.about.line_numbers", "Line Numbers", "Show or hide line numbers and the current column."),
        EditorAboutItem("editor.about.quickkeys_toggle", "Quick Keys Toggle", "Show or hide the bottom row with TAB, symbols, and digits."),
        EditorAboutItem("editor.about.about", "About PawnMC Editor", "Open this guide to PawnMC's editor controls."),
        EditorAboutItem("editor.about.search_file", "Search in File", "Find text in the current file and move between matches."),
        EditorAboutItem("editor.about.open_folder", "Open Folder", "Choose a workspace folder from the floating toolbar."),
        EditorAboutItem("editor.about.save", "Save", "Save the active file."),
        EditorAboutItem("editor.about.save_as_all", "Save As or Save All", "Save the current file under a new name, or save all modified workspace files."),
        EditorAboutItem("editor.about.compile", "Compile", "Save open workspace changes and compile the active Pawn file."),
        EditorAboutItem("editor.about.explorer", "Workspace Panel Button", "Show or hide the floating workspace panel."),
        EditorAboutItem("editor.about.tabs", "File Tabs", "Switch between open files or close a file from its tab."),
        EditorAboutItem("editor.about.new_tab", "Add or Open File", "Open the workspace panel or choose another folder."),
        EditorAboutItem("editor.about.tab_key", "TAB", "Insert one indentation level at the cursor."),
        EditorAboutItem("editor.about.symbol_keys", "Symbol and Number Keys", "Insert braces, parentheses, brackets, punctuation, or digits at the cursor.")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(localizer.get("editor.about.title", language, "About PawnMC Editor")) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    localizer.get(
                        "editor.about.intro",
                        language,
                        "The PawnMC editor gives you syntax highlighting, completion, workspace tools, and direct compilation."
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            localizer.get("${item.key}.title", language, item.title),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            localizer.get("${item.key}.desc", language, item.description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(localizer.get("editor.about.close", language, "Close"))
            }
        }
    )
}

/**
 * Shown when a workspace has no open document at all.
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
                imageVector = Icons.Filled.Explore,
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
                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Explorer")
                }
                FilledTonalButton(onClick = onOpenFolder) {
                    Icon(_Icons.FolderRect, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Folder")
                }
            }
        }
    }
}

/** Side of the editor the floating compile button rests on. */
private val FLOAT_MARGIN = 16.dp

/**
 * Extensions the Xed editor accepts when a file is opened into it.
 *
 * The compiler only ever accepts `.pawn/.pwn/.p/.inc`; the extra text formats
 * (`.txt`, `.log`, `.cfg`) are editor-only: they can be read and edited, but a
 * compile request against them is rejected by the main view model.
 */
internal val EDITOR_OPENABLE_EXTENSIONS = setOf("pawn", "pwn", "p", "inc", "txt", "log", "cfg")

/** Vertical gap between the stacked floating controls. */
private val FLOAT_GAP = 10.dp

/** Diameter of the floating compile button. */
private val COMPILE_BUTTON_SIZE = 58.dp

/** Diameter of the floating workspace panel button. */
private val PANEL_BUTTON_SIZE = 48.dp

/** Icon colours for the floating actions. */
private val FLOAT_TINT_NEUTRAL = Color(0xFF8A8A94)
private val FLOAT_TINT_PORTAL = Color(0xFFE7C66A)
private val FLOAT_TINT_ACTION = Color(0xFF43A047)
private val FLOAT_TINT_ORIENTATION = Color(0xFFE35B52)

/** Width of the rectangle-style floating toolbar. */
private val TOOLBAR_WIDTH = 52.dp

/** Height fraction of the canvas the floating workspace panel occupies. */
private const val EXPLORER_HEIGHT_FRACTION = 0.7f

/** Gap kept between the floating workspace panel and the canvas edges. */
private val EXPLORER_EDGE_MARGIN = 10.dp

/** Height of the rectangle-style floating toolbar (drag handle + four entries). */
private val TOOLBAR_HEIGHT = 232.dp

/**
 * Rest position of a floating control above the compile button.
 */
internal fun restOffsetAbove(
    controlWidth: Dp,
    controlHeight: Dp,
    maxWidthPx: Float,
    maxHeightPx: Float,
    density: Density,
    reservedBelow: Dp = 0.dp,
    alignRight: Boolean = true
): IntOffset = with(density) {
    val compilePx = COMPILE_BUTTON_SIZE.toPx()
    val controlWidthPx = controlWidth.toPx()
    val controlHeightPx = controlHeight.toPx()
    val marginPx = FLOAT_MARGIN.toPx()
    val gapPx = FLOAT_GAP.toPx()
    val reservedBelowPx = reservedBelow.toPx()

    val x = if (alignRight) {
        (maxWidthPx - marginPx - controlWidthPx - (compilePx - controlWidthPx) / 2f)
            .coerceAtLeast(0f)
    } else {
        // Left column: mirrored of the right rest position, so the gap between the
        // control and the left edge equals the gap the right column keeps.
        (marginPx - (compilePx - controlWidthPx) / 2f).coerceAtLeast(0f)
    }
    val compileTop = maxHeightPx - marginPx - compilePx
    val y = (compileTop - gapPx - reservedBelowPx - controlHeightPx).coerceAtLeast(0f)

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
    saveEnabled: Boolean,
    isSearchOpen: Boolean,
    onBeforeAction: () -> Unit,
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

        val draggedOffset = remember { mutableStateOf<IntOffset?>(null) }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier = Modifier
                .offset { draggedOffset.value ?: restOffset }
                .width(TOOLBAR_WIDTH)
                .height(TOOLBAR_HEIGHT)
                .pointerInput(maxWidthPx, maxHeightPx, toolbarWidthPx, toolbarHeightPx, restOffset) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedOffset.value ?: restOffset
                            val newX = (current.x + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - toolbarWidthPx).coerceAtLeast(0f))
                            val newY = (current.y + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - toolbarHeightPx).coerceAtLeast(0f))
                            draggedOffset.value = IntOffset(newX.roundToInt(), newY.roundToInt())
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
                    tint = FLOAT_TINT_ACTION,
                    onClick = {
                        onBeforeAction()
                        onSearchClick()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    icon = _Icons.FolderRect,
                    contentDescription = "Open workspace folder",
                    testTag = "editor_floating_open_workspace",
                    tint = FLOAT_TINT_ACTION,
                    onClick = {
                        onBeforeAction()
                        onOpenWorkspaceClick()
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    // Stopwatch-style save: the running save differs from the
                    // save-as/save-all entry below so the two never read as twins.
                    icon = Icons.Filled.Timer,
                    contentDescription = "Save file",
                    testTag = "editor_floating_save",
                    isActive = hasUnsavedChanges,
                    tint = FLOAT_TINT_ACTION,
                    onClick = {
                        onBeforeAction()
                        onSaveClick()
                    },
                    enabled = saveEnabled
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                ToolBarEntry(
                    icon = Icons.Filled.TimerOff,
                    contentDescription = if (workspaceOpen) "Save All" else "Save As",
                    testTag = "editor_floating_save_all",
                    tint = FLOAT_TINT_ACTION,
                    onClick = {
                        onBeforeAction()
                        onSaveAllClick()
                    }
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
    isActive: Boolean = false,
    enabled: Boolean = true,
    /** Icon colour; the toolbar actions share the green action tint. */
    tint: Color = Color.Unspecified
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(46.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                tint != Color.Unspecified -> tint
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    reservedBelow: Dp = 0.dp,
    alignRight: Boolean = true,
    bubbleIndex: Int = 0,
    /** Icon colour. Each action carries its own so the stack reads by function. */
    tint: Color = Color.Unspecified,
    editor: CodeEditor?,
    onClick: () -> Unit
) {
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
    ) {
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }
        val restOffset = restOffsetAbove(
            size,
            size,
            maxWidthPx,
            maxHeightPx,
            density,
            reservedBelow,
            alignRight
        )
        val sizePx = with(density) { size.toPx() }

        val draggedOffset = remember { mutableStateOf<IntOffset?>(null) }

        // First-entrance bubble: each floating button rises into place one after
        // another, scaled like a bubble surfacing, instead of the whole stack
        // popping in at once. One-shot: once it has played, it never plays again.
        var bubbleShown by rememberSaveable { mutableStateOf(false) }
        val bubbleProgress = remember { Animatable(if (bubbleShown) 1f else 0f) }
        LaunchedEffect(Unit) {
            if (!bubbleShown) {
                delay(bubbleIndex * 90L)
                bubbleProgress.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
                bubbleShown = true
            }
        }

        Surface(
            onClick = {
                hideAutoCompletion(editor)
                onClick()
            },
            shape = RoundedCornerShape(14.dp),
            color = if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            shadowElevation = 6.dp,
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .alpha(bubbleProgress.value.coerceIn(0f, 1f))
                .offset {
                    IntOffset(
                        draggedOffset.value?.x ?: restOffset.x,
                        (draggedOffset.value?.y ?: restOffset.y) +
                            ((1f - bubbleProgress.value) * 40.dp.toPx()).roundToInt()
                    )
                }
                .scale(
                    0.6f + 0.4f * bubbleProgress.value.coerceIn(0f, 1f)
                )
                .size(size)
                .pointerInput(maxWidthPx, maxHeightPx, sizePx, restOffset) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val current = draggedOffset.value ?: restOffset
                            val newX = (current.x + dragAmount.x)
                                .coerceIn(0f, (maxWidthPx - sizePx).coerceAtLeast(0f))
                            val newY = (current.y + dragAmount.y)
                                .coerceIn(0f, (maxHeightPx - sizePx).coerceAtLeast(0f))
                            draggedOffset.value = IntOffset(newX.roundToInt(), newY.roundToInt())
                        }
                    )
                }
                .testTag(testTag)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (tint != Color.Unspecified) {
                        tint
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
private fun EditorTabStrip(
    documents: List<OpenDocument>,
    activePath: String?,
    onSelectTab: (java.io.File) -> Unit,
    onCloseTab: (java.io.File) -> Unit,
    onNewTabClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    onOpenPawnFileClick: () -> Unit,
    onCreateFileClick: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
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
                                _Icons.IncludeRect
                            } else {
                                _Icons.FileRect
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
                                text = "~",
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
                    imageVector = Icons.Filled.Explore,
                    contentDescription = "Toggle workspace panel",
                    modifier = Modifier.size(18.dp)
                )
            }
            Box {
                IconButton(
                    onClick = { showAddMenu = true },
                    modifier = Modifier
                        .size(TAB_STRIP_HEIGHT)
                        .testTag("editor_tab_open_file")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add file",
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Open workspace folder") },
                        onClick = {
                            showAddMenu = false
                            onOpenFileClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open a file") },
                        onClick = {
                            showAddMenu = false
                            onOpenPawnFileClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("New Pawn file") },
                        onClick = {
                            showAddMenu = false
                            onCreateFileClick()
                        }
                    )
                }
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
    val lineText = target.text.getLine(line).toString()

    // Match the leading whitespace of the current line and add one level on top,
    // so consecutive [TAB] presses walk in cleanly instead of drifting right by
    // a fixed amount every time.
    val leading = lineText.take(column).indexOfFirst { !it.isWhitespace() }
        .let { if (it < 0) column else it }
    val baseIndent = lineText.take(leading)
    target.text.insert(line, column, baseIndent + "    ")
}

/**
 * Formats the caret readout of the status bar as `line-column`, both one-based.
 *
 * [line] and [column] are the raw, 0-based values a `GtkTextIter`-style caret
 * reports, so the one-based conversion happens here and nowhere else. Doing the
 * `+1` both where the state is captured and again here is what made the bar show
 * "5-7" for a caret on line 4, column 7.
 */
internal fun cursorPositionLabel(line: Int, column: Int): String =
    "${line + 1}${LINE_COLUMN_SEPARATOR}${column + 1}"

internal fun resolveJumpColumn(input: String?, lineLength: Int): Int =
    input
        ?.toIntOrNull()
        ?.takeIf { it > 0 && it - 1 <= lineLength }
        ?.minus(1)
        ?: 0

/**
 * Bracket characters the "Jump to Matching Bracket" action recognises.
 * Openers jump forward to their closer, closers jump backward to their opener.
 */
private val BRACKET_PAIRS = mapOf(
    '(' to ')',
    '{' to '}',
    '[' to ']'
)

/**
 * Finds the bracket that matches the one at the caret and returns its
 * `line to column` position (both 0-based), or `null` when the caret is not
 * on a bracket or the bracket is unmatched.
 */
internal fun findMatchingBracketPosition(editor: CodeEditor): Pair<Int, Int>? {
    val cursor = editor.cursor
    val line = cursor.leftLine
    val column = cursor.leftColumn
    if (line < 0 || line >= editor.text.lineCount) return null

    val lineText = editor.text.getLine(line).toString()
    if (column >= lineText.length) return null
    val start = lineText[column]

    val opening = BRACKET_PAIRS[start]
    val closing = BRACKET_PAIRS.entries.firstOrNull { it.value == start }?.key
    if (opening == null && closing == null) return null

    if (opening != null) {
        // Forward scan: count nesting depth of the same kind of pair.
        var depth = 0
        for (l in line until editor.text.lineCount) {
            val text = editor.text.getLine(l).toString()
            val from = if (l == line) column else 0
            for (c in from until text.length) {
                when (text[c]) {
                    start -> depth++
                    opening -> {
                        depth--
                        if (depth == 0) return l to c
                    }
                }
            }
        }
        return null
    }

    // Backward scan for a closer.
    var depth = 0
    for (l in line downTo 0) {
        val text = editor.text.getLine(l).toString()
        val from = if (l == line) column else text.length - 1
        for (c in from downTo 0) {
            when (text[c]) {
                start -> depth++
                closing -> {
                    depth--
                    if (depth == 0) return l to c
                }
            }
        }
    }
    return null
}

/** Accepts the visible completion, or indents when no suggestion is open. */
private fun applyTabAction(editor: CodeEditor?) {
    val target = editor ?: return
    val completion = target.getComponent(EditorAutoCompletion::class.java)
    if (completion.isShowing()) {
        if (completion.getCurrentPosition() == -1) completion.moveDown()
        if (completion.select()) return
    }

    completion.hide()
    insertIndentAtCursor(target)
}

private fun hideAutoCompletion(editor: CodeEditor?) {
    editor?.getComponent(EditorAutoCompletion::class.java)?.hide()
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
    /*
     * The bar shows the digits by default and keeps the symbol row hidden until it
     * is asked for. A permanent symbol row plus a digits row took two stack lines out
     * of the editor viewport for keys most edits never touch.
     */
    val symbols = listOf(
        "{", "}", "(", ")", "[", "]", ";", ":", ",", "\"", "'", "#", "=", ">", "<", "!", "&", "|"
    )
    val digits = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    var symbolsVisible by rememberSaveable { mutableStateOf(false) }
    val symbolScroll = rememberScrollState()

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            if (symbolsVisible) {
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
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = if (symbolsVisible) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                /*
                 * The toggle sits ahead of the digits, at the same weight, so it never
                 * shifts the row when the symbol line appears or disappears.
                 */
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (symbolsVisible) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { symbolsVisible = !symbolsVisible }
                        .testTag("editor_symbols_toggle")
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (symbolsVisible) {
                                Icons.Filled.KeyboardArrowDown
                            } else {
                                Icons.Filled.KeyboardArrowUp
                            },
                            contentDescription = if (symbolsVisible) {
                                "Hide the extra keys"
                            } else {
                                "Show the extra keys"
                            },
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(vertical = 6.dp)
                                .size(14.dp)
                        )
                    }
                }

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
        val n_draggedOffset = remember { mutableStateOf<IntOffset?>(null) }
        val n_restOffset = IntOffset(
            x = (n_maxWidth - n_diameterPx - with(n_density) { 16.dp.toPx() })
                .coerceAtLeast(0f)
                .roundToInt(),
            y = (n_maxHeight - n_diameterPx - with(n_density) { 16.dp.toPx() })
                .coerceAtLeast(0f)
                .roundToInt()
        )
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
                .offset { n_draggedOffset.value ?: n_restOffset }
                .size(n_diameter)
                .pointerInput(enabled, n_maxWidth, n_maxHeight, n_diameterPx, n_restOffset) {
                    detectDragGestures(
                        onDragEnd = { },
                        onDragCancel = { },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val n_current = n_draggedOffset.value ?: n_restOffset
                            val n_newX = (n_current.x + dragAmount.x)
                                .coerceIn(0f, (n_maxWidth - n_diameterPx).coerceAtLeast(0f))
                            val n_newY = (n_current.y + dragAmount.y)
                                .coerceIn(0f, (n_maxHeight - n_diameterPx).coerceAtLeast(0f))
                            n_draggedOffset.value = IntOffset(n_newX.roundToInt(), n_newY.roundToInt())
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
                    imageVector = _Icons.Hotspot,
                    contentDescription = "Compile active file",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

/**
 * Scroll sidebar of the Sora code canvas.
 *
 * The editor widget owns the scrolling and exposes it as a line offset, so this strip
 * reads two numbers from the live editor instead of a Compose scroll state:
 *
 *  - [CodeEditor.firstVisibleLine] over the total line count gives the position, which
 *    is also how "how far into the file am I" is answered while typing;
 *  - the rows that fit the current canvas height over the remaining lines gives the
 *    thumb length, so a 4000-line script shows a visibly shorter thumb than a 40-line
 *    include, and the strip regrows when the keyboard closes.
 *
 * Dragging or tapping the track jumps the editor, which is the one interaction a bare
 * sidebar should offer on a canvas that is itself the scroll surface.
 */
@Composable
private fun EditorScrollSidebar(
    editor: CodeEditor?,
    activeDocument: OpenDocument?,
    singleFileContent: String?,
    onBeforeSeek: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (editor == null) return
    // Snapshot states only exist in a composition, so the bindings are hoisted here and
    // the strip itself stays a plain, testable function of numbers.
    var firstLine by remember(editor) { mutableIntStateOf(0) }
    var rowsOnScreen by remember(editor) { mutableFloatStateOf(1f) }
    // The buffer under the canvas: its text gives the total line count directly and is
    // already a snapshot state, so a file switch is picked up on the next frame.
    val shownText = activeDocument?.content ?: singleFileContent.orEmpty()
    val totalLines = remember(shownText) { shownText.count { it == '\n' } + 1 }

    // `firstVisibleLine` is plain widget state the snapshot system cannot observe, so it
    // is polled while the strip is on screen. One integer read, four times a second.
    LaunchedEffect(editor) {
        firstLine = editor.firstVisibleLine
        while (true) {
            val current = runCatching { editor.firstVisibleLine }.getOrDefault(0)
            if (current != firstLine) firstLine = current
            delay(SCROLL_SIDEBAR_POLL_MS)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        // How many rows the canvas height holds right now; written back below so the
        // strip follows the keyboard opening and closing.
        // `rowHeight` is an Int on the widget, so the division is done in Float explicitly:
        // mixing the two here is a compile error, not an implicit widening.
        val rowsAfterLayout: Float = with(density) {
            val rowHeightPx = editor.rowHeight.coerceAtLeast(1).toFloat()
            (maxHeight.toPx() / rowHeightPx).coerceAtLeast(1f)
        }
        LaunchedEffect(rowsAfterLayout) { rowsOnScreen = rowsAfterLayout }
        if (totalLines <= 0) return@BoxWithConstraints

        EditorSeekStrip(
            progress = (firstLine.toFloat() / (totalLines - 1).coerceAtLeast(1))
                .coerceIn(0f, 1f),
            visibleFraction = (rowsOnScreen / totalLines).coerceIn(0f, 1f),
            totalLines = totalLines,
            firstVisibleLine = firstLine,
            lastVisibleLine = (firstLine + rowsOnScreen.toInt()).coerceAtMost(totalLines - 1),
            onSeek = { fraction ->
                onBeforeSeek()
                val target = (fraction * (totalLines - 1)).roundToInt()
                editor.jumpToLine(target.coerceIn(0, totalLines - 1))
            }
        )
    }
}

/** Track + thumb of the editor sidebar; measured against the caller's height. */
@Composable
private fun EditorSeekStrip(
    progress: Float,
    visibleFraction: Float,
    totalLines: Int,
    firstVisibleLine: Int,
    lastVisibleLine: Int,
    onSeek: (Float) -> Unit
) {
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .width(16.dp)
            .fillMaxHeight()
    ) {
        val stripHeightPx = with(density) { (maxHeight - 8.dp).toPx() }.coerceAtLeast(1f)
        val minThumbPx = with(density) { 28.dp.toPx() }
        val thumbHeightPx = (stripHeightPx * visibleFraction)
            .coerceIn(minThumbPx.coerceAtMost(stripHeightPx), stripHeightPx)
        val travelPx = (stripHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetPx = travelPx * progress
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // First and last buffered line on screen: the sidebar's thumb already
            // tells the length, these two numbers name it.
            Text(
                text = (firstVisibleLine + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = colors.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(totalLines, thumbHeightPx) {
                        detectTapGestures { position ->
                            onSeek(((position.y - thumbHeightPx / 2f) / travelPx).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(totalLines, thumbHeightPx) {
                        // Jump to the finger, then follow it: the editor reports the new
                        // offset a frame later, so deltas from a stored start are stable.
                        var dragFraction = progress
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragFraction = ((offset.y - thumbHeightPx / 2f) / travelPx)
                                    .coerceIn(0f, 1f)
                                onSeek(dragFraction)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragFraction = (dragFraction + dragAmount.y / travelPx)
                                    .coerceIn(0f, 1f)
                                onSeek(dragFraction)
                            }
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(colors.onSurface.copy(alpha = 0.08f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                        .width(4.dp)
                        .height(thumbHeightDp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.primary.copy(alpha = 0.75f))
                )
            }
            Text(
                text = (lastVisibleLine + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = colors.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1
            )
        }
    }
}

/**
 * Poll interval of the editor scroll sidebar.
 *
 * `CodeEditor.firstVisibleLine` is plain widget state, invisible to snapshot, so the
 * strip samples it instead of being pushed to. This only runs while the strip is on
 * screen, and one integer read four times a second is not measurable next to a repaint.
 */
private const val SCROLL_SIDEBAR_POLL_MS = 200L
