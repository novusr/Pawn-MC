package com.rvdjv.pawnmc.data.compiler

import android.util.Log
import com.rvdjv.pawnmc.data.compiler.Names.CONVERTIBLE_EXTENSIONS
import com.rvdjv.pawnmc.data.compiler.Names.BACKUP_DIR_SUFFIX
import java.io.File

/**
 * "Ignore case" filesystem conversion of a project folder.
 *
 * Backs the folder up as `<folder>.backup`, lowercases every entry name and
 * rewrites every static `#include` reference so a case sensitive filesystem
 * resolves the same names the NTFS-authored scripts used.
 */
object CaseConversion {

    private val INCLUDE_DIRECTIVE_REGEX =
        Regex("""(?i)#include\s*(?:\"([^\"]+)\"|'([^']+)'|<([^>]+)>)""")

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
            val n_original_value = match.groupValues[1]
                .ifBlank { match.groupValues[2] }
                .ifBlank { match.groupValues[3] }
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

    /** Replaces the backup with a snapshot of the current working folder. */
    fun refreshBackupForCompile(workingDir: File): Boolean {
        if (!workingDir.isDirectory) return false
        val backup = backupDirFor(workingDir)
        val parent = backup.parentFile ?: return false
        val suffix = System.nanoTime().toString()
        val staging = File(parent, ".${backup.name}.staging-$suffix")
        val previous = File(parent, ".${backup.name}.previous-$suffix")

        return try {
            copyDirectoryRecursively(workingDir, staging)
            if (backup.exists() && !backup.renameTo(previous)) {
                staging.deleteRecursively()
                return false
            }
            if (!staging.renameTo(backup)) {
                previous.renameTo(backup)
                staging.deleteRecursively()
                return false
            }
            previous.deleteRecursively()
            true
        } catch (e: Exception) {
            staging.deleteRecursively()
            Log.e("Compiler", "Could not refresh backup for ${workingDir.absolutePath}", e)
            false
        }
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
            Log.e("Compiler", "Ignore-case conversion failed for ${n_working.absolutePath}", e)
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
}