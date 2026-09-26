package com.rvdjv.pawnmc.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class XedEditorViewModel(
    val filePath: String
) : ViewModel() {

    val file = File(filePath)
    val fileName: String = file.name

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
        loadFile()
    }

    fun loadFile() {
        isLoading = true
        loadError = null
        viewModelScope.launch {
            try {
                if (!file.exists()) {
                    loadError = "File not found: $filePath"
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
