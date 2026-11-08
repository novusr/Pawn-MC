package com.rvdjv.pawnmc.apps_data.data_compiler

import android.util.Log
import com.rvdjv.pawnmc.apps_data.data_compiler.Names.AUTO_FALLBACK_ERROR_THRESHOLD
import com.rvdjv.pawnmc.apps_data.data_compiler.Names.SSCANF_NO_NICE_FEATURES_FLAG
import com.rvdjv.pawnmc.apps_data.data_compiler.Names.STR_PAWNCC_BINARY_NAME
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.apps_data.data_config.SystemNotification
import com.rvdjv.pawnmc.apps_data.data_library.LibraryStore
import com.rvdjv.pawnmc.apps_data.data_logging._compiler_store
import java.io.File

/**
 * Execution layer: loads the native library, builds the argument list, runs the
 * compiler and keeps the one-shot fallback to the other compiler version.
 *
 * The libraries themselves live in `Documents/PawnMC/library/<abi>/` (see
 * [com.rvdjv.pawnmc.apps_data.data_library.LibraryStore]); this object only resolves which
 * ABI folder the compile has to read from and routes the request through
 * [NativeBridge], which keeps one JNI surface and swaps the version underneath.
 */
object Runner {

    private var INITIALIZED_VER: ContentConfig.CompilerVersion? = null
    private var IS_INITIALIZED = false
    private var FALL_MUTATION = false

    /** Host context used to find `Documents/PawnMC/library/<abi>`; null disables it. */
    @Volatile
    private var HOST: android.content.Context? = null

    /**
     * Hands the compiler layer its storage root. Called from the app entry point once
     * `Documents/PawnMC` exists, and safe to call again after a folder change.
     */
    fun attachHost(context: android.content.Context?) {
        HOST = context?.applicationContext
    }

    /** `library/<abi>` the compile is reading from, for the UI and the log. */
    fun libraryDirectory(): File? = HOST?.let { NativeBridge.libraryDirectory(it) }

    /**
     * Re-unpacks `library/<abi>/<abi>.zip` when a library is missing or has the wrong ABI.
     *
     * `LibraryStore` skips the archive when every file has the expected size and ABI,
     * so a stale compiler from another architecture is repaired without reinstalling.
     */
    private fun extractLibraries(): Boolean {
        val n_host = HOST ?: return false
        return LibraryStore.ensureExtracted(n_host, LibraryStore.deviceAbi())
    }

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
        return ContentConfig.getInstanceOrNull()?.n_forced_compiler_mode == true
    }

    internal fun compilerVersionForMode(
        forcedMode: Boolean,
        selectedVersion: ContentConfig.CompilerVersion,
        detectedVersion: ContentConfig.CompilerVersion?
    ): ContentConfig.CompilerVersion =
        if (forcedMode) selectedVersion else detectedVersion ?: ContentConfig.CompilerVersion.V3107

    /** Resets one-shot compiler recovery state for a new app process/session. */
    /** Resets session fallback state for a new app process/session. */
    @Synchronized
    fun resetSessionState() {
        FALL_MUTATION = false
        CONSECUTIVE_FAILURES = 0
    }

    /**
     * `__addressof` auto-switch, driven by the same reading the explainer uses.
     *
     * The switch fires only when every condition below holds:
     *
     *  1. the output really contains an `undefined symbol` hit for
     *     `__addressof`, read with [Explainer]'s own quoted-symbol patterns
     *     (the same ones that fill `%s` in the user-facing explanation) or the
     *     bare symbol mention;
     *  2. the running compiler is 3.10.7;
     *  3. the compiler mode is Auto, not Forced.
     *
     * Anything else - forced mode, a non-3.10.7 run, output without the symbol -
     * leaves the stored version untouched.
     */
    @Synchronized
    fun applyAddressOfPolicy(output: String, runningVersion: ContentConfig.CompilerVersion) {
        if (output.isBlank()) return

        val n_config = ContentConfig.getInstanceOrNull() ?: return

        // Condition 3 - Auto mode only.
        if (n_config.n_forced_compiler_mode) return

        // Condition 2 - the run that produced this output was 3.10.7.
        if (runningVersion != ContentConfig.CompilerVersion.V3107) return

        // Condition 1 - read the symbol the way the explainer reads it: the
        // quoted-symbol regexes from [Explainer.QUOTED_SYMBOL_PATTERNS] plus the
        // unquoted spelling, so `undefined symbol "__addressof"`,
        // `undefined symbol '__addressof'` and a bare `__addressof(` all hit.
        val n_undefinedAddressOf = Explainer.explainsUndefinedSymbol(output, "__addressof") ||
            output.contains("__addressof", ignoreCase = true)
        if (!n_undefinedAddressOf) return

        if (n_config.n_compiler_version != ContentConfig.CompilerVersion.V3107) return
        // The setter writes both SharedPreferences and the JSON mirror in
        // Documents/PawnMC/config/, so the switch survives a restart.
        n_config.n_compiler_version = ContentConfig.CompilerVersion.V31011
        Log.i(
            "Compiler",
            "__addressof undefined under 3.10.7 in Auto mode; " +
            "stored compiler version moved to 3.10.11"
        )
        SystemNotification.restartNeeded(
            SystemNotification.contextOrNull(),
            ContentConfig.CompilerVersion.V3107.label,
            ContentConfig.CompilerVersion.V31011.label
        )
    }

    /** Enables the one-shot assist toggles after a failed compile. */
    private fun applyAutomaticFailureToggles(exitCode: Int, output: String) {
        val n_config = ContentConfig.getInstanceOrNull() ?: return

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

    /**
     * Makes [version] the active compiler.
     *
     * The bridge loads once; switching versions only re-points it at another
     * `lib<version>.so` from `library/<abi>/`, so a swap no longer needs a restart.
     * When the extracted folder does not provide the version (the user deleted it, or
     * the archive was never unpacked) the call reports failure and the compile
     * returns an explicit error instead of an `UnsatisfiedLinkError`.
     */
    private fun ensureInitialized(version: ContentConfig.CompilerVersion): Boolean {
        val n_host = HOST
        if (n_host == null) {
            Log.e("Compiler", "No host context; cannot locate ${version.libraryName}")
            return false
        }

        if (IS_INITIALIZED && INITIALIZED_VER == version && NativeBridge.isActive(n_host, version.libraryName)) {
            return true
        }

        val n_ready = NativeBridge.prepare(n_host, version.libraryName)
        if (!n_ready) {
            Log.e(
                "Compiler",
                "${version.libraryName} is not loadable. " +
                    NativeBridge.readinessError(n_host, version.libraryName)
            )
            return false
        }

        INITIALIZED_VER = version
        IS_INITIALIZED = true
        Log.i("Compiler", "Loaded: ${version.libraryName} from ${libraryDirectory()?.absolutePath}")
        return true
    }

    /** Loaded native compiler version, or null before the first compile. */
    fun getLoadedVersion(): ContentConfig.CompilerVersion? = INITIALIZED_VER

    /**
     * True when switching to [requestedVersion] cannot be honoured in this process.
     *
     * A swap no longer needs a restart: every version is its own `lib<version>.so` in
     * `library/<abi>/` and the bridge only re-points at it. This stays only for the case
     * where there is no host context at all and therefore no folder to load from.
     */
    fun isRestartRequired(requestedVersion: ContentConfig.CompilerVersion): Boolean =
        HOST == null && IS_INITIALIZED && INITIALIZED_VER != requestedVersion

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
        version: ContentConfig.CompilerVersion = ContentConfig.CompilerVersion.V3107
    ): Pair<Int, String> {
        val n_config = ContentConfig.getInstanceOrNull()
        val n_isForced = n_config?.n_forced_compiler_mode == true

        val n_result = compileWithVersion(sourceFile, options, version)
        // Pure auto-switch: no forced-mode condition, no automatic recompile.
        // Run on the parsed text; the parse no longer drops the first line, so
        // an `undefined "__addressof"` on line one reaches the recovery here.
        applyAddressOfPolicy(n_result.second, version)

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

    /** Runs the manual command; the same __addressof auto-switch applies. */
    fun compileManual(
        sourceFile: String,
        options: List<String>,
        version: ContentConfig.CompilerVersion
    ): Pair<Int, String> {
        val first = compileWithVersion(sourceFile, options, version, manualArguments = true)
        applyAddressOfPolicy(first.second, version)
        return first
    }

    internal fun compilerArgumentsForManual(sourceFile: String, options: List<String>): List<String> =
        buildList {
            add(STR_PAWNCC_BINARY_NAME)
            add(sourceFile)
            addAll(options)
        }

    internal fun compilerOptionsForVersion(
        options: List<String>,
        version: ContentConfig.CompilerVersion
    ): List<String> {
        val normalized = mutableListOf<String>()
        var hasSscanfFlag = false
        val versionSafeOptions = if (
            version == ContentConfig.CompilerVersion.V3107 &&
            options.any { ContentConfig.isO2OptimizationFlag(it) }
        ) {
            options.filterNot { ContentConfig.isOptimizationFlag(it) } + "-O=1"
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
        if (normalized.none { ContentConfig.isVerbosityFlag(it) }) {
            normalized += "-v0"
        }
        if (version != ContentConfig.CompilerVersion.V3107) return normalized
        return normalized.filterNot { it == SSCANF_NO_NICE_FEATURES_FLAG } + SSCANF_NO_NICE_FEATURES_FLAG
    }

    internal fun compilerArgumentsForVersion(
        sourceFile: String,
        options: List<String>,
        version: ContentConfig.CompilerVersion
    ): List<String> = buildList {
        add(STR_PAWNCC_BINARY_NAME)
        add(sourceFile)
        addAll(compilerOptionsForVersion(options, version))
    }

    /** Runs the selected compiler version with the provided arguments. */
    private fun compileWithVersion(
        sourceFile: String,
        options: List<String>,
        version: ContentConfig.CompilerVersion,
        manualArguments: Boolean = false
    ): Pair<Int, String> {
        // The archive is unpacked at startup; a missing or wrong-ABI file gets one
        // repair attempt before the compile reports failure.
        // `LibraryStore` re-unpacks when a compiler library is missing or has the wrong ABI.
        var n_initialized = ensureInitialized(version)
        if (!n_initialized && extractLibraries()) {
            n_initialized = ensureInitialized(version)
        }
        if (!n_initialized) {
            val n_host = HOST
            val n_detail = n_host?.let {
                NativeBridge.readinessError(it, version.libraryName)
            } ?: "PawnMC has no storage context for Documents/PawnMC/library."
            return -1 to "Failed to load compiler library ${version.libraryName}. $n_detail"
        }

        Log.d("Compiler", "Compiling with: ${INITIALIZED_VER?.label}")

        // Automatic assists last exactly one compile.
        val n_config = ContentConfig.getInstanceOrNull()
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
                ContentConfig.getInstance().applicationContextHolder(),
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
        if (n_match == null) {
            // No exit-code banner: the raw text *is* the output. Trimming a line
            // here used to swallow the first error (an `undefined "__addressof"`
            // on line one was cut off and the recovery never saw it).
            return -1 to output
        }
        val n_exitCode = n_match.groupValues[1].toIntOrNull() ?: -1
        val n_actualOutput = output.substringAfter('\n', "")
        return n_exitCode to n_actualOutput
    }

    /** Last captured compiler output, or empty when not initialized. */
    fun getCapturedOutput(): String = if (IS_INITIALIZED) getOutput() else ""

    /** Last captured compiler errors, or empty when not initialized. */
    fun getCapturedErrors(): String = if (IS_INITIALIZED) getErrors() else ""

    private fun compile(args: Array<String>): String = NativeBridge.compile(args)
    private fun getOutput(): String = NativeBridge.getOutput()
    private fun getErrors(): String = NativeBridge.getErrors()
}