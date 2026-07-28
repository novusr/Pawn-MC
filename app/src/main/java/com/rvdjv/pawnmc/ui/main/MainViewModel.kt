package com.rvdjv.pawnmc.ui.main

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.compiler.PawnCompiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(private val config: CompilerConfig) : ViewModel() {

    var selectedFilePath by mutableStateOf<String?>(null)
        private set

    var selectionError by mutableStateOf<String?>(null)
        private set

    var isCompiling by mutableStateOf(false)
        private set

    var outputText by mutableStateOf("Ready to compile...\n")
        private set

    var lastExitCode by mutableStateOf<Int?>(null)
        private set

    fun loadLastSelectedFile() {
        val lastPath = config.lastSelectedFilePath
        if (lastPath != null && File(lastPath).exists()) {
            selectedFilePath = lastPath
            outputText = "Loaded file: $lastPath\n"
        }
    }

    fun handleInitialUri(uri: Uri?) {
        uri?.let { u ->
            val path = u.path
            if (path != null) {
                val validExtensions = setOf("pawn", "pwn", "p", "inc")
                if (File(path).extension.lowercase() in validExtensions) {
                    selectedFilePath = path
                    config.lastSelectedFilePath = path
                    selectionError = null
                    lastExitCode = null
                    outputText = "Opened file: $path\n"
                } else {
                    selectionError = "Invalid file type! (only: .pawn .pwn .p)"
                }
            }
        }
    }

    fun selectFile(path: String) {
        val validExtensions = setOf("pawn", "pwn", "p", "inc")
        if (File(path).extension.lowercase() in validExtensions) {
            selectedFilePath = path
            config.lastSelectedFilePath = path
            selectionError = null
            lastExitCode = null
        } else {
            selectionError = "Invalid file type! (only: .pawn .pwn .p)"
        }
    }

    fun compileFile(path: String, isStoragePermissionGranted: Boolean, onPermissionRequired: () -> Unit) {
        if (!isStoragePermissionGranted) {
            onPermissionRequired()
            return
        }

        isCompiling = true
        outputText = ""
        viewModelScope.launch {
            val options = config.buildOptions()
            val version = config.compilerVersion

            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(path, options, version)
            }
            val duration = System.currentTimeMillis() - startTime

            outputText += result.second
            val timeString = if (duration >= 1000) {
                String.format("%.2f seconds", duration / 1000.0)
            } else {
                "$duration ms"
            }
            outputText += "\nCompilation time: $timeString\n"

            lastExitCode = result.first
            isCompiling = false
        }
    }

    fun getCompilerConfig(): CompilerConfig = config
}

class MainViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(CompilerConfig.getInstance(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
