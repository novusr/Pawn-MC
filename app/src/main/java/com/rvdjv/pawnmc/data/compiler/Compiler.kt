package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * Facade of the compiler layer, forwarding to the focused helpers.
 */
object Compiler {

    /** True when auto-detection and fallback switching are disabled by the user. */
    fun isForcedModeEnabled(): Boolean = Runner.isForcedModeEnabled()

    internal fun compilerVersionForMode(
        forcedMode: Boolean,
        selectedVersion: CompilerConfig.CompilerVersion,
        detectedVersion: CompilerConfig.CompilerVersion?
    ): CompilerConfig.CompilerVersion =
        Runner.compilerVersionForMode(forcedMode, selectedVersion, detectedVersion)

    /** Resets the compile fallback state for this session. */
    fun resetSessionState() = Runner.resetSessionState()

    /** Loaded native compiler version, or null before the first compile. */
    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = Runner.getLoadedVersion()

    /** True when switching to [requestedVersion] needs an app restart. */
    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean =
        Runner.isRestartRequired(requestedVersion)

    /** True when the error count in [output] reaches the fallback [threshold]. */
    internal fun shouldRetryWithFallback(output: String, threshold: Int = Names.AUTO_FALLBACK_ERROR_THRESHOLD): Boolean =
        Runner.shouldRetryWithFallback(output, threshold)

    /** Highest error total found in compiler output. */
    internal fun extractErrorCount(output: String): Int = Runner.extractErrorCount(output)

    /** Nearby compiler version mapped to a supported PawnMC version, or null. */
    fun detectCompilerVersionForFile(sourceFile: String): CompilerConfig.CompilerVersion? =
        Detection.detectCompilerVersionForFile(sourceFile)

    /** Best nearby compiler match for [sourceFile], or null. */
    fun detectNearbyCompiler(sourceFile: String): Detection.NearbyCompilerMatch? =
        Detection.detectNearbyCompiler(sourceFile)

    /** Selects one existing conventional include folder, or the stable `include/` fallback. */
    fun discoverRelevantIncludePaths(sourceFile: String, knownPaths: List<String> = emptyList()): List<String> =
        Detection.discoverRelevantIncludePaths(sourceFile, knownPaths)

    /** Lowercases every static `#include` reference of a source. */
    fun lowercaseIncludeReferences(content: String): String =
        CaseConversion.lowercaseIncludeReferences(content)

    /** Folder that receives the untouched copy of [workingDir]. */
    fun backupDirFor(workingDir: File): File = CaseConversion.backupDirFor(workingDir)

    /** Replaces the backup with a snapshot of the current working folder. */
    fun refreshBackupForCompile(workingDir: File): Boolean =
        CaseConversion.refreshBackupForCompile(workingDir)

    /** True when [workingDir] still has to be converted. */
    fun needsConversion(workingDir: File, rememberedBackupDir: String? = null): Boolean =
        CaseConversion.needsConversion(workingDir, rememberedBackupDir)

    /** Backs up the folder of the browsed file and lowercases it for a case sensitive filesystem. */
    fun convertFolderToLowerCase(
        selectedFilePath: String,
        rememberedBackupDir: String? = null
    ): CaseConversion.ConversionResult =
        CaseConversion.convertFolderToLowerCase(selectedFilePath, rememberedBackupDir)

    /** Finds a file inside [dir] whose name matches [name] ignoring case. */
    fun findFileIgnoreCase(dir: File, name: String): File? =
        CaseConversion.findFileIgnoreCase(dir, name)

    /** Starts the native Pawn compiler job for the selected source file. */
    fun compile(
        sourceFile: String,
        options: List<String> = emptyList(),
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V3107
    ): Pair<Int, String> = Runner.compile(sourceFile, options, version)

    /** Runs a console-specified pawncc invocation without GUI options or automatic fallback. */
    fun compileManual(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): Pair<Int, String> = Runner.compileManual(sourceFile, options, version)

    internal fun compilerArgumentsForManual(sourceFile: String, options: List<String>): List<String> =
        Runner.compilerArgumentsForManual(sourceFile, options)

    internal fun compilerOptionsForVersion(
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = Runner.compilerOptionsForVersion(options, version)

    internal fun compilerArgumentsForVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = Runner.compilerArgumentsForVersion(sourceFile, options, version)

    fun explainCompilerOutput(
        rawText: String,
        cacheDir: File,
        language: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN
    ): String = Explainer.explainCompilerOutput(rawText, cacheDir, language)

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
