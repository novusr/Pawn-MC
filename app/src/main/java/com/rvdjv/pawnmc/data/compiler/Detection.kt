package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.compiler.Names.COMPILER_BINARY_NAMES
import com.rvdjv.pawnmc.data.compiler.Names.INCLUDE_PATH_VARIANTS
import com.rvdjv.pawnmc.data.compiler.Names.STR_INCLUDE_DIR_NAME
import com.rvdjv.pawnmc.data.compiler.Names.STR_PAWNO_DIR_NAME
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

/**
 * Discovery of a native Pawn compiler next to the opened source.
 */
object Detection {

    private val PRODUCT_VERSION_REGEX = """\b\d+\.\d+\.\d+\b""".toRegex()

    data class NearbyCompilerMatch(
        val version: CompilerConfig.CompilerVersion,
        val filePath: String,
        val productVersion: String?,
        val sizeBytes: Long,
        val md5: String?
    )

    private data class CompilerMetadata(
        val version: CompilerConfig.CompilerVersion?,
        val productVersion: String?,
        val sizeBytes: Long,
        val md5: String?
    )

    /** Nearby compiler version mapped to a supported PawnMC version, or null. */
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

    /** Best nearby compiler match for [sourceFile], or null. */
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

    /** Selects one existing conventional include folder, or the stable `include/` fallback. */
    fun discoverRelevantIncludePaths(sourceFile: String, knownPaths: List<String> = emptyList()): List<String> {
        val known = knownPaths.asSequence()
            .map { CompilerConfig.includePathKey(it) }
            .filter { it.isNotBlank() && it != "/" }
            .toSet()

        val sourceDir = File(sourceFile).absoluteFile.parentFile ?: return emptyList()
        val projectRoot = if (sourceDir.name.equals("gamemodes", ignoreCase = true)) {
            sourceDir.parentFile ?: sourceDir
        } else {
            sourceDir
        }

        val candidateRoots = linkedSetOf(projectRoot, sourceDir)
        sourceDir.parentFile?.let { candidateRoots += it }
        detectNearbyCompiler(sourceFile)?.filePath?.let { compilerPath ->
            File(compilerPath).parentFile?.let { compilerDir ->
                candidateRoots += if (compilerDir.name.equals(STR_PAWNO_DIR_NAME, ignoreCase = true)) {
                    compilerDir.parentFile ?: compilerDir
                } else {
                    compilerDir
                }
            }
        }

        val selectedInclude = INCLUDE_PATH_VARIANTS.asSequence()
            .flatMap { variant -> candidateRoots.asSequence().map { root -> File(root, variant) } }
            .firstOrNull { candidate -> runCatching { candidate.isDirectory }.getOrDefault(false) }
            ?: File(projectRoot, STR_INCLUDE_DIR_NAME)

        val normalizedPath = CompilerConfig.normalPath(selectedInclude.absolutePath)
        val key = CompilerConfig.includePathKey(normalizedPath)
        return if (key.isBlank() || key == "/" || key in known) emptyList() else listOf(normalizedPath)
    }

    /** Reads product version, size and MD5 from a detected pawncc binary. */
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
}