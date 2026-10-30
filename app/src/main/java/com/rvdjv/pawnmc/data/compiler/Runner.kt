package com.rvdjv.pawnmc.data.compiler

import android.util.Log
import com.rvdjv.pawnmc.data.compiler.Names.AUTO_FALLBACK_ERROR_THRESHOLD
import com.rvdjv.pawnmc.data.compiler.Names.SSCANF_NO_NICE_FEATURES_FLAG
import com.rvdjv.pawnmc.data.compiler.Names.STR_PAWNCC_BINARY_NAME
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.log._compiler_store
import java.io.File

/**
 * Execution layer: loads the native library, builds the argument list, runs the
 * compiler and keeps the one-shot fallback to the other compiler version.
 */
object Runner {

    private var INITIALIZED_VER: CompilerConfig.CompilerVersion? = null
    private var IS_INITIALIZED = false
    private var FALL_MUTATION = false

    /** Consecutive failed compiles of this session; a success resets it. */
    @Volatile
    private var CONSECUTIVE_FAILURES = 0

    /** Minimum failures in a row before "Explain output" is enabled automatically. */
    private const val AUTO_EXPLAIN_FAILURE_THRESHOLD = 3

    /** Matches `cannot read from file "path/with/separators.inc"` with / or \ paths. */
    private val CANNOT_READ_FROM_FILE_REGEX =
        Regex("""(?i)cannot\s+read\s+from\s+file\s+"[^"]*[/\\\\][^"]*"""")

    private val EXIT_CODE_REGEX = """^Exit code: (-?\d+)""".toRegex()
    private val ERROR_COUNT_REGEX = """(?i)(\d+)\s+errors?\.?""".toRegex()

    /** True when auto-detection and fallback switching are disabled by the user. */
    fun isForcedModeEnabled(): Boolean {
        return CompilerConfig.getInstanceOrNull()?.n_forced_compiler_mode == true
    }

    internal fun compilerVersionForMode(
        forcedMode: Boolean,
        selectedVersion: CompilerConfig.CompilerVersion,
        detectedVersion: CompilerConfig.CompilerVersion?
    ): CompilerConfig.CompilerVersion =
        if (forcedMode) selectedVersion else detectedVersion ?: CompilerConfig.CompilerVersion.V3107

    /** Resets the compile fallback state for this session. */
    fun resetSessionState() {
        FALL_MUTATION = false
        CONSECUTIVE_FAILURES = 0
    }

    /** Enables the one-shot assist toggles after a failed compile. */
    private fun applyAutomaticFailureToggles(exitCode: Int, output: String) {
        val n_config = CompilerConfig.getInstanceOrNull() ?: return

        if (exitCode == 0) {
            CONSECUTIVE_FAILURES = 0
            return
        }
        CONSECUTIVE_FAILURES++

        if (CONSECUTIVE_FAILURES >= AUTO_EXPLAIN_FAILURE_THRESHOLD && !n_config.n_explain_output) {
            Log.i("Compiler", "Auto-assist: enabling Explain output after $CONSECUTIVE_FAILURES consecutive failures")
            n_config.n_explain_output = true
            n_config.n_explain_auto = true
        }

        if (!n_config.n_ignore_case && CANNOT_READ_FROM_FILE_REGEX.containsMatchIn(output)) {
            Log.i("Compiler", "Auto-assist: 'cannot read from file' detected, enabling Ignore case for the next compile")
            n_config.n_ignore_case = true
            n_config.n_ignore_case_auto = true
        }
    }

    /** Loads the selected native compiler library once per session. */
    private fun ensureInitialized(version: CompilerConfig.CompilerVersion): Boolean {
        if (IS_INITIALIZED) {
            if (INITIALIZED_VER != version) {
                Log.w("Compiler",
                    "Requested ${version.label} but ${INITIALIZED_VER?.label} is already loaded. " +
                    "App restart required for a full version swap.")
            }
            return true
        }

        return try {
            System.loadLibrary(version.libraryName)
            INITIALIZED_VER = version
            IS_INITIALIZED = true
            Log.i("Compiler", "Loaded: ${version.libraryName}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("Compiler", "Failed to load: ${version.libraryName}", e)
            false
        }
    }

    /** Loaded native compiler version, or null before the first compile. */
    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = INITIALIZED_VER

    /** True when switching to [requestedVersion] needs an app restart. */
    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean {
        return IS_INITIALIZED && INITIALIZED_VER != requestedVersion
    }

    /** True when the error count in [output] reaches the fallback [threshold]. */
    internal fun shouldRetryWithFallback(output: String, threshold: Int = AUTO_FALLBACK_ERROR_THRESHOLD): Boolean {
        return extractErrorCount(output) >= threshold
    }

    /** Highest error total found in compiler output. */
    internal fun extractErrorCount(output: String): Int {
        return ERROR_COUNT_REGEX.findAll(output)
            .map { it.groupValues[1].toIntOrNull() ?: 0 }
            .maxOrNull() ?: 0
    }

    /** Starts the native Pawn compiler job for the selected source file. */
    fun compile(
        sourceFile: String,
        options: List<String> = emptyList(),
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V3107
    ): Pair<Int, String> {
        val n_config = CompilerConfig.getInstanceOrNull()
        val n_isForced = n_config?.n_forced_compiler_mode == true

        val n_result = compileWithVersion(sourceFile, options, version)

        if (!n_isForced && !FALL_MUTATION && shouldRetryWithFallback(n_result.second)) {
            val n_fallbackVersion = version.other()
            FALL_MUTATION = true

            if (n_config != null) {
                Log.w(
                    "Compiler",
                    "fail! found: ${extractErrorCount(n_result.second)}" +
                    " errors in ${version.label}. " +
                    "trying ${n_fallbackVersion.label} for the next retry."
                )
                n_config.n_compiler_version = n_fallbackVersion
            }

            val n_retryResult = compileWithVersion(sourceFile, options, n_fallbackVersion)
            if (n_retryResult.first >= 0 ||
            n_retryResult.second.isNotBlank())
            {
                return n_retryResult
            }
        }

        return n_result
    }

    /** Runs pawncc with exactly the arguments entered by the manual console. */
    fun compileManual(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): Pair<Int, String> = compileWithVersion(sourceFile, options, version, manualArguments = true)

    internal fun compilerArgumentsForManual(sourceFile: String, options: List<String>): List<String> =
        buildList {
            add(STR_PAWNCC_BINARY_NAME)
            add(sourceFile)
            addAll(options)
        }

    internal fun compilerOptionsForVersion(
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> {
        val normalized = mutableListOf<String>()
        var hasSscanfFlag = false
        val versionSafeOptions = if (
            version == CompilerConfig.CompilerVersion.V3107 &&
            options.any { CompilerConfig.isO2OptimizationFlag(it) }
        ) {
            options.filterNot { CompilerConfig.isOptimizationFlag(it) } + "-O=1"
        } else {
            options
        }
        versionSafeOptions.forEach { option ->
            val canonicalOption = if (option == "-D$SSCANF_NO_NICE_FEATURES_FLAG") {
                SSCANF_NO_NICE_FEATURES_FLAG
            } else option
            if (canonicalOption == SSCANF_NO_NICE_FEATURES_FLAG) {
                if (!hasSscanfFlag) normalized += canonicalOption
                hasSscanfFlag = true
            } else {
                normalized += canonicalOption
            }
        }
        // Quiet run: unless the arguments already name a verbosity level, the native
        // run is silenced behind the scenes with `-v0` (the mirror of the default in
        // `buildOptionsFor` for console-style invocations that skip the GUI options).
        if (normalized.none { CompilerConfig.isVerbosityFlag(it) }) {
            normalized += "-v0"
        }
        if (version != CompilerConfig.CompilerVersion.V3107) return normalized
        return normalized.filterNot { it == SSCANF_NO_NICE_FEATURES_FLAG } + SSCANF_NO_NICE_FEATURES_FLAG
    }

    internal fun compilerArgumentsForVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = buildList {
        add(STR_PAWNCC_BINARY_NAME)
        add(sourceFile)
        addAll(compilerOptionsForVersion(options, version))
    }

    /** Runs the selected compiler version with the provided arguments. */
    private fun compileWithVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion,
        manualArguments: Boolean = false
    ): Pair<Int, String> {
        if (!ensureInitialized(version)) {
            return -1 to "Failed to load compiler library"
        }

        Log.d("Compiler", "Compiling with: ${INITIALIZED_VER?.label}")

        // Automatic assists last exactly one compile.
        val n_config = CompilerConfig.getInstanceOrNull()
        if (n_config?.n_explain_auto == true) {
            Log.i("Compiler", "Auto-assist: dropping the temporary Explain output instruction")
            n_config.n_explain_output = false
            n_config.n_explain_auto = false
        }

        if (manualArguments && n_config?.n_ignore_case_auto == true) {
            Log.i("Compiler", "Auto-assist: disabling the temporary Ignore case instruction")
            n_config.n_ignore_case = false
            n_config.n_ignore_case_auto = false
        }

        val n_args = if (manualArguments) {
            compilerArgumentsForManual(sourceFile, options)
        } else {
            compilerArgumentsForVersion(sourceFile, options, version)
        }
        val n_startTime = System.currentTimeMillis()
        val n_output = compile(n_args.toTypedArray())
        val n_duration = System.currentTimeMillis() - n_startTime
        val n_parsedResult = parseCompilerOutput(n_output)

        runCatching {
            val n_source = File(sourceFile)
            _compiler_store.record(
                CompilerConfig.getInstance().applicationContextHolder(),
                _compiler_store.Entry(
                    timestamp = n_startTime,
                    sourcePath = sourceFile,
                    sourceName = n_source.name,
                    exitCode = n_parsedResult.first,
                    durationMs = n_duration,
                    compilerVersion = version.label,
                    output = n_parsedResult.second
                )
            )
        }.onFailure { Log.e("Compiler", "Failed to record compile log", it) }

        applyAutomaticFailureToggles(n_parsedResult.first, n_parsedResult.second)

        return n_parsedResult
    }

    /** Parses the compiler exit code and the text output produced by the native runner. */
    private fun parseCompilerOutput(output: String): Pair<Int, String> {
        val n_match = EXIT_CODE_REGEX.find(output)

        val n_exitCode = n_match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val n_actualOutput = output.substringAfter('\n', "")

        return n_exitCode to n_actualOutput
    }

    /** Last captured compiler output, or empty when not initialized. */
    fun getCapturedOutput(): String = if (IS_INITIALIZED) getOutput() else ""

    /** Last captured compiler errors, or empty when not initialized. */
    fun getCapturedErrors(): String = if (IS_INITIALIZED) getErrors() else ""

    private external fun compile(args: Array<String>): String
    private external fun getOutput(): String
    private external fun getErrors(): String
}