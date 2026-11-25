package com.rvdjv.pawnmc.user_interface.editor.xedapi

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
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
        if (filePath.isNotBlank()) {
            // Deferred until the session finished restoring, not just one loop hop: a
            // restored workspace owns the editor, and loading the single-file buffer
            // underneath it left the file in no editor at all (an empty, tab-less
            // document). A short timeout keeps a slow restore from blocking the editor.
            viewModelScope.launch {
                var waited = 0
                while (workspace.isRestoring && waited < RESTORE_WAIT_MS) {
                    kotlinx.coroutines.delay(16)
                    waited += 16
                }
                if (workspace.isWorkspaceOpen) {
                    isLoading = false
                } else {
                    loadFile()
                }
            }
        } else {
            isLoading = false
        }
    }

    private companion object {
        /** How long the initial file load waits for the workspace restore. */
        const val RESTORE_WAIT_MS = 300
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
        if (!workspace.isWorkspaceOpen) loadFile()
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
