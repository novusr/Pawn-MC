package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * kotlin wrapper
 *
 * Facade of the compiler layer: it only forwards the public API to the focused
 * helpers so every existing caller keeps working.
 *
 * - [Runner] loads the native library and runs the compile job.
 * - [Detection] finds a nearby `pawncc` binary and its include folder.
 * - [CaseConversion] implements the "Ignore case" filesystem conversion.
 * - [Explainer] annotates raw compiler output with hints.
 * - [Names] holds the shared names, extensions and flags.
 */
object Compiler {

    /**
     * Checks whether forced mode is enabled by the user.
     *
     * @return true if auto-detection and fallback switching are disabled
     */
    fun isForcedModeEnabled(): Boolean = Runner.isForcedModeEnabled()

    internal fun compilerVersionForMode(
        forcedMode: Boolean,
        selectedVersion: CompilerConfig.CompilerVersion,
        detectedVersion: CompilerConfig.CompilerVersion?
    ): CompilerConfig.CompilerVersion =
        Runner.compilerVersionForMode(forcedMode, selectedVersion, detectedVersion)

    /**
     * Resets the current compile fallback mutation state for this session.
     */
    fun resetSessionState() = Runner.resetSessionState()

    /**
     * Gets the currently loaded Native compiler version.
     *
     * @return loaded compiler version, or null if nothing has been initialized yet
     */
    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = Runner.getLoadedVersion()

    /**
     * Checks if the app must restart after version switching.
     *
     * @param requestedVersion target compiler version
     * @return true when a restart is needed due to a different loaded library
     */
    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean =
        Runner.isRestartRequired(requestedVersion)

    /**
     * Decides whether compiler fallback should happen based on the detected error count.
     *
     * @param output compiler output text
     * @param threshold required number of errors before fallback happens
     * @return true when the threshold is reached
     */
    internal fun shouldRetryWithFallback(output: String, threshold: Int = Names.AUTO_FALLBACK_ERROR_THRESHOLD): Boolean =
        Runner.shouldRetryWithFallback(output, threshold)

    /**
     * Counts the highest error total found in compiler output.
     *
     * @param output compiler output text
     * @return highest error count from the log
     */
    internal fun extractErrorCount(output: String): Int = Runner.extractErrorCount(output)

    /**
     * Detects a nearby Pawn compiler and maps it to the supported PawnMC version when metadata matches.
     *
     * @param sourceFile selected pawn source file path
     * @return matched compiler version or null when forced mode or no valid match is found
     */
    fun detectCompilerVersionForFile(sourceFile: String): CompilerConfig.CompilerVersion? =
        Detection.detectCompilerVersionForFile(sourceFile)

    /**
     * Scans nearby folders for a Pawn compiler executable and returns the best detected match.
     *
     * @param sourceFile selected Pawn file path
     * @return compiler match information or null if not found
     */
    fun detectNearbyCompiler(sourceFile: String): Detection.NearbyCompilerMatch? =
        Detection.detectNearbyCompiler(sourceFile)

    /** Selects one existing conventional include folder, or the stable `include/` fallback. */
    fun discoverRelevantIncludePaths(sourceFile: String, knownPaths: List<String> = emptyList()): List<String> =
        Detection.discoverRelevantIncludePaths(sourceFile, knownPaths)

    /**
     * Lowercases every `#include "..."` / `#include '...'` reference of a source.
     *
     * The conversion is unconditional: Android storage is case sensitive while the
     * NTFS-authored scripts are not, so `Aaaa` becomes `aaa` without exception.
     */
    fun lowercaseIncludeReferences(content: String): String =
        CaseConversion.lowercaseIncludeReferences(content)

    /** Folder that receives the untouched copy of [workingDir]. */
    fun backupDirFor(workingDir: File): File = CaseConversion.backupDirFor(workingDir)

    /** Replaces the backup with a snapshot of the current working folder. */
    fun refreshBackupForCompile(workingDir: File): Boolean =
        CaseConversion.refreshBackupForCompile(workingDir)

    /**
     * Decides whether the conversion has to run for [workingDir].
     *
     * Once `<folder>.backup` exists the folder has already been converted, so the
     * instructions are skipped even while "Ignore case" stays enabled. The key
     * stored in the configuration only has to agree with the filesystem; if the
     * folder was deleted the instructions run again.
     */
    fun needsConversion(workingDir: File, rememberedBackupDir: String? = null): Boolean =
        CaseConversion.needsConversion(workingDir, rememberedBackupDir)

    /**
     * Backs up the folder of the browsed file and rewrites it for a case sensitive
     * filesystem.
     *
     * The whole folder holding the selected file is copied to `<folder>.backup`
     * first, so the original names are always recoverable. The working folder is
     * then modified in place: every file and folder name is lowercased (the name of
     * the selected file is kept as-is) and every static include reference in the
     * folder is lowercased too.
     */
    fun convertFolderToLowerCase(
        selectedFilePath: String,
        rememberedBackupDir: String? = null
    ): CaseConversion.ConversionResult =
        CaseConversion.convertFolderToLowerCase(selectedFilePath, rememberedBackupDir)

    /** Finds a file inside [dir] whose name matches [name] ignoring case. */
    fun findFileIgnoreCase(dir: File, name: String): File? =
        CaseConversion.findFileIgnoreCase(dir, name)

    /**
     * Starts the native Pawn compiler job for the selected source file.
     *
     * @param sourceFile path of the Pawn source file
     * @param options compiler flags to pass through
     * @param version compiler version to execute
     * @return exit code and captured output text
     */
    fun compile(
        sourceFile: String,
        options: List<String> = emptyList(),
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V3107
    ): Pair<Int, String> = Runner.compile(sourceFile, options, version)

    internal fun compilerOptionsForVersion(
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = Runner.compilerOptionsForVersion(options, version)

    internal fun compilerArgumentsForVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = Runner.compilerArgumentsForVersion(sourceFile, options, version)

    fun explainCompilerOutput(rawText: String, cacheDir: File): String =
        Explainer.explainCompilerOutput(rawText, cacheDir)

    /**
     * Returns the last captured compiler output from the native layer.
     *
     * @return output string or empty when not initialized
     */
    fun getCapturedOutput(): String = Runner.getCapturedOutput()

    /**
     * Returns the last captured compiler errors from the native layer.
     *
     * @return error string or empty when not initialized
     */
    fun getCapturedErrors(): String = Runner.getCapturedErrors()
}
