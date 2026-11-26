package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.user_interface.editor.workspace.WorkspaceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(
    initialFilePath: String,
    context: Context? = null,
    initialFileUri: Uri? = null,
    initialIsTemporaryFile: Boolean = initialFileUri == null &&
        context?.let { AppStorage.isInternalTemporaryPawnFile(it, initialFilePath) } == true
) : ViewModel() {

    private val appContext = context?.applicationContext
    private val config = appContext?.let {
        ContentConfig.getInstanceOrNull() ?: ContentConfig.getInstance(it)
    }

    /** Single file opened from the app, outside any workspace. */
    var filePath by mutableStateOf(initialFilePath)
        private set

    private var activeDocumentUri: Uri? = initialFileUri
    var isTemporaryFile by mutableStateOf(initialIsTemporaryFile)
        private set

    val file: File get() = File(filePath)
    val fileName: String get() = file.name

    /**
     * Name shown for the open file when it differs from the path on disk.
     *
     * A document opened from outside PawnMC ("Open with" / PocketSAMP File Manager)
     * is copied into the cache as `pawnmc-open-<nano>.<ext>` so the editor has a real
     * path to edit. Without this the tab, the title bar and the workspace list showed
     * that staging name instead of the file the user actually opened.
     */
    var displayName by mutableStateOf<String?>(null)
        private set

    /** Name to render for the single file: [displayName] when set, else the file name. */
    val visibleFileName: String get() = displayName ?: fileName

    /** Path currently held in [fileContent], used to skip redundant reloads. */
    private var loadedFilePath: String? = null

    //
    // Observation counters, read only through [showsStandaloneFile].
    //
    // Whether the open file belongs to a workspace is answered by walking every workspace
    // and its documents. That answer is read during composition at least four times per
    // `EditorScreen` pass, on *every* recomposition. Keeping it as a plain getter meant the
    // snapshot system never saw it: a workspace document being registered (or a workspace
    // opening) did not invalidate the screen, so the tab strip followed the click while
    // the canvas kept rendering the old file. Reading these counters inside the getter
    // subscribes the composition to the exact changes that can flip the answer.
    //
    /** Bumped whenever the list of open workspaces may have changed. */
    private var workspaceListRevision by mutableIntStateOf(0)
    /** Bumped whenever any workspace may have gained, dropped or activated a document. */
    private var workspaceDocumentsRevision by mutableIntStateOf(0)

    /** Re-marks the standalone question as recomposition-worthy. */
    fun invalidateWorkspaceObservation() {
        workspaceListRevision++
        workspaceDocumentsRevision++
    }

    private var latestEditorContent: String = ""
    private var loadGeneration = 0
    private var activeSaveJob: kotlinx.coroutines.Job? = null

    /** Workspace opened from the editor; empty until the user picks a folder. */
    val workspace = WorkspaceSession(context)

    var isLoading by mutableStateOf(true)
        private set

    var loadError by mutableStateOf<String?>(null)
        private set

    var fileContent by mutableStateOf<String?>(null)
        private set

    var lastSavedContent by mutableStateOf<String?>(null)
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf<String?>(null)
        private set

    init {
        // A restored session repoints the single file at its cache copy; the document's
        // own name is remembered next to the path so the tab keeps reading naturally.
        displayName = config?.n_last_selected_file_display_name
        // `workspace.isWorkspaceOpen` is already settled here: WorkspaceSession fills its
        // list synchronously in its own init and only hands the folder scans to the IO
        // dispatcher. Reading it up front keeps this constructor free of any deferred
        // work, which is what makes the view model constructible without a context.
        if (filePath.isNotBlank() && !workspace.isWorkspaceOpen) loadFile()
        else isLoading = false
    }

    /** Points the editor at [path], the file the main screen has selected. */
    fun attachFile(path: String, name: String? = null) {
        if (path.isBlank() || (path == filePath && loadedFilePath == path)) {
            // A pure name change (the same buffer shown under its real document name)
            // must not reload the file.
            if (path.isNotBlank() && path == filePath) displayName = name
            return
        }
        displayName = name
        if (path != filePath) {
            activeDocumentUri = null
            config?.n_last_selected_file_uri = null
        }
        filePath = path
        isTemporaryFile =
            activeDocumentUri == null &&
                appContext?.let { AppStorage.isInternalTemporaryPawnFile(it, path) } == true
        // Load unconditionally: when a workspace already holds this file, the editor
        // renders it from that document's buffer, and otherwise from this one. Loading
        // only "when no workspace is open" left the file empty whenever a workspace had
        // been restored in the background.
        if (!workspace.openWorkspaces.any { it.documentFor(file) != null }) loadFile()
    }

    /**
     * True when the open file belongs to no workspace - an outside document, or a single
     * file the main screen selected - so the editor renders it as a lone buffer.
     *
     * `isWorkspaceOpen` alone is not enough: an unrelated workspace restored in the
     * background used to mask the file just opened from another app, which showed up as
     * an empty editor even though the document content had been read.
     */
    val showsStandaloneFile: Boolean
        get() {
            // Snapshot reads first: they are what makes the composition re-evaluate the
            // answer when a workspace opens or a document is registered (see the counters
            // above). The plain scan below is not observed by Compose.
            if (workspaceListRevision < 0 || workspaceDocumentsRevision < 0) return false
            return filePath.isNotBlank() &&
                !workspace.openWorkspaces.any { it.documentFor(file) != null }
        }

    fun loadFile() {
        val n_path = filePath
        if (n_path.isBlank()) {
            isLoading = false
            return
        }
        loadedFilePath = n_path
        val generation = ++loadGeneration
        isLoading = true
        loadError = null
        // Edits made while the read is in flight are newer than the file.
        val contentAtLoadStart = latestEditorContent
        viewModelScope.launch {
            try {
                if (!file.exists() && activeDocumentUri == null) {
                    loadError = "File not found: $n_path"
                    return@launch
                }
                val content = withContext(Dispatchers.IO) {
                    activeDocumentUri?.let { uri ->
                        val context = appContext ?: error("File access is unavailable")
                        val input = context.contentResolver.openInputStream(uri)
                            ?: error("Unable to open the saved destination")
                        input.bufferedReader().use { it.readText() }.also { text ->
                            ensureParentDirectory(file)
                            file.writeText(text)
                        }
                    } ?: file.readText()
                }
                if (generation != loadGeneration || n_path != filePath) return@launch
                val editedDuringLoad = latestEditorContent != contentAtLoadStart
                if (!editedDuringLoad) {
                    fileContent = content
                    lastSavedContent = content
                    latestEditorContent = content
                    hasUnsavedChanges = false
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    loadError = "Failed to open file: ${e.localizedMessage ?: "Unknown error"}"
                }
            } finally {
                if (generation == loadGeneration) isLoading = false
            }
        }
    }

    fun onContentChanged(newContent: String) {
        latestEditorContent = newContent
        val original = lastSavedContent ?: ""
        hasUnsavedChanges = !isTemporaryFile && newContent != original
    }

    fun saveFile(contentToSave: String, onSaved: (Boolean) -> Unit = {}) {
        if (isTemporaryFile) {
            statusMessage = "The internal temporary file cannot be saved. Use Save As to create a Pawn file."
            onSaved(false)
            return
        }
        if (isSaving) {
            statusMessage = "Save already in progress"
            return
        }
        isSaving = true
        statusMessage = "Saving..."
        activeSaveJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    writeContent(contentToSave)
                }
                lastSavedContent = contentToSave
                hasUnsavedChanges = latestEditorContent != contentToSave
                isSaving = false
                activeSaveJob = null
                statusMessage = "Saved successfully"
                onSaved(true)
            } catch (e: Exception) {
                isSaving = false
                activeSaveJob = null
                statusMessage = "Save failed: ${e.localizedMessage}"
                onSaved(false)
            }
        }
    }

    suspend fun saveFileSync(currentContent: String): Boolean {
        if (isTemporaryFile) {
            statusMessage = "The internal temporary file cannot be saved. Use Save As to create a Pawn file."
            return false
        }
        activeSaveJob?.join()
        if (isSaving) return false
        isSaving = true
        return try {
            withContext(Dispatchers.IO) {
                writeContent(currentContent)
            }
            lastSavedContent = currentContent
            hasUnsavedChanges = latestEditorContent != currentContent
            statusMessage = "Saved successfully"
            isSaving = false
            true
        } catch (e: Exception) {
            statusMessage = "Save failed: ${e.localizedMessage}"
            isSaving = false
            false
        }
    }

    fun saveAs(
        uri: Uri,
        content: String,
        displayName: String,
        onSaved: (Boolean) -> Unit = {}
    ) {
        if (isSaving) {
            statusMessage = "Save already in progress"
            onSaved(false)
            return
        }
        val context = appContext
        if (context == null) {
            statusMessage = "Save As failed: file access is unavailable"
            onSaved(false)
            return
        }
        if (File(displayName).extension.lowercase() !in setOf("pawn", "pwn", "p", "inc")) {
            statusMessage = "Save As failed: choose a .pawn, .pwn, .p, or .inc filename"
            onSaved(false)
            return
        }

        isSaving = true
        statusMessage = "Saving..."
        viewModelScope.launch {
            try {
                val safeName = displayName
                    .substringAfterLast('/')
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "untitled.pwn" }
                val mirrorFile = File(
                    File(File(context.filesDir, "PawnMC"), "SaveAs")
                        .resolve(Integer.toHexString(uri.toString().hashCode())),
                    safeName
                )
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Unable to open the selected destination")
                    output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    ensureParentDirectory(mirrorFile)
                    mirrorFile.writeText(content)
                }

                activeDocumentUri = uri
                filePath = mirrorFile.absolutePath
                loadedFilePath = filePath
                isTemporaryFile = false
                fileContent = content
                latestEditorContent = content
                lastSavedContent = content
                hasUnsavedChanges = false
                config?.n_last_selected_file_path = filePath
                config?.n_last_selected_file_uri = uri.toString()
                statusMessage = "Saved successfully"
                isSaving = false
                onSaved(true)
            } catch (e: Exception) {
                isSaving = false
                statusMessage = "Save As failed: ${e.localizedMessage ?: "Unknown error"}"
                onSaved(false)
            }
        }
    }

    private fun writeContent(content: String) {
        activeDocumentUri?.let { uri ->
            val context = appContext ?: error("File access is unavailable")
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: error("Unable to open the saved destination")
            output.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }
        ensureParentDirectory(file)
        file.writeText(content)
    }

    private fun ensureParentDirectory(target: File) {
        val parent = target.parentFile ?: return
        if (!parent.isDirectory && !parent.mkdirs()) {
            error("Unable to create directory: ${parent.absolutePath}")
        }
    }

    fun clearStatusMessage() {
        statusMessage = null
    }
}

class EditorViewModelFactory(private val filePath: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            // `Class.cast` is the checked form of `as T`, so a mismatched model class
            // fails at the factory instead of leaving an unchecked warning behind.
            return modelClass.cast(EditorViewModel(filePath))
                ?: throw IllegalArgumentException("Unknown ViewModel class")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
/**
 * Builds the editor view model once for the whole activity.
 */
class EditorViewModelFactoryForActivity(context: Context) : ViewModelProvider.Factory {

    private val appContext: Context = context.applicationContext ?: context

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            val config = ContentConfig.getInstanceOrNull() ?: ContentConfig.getInstance(appContext)
            val path = config.n_last_selected_file_path.orEmpty()
            val uri = config.n_last_selected_file_uri?.let { Uri.parse(it) }
            return modelClass.cast(EditorViewModel(path, appContext, uri))
                ?: throw IllegalArgumentException("Unknown ViewModel class")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
