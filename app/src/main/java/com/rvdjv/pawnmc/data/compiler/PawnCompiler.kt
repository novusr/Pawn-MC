package com.rvdjv.pawnmc.data.compiler

import android.util.Log
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * kotlin wrapper
 */
object PawnCompiler {

    data class NearbyCompilerMatch(
        val version: CompilerConfig.CompilerVersion,
        val filePath: String,
        val productVersion: String?,
        val sizeBytes: Long,
        val md5: String?
    )

    private const val AUTO_FALLBACK_ERROR_THRESHOLD = 5
    private var INITIALIZED_VER: CompilerConfig.CompilerVersion? = null
    private var IS_INITIALIZED = false
    private var FALL_MUTATION = false

    /**
     * Checks whether forced mode is enabled by the user.
     *
     * @return true if auto-detection and fallback switching are disabled
     */
    fun isForcedModeEnabled(): Boolean {
        return CompilerConfig.getInstanceOrNull()?.n_forced_compiler_mode == true
    }

    private val EXIT_CODE_REGEX = """^Exit code: (-?\d+)""".toRegex()
    private val ERROR_COUNT_REGEX = """(?i)(\d+)\s+errors?\.?""".toRegex()
    private val PRODUCT_VERSION_REGEX = """\b\d+\.\d+\.\d+\b""".toRegex()

    /**
     * Resets the current compile fallback mutation state for this session.
     */
    fun resetSessionState() {
        FALL_MUTATION = false
    }

    /**
     * Loads the selected native compiler library once per session.
     *
     * @param version selected compiler version
     * @return true when load succeeds, otherwise false
     */
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

    /**
     * Gets the currently loaded Native compiler version.
     *
     * @return loaded compiler version, or null if nothing has been initialized yet
     */
    fun getLoadedVersion(): CompilerConfig.CompilerVersion? = INITIALIZED_VER

    /**
     * Checks if the app must restart after version switching.
     *
     * @param requestedVersion target compiler version
     * @return true when a restart is needed due to a different loaded library
     */
    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean {
        return IS_INITIALIZED && INITIALIZED_VER != requestedVersion
    }

    /**
     * Decides whether compiler fallback should happen based on the detected error count.
     *
     * @param output compiler output text
     * @param threshold required number of errors before fallback happens
     * @return true when the threshold is reached
     */
    internal fun shouldRetryWithFallback(output: String, threshold: Int = AUTO_FALLBACK_ERROR_THRESHOLD): Boolean {
        return extractErrorCount(output) >= threshold
    }

    /**
     * Counts the highest error total found in compiler output.
     *
     * @param output compiler output text
     * @return highest error count from the log
     */
    internal fun extractErrorCount(output: String): Int {
        return ERROR_COUNT_REGEX.findAll(output)
            .map { it.groupValues[1].toIntOrNull() ?: 0 }
            .maxOrNull() ?: 0
    }

    /**
     * Detects a nearby Pawn compiler and maps it to the supported PawnMC version when metadata matches.
     *
     * @param sourceFile selected pawn source file path
     * @return matched compiler version or null when forced mode or no valid match is found
     */
    fun detectCompilerVersionForFile(sourceFile: String): CompilerConfig.CompilerVersion? {
        val n_config = CompilerConfig.getInstanceOrNull()
        if (n_config?.n_forced_compiler_mode == true) {
            return null
        }

        val n_match = detectNearbyCompiler(sourceFile) ?: return null
        if (n_config != null) {
            n_config.n_detected_compiler_product_version = n_match.productVersion
            n_config.n_detected_compiler_size_bytes = n_match.sizeBytes
            n_config.n_detected_compiler_md5 = n_match.md5
        }
        return n_match.version
    }

    /**
     * Scans nearby folders for a Pawn compiler executable and returns the best detected match.
     *
     * @param sourceFile selected Pawn file path
     * @return compiler match information or null if not found
     */
    fun detectNearbyCompiler(sourceFile: String): NearbyCompilerMatch? {
        val n_sourceFile = File(sourceFile)
        if (!n_sourceFile.exists() || n_sourceFile.extension.isBlank()) return null

        val n_searchRoots = LinkedHashSet<File>()
        var n_currentDir = n_sourceFile.parentFile
        var n_depth = 0
        while (n_currentDir != null && n_depth < 6) {
            n_searchRoots += n_currentDir
            n_searchRoots += File(n_currentDir, "pawno")
            n_searchRoots += File(n_currentDir, "pawn")
            n_currentDir = n_currentDir.parentFile
            n_depth += 1
        }

        for (n_dir in n_searchRoots) {
            val n_pawnoDir = File(n_dir, "pawno")
            val n_pawnDir = File(n_dir, "pawn")
            val n_candidates = listOf(
                File(n_dir, "pawncc.exe"),
                File(n_dir, "pawncc"),
                File(n_pawnoDir, "pawncc.exe"),
                File(n_pawnDir, "pawncc.exe")
            ).distinctBy { it.absolutePath }

            for (n_candidate in n_candidates) {
                if (!n_candidate.exists() || !n_candidate.isFile) continue
                val n_metadata = readCompilerMetadata(n_candidate)
                val n_version = n_metadata.version ?: continue
                return NearbyCompilerMatch(
                    version = n_version,
                    filePath = n_candidate.absolutePath,
                    productVersion = n_metadata.productVersion,
                    sizeBytes = n_metadata.sizeBytes,
                    md5 = n_metadata.md5
                )
            }
        }

        return null
    }

    /**
     * Builds include paths based on the selected file directory and the nearest detected compiler folder.
     *
     * @param sourceFile selected Pawn file path
     * @return include directories that should be added as -i values
     */
    fun discoverRelevantIncludePaths(sourceFile: String): List<String> {
        val n_result = linkedSetOf<String>()

        val n_sourceDir = File(sourceFile).parentFile
        if (n_sourceDir != null && n_sourceDir.exists() && n_sourceDir.isDirectory) {
            n_result += n_sourceDir.absolutePath
        }

        val n_compilerMatch = detectNearbyCompiler(sourceFile)
        val n_compilerFile = n_compilerMatch?.let { File(it.filePath) }
        if (n_compilerFile != null && n_compilerFile.exists() && n_compilerFile.isFile) {
            val n_compilerDir = n_compilerFile.parentFile
            val n_baseDir = n_compilerDir?.absoluteFile ?: File(sourceFile).parentFile
            if (n_baseDir != null && n_baseDir.exists() && n_baseDir.isDirectory) {
                val n_includeCandidate = File(n_baseDir, "include")
                if (!n_includeCandidate.exists() || n_includeCandidate.isDirectory) {
                    n_result += n_includeCandidate.absolutePath
                }
            }
        }

        return n_result.filter { it.isNotBlank() }
    }

    /**
     * Reads metadata from a detected pawncc binary, including product version, size, and MD5 hash.
     *
     * @param file compiler executable file
     * @return extracted metadata wrapper
     */
    private fun readCompilerMetadata(file: File): CompilerMetadata {
        val n_bytes = runCatching { file.readBytes() }.getOrElse { byteArrayOf() }
        val n_utf16Text = runCatching { String(n_bytes, Charsets.UTF_16LE) }.getOrElse { "" }
        val n_fallbackText = runCatching { String(n_bytes, Charsets.ISO_8859_1) }.getOrElse { "" }
        val n_productVersion = PRODUCT_VERSION_REGEX.find(n_utf16Text)?.value
            ?: PRODUCT_VERSION_REGEX.find(n_fallbackText)?.value
        val n_sizeBytes = file.length()
        val n_md5 = try {
            val n_digest = java.security.MessageDigest.getInstance("MD5")
            val n_hash = n_digest.digest(n_bytes)
            n_hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }

        val n_version = CompilerConfig.CompilerVersion.entries.firstOrNull { it.matchesDetected(n_productVersion, n_sizeBytes, n_md5) }
            ?: CompilerConfig.CompilerVersion.entries.firstOrNull {
                it.nearestSupportedEquivalent(n_productVersion, n_sizeBytes, n_md5) != null
            }?.nearestSupportedEquivalent(n_productVersion, n_sizeBytes, n_md5)
        return CompilerMetadata(n_version, n_productVersion, n_sizeBytes, n_md5)
    }

    private data class CompilerMetadata(
        val version: CompilerConfig.CompilerVersion?,
        val productVersion: String?,
        val sizeBytes: Long,
        val md5: String?
    )

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
    ): Pair<Int, String> {
        val n_config = CompilerConfig.getInstanceOrNull()
        val n_isForced = n_config?.n_forced_compiler_mode == true

        val n_result = compileWithVersion(sourceFile, options, version)

        if (!n_isForced && !FALL_MUTATION && shouldRetryWithFallback(n_result.second)) {
            val n_fallbackVersion = version.other()
            FALL_MUTATION = true

            if (n_config != null) {
                Log.w(
                    "PawnCompiler",
                    "Detected ${extractErrorCount(n_result.second)} Errors in ${version.label}. " +
                    "Switching compiler to ${n_fallbackVersion.label} for the next retry."
                )
                n_config.n_compiler_version = n_fallbackVersion
            }

            val n_retryResult = compileWithVersion(sourceFile, options, n_fallbackVersion)
            if (n_retryResult.first >= 0 || n_retryResult.second.isNotBlank()) {
                return n_retryResult
            }
        }

        return n_result
    }

    /**
     * Runs the selected compiler version with the provided arguments.
     *
     * @param sourceFile Pawn source file path
     * @param options compiler options list
     * @param version compiler version to use
     * @return exit code and captured output text
     */
    private fun compileWithVersion(
        sourceFile: String,
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): Pair<Int, String> {
        if (!ensureInitialized(version)) {
            return -1 to "Failed to load compiler library"
        }

        Log.d("PawnCompiler", "Compiling with: ${INITIALIZED_VER?.label}")

        val n_logFile = runCatching {
            File(sourceFile).let { n_file ->
                val n_baseName = n_file.nameWithoutExtension
                File(n_file.parent, "$n_baseName.log")
            }
        }.getOrNull()

        n_logFile?.takeIf { it.exists() }?.runCatching { delete() }
            ?.onFailure { Log.e("PawnCompiler", "Failed to delete old log file: ${n_logFile.absolutePath}", it) }

        val n_args = buildList {
            add("pawncc")
            addAll(options)
            add(sourceFile)
        }

        val n_output = compile(n_args.toTypedArray())
        val n_parsedResult = parseCompilerOutput(n_output)

        n_logFile?.runCatching { writeText(n_parsedResult.second) }
            ?.onFailure { Log.e("PawnCompiler", "Failed to write log file: ${n_logFile.absolutePath}", it) }

        return n_parsedResult
    }

    /**
     * Parses the compiler exit code and the text output produced by the native runner.
     *
     * @param output raw compiler output text
     * @return exit code and trimmed human readable output
     */
    private fun parseCompilerOutput(output: String): Pair<Int, String> {
        val n_match = EXIT_CODE_REGEX.find(output)

        val n_exitCode = n_match?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val n_actualOutput = output.substringAfter('\n', "")

        return n_exitCode to n_actualOutput
    }

    /**
     * Returns the last captured compiler output from the native layer.
     *
     * @return output string or empty when not initialized
     */
    fun getCapturedOutput(): String = if (IS_INITIALIZED) getOutput() else ""

    /**
     * Returns the last captured compiler errors from the native layer.
     *
     * @return error string or empty when not initialized
     */
    fun getCapturedErrors(): String = if (IS_INITIALIZED) getErrors() else ""

    private external fun compile(args: Array<String>): String
    private external fun getOutput(): String
    private external fun getErrors(): String
}
