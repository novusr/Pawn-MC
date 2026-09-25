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
    private val EXIT_CODE_REGEX = """^Exit code: (-?\d+)""".toRegex()
    private val ERROR_COUNT_REGEX = """(?i)(\d+)\s+errors?\.?""".toRegex()
    private val PRODUCT_VERSION_REGEX = """\b\d+\.\d+\.\d+\b""".toRegex()

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

    // Detect the compiler only when the nearby pawncc.exe metadata truly matches one of the known Pawn versions.
    fun detectCompilerVersionForFile(sourceFile: String): CompilerConfig.CompilerVersion? {
        val match = detectNearbyCompiler(sourceFile) ?: return null
        val config = CompilerConfig.getInstanceOrNull()
        if (config != null) {
            config.n_detected_compiler_product_version = match.productVersion
            config.n_detected_compiler_size_bytes = match.sizeBytes
            config.n_detected_compiler_md5 = match.md5
        }
        return match.version
    }

    // Find the nearest compiler executable around the selected file and return the matching version only if the EXE
    // metadata clearly matches a supported Pawn compiler. If it is missing or does not match, return null so the app
    // can safely keep the default compiler at 3.10.7 without forcing a wrong version.
    fun detectNearbyCompiler(sourceFile: String): NearbyCompilerMatch? {
        val file = File(sourceFile)
        if (!file.exists() || file.extension.isBlank()) return null

        val searchRoots = LinkedHashSet<File>()
        var current: File? = file.parentFile
        var depth = 0
        while (current != null && depth < 6) {
            searchRoots += current
            searchRoots += File(current, "pawno")
            searchRoots += File(current, "pawn")
            current = current.parentFile
            depth += 1
        }

        for (dir in searchRoots) {
            val candidates = listOf(
                File(dir, "pawncc.exe"),
                File(dir, "pawncc"),
                File(dir, "pawncc.exe"),
                File(dir, "pawno/pawncc.exe"),
                File(dir, "pawn/pawncc.exe")
            ).distinctBy { it.absolutePath }

            for (candidate in candidates) {
                if (!candidate.exists() || !candidate.isFile) continue
                val metadata = readCompilerMetadata(candidate)
                val version = metadata.version ?: continue
                return NearbyCompilerMatch(
                    version = version,
                    filePath = candidate.absolutePath,
                    productVersion = metadata.productVersion,
                    sizeBytes = metadata.sizeBytes,
                    md5 = metadata.md5
                )
            }
        }

        return null
    }

    // Build include paths from the selected source directory and the compiler directory. If the compiler is found at
    // .../something/pawncc.exe or .../something/pawno/pawncc.exe, we normalize it to the include folder by replacing
    // the executable path with an include folder, such as .../something/include or .../something/pawno/include.
    fun discoverRelevantIncludePaths(sourceFile: String): List<String> {
        val result = linkedSetOf<String>()

        val sourceDir = File(sourceFile).parentFile
        if (sourceDir != null && sourceDir.exists() && sourceDir.isDirectory) {
            result += sourceDir.absolutePath
        }

        val compilerMatch = detectNearbyCompiler(sourceFile)
        val compilerFile = compilerMatch?.let { File(it.filePath) }
        if (compilerFile != null && compilerFile.exists() && compilerFile.isFile) {
            val compilerDir = compilerFile.parentFile
            val baseDir = compilerDir?.absoluteFile ?: File(sourceFile).parentFile
            if (baseDir != null && baseDir.exists() && baseDir.isDirectory) {
                val includeCandidate = File(baseDir, "include")
                if (!includeCandidate.exists() || includeCandidate.isDirectory) {
                    result += includeCandidate.absolutePath
                }
            }
        }

        return result.filter { it.isNotBlank() }
    }

    private fun readCompilerMetadata(file: File): CompilerMetadata {
        val bytes = runCatching { file.readBytes() }.getOrElse { byteArrayOf() }
        val utf16Text = runCatching { String(bytes, Charsets.UTF_16LE) }.getOrElse { "" }
        val fallbackText = runCatching { String(bytes, Charsets.ISO_8859_1) }.getOrElse { "" }
        val productVersion = PRODUCT_VERSION_REGEX.find(utf16Text)?.value
            ?: PRODUCT_VERSION_REGEX.find(fallbackText)?.value
        val sizeBytes = file.length()
        val md5 = try {
            val digest = java.security.MessageDigest.getInstance("MD5")
            val hash = digest.digest(bytes)
            hash.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }

        val version = CompilerConfig.CompilerVersion.entries.firstOrNull { it.matchesDetected(productVersion, sizeBytes, md5) }
        return CompilerMetadata(version, productVersion, sizeBytes, md5)
    }

    private data class CompilerMetadata(
        val version: CompilerConfig.CompilerVersion?,
        val productVersion: String?,
        val sizeBytes: Long,
        val md5: String?
    )

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
