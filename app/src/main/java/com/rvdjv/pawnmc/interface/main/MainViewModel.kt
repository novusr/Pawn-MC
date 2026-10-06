package com.rvdjv.pawnmc.`interface`.main

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rvdjv.pawnmc.data.compiler.CaseConversion
import com.rvdjv.pawnmc.data.compiler.Compiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(
    private val config: CompilerConfig,
    private val appDirectory: File = File("."),
    private val context: Context? = null
) : ViewModel() {

    companion object {
        const val TEMPORARY_FILE_NAME = "unit.pwn"
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
            /**
             * <library>console</library>
             * <summary>Prints a string to the server console (not in-game chat) and logs (server_log.txt).</summary>
             * <param name="string">The string to print</param>
             * <seealso name="printf"/>
             */
            native print(const string[]);

            main() {
                print("Hello, World!");
            }

            public OnGameModeInit() {
                print("OnGameModeInit!");
                return 1;
            }

            public OnGameModeExit() {
                print("OnGameModeExit!");
                return 1;
            }

            public OnPlayerConnect(playerid) {
                print("OnPlayerConnect!");
                return 1;
            }

            public OnPlayerDisconnect(playerid, reason) {
                print("OnPlayerDisconnect!");
                return 1;
            }

        """.trimIndent()

        fun createTemporaryPawnFile(baseDir: File): File {
            val directory = File(baseDir, "TMP")
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

    var outputText by mutableStateOf("Ah shit, here we go again.\n")
        private set

    var hasCompilerOutput by mutableStateOf(false)
        private set

    var lastExitCode by mutableStateOf<Int?>(null)
        private set

    private var detectedCompilerVersionCache: CompilerConfig.CompilerVersion? = null
    private var detectedCompilerSourcePath: String? = null

    /**
     * Include folders this process already created, so the auto-detection launch does not
     * repeat an `exists()` probe on every scan.
     *
     * Deliberately scoped to the view model rather than to the process: a folder can
     * disappear (unmounted SD card, deleted project) while the app is alive, and the probe
     * has to be allowed to recreate it.
     */
    private val validIncludePathCache = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
        val isTemporaryStarter = lastPath?.let(::File)?.let { file ->
            file.name == TEMPORARY_FILE_NAME && file.parentFile?.name == "TMP"
        } == true
        if (!isTemporaryStarter && lastPath != null && File(lastPath).exists()) {
            selectFile(lastPath, isStartupLoad = true)
        } else {
            selectedFilePath = null
            config.n_last_selected_file_path = null
            temporaryFileNotice = null
            // Nothing to restore means the startup path still has to settle the compiler
            // mode, which is filesystem work and belongs off the main thread.
            warmUpCompilerDetection(null)
        }
    }

    fun handleInitialUri(uri: Uri?) {
        uri?.let { u ->
            val appContext = context ?: return@let
            val path = when (u.scheme) {
                "content" -> {
                    val contentResolver = appContext.contentResolver
                    val copied = runCatching {
                        val extension = u.lastPathSegment?.substringAfterLast('.', "pwn") ?: "pwn"
                        val fileName = "pawnmc-open-${System.nanoTime()}.$extension"
                        val target = File(appContext.cacheDir, fileName)
                        contentResolver.openInputStream(u)?.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target
                    }.getOrNull()
                    copied?.absolutePath
                }
                else -> u.path
            }

            if (path != null) {
                val validExtensions = setOf("pawn", "pwn", "p", "inc")
                if (File(path).extension.lowercase() in validExtensions) {
                    selectFile(path, openedFromIntent = true)
                } else {
                    selectionError = "Invalid file type! (only: .pawn .pwn .p .inc)"
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
                Compiler.convertFolderToLowerCase(
                    selectedFilePath = path,
                    rememberedBackupDir = config.n_case_insensitive_backup_dir
                )
            }

            when (n_result.status) {
                CaseConversion.ConversionStatus.CONVERTED -> {
                    config.n_case_insensitive_backup_dir = n_result.backupDir.absolutePath
                    filesystemNotice = IGNORE_CASE_DONE_NOTICE.format(n_result.backupDir.name)
                    outputText +=
                        "Ignore case: renamed ${n_result.renamedEntries} entries, " +
                            "rewrote includes in ${n_result.rewrittenIncludes} file(s), " +
                            "backup at ${n_result.backupDir.absolutePath}\n"
                }
                CaseConversion.ConversionStatus.ALREADY_CONVERTED -> {
                    config.n_case_insensitive_backup_dir = n_result.backupDir.absolutePath
                    filesystemNotice = IGNORE_CASE_SKIPPED_NOTICE.format(n_result.backupDir.name)
                    outputText += "Ignore case: skipped, ${n_result.backupDir.name} already exists\n"
                }
                CaseConversion.ConversionStatus.NOT_NEEDED -> {
                    filesystemNotice = null
                    outputText += "Ignore case: nothing to convert in ${n_result.workingDir.name}\n"
                }
                CaseConversion.ConversionStatus.FAILED -> {
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

    /**
     * Reconciles the include paths and the compiler version with [sourcePath].
     *
     * Both discovery and detection read the file system (and [Compiler.detectCompilerVersionForFile]
     * additionally hashes the compiler binary), so they run on the IO dispatcher and
     * only the results are published back to the main thread. The launch is deliberately
     * not joined: the caller is usually a UI event that must return immediately.
     */
    private fun applyCompilerAutoDetection(sourcePath: String?) {
        viewModelScope.launch {
            val n_detection = withContext(Dispatchers.IO) {
                val n_currentPaths = config.n_include_paths
                val n_autoIncludePaths = if (config.n_forced_include_path_auto) {
                    emptyList()
                } else {
                    // Discovery skips every folder the config already holds.
                    Compiler.discoverRelevantIncludePaths(sourcePath ?: "", n_currentPaths)
                }

                // Auto-detect only when automatic compiler selection is enabled, and only
                // when the metadata on disk has not already produced exactly this result;
                // detection hashes the whole compiler binary, so repeating it on every
                // launch is pure duplicated IO.
                val n_detected: CompilerConfig.CompilerVersion?
                if (config.n_forced_compiler_mode) {
                    n_detected = null
                } else {
                    val n_cached = cachedDetection(config, sourcePath)
                    n_detected = n_cached ?: Compiler.detectCompilerVersionForFile(sourcePath ?: "")
                }

                DetectionResult(n_currentPaths, n_autoIncludePaths, n_detected)
            }

            val n_currentPaths = n_detection.currentPaths
            val n_autoIncludePaths = n_detection.autoIncludePaths
            val n_detected = n_detection.detectedVersion
            if (n_autoIncludePaths.isNotEmpty()) {                val n_createdPaths = withContext(Dispatchers.IO) {
                    n_autoIncludePaths.filter { path ->
                        val directory = File(path)
                        !directory.exists() && directory.mkdirs()
                    }
                }
                // Merge against the value the store holds now, so a path added from
                // Settings while the scan was running is not written away.
                config.n_include_paths = config.n_include_paths + n_autoIncludePaths
                if (n_createdPaths.isNotEmpty()) {
                    validIncludePathCache.removeAll(n_createdPaths.toSet())
                }
            }

            if (config.n_forced_compiler_mode) return@launch

            if (n_detected != null) {
                config.n_compiler_version = n_detected
                detectedCompilerVersionCache = n_detected
                detectedCompilerSourcePath = sourcePath
                return@launch
            }
            if (sourcePath.isNullOrBlank()) return@launch

            // Keep the compiler default at 3.10.7 when the EXE is absent or the metadata
            // does not match any known version.
            config.n_compiler_version = CompilerConfig.CompilerVersion.V3107
            detectedCompilerVersionCache = CompilerConfig.CompilerVersion.V3107
            detectedCompilerSourcePath = sourcePath
        }
    }

    /** Outcome of the IO-side half of [applyCompilerAutoDetection]. */
    private data class DetectionResult(
        val currentPaths: List<String>,
        val autoIncludePaths: List<String>,
        val detectedVersion: CompilerConfig.CompilerVersion?
    )

    /**
     * The version the on-disk metadata produced for [sourcePath], or `null` when it has
     * to be read again.
     *
     * The detection result is a pure function of the compiler metadata, and that metadata
     * is stored in [config] by the detection itself, so a second run for the same source
     * file in the same process cannot produce a different answer.
     */
    private fun cachedDetection(
        config: CompilerConfig,
        sourcePath: String?
    ): CompilerConfig.CompilerVersion? =
        detectedCompilerVersionCache.takeIf { it != null && detectedCompilerSourcePath == sourcePath }

    /**
     * Settles the compiler mode for a launch that has no source file yet.
     *
     * Kept separate from [applyCompilerAutoDetection] so the include-path discovery,
     * which needs a real source path, is skipped instead of being called with a blank one.
     */
    private fun warmUpCompilerDetection(sourcePath: String?) = applyCompilerAutoDetection(sourcePath)

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
        val detectedVersion = Compiler.detectCompilerVersionForFile(sourcePath)
        val version = Compiler.compilerVersionForMode(
            forcedMode = forcedCompilerMode,
            selectedVersion = config.n_compiler_version,
            detectedVersion = detectedVersion
        )
        if (!forcedCompilerMode) config.n_compiler_version = version

        // The conversion already happened when the file was browsed, so compiling
        // only has to make sure the folder is still consistent.
        if (config.n_ignore_case) {
            val n_needsConversion = Compiler.needsConversion(
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
            val selectedVersion = config.n_compiler_version
            val options = config.buildOptions(selectedVersion)

            val backupRefreshed = withContext(Dispatchers.IO) {
                !config.n_ignore_case || Compiler.refreshBackupForCompile(
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
                Compiler.compile(sourcePath, options, selectedVersion)
            }
            val duration = System.currentTimeMillis() - startTime

            val compilerOutput = if (config.n_explain_output) {
                Compiler.explainCompilerOutput(result.second, appDirectory, config.n_app_language)
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
            val config = CompilerConfig.getInstanceOrNull() ?: CompilerConfig.getInstance(context)
            // `Class.cast` is the checked form of `as T`: it throws here instead of
            // leaving an unchecked warning and a potential caller-side ClassCastException.
            return modelClass.cast(MainViewModel(config, context.filesDir, context))
                ?: throw IllegalArgumentException("Unknown ViewModel class")
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
