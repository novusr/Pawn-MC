package com.rvdjv.pawnmc.`interface`.editor.xedapi

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.`interface`.editor.Workspace.WorkspaceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(
    initialFilePath: String,
    context: Context? = null
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
    private var latestEditorContent: String = ""

    /**
     * Workspace opened from the editor. Empty until the user picks a folder, so
     * the single-file flow keeps working exactly as before.
     *
     * The context is handed over so the folders picked here survive a restart: the
     * view model writes them to the config and re-opens them on the next launch.
     */
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
        // A restored workspace owns the initial editor. Do not preload the remembered
        // main-screen file (often the temporary unit.pwn) behind an empty workspace.
        if (filePath.isNotBlank() && !workspace.isWorkspaceOpen) loadFile()
        else isLoading = false
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
        // Preserve the main-screen selection as a fallback if the workspace is closed,
        // but do not load it into or add it to an active workspace.
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
                latestEditorContent = content
                hasUnsavedChanges = false
                isLoading = false
            } catch (e: Exception) {
                loadError = "Failed to open file: ${e.localizedMessage ?: "Unknown error"}"
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
 * Builds the editor view model for the whole activity.
 *
 * The instance is created once and reused, so the workspaces opened in the
 * editor survive the editor being closed (for example after a compile) and are
 * still there the next time it is opened.
 */
class EditorViewModelFactoryForActivity(context: Context) : ViewModelProvider.Factory {

    /** Kept as a property so the reference is never lost to shadowing. */
    private val appContext: Context = context.applicationContext ?: context

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EditorViewModel::class.java)) {
            val config = CompilerConfig.getInstanceOrNull() ?: CompilerConfig.getInstance(appContext)
            val path = config.n_last_selected_file_path.orEmpty()
            // The context lets the view model persist the workspace folders, so
            // a folder picked in the editor is still open on the next launch.
            // `Class.cast` performs the check generics cannot, so no unchecked warning
            // is needed: a mismatched model class fails here rather than at the caller.
            return modelClass.cast(EditorViewModel(path, appContext))
                ?: throw IllegalArgumentException("Unknown ViewModel class")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
