package com.rvdjv.pawnmc.ui.editor

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.config.CompilerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class XedEditorViewModel(
    initialFilePath: String
) : ViewModel() {

    /**
     * Single file opened from the app, i.e. the file that was active outside any
     * workspace. Mutable so the host can attach another file without recreating
     * the view model, which would drop the open workspaces.
     */
    var filePath by mutableStateOf(initialFilePath)
        private set

    val file: File get() = File(filePath)
    val fileName: String get() = file.name

    /** Path currently held in [fileContent], used to skip redundant reloads. */
    private var loadedFilePath: String? = null

    /**
     * Workspace opened from the editor. Empty until the user picks a folder, so
     * the single-file flow keeps working exactly as before.
     */
    val workspace = XedWorkspaceViewModel()

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
        if (filePath.isNotBlank()) loadFile()
    }

    /**
     * Points the editor at [path], which is the file the main screen has selected.
     *
     * The workspace list is deliberately left untouched: the workspaces and their
     * opened files have to survive closing and reopening the editor.
     */
    fun attachFile(path: String) {
        if (path.isBlank() || (path == filePath && loadedFilePath == path)) return
        filePath = path
        // Inside a workspace the editor shows workspace buffers, so reloading the
        // single-file content would only cost time.
        if (!workspace.isWorkspaceOpen) loadFile()
    }

    fun loadFile() {
        val n_path = filePath
        if (n_path.isBlank()) {
            isLoading = false
            return
        }
        loadedFilePath = n_path
        isLoading = true
        loadError = null
        viewModelScope.launch {
            try {
                if (!file.exists()) {
                    loadError = "File not found: $n_path"
                    isLoading = false
                    return@launch
                }
                val content = withContext(Dispatchers.IO) {
                    file.readText()
                }
                fileContent = content
                lastSavedContent = content
                hasUnsavedChanges = false
                isLoading = false
                // The file the user picked on the main screen becomes the first tab of
                // the Xed workspace, so the tab strip and the explorer are usable right
                // away instead of only after a folder is opened from the editor.
                if (!workspace.isWorkspaceOpen) {
                    workspace.registerExternalFile(file)
                }
            } catch (e: Exception) {
                loadError = "Failed to open file: ${e.localizedMessage ?: "Unknown error"}"
                isLoading = false
            }
        }
    }

    fun onContentChanged(newContent: String) {
        val original = lastSavedContent ?: ""
        hasUnsavedChanges = (newContent != original)
    }

    fun saveFile(currentContent: String, onSaved: (Boolean) -> Unit = {}) {
        if (isSaving) return
        isSaving = true
        statusMessage = "Saving..."
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.writeText(currentContent)
                }
                lastSavedContent = currentContent
                hasUnsavedChanges = false
                isSaving = false
                statusMessage = "Saved successfully"
                onSaved(true)
            } catch (e: Exception) {
                isSaving = false
                statusMessage = "Save failed: ${e.localizedMessage}"
                onSaved(false)
            }
        }
    }

    suspend fun saveFileSync(currentContent: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                file.writeText(currentContent)
            }
            lastSavedContent = currentContent
            hasUnsavedChanges = false
            statusMessage = "Saved successfully"
            true
        } catch (e: Exception) {
            statusMessage = "Save failed: ${e.localizedMessage}"
            false
        }
    }

    fun clearStatusMessage() {
        statusMessage = null
    }
}

class XedEditorViewModelFactory(private val filePath: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XedEditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return XedEditorViewModel(filePath) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

/**
 * Builds the editor view model for the whole activity.
 *
 * The instance is created once and reused, so the workspaces opened in the Xed
 * editor survive the editor being closed (for example after a compile) and are
 * still there the next time it is opened.
 */
class XedEditorViewModelFactoryForActivity(context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(XedEditorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            val path = CompilerConfig.getInstance(context).n_last_selected_file_path.orEmpty()
            return XedEditorViewModel(path) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
