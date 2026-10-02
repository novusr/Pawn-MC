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

class MainViewModel(
    private val config: CompilerConfig,
    private val appDirectory: File = File(".")
) : ViewModel() {

    companion object {
        const val TEMPORARY_FILE_NAME = "main.pwn"
        const val TEMPORARY_FILE_NOTICE = "This is a temporary file because you have not selected your own Pawn file yet."
        const val IGNORE_CASE_BUSY_NOTICE =
            "Ignore case is enabled. Backing up this folder and converting every file name and #include reference to lowercase..."
        const val IGNORE_CASE_DONE_NOTICE =
            "Ignore case applied: a backup copy of the folder was created as \"%s\" and every other file name plus its #include references were lowercased."
        const val IGNORE_CASE_SKIPPED_NOTICE =
            "Ignore case already applied: the backup folder \"%s\" exists, so the conversion was skipped."
        const val IGNORE_CASE_FAILED_NOTICE =
            "Ignore case could not finish for this folder. The original files were left untouched."
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

    var n_app_language by mutableStateOf(config.n_app_language)
        private set

    var n_editor_background_color by mutableStateOf(config.n_editor_background_color)
        private set

    var selectedFilePath by mutableStateOf<String?>(null)
        private set

    var selectionError by mutableStateOf<String?>(null)
        private set

    /** Compile request queued by Xed and handled by the mounted main screen. */
    var pendingCompilePath by mutableStateOf<String?>(null)
        private set

    var compileRequestId by mutableStateOf(0)
        private set

    var temporaryFileNotice by mutableStateOf<String?>(null)
        private set

    /** Notice shown while the ignore-case conversion rewrites the folder. */
    var filesystemNotice by mutableStateOf<String?>(null)
        private set

    /** Compile stays disabled until the conversion has finished. */
    var isPreparingFilesystem by mutableStateOf(false)
        private set

    var isCompiling by mutableStateOf(false)
        private set

    var outputText by mutableStateOf("Ready to compile...\n")
        private set

    var hasCompilerOutput by mutableStateOf(false)
        private set

    var lastExitCode by mutableStateOf<Int?>(null)
        private set

    fun refreshTheme() {
        n_app_theme = config.n_app_theme
    }

    fun refreshLanguage() {
        n_app_language = config.n_app_language
    }

    /**
     * Re-reads the Xed editor background preference.
     *
     * Settings lives in its own activity, so the editor has to pick the value up
     * when the main activity resumes after the user changes it.
     */
    fun refreshEditorBackgroundColor() {
        n_editor_background_color = config.n_editor_background_color
    }

    fun loadLastSelectedFile() {
        val lastPath = config.n_last_selected_file_path
        if (lastPath != null && File(lastPath).exists()) {
            selectFile(lastPath, isStartupLoad = true)
        }
    }

    fun handleInitialUri(uri: Uri?) {
        uri?.let { u ->
            val path = u.path
            if (path != null) {
                val validExtensions = setOf("pawn", "pwn", "p", "inc")
                if (File(path).extension.lowercase() in validExtensions) {
                    selectFile(path, openedFromIntent = true)
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
        filesystemNotice = null
        selectionError = null
        lastExitCode = null
        outputText = "Temporary file created: ${file.absolutePath}\n"
        applyCompilerAutoDetection(file.absolutePath)
        return file.absolutePath
    }

    fun selectFile(
        path: String,
        isStartupLoad: Boolean = false,
        openedFromIntent: Boolean = false
    ) {
        val validExtensions = setOf("pawn", "pwn", "p", "inc")
        val n_source = File(path)
        if (n_source.extension.lowercase() !in validExtensions) {
            selectionError = "Invalid file type! (only: .pawn .pwn .p)"
            return
        }

        if (!config.n_ignore_case) {
            selectionError = null
            temporaryFileNotice = null
            filesystemNotice = null
            lastExitCode = null
            selectedFilePath = path
            config.n_last_selected_file_path = path
            outputText = if (openedFromIntent) "Opened file: $path\n" else "Opened file: $path\n"
            applyCompilerAutoDetection(path)
            return
        }

        // "Ignore case" backs the folder up and lowercases it in the background,
        // so the compile action stays disabled until the folder is consistent.
        selectionError = null
        temporaryFileNotice = null
        lastExitCode = null
        selectedFilePath = path
        config.n_last_selected_file_path = path
        if (!isStartupLoad) {
            outputText = "Selected file: $path\n"
        }
        isPreparingFilesystem = true
        filesystemNotice = IGNORE_CASE_BUSY_NOTICE

        viewModelScope.launch {
            val n_result = withContext(Dispatchers.IO) {
                PawnCompiler.convertFolderToLowerCase(
                    selectedFilePath = path,
                    rememberedBackupDir = config.n_case_insensitive_backup_dir
                )
            }

            when (n_result.status) {
                PawnCompiler.ConversionStatus.CONVERTED -> {
                    config.n_case_insensitive_backup_dir = n_result.backupDir.absolutePath
                    filesystemNotice = IGNORE_CASE_DONE_NOTICE.format(n_result.backupDir.name)
                    outputText +=
                        "Ignore case: renamed ${n_result.renamedEntries} entries, " +
                            "rewrote includes in ${n_result.rewrittenIncludes} file(s), " +
                            "backup at ${n_result.backupDir.absolutePath}\n"
                }
                PawnCompiler.ConversionStatus.ALREADY_CONVERTED -> {
                    config.n_case_insensitive_backup_dir = n_result.backupDir.absolutePath
                    filesystemNotice = IGNORE_CASE_SKIPPED_NOTICE.format(n_result.backupDir.name)
                    outputText += "Ignore case: skipped, ${n_result.backupDir.name} already exists\n"
                }
                PawnCompiler.ConversionStatus.NOT_NEEDED -> {
                    filesystemNotice = null
                    outputText += "Ignore case: nothing to convert in ${n_result.workingDir.name}\n"
                }
                PawnCompiler.ConversionStatus.FAILED -> {
                    config.clearCaseInsensitiveBackupDir()
                    filesystemNotice = IGNORE_CASE_FAILED_NOTICE
                    outputText += "Ignore case: conversion failed for ${n_result.workingDir.absolutePath}\n"
                }
            }

            selectedFilePath = n_result.sourceFile
            config.n_last_selected_file_path = n_result.sourceFile
            isPreparingFilesystem = false
            applyCompilerAutoDetection(n_result.sourceFile)
        }
    }

    private fun applyCompilerAutoDetection(sourcePath: String) {
        if (!config.n_forced_include_path_auto) {
            // Discovery skips every folder the config already holds.
            val currentPaths = config.n_include_paths
            val autoIncludePaths = PawnCompiler.discoverRelevantIncludePaths(sourcePath, currentPaths)
            if (autoIncludePaths.isNotEmpty()) {
                autoIncludePaths.forEach { includePath ->
                    val directory = File(includePath)
                    if (!directory.exists()) directory.mkdirs()
                }
                config.n_include_paths = currentPaths + autoIncludePaths
            }
        }

        if (config.n_forced_compiler_mode) return

        // Auto-detect only when automatic compiler selection is enabled.
        val detectedVersion = PawnCompiler.detectCompilerVersionForFile(sourcePath)
        if (detectedVersion != null) {
            config.n_compiler_version = detectedVersion
            outputText += "Detected compiler: ${detectedVersion.label}\n"
            return
        }

        // Keep the compiler default at 3.10.7 when the EXE is absent or the metadata does not match any known version.
        config.n_compiler_version = CompilerConfig.CompilerVersion.V3107
        outputText += "Compiler fallback: ${CompilerConfig.CompilerVersion.V3107.label}\n"
    }

    fun compileFile(path: String, isStoragePermissionGranted: Boolean, onPermissionRequired: () -> Unit) {
        // A path typed with a script extension (`.pawn`, `.pwn`, `.p`, `.inc`) is cleaned
        // up first. This never cancels the compilation: an existing source file keeps its
        // extension, only a path that does not exist gets the extension removed.
        val sourcePath = CompilerConfig.resolveCompilePath(path)
        if (isPreparingFilesystem) {
            outputText = "Wait for the ignore-case conversion to finish before compiling.\n"
            return
        }
        if (!isStoragePermissionGranted) {
            onPermissionRequired()
            return
        }

        val forcedCompilerMode = config.n_forced_compiler_mode
        val detectedVersion = PawnCompiler.detectCompilerVersionForFile(sourcePath)
        val version = PawnCompiler.compilerVersionForMode(
            forcedMode = forcedCompilerMode,
            selectedVersion = config.n_compiler_version,
            detectedVersion = detectedVersion
        )
        if (!forcedCompilerMode) config.n_compiler_version = version

        // The conversion already happened when the file was browsed, so compiling
        // only has to make sure the folder is still consistent.
        if (config.n_ignore_case) {
            val n_needsConversion = PawnCompiler.needsConversion(
                workingDir = File(sourcePath).parentFile ?: File("."),
                rememberedBackupDir = config.n_case_insensitive_backup_dir
            )
            if (n_needsConversion) {
                selectFile(sourcePath)
                return
            }
        }

        isCompiling = true
        outputText = ""
        viewModelScope.launch {
            val options = config.buildOptions()
            val selectedVersion = config.n_compiler_version

            val backupRefreshed = withContext(Dispatchers.IO) {
                !config.n_ignore_case || PawnCompiler.refreshBackupForCompile(
                    File(sourcePath).parentFile ?: File(".")
                )
            }
            if (!backupRefreshed) {
                outputText = "Failed to refresh the .backup folder; compilation was not started.\n"
                lastExitCode = -1
                isCompiling = false
                return@launch
            }

            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(sourcePath, options, selectedVersion)
            }
            val duration = System.currentTimeMillis() - startTime

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

    /**
     * Registers [path] as the file to compile and queues the compilation.
     *
     * The file is validated and registered with the compiler system exactly like a
     * file chosen through Browse File, so auto-detection and the ignore-case
     * handling stay identical, and the queued request is consumed by the main
     * screen when it becomes visible again.
     */
    fun requestCompileFromEditor(path: String) {
        hasCompilerOutput = true
        val n_validExtensions = setOf("pawn", "pwn", "p", "inc")
        if (File(path).extension.lowercase() !in n_validExtensions) {
            outputText = "Cannot compile '${File(path).name}': unsupported file type.\n"
            return
        }
        selectedFilePath = path
        config.n_last_selected_file_path = path
        applyCompilerAutoDetection(path)
        pendingCompilePath = path
        compileRequestId++
    }

    /** Returns the queued editor compile request once, or `null` when there is none. */
    fun consumePendingCompile(): String? {
        val n_path = pendingCompilePath
        pendingCompilePath = null
        return n_path
    }
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
