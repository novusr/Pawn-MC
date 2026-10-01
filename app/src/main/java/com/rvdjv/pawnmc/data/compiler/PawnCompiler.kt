package com.rvdjv.pawnmc.data.compiler

import android.util.Log
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * kotlin wrapper
 */
object PawnCompiler {

    private const val STR_PAWNO_DIR_NAME              = "pawno"

    private const val STR_PAWNCC_BINARY_NAME          = "pawncc"
    private const val STR_PAWNCC_FILE_NAME            = "pawncc.exe"

    private const val STR_MODES_DIR_NAME              = "gamemodes"
    private const val STR_INCLUDE_DIR_NAME            = "include"
    private const val STR_PATH_SEPARATOR              = "/"
    private const val STR_INCLUDE_PATH_SUFFIX         = "/include"
    
    private const val STR_PAWN_FILE_EXTENSION         = "pawn"
    private const val STR_PWN_FILE_EXTENSION          = "pwn"
    private const val STR_PAWN_SOURCE_SHORT_EXTENSION = "p"
    private const val STR_PAWN_INCLUDE_EXTENSION      = "inc"

    /** Suffix of the untouched copy created next to a converted folder. */
    private const val BACKUP_DIR_SUFFIX = ".backup"
    private const val SSCANF_NO_NICE_FEATURES_FLAG = "SSCANF_NO_NICE_FEATURES=1"

    private val INCLUDE_PATH_VARIANTS = listOf(
        "$STR_PAWNO_DIR_NAME/$STR_INCLUDE_DIR_NAME",
        STR_MODES_DIR_NAME
    )

    private val COMPILER_BINARY_NAMES = listOf(
        STR_PAWNCC_FILE_NAME,
        STR_PAWNCC_BINARY_NAME
    )

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

    /** Outcome of the "Ignore case" filesystem conversion for one folder. */
    enum class ConversionStatus {
        /** The folder was backed up and converted during this call. */
        CONVERTED,

        /** The backup folder already existed, so nothing was touched. */
        ALREADY_CONVERTED,

        /** Nothing had to be converted (empty folder, or no Pawn file in it). */
        NOT_NEEDED,

        /** The conversion failed; the original folder is left untouched. */
        FAILED
    }

    data class ConversionResult(
        val status: ConversionStatus,
        val workingDir: File,
        val backupDir: File,
        val sourceFile: String,
        val renamedEntries: Int = 0,
        val rewrittenIncludes: Int = 0
    )

    /**
     * Lowercases every `#include "..."` / `#include '...'` reference of a source.
     *
     * The conversion is unconditional: Android storage is case sensitive while the
     * NTFS-authored scripts are not, so `Aaaa` becomes `aaa` without exception.
     */
    fun lowercaseIncludeReferences(content: String): String =
        INCLUDE_DIRECTIVE_REGEX.replace(content) { match ->
            val n_original_value = match.groupValues[1].ifBlank { match.groupValues[2] }
            if (n_original_value.isBlank()) return@replace match.value
            val n_prefix = match.value.substringBefore(n_original_value)
            val n_suffix = match.value.substringAfterLast(n_original_value)
            "$n_prefix${n_original_value.lowercase()}$n_suffix"
        }

    /** Folder that receives the untouched copy of [workingDir]. */
    fun backupDirFor(workingDir: File): File {
        val n_parent = workingDir.parentFile ?: return workingDir
        return File(n_parent, "${workingDir.name}$BACKUP_DIR_SUFFIX")
    }

    /**
     * Decides whether the conversion has to run for [workingDir].
     *
     * Once `<folder>.backup` exists the folder has already been converted, so the
     * instructions are skipped even while "Ignore case" stays enabled. The key
     * stored in the configuration only has to agree with the filesystem; if the
     * folder was deleted the instructions run again.
     */
    fun needsConversion(workingDir: File, rememberedBackupDir: String? = null): Boolean {
        val n_backup = backupDirFor(workingDir)
        if (n_backup.isDirectory) return false
        // The stored key alone is not enough: the backup must still be on disk.
        if (!rememberedBackupDir.isNullOrBlank()) {
            val n_remembered = File(rememberedBackupDir)
            if (n_remembered.absolutePath != n_backup.absolutePath && n_remembered.isDirectory) return false
        }
        return hasConvertibleEntry(workingDir)
    }

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
    ): ConversionResult {
        val n_source = File(selectedFilePath)
        if (!n_source.isFile) {
            val n_fallbackDir = n_source.parentFile
            return ConversionResult(
                status = ConversionStatus.FAILED,
                workingDir = n_fallbackDir ?: File("."),
                backupDir = n_fallbackDir?.let { backupDirFor(it) } ?: File("."),
                sourceFile = selectedFilePath
            )
        }

        val n_working = n_source.parentFile
            ?: return ConversionResult(
                status = ConversionStatus.FAILED,
                workingDir = File("."),
                backupDir = File("."),
                sourceFile = selectedFilePath
            )
        val n_backup = backupDirFor(n_working)
        val n_sourceInWorking = findFileIgnoreCase(n_working, n_source.name) ?: n_source

        if (!needsConversion(n_working, rememberedBackupDir)) {
            val n_alreadyConverted = n_backup.isDirectory
            return ConversionResult(
                status = if (n_alreadyConverted) {
                    ConversionStatus.ALREADY_CONVERTED
                } else {
                    ConversionStatus.NOT_NEEDED
                },
                workingDir = n_working,
                backupDir = n_backup,
                sourceFile = n_sourceInWorking.absolutePath
            )
        }

        return try {
            copyDirectoryRecursively(n_working, n_backup)

            val n_renamed = lowercaseEntryNames(n_working, n_sourceInWorking.name)
            val n_rewritten = rewriteIncludesInProject(n_working)

            ConversionResult(
                status = ConversionStatus.CONVERTED,
                workingDir = n_working,
                backupDir = n_backup,
                sourceFile = findFileIgnoreCase(n_working, n_sourceInWorking.name)?.absolutePath
                    ?: File(n_working, n_sourceInWorking.name).absolutePath,
                renamedEntries = n_renamed,
                rewrittenIncludes = n_rewritten
            )
        } catch (e: Exception) {
            Log.e("PawnCompiler", "Ignore-case conversion failed for ${n_working.absolutePath}", e)
            ConversionResult(
                status = ConversionStatus.FAILED,
                workingDir = n_working,
                backupDir = n_backup,
                sourceFile = n_source.absolutePath
            )
        }
    }

    private fun hasConvertibleEntry(workingDir: File): Boolean =
        workingDir.listFiles()?.any { !it.isDirectory && isConvertibleSource(it) } == true

    private fun isConvertibleSource(file: File): Boolean =
        file.extension.lowercase() in CONVERTIBLE_EXTENSIONS

    /** Finds a file inside [dir] whose name matches [name] ignoring case. */
    fun findFileIgnoreCase(dir: File, name: String): File? =
        dir.walkTopDown().firstOrNull { it.isFile && it.name.equals(name, ignoreCase = true) }

    /**
     * Renames every entry of [rootDir] to lowercase, except the file called
     * [keepName], which is the file the user selected and must stay resolvable by
     * the exact name that is shown in the UI.
     */
    private fun lowercaseEntryNames(rootDir: File, keepName: String): Int {
        // Files first, then folders from the deepest level up, so a folder is only
        // renamed once its contents have been moved out of the way.
        val n_files = rootDir.walkTopDown().filter { it.isFile }.toList()
        val n_dirs = rootDir.walkTopDown().filter { it.isDirectory }
            .sortedByDescending { it.path.count { ch -> ch == File.separatorChar } }
            .toList()

        var n_renamed = 0
        n_files.forEach { file ->
            if (file.name.equals(keepName, ignoreCase = true)) return@forEach
            if (renameToLowercase(file)) n_renamed++
        }
        n_dirs.forEach { dir ->
            if (dir.absolutePath == rootDir.absolutePath) return@forEach
            if (renameToLowercase(dir)) n_renamed++
        }
        return n_renamed
    }

    /**
     * Renames a single entry to its lowercase form.
     *
     * The entry is first moved to a temporary name so a case-only rename cannot be
     * mistaken for a no-op, and a name that is already taken gains a numeric
     * suffix instead of overwriting the other entry.
     */
    private fun renameToLowercase(entry: File): Boolean {
        val n_lower = entry.name.lowercase()
        if (n_lower == entry.name) return false

        val n_parent = entry.parentFile ?: return false
        val n_temporary = File(n_parent, ".rename_${System.nanoTime()}_${entry.name.take(8)}")
        if (!entry.renameTo(n_temporary)) return false

        val n_target = uniqueTarget(n_parent, n_lower)
        if (!n_temporary.renameTo(n_target)) {
            // Put the original name back rather than losing the file.
            n_temporary.renameTo(entry)
            return false
        }
        return true
    }

    /** Appends ` (n)` before the extension until the name is free. */
    private fun uniqueTarget(parent: File, lowerName: String): File {
        var n_candidate = File(parent, lowerName)
        if (!n_candidate.exists()) return n_candidate

        val n_base = lowerName.substringBeforeLast('.', lowerName)
        val n_ext = lowerName.substringAfterLast('.', "")
        var n_index = 2
        while (n_candidate.exists()) {
            val n_name = if (n_ext.isEmpty()) "$n_base ($n_index)" else "$n_base ($n_index).$n_ext"
            n_candidate = File(parent, n_name)
            n_index++
        }
        return n_candidate
    }

    /** Lowercases the include references of every Pawn source inside [rootDir]. */
    private fun rewriteIncludesInProject(rootDir: File): Int {
        var n_rewrittenFiles = 0
        rootDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (!isConvertibleSource(file)) return@forEach

            val n_content = file.readText()
            val n_rewritten = lowercaseIncludeReferences(n_content)
            if (n_rewritten != n_content) {
                file.writeText(n_rewritten)
                n_rewrittenFiles++
            }
        }
        return n_rewrittenFiles
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

    /** Extensions whose static include references are rewritten. */
    private val CONVERTIBLE_EXTENSIONS = setOf(
        STR_PAWN_FILE_EXTENSION,
        STR_PWN_FILE_EXTENSION,
        STR_PAWN_SOURCE_SHORT_EXTENSION,
        STR_PAWN_INCLUDE_EXTENSION
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
            sr_root += File(dir, STR_PAWNO_DIR_NAME)
            dir = dir.parentFile
            depth += 1
        }

        val compilerNames = COMPILER_BINARY_NAMES

        for (root in sr_root) {
            val rootCandidates = listOf(
                root,
                File(root, STR_PAWNO_DIR_NAME)
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
     * Every candidate is compared against [knownPaths] first and dropped when that folder is
     * already registered, so re-discovering the same project does not repeat work the
     * configuration already holds. Only genuinely new folders are returned, which means
     * callers can append the result without running another filter pass.
     *
     * @param sourceFile selected Pawn file path
     * @param knownPaths include paths already registered, used to skip duplicates early
     * @return include directories that should be added as -i values
     */
    fun discoverRelevantIncludePaths(sourceFile: String, knownPaths: List<String> = emptyList()): List<String> {
        val result = linkedSetOf<String>()
        val resultKeys = mutableSetOf<String>()
        val known = knownPaths.asSequence()
            .map { CompilerConfig.includePathKey(it) }
            .filter { it.isNotBlank() && it != "/" }
            .toSet()

        fun offer(candidate: File) {
            val absolutePath = CompilerConfig.normalPath(candidate.absolutePath)
            if (absolutePath.isBlank()) return
            val key = CompilerConfig.includePathKey(absolutePath)
            if (key.isBlank() || key == "/") return
            if (key in known) return
            if (!resultKeys.add(key)) return
            result += absolutePath
        }

        val sourceDir = File(sourceFile).parentFile
        val baseDirs = linkedSetOf<File>()

        if (sourceDir != null && sourceDir.exists() && sourceDir.isDirectory) {
            baseDirs += sourceDir
            baseDirs += sourceDir.parentFile
        }

        val detectedCompiler = detectNearbyCompiler(sourceFile)
        detectedCompiler?.filePath?.let { compilerPath ->
            val compilerFile = File(compilerPath)
            compilerFile.parentFile?.let { baseDirs += it }
        }

        baseDirs.forEach { baseDir ->
            INCLUDE_PATH_VARIANTS.forEach { variant ->
                val candidate = File(baseDir, variant)
                offer(candidate)
            }
        }

        val sourceParent = sourceDir?.absoluteFile
        if (sourceParent != null) {
            val sourceGamemodes = File(sourceParent, STR_MODES_DIR_NAME)
            offer(sourceGamemodes)
        }

        val baseCandidate = sourceParent ?: File(System.getProperty("java.io.tmpdir"))
        offer(File(baseCandidate, "$STR_PAWNO_DIR_NAME/$STR_INCLUDE_DIR_NAME"))
        offer(File(baseCandidate, STR_MODES_DIR_NAME))

        // Discovery probes several conventional locations that are usually absent, so
        // keep only folders that really exist on disk.
        return result.filter { path ->
            runCatching { File(path).isDirectory }.getOrDefault(false)
        }
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

    internal fun compilerOptionsForVersion(
        options: List<String>,
        version: CompilerConfig.CompilerVersion
    ): List<String> = if (version == CompilerConfig.CompilerVersion.V3107) {
        options.filterNot { it == SSCANF_NO_NICE_FEATURES_FLAG } + SSCANF_NO_NICE_FEATURES_FLAG
    } else {
        options
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
            ?.onFailure { Log.e("PawnCompiler",
            "Failed to delete old log file: ${n_logFile.absolutePath}", it) }

        val n_args = buildList {
            add(STR_PAWNCC_BINARY_NAME)
            addAll(compilerOptionsForVersion(options, version))
            add(sourceFile)
        }

        val n_output = compile(n_args.toTypedArray())
        val n_parsedResult = parseCompilerOutput(n_output)

        n_logFile?.runCatching { writeText(n_parsedResult.second) }
            ?.onFailure { Log.e("PawnCompiler",
            "Failed to write log file: ${n_logFile.absolutePath}", it) }

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
                        ?: "No local explanation found for this compiler message. " +
                        "Check the Pawn error reference for details."
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
