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
    private val INCLUDE_DIRECTIVE_REGEX = Regex("""(?i)#include\s*(?:\"([^\"]+)\"|'([^']+)')""")
    private val COMPILER_MESSAGE_REGEX = Regex("""(?i)(warning|error|fatal)\s+(\d+)""")
    private val KNOWN_ERROR_EXPLANATIONS = mapOf(
        "017" to "Undefined symbol. Check that the identifier is declared, included, and spelled exactly the same as the definition.",
        "021" to "Symbol already defined. A duplicate declaration exists in the same scope.",
        "100" to "Cannot read from file. The compiler could not open the given source or include file. Check the path, permissions, and file existence.",
        "101" to "Cannot write to file. The output target could not be created or written to. Check permissions and the destination folder.",
        "217" to "A value is assigned but never used. Remove the unused assignment or use the variable before it goes out of scope.",
        "205" to "Redundant code: constant expression is zero. This condition is always false and makes the block unreachable.",
        "211" to "Possibly unintended assignment. An assignment appears in a condition; use == for comparison instead.",
        "015" to "Default case must be the last case. Place default after all explicit case blocks.",
        "016" to "Multiple defaults are not allowed in the same switch statement.",
        "024" to "Break or continue is out of context. It must be inside a loop or switch block.",
        "040" to "Duplicate case label. Each case value must be unique within the switch statement.",
        "061" to "Recursive include detected. Break the include cycle with guards or forward declarations.",
        "200" to "Identifier is truncated to the implementation limit. Use shorter, unique names to avoid collisions.",
        "203" to "A symbol is declared but never used. It may be dead code or an unfinished implementation.",
        "204" to "A value is assigned but never read. This often indicates redundant initialization or a logic bug.",
        "217" to "Loose indentation. This warning is cosmetic but can hide structural mistakes and make code harder to read.",
        "214" to "Literal array or string passed to a non-const parameter. Declare the parameter as const or copy the data to a mutable buffer."
    )

    data class PreparedWorkspace(
        val originalDir: File,
        val normalizedDir: File,
        val sourceFile: String,
        val originalSourceFile: String
    )

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
        val source = File(sourceFile)
        if (!source.exists() || source.extension.isBlank()) return null

        val sr_root = linkedSetOf<File>()
        var dir = source.parentFile
        var depth = 0
        while (dir != null && depth < 6) {
            sr_root += dir
            sr_root += File(dir, "pawno")
            sr_root += File(dir, "pawn")
            dir = dir.parentFile
            depth += 1
        }

        val compilerNames = listOf("pawncc.exe", "pawncc")

        for (root in sr_root) {
            val rootCandidates = listOf(
                root, File(root, "pawno"), File(root, "pawn")
            )

            val candidates = rootCandidates.flatMap { baseDir ->
                compilerNames.map { name -> File(baseDir, name) }
            }.distinctBy { it.absolutePath }

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

    /**
     * Builds include paths based on the selected file directory and the nearest detected compiler folder.
     *
     * @param sourceFile selected Pawn file path
     * @return include directories that should be added as -i values
     */
    fun discoverRelevantIncludePaths(sourceFile: String): List<String> {
        val n_result = linkedSetOf<String>()
        val n_include_variants = listOf("pawno/include",
                                        "qawno/include",
                                        "include",
                                        "includes",
                                        "gamemodes")
        val n_force_inc_auto = CompilerConfig.getInstanceOrNull()?.n_forced_include_path_auto == true

        val n_src_file = File(sourceFile)
        val n_src_dir = n_src_file.parentFile
        if (
            n_src_dir != null &&
            n_src_dir.exists() &&
            n_src_dir.isDirectory
        ) {
            n_include_variants.forEach { n_variant ->
                val n_candidate = File(n_src_dir, n_variant)
                if (n_candidate.exists() && n_candidate.isDirectory) {
                    n_result += CompilerConfig.normalizeIncludePath(n_candidate.absolutePath)
                }
            }

            val n_src_g_dir = File(n_src_dir, "gamemodes")
            if (n_src_g_dir.exists() || n_src_g_dir.parentFile != null) {
                n_result += CompilerConfig.normalizeIncludePath(n_src_g_dir.absolutePath)
            }
        }

        if (!n_force_inc_auto) {
            val n_match = detectNearbyCompiler(sourceFile)
            val n_compiler_file = n_match?.let { File(it.filePath) }
            if (
                n_compiler_file != null &&
                n_compiler_file.exists() &&
                n_compiler_file.isFile
            ) {
                val n_compiler_dir = n_compiler_file.parentFile
                val n_base_dir = n_compiler_dir?.absoluteFile ?: n_src_dir
                if (
                    n_base_dir != null &&
                    n_base_dir.exists() &&
                    n_base_dir.isDirectory
                ) {
                    n_include_variants.forEach { n_variant ->
                        val n_candidate = File(n_base_dir, n_variant)
                        if (n_candidate.exists() && n_candidate.isDirectory) {
                            n_result += CompilerConfig.normalizeIncludePath(n_candidate.absolutePath)
                        } else if (n_candidate.parentFile != null && n_candidate.parentFile?.exists() == true &&
                            n_variant.endsWith("/include"))
                        {
                            n_result += CompilerConfig.normalizeIncludePath(n_candidate.absolutePath)
                        }
                    }
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

    fun normalizeCaseInsensitiveProject(originalDir: File): File {
        if (!originalDir.exists() || !originalDir.isDirectory) return originalDir

        val projectRoot = originalDir.parentFile ?: return originalDir
        val normalizedDir = File(projectRoot, "${originalDir.name}2")

        if (normalizedDir.absolutePath == originalDir.absolutePath) return originalDir
        if (normalizedDir.exists()) normalizedDir.deleteRecursively()

        copyDirectoryRecursively(originalDir, normalizedDir)
        normalizeProjectNames(normalizedDir)
        rewriteIncludesInProject(normalizedDir)

        return normalizedDir
    }

    fun prepareCaseInsensitiveWorkspace(sourceFilePath: String): PreparedWorkspace? {
        val n_src_file = File(sourceFilePath)
        if (!n_src_file.exists() || n_src_file.isDirectory) return null

        val n_original_dir = n_src_file.parentFile ?: return null
        val n_normalized_dir = normalizeCaseInsensitiveProject(n_original_dir)

        val n_final_src_file = n_normalized_dir.walkTopDown()
            .firstOrNull { it.isFile && it.name.equals(n_src_file.name, ignoreCase = true) }
            ?: File(n_normalized_dir, n_src_file.name.lowercase())

        return PreparedWorkspace(
            originalDir = n_original_dir,
            normalizedDir = n_normalized_dir,
            sourceFile = n_final_src_file.absolutePath,
            originalSourceFile = n_src_file.absolutePath
        )
    }

    fun finalizeCaseInsensitiveWorkspace(preparedWorkspace: PreparedWorkspace) {
        val originalDir = preparedWorkspace.originalDir
        val normalizedDir = preparedWorkspace.normalizedDir
        val backupDir = File(originalDir.parentFile, "${originalDir.name}.backup")

        if (backupDir.exists()) backupDir.deleteRecursively()
        if (originalDir.exists()) {
            originalDir.renameTo(backupDir)
        }
        if (normalizedDir.exists()) {
            normalizedDir.renameTo(originalDir)
        }
    }

    private fun copyDirectoryRecursively(source: File, target: File) {
        if (!source.exists()) return
        target.mkdirs()
        source.listFiles()?.forEach { child ->
            val destination = File(target, child.name)
            if (child.isDirectory) {
                copyDirectoryRecursively(child, destination)
            } else {
                child.copyTo(destination, overwrite = true)
            }
        }
    }

    private fun normalizeProjectNames(rootDir: File) {
        val filesToRename = rootDir.walkTopDown().filter { it.isFile }.toList()
        val seenNames = linkedSetOf<String>()

        filesToRename.forEach { file ->
            val lowerName = file.name.lowercase()
            if (lowerName in seenNames) {
                file.delete()
                return@forEach
            }
            seenNames += lowerName

            if (file.name != lowerName) {
                val renamedFile = File(file.parentFile, lowerName)
                if (renamedFile.exists() && renamedFile.absolutePath != file.absolutePath) {
                    renamedFile.delete()
                }
                file.renameTo(renamedFile)
            }
        }
    }

    private fun rewriteIncludesInProject(rootDir: File) {
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val n_ext = file.extension.lowercase()
            if (n_ext !in setOf("pawn", "pwn", "p", "inc")) return@forEach

            val n_content = file.readText()
            val n_rewritten = INCLUDE_DIRECTIVE_REGEX.replace(n_content) { match ->
                val n_original_value = match.groupValues[1].ifBlank { match.groupValues[2] }
                if (n_original_value.isBlank()) return@replace match.value

                val n_lowered = n_original_value.lowercase()
                val n_prefix = match.value.substringBefore(n_original_value)
                val n_suffix = match.value.substringAfterLast(n_original_value)
                "$n_prefix$n_lowered$n_suffix"
            }

            if (n_rewritten != n_content) {
                file.writeText(n_rewritten)
            }
        }
    }

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
                    "ok! found: ${extractErrorCount(n_result.second)}" +
                    " errors in ${version.label}. " +
                    "trying ${n_fallbackVersion.label} for the next retry."
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

    fun explainCompilerOutput(rawText: String, cacheDir: File): String {
        if (rawText.isBlank()) return rawText

        val outputCache = File(cacheDir, "compiler_output_cache_${System.nanoTime()}.log")
        outputCache.writeText(rawText)

        val builder = StringBuilder()
        outputCache.useLines { lines ->
            lines.forEach { line ->
                builder.appendLine(line)
                val match = COMPILER_MESSAGE_REGEX.find(line)
                if (match != null) {
                    val code = match.groupValues[2].trimStart('0')
                    val explanation = KNOWN_ERROR_EXPLANATIONS[code]
                        ?: KNOWN_ERROR_EXPLANATIONS[match.groupValues[2]]
                        ?: "No local explanation found for this compiler message. Check the Pawn error reference for details."
                    builder.appendLine("    [explain] $explanation")
                }
            }
        }

        outputCache.delete()
        return builder.toString()
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
