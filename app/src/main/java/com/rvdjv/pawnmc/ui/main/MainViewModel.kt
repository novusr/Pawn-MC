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

data class IncludePathChoice(
    val options: List<String>,
    val selected: String? = null
)

class MainViewModel(
    private val config: CompilerConfig,
    private val appDirectory: File = File(".")
) : ViewModel() {

    companion object {
        const val TEMPORARY_FILE_NAME = "main.pwn"
        const val TEMPORARY_FILE_NOTICE = "This is a temporary file because you have not selected your own Pawn file yet."
        val TEMPORARY_FILE_CONTENT = """
            native printf(const format[], {Float,_}:...);
            main() {
                printf("Hello, World!");
            }
        """.trimIndent()

        fun createTemporaryPawnFile(baseDir: File): File {
            val directory = File(baseDir, "temporary")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val tempFile = File(directory, TEMPORARY_FILE_NAME)
            if (!tempFile.exists()) {
                tempFile.writeText(TEMPORARY_FILE_CONTENT)
            }
            return tempFile
        }
    }

    var n_app_theme by mutableStateOf(config.n_app_theme)
        private set

    var selectedFilePath by mutableStateOf<String?>(null)
        private set

    var selectionError by mutableStateOf<String?>(null)
        private set

    var temporaryFileNotice by mutableStateOf<String?>(null)
        private set

    var isCompiling by mutableStateOf(false)
        private set

    var outputText by mutableStateOf("Ready to compile...\n")
        private set

    var lastExitCode by mutableStateOf<Int?>(null)
        private set

    var pendingIncludeChoice by mutableStateOf<IncludePathChoice?>(null)

    fun refreshTheme() {
        n_app_theme = config.n_app_theme
    }

    fun loadLastSelectedFile() {
        val lastPath = config.n_last_selected_file_path
        if (lastPath != null && File(lastPath).exists()) {
            val resolvedPath = if (config.n_ignore_case) {
                PawnCompiler.prepareCaseInsensitiveWorkspace(lastPath)?.sourceFile ?: lastPath
            } else lastPath
            selectedFilePath = resolvedPath
            config.n_last_selected_file_path = resolvedPath
            outputText = "Loaded file: $resolvedPath\n"
        }
    }

    fun handleInitialUri(uri: Uri?) {
        uri?.let { u ->
            val path = u.path
            if (path != null) {
                val validExtensions = setOf("pawn", "pwn", "p", "inc")
                if (File(path).extension.lowercase() in validExtensions) {
                    val resolvedPath = if (config.n_ignore_case) {
                        PawnCompiler.prepareCaseInsensitiveWorkspace(path)?.sourceFile ?: path
                    } else path
                    selectedFilePath = resolvedPath
                    config.n_last_selected_file_path = resolvedPath
                    selectionError = null
                    temporaryFileNotice = null
                    lastExitCode = null
                    outputText = "Opened file: $resolvedPath\n"
                    applyCompilerAutoDetection(resolvedPath)
                } else {
                    selectionError = "Invalid file type! (only: .pawn .pwn .p)"
                }
            }
        }
    }

    fun ensureTemporaryFileSelected(): String {
        val file = createTemporaryPawnFile(appDirectory)
        selectedFilePath = file.absolutePath
        config.n_last_selected_file_path = file.absolutePath
        temporaryFileNotice = TEMPORARY_FILE_NOTICE
        selectionError = null
        lastExitCode = null
        outputText = "Temporary file created: ${file.absolutePath}\n"
        applyCompilerAutoDetection(file.absolutePath)
        return file.absolutePath
    }

    fun selectFile(path: String) {
        val validExtensions = setOf("pawn", "pwn", "p", "inc")
        if (File(path).extension.lowercase() in validExtensions) {
            val resolvedPath = if (config.n_ignore_case) {
                PawnCompiler.prepareCaseInsensitiveWorkspace(path)?.sourceFile ?: path
            } else path
            selectedFilePath = resolvedPath
            config.n_last_selected_file_path = resolvedPath
            selectionError = null
            temporaryFileNotice = null
            lastExitCode = null
            applyCompilerAutoDetection(resolvedPath)
        } else {
            selectionError = "Invalid file type! (only: .pawn .pwn .p)"
        }
    }

    fun resolvePendingIncludeChoice(sourcePath: String) {
        val relevantPaths = PawnCompiler.discoverRelevantIncludePaths(sourcePath)
            .filter { it.contains("pawno", ignoreCase = true) || it.contains("qawno", ignoreCase = true) }
            .distinct()

        val hasExistingManualChoice = config.n_include_paths.any {
            it.contains("pawno", ignoreCase = true) || it.contains("qawno", ignoreCase = true)
        }

        if (relevantPaths.size <= 1 || hasExistingManualChoice) {
            if (relevantPaths.isNotEmpty()) {
                val mergedPaths = config.n_include_paths.toMutableList()
                relevantPaths.forEach { path ->
                    val normalizedPath = CompilerConfig.normalizeIncludePath(path)
                    if (normalizedPath !in mergedPaths) mergedPaths.add(normalizedPath)
                }
                config.n_include_paths = mergedPaths
            }
            pendingIncludeChoice = null
            return
        }

        pendingIncludeChoice = IncludePathChoice(relevantPaths)
    }

    fun confirmIncludeChoice(path: String) {
        val normalized = CompilerConfig.normalizeIncludePath(path)
        val mergedPaths = config.n_include_paths.toMutableList()
        if (normalized !in mergedPaths) mergedPaths.add(normalized)
        config.n_include_paths = mergedPaths
        pendingIncludeChoice = null
    }

    private fun applyCompilerAutoDetection(sourcePath: String) {
        // Auto-detect the compiler only when the nearby pawncc.exe metadata matches a supported version.
        val detectedVersion = PawnCompiler.detectCompilerVersionForFile(sourcePath)
        val autoIncludePaths = PawnCompiler.discoverRelevantIncludePaths(sourcePath)
        val mergedPaths = config.n_include_paths.toMutableList()
        autoIncludePaths.forEach { path ->
            val normalizedPath = CompilerConfig.normalizeIncludePath(path)
            if (normalizedPath !in mergedPaths) mergedPaths.add(normalizedPath)
        }
        config.n_include_paths = mergedPaths
        resolvePendingIncludeChoice(sourcePath)

        if (detectedVersion != null) {
            config.n_compiler_version = detectedVersion
            outputText = "Detected compiler: ${detectedVersion.label}\n"
            return
        }

        // Keep the compiler default at 3.10.7 when the EXE is absent or the metadata does not match any known version.
        config.n_compiler_version = CompilerConfig.CompilerVersion.V3107
        outputText += "Compiler fallback: ${CompilerConfig.CompilerVersion.V3107.label}\n"
    }

    fun compileFile(path: String, isStoragePermissionGranted: Boolean, onPermissionRequired: () -> Unit) {
        if (!isStoragePermissionGranted) {
            onPermissionRequired()
            return
        }

        val detectedVersion = PawnCompiler.detectCompilerVersionForFile(path)
        val version = detectedVersion ?: CompilerConfig.CompilerVersion.V3107
        config.n_compiler_version = version

        val preparedWorkspace = if (config.n_ignore_case) {
            PawnCompiler.prepareCaseInsensitiveWorkspace(path)
        } else null
        val compilePath = preparedWorkspace?.sourceFile ?: path
        if (preparedWorkspace != null) {
            selectedFilePath = compilePath
            config.n_last_selected_file_path = compilePath
        }

        isCompiling = true
        outputText = ""
        viewModelScope.launch {
            val options = config.buildOptions()
            val selectedVersion = config.n_compiler_version

            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(compilePath, options, selectedVersion)
            }
            val duration = System.currentTimeMillis() - startTime

            if (preparedWorkspace != null) {
                PawnCompiler.finalizeCaseInsensitiveWorkspace(preparedWorkspace)
                val restoredPath = preparedWorkspace.originalDir.absolutePath + File.separator + File(compilePath).name
                selectedFilePath = restoredPath
                config.n_last_selected_file_path = restoredPath
            }

            val compilerOutput = if (config.n_explain_output) {
                PawnCompiler.explainCompilerOutput(result.second, appDirectory)
            } else {
                result.second
            }

            outputText += compilerOutput
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
            val config = CompilerConfig.getInstanceOrNull() ?: CompilerConfig.getInstance(context)
            return MainViewModel(config, context.filesDir) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
