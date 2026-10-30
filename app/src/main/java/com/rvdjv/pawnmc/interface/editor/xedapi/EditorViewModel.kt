package com.rvdjv.pawnmc.`interface`.editor.xedapi

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.`interface`.editor.workspace.WorkspaceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(
    initialFilePath: String,
    context: Context? = null
) : ViewModel() {

    /** Single file opened from the app, outside any workspace. */
    var filePath by mutableStateOf(initialFilePath)
        private set

    val file: File get() = File(filePath)
    val fileName: String get() = file.name

    /** Path currently held in [fileContent], used to skip redundant reloads. */
    private var loadedFilePath: String? = null
    private var latestEditorContent: String = ""

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
        if (filePath.isNotBlank() && !workspace.isWorkspaceOpen) loadFile()
        else isLoading = false
    }

    /** Points the editor at [path], the file the main screen has selected. */
    fun attachFile(path: String) {
        if (path.isBlank() || (path == filePath && loadedFilePath == path)) return
        filePath = path
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
        // Edits made while the read is in flight are newer than the file.
        val contentAtLoadStart = latestEditorContent
        viewModelScope.launch {
            try {
                if (!file.exists()) {
                    loadError = "File not found: $n_path"
                    return@launch
                }
                val content = withContext(Dispatchers.IO) {
                    file.readText()
                }
                val editedDuringLoad = latestEditorContent != null && latestEditorContent != contentAtLoadStart
                if (!editedDuringLoad) {
                    fileContent = content
                    lastSavedContent = content
                    latestEditorContent = content
                    hasUnsavedChanges = false
                }
            } catch (e: Exception) {
                loadError = "Failed to open file: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                isLoading = false
            }
        }
    }

    fun onContentChanged(newContent: String) {
        latestEditorContent = newContent
        val original = lastSavedContent ?: ""
        hasUnsavedChanges = (newContent != original)
    }

    fun saveFile(contentToSave: String, onSaved: (Boolean) -> Unit = {}) {
        if (isSaving) {
            statusMessage = "Save already in progress"
            return
        }
        isSaving = true
        statusMessage = "Saving..."
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    file.writeText(contentToSave)
                }
                lastSavedContent = contentToSave
                hasUnsavedChanges = latestEditorContent != contentToSave
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
            hasUnsavedChanges = latestEditorContent != currentContent
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
            val config = CompilerConfig.getInstanceOrNull() ?: CompilerConfig.getInstance(appContext)
            val path = config.n_last_selected_file_path.orEmpty()
            return modelClass.cast(EditorViewModel(path, appContext))
                ?: throw IllegalArgumentException("Unknown ViewModel class")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
