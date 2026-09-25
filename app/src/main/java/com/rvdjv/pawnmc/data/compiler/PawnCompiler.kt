package com.rvdjv.pawnmc.data.compiler

import android.util.Log
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * kotlin wrapper
 */
object PawnCompiler {

    private const val AUTO_FALLBACK_ERROR_THRESHOLD = 5
    private var INITIALIZED_VER: CompilerConfig.CompilerVersion? = null
    private var IS_INITIALIZED = false
    private var FALL_MUTATION = false
    private val EXIT_CODE_REGEX = """^Exit code: (-?\d+)""".toRegex()
    private val ERROR_COUNT_REGEX = """(?i)(\d+)\s+errors?\.?""".toRegex()

    fun resetSessionState() {
        FALL_MUTATION = false
    }

    // load lib when app is opened
    private fun ensureInitialized(version: CompilerConfig.CompilerVersion): Boolean {
        if (IS_INITIALIZED) {
            if (INITIALIZED_VER != version) {
                Log.w("PawnCompiler",
                    "Requested ${version.label} but ${INITIALIZED_VER?.label} is already loaded. " +
                    "App restart required for a full version swap.")
            }
            return true
        }

        return try {
            System.loadLibrary(version.libraryName)
            INITIALIZED_VER = version
            IS_INITIALIZED = true
            Log.i("PawnCompiler", "Loaded: ${version.libraryName}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("PawnCompiler", "Failed to load: ${version.libraryName}", e)
            false
        }
    }

    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = INITIALIZED_VER

    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean {
        return IS_INITIALIZED && INITIALIZED_VER != requestedVersion
    }

    internal fun shouldRetryWithFallback(output: String, threshold: Int = AUTO_FALLBACK_ERROR_THRESHOLD): Boolean {
        return extractErrorCount(output) >= threshold
    }

    internal fun extractErrorCount(output: String): Int {
        return ERROR_COUNT_REGEX.findAll(output)
            .map { it.groupValues[1].toIntOrNull() ?: 0 }
            .maxOrNull() ?: 0
    }

    /**
     * pawn file compilation
     * 
     * @param sourceFile absolute path to pawn source file
     * @param options compiler options
     * @param version compiler versions
     * @return exitCode capturedOutput
     */
    fun compile(
        sourceFile: String,
        options: List<String> = emptyList(),
        version: CompilerConfig.CompilerVersion = CompilerConfig.CompilerVersion.V3107
    ): Pair<Int, String> {
        val result = compileWithVersion(sourceFile, options, version)

        if (!FALL_MUTATION && shouldRetryWithFallback(result.second)) {
            val fallbackVersion = version.other()
            FALL_MUTATION = true

            val config = CompilerConfig.getInstanceOrNull()
            if (config != null) {
                Log.w(
                    "PawnCompiler",
                    "Detected ${extractErrorCount(result.second)} Errors in ${version.label}. " +
                    "Switching compiler to ${fallbackVersion.label} for the next retry."
                )
                config.n_compiler_version = fallbackVersion
            }

            val retryResult = compileWithVersion(sourceFile, options, fallbackVersion)
            if (retryResult.first >= 0 || retryResult.second.isNotBlank()) {
                return retryResult
            }
        }

        return result
    }

    private fun compileWithVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): Pair<Int, String> {
        if (!ensureInitialized(version)) {
            return -1 to "Failed to load compiler library"
        }

        Log.d("PawnCompiler", "Compiling with: ${INITIALIZED_VER?.label}")

        val logFile = runCatching {
            File(sourceFile).let { f ->
                val base = f.nameWithoutExtension
                File(f.parent, "$base.log")
            }
        }.getOrNull()

        logFile?.takeIf { it.exists() }?.runCatching { delete() }
            ?.onFailure { Log.e("PawnCompiler", "Failed to delete old log file: ${logFile.absolutePath}", it) }

        val args = buildList {
            add("pawncc")
            addAll(options)
            add(sourceFile)
        }

        val output = compile(args.toTypedArray())
        val parsedResult = parseCompilerOutput(output)

        logFile?.runCatching { writeText(parsedResult.second) }
            ?.onFailure { Log.e("PawnCompiler", "Failed to write log file: ${logFile.absolutePath}", it) }

        return parsedResult
    }

    private fun parseCompilerOutput(output: String): Pair<Int, String> {
        val match = EXIT_CODE_REGEX.find(output)

        val exitCode = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val actualOutput = output.substringAfter('\n', "")

        return exitCode to actualOutput
    }

    fun getCapturedOutput(): String = if (IS_INITIALIZED) getOutput() else ""
    fun getCapturedErrors(): String = if (IS_INITIALIZED) getErrors() else ""

    private external fun compile(args: Array<String>): String
    private external fun getOutput(): String
    private external fun getErrors(): String
}
