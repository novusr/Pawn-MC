package com.rvdjv.pawnmc.`interface`.editor.xedapi

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Copies every file of an opened workspace into the device's Downloads folder.
 *
 * The floating action exists because a workspace lives in a folder the user picked
 * once; getting the whole thing off the phone otherwise means opening a file manager
 * and hunting for it. The copy is flat-free: the workspace's internal layout is
 * preserved under a single top-level folder so relative `#include` paths still make
 * sense when the result is moved to a PC.
 *
 * Files are written through MediaStore on Android 10+, which needs no storage
 * permission; older releases fall back to the public Downloads directory directly.
 */
internal object BulkDownload {

    /** Upper bound on files per run; a tree walk over a huge folder would freeze the UI. */
    private const val MAX_FILES = 2000
    /** Result shown in a toast; [copied] is reported even on partial failure. */
    data class Result(val copied: Int, val failed: Int, val message: String)

    /**
     * @param onProgress receives one line per file, so the caller can log the run
     *                   somewhere visible without blocking the UI thread.
     */
    fun downloadAll(context: Context, onProgress: (String) -> Unit = {}): Result {
        val root = activeWorkspaceRoot?.let(::File)
            ?: return Result(0, 0, "Open a workspace first")

        // A tree walk over a huge folder would freeze the export and copy files the
        // user never opened in the editor. The cap keeps the action predictable and
        // reports the truncation instead of silently dropping files.
        val allFiles = root.walkTopDown()
            .filter { it.isFile }
            // Editor backups and the app's own scratch files are not workspace content.
            .filterNot { it.name.endsWith(".bak") || it.name.startsWith(".~") }
        val files = allFiles.take(MAX_FILES).toList()
        val truncated = files.size == MAX_FILES
        if (files.isEmpty()) {
            return Result(0, 0, "No files to download in ${folderLabel(root)}")
        }

        var copied = 0
        var failed = 0
        val folderName = sanitize(folderLabel(root))

        for (file in files) {
            val relative = file.relativeToOrNull(root)?.invariantSeparatorsPath ?: continue
            val ok = runCatching {
                writeToDownloads(context, folderName, relative, file)
            }.getOrElse { error ->
                onProgress("Failed: $relative (${error.message ?: "unknown error"})")
                false
            }
            if (ok) {
                copied++
                onProgress("Downloaded: $relative")
            } else {
                failed++
            }
        }

        val message = when {
            failed == 0 && truncated -> "Downloaded $copied files (first $MAX_FILES only)"
            failed == 0 -> "Downloaded $copied file${if (copied == 1) "" else "s"} to Downloads/$folderName"
            copied == 0 -> "Download failed for all $failed file${if (failed == 1) "" else "s"}"
            else -> "Downloaded $copied files, $failed failed"
        }
        return Result(copied, failed, message)
    }

    /** Folder name for messages; the filesystem root has an empty [File.getName]. */
    private fun folderLabel(root: File): String =
        root.name.ifBlank { root.absolutePath }

    /**
     * Absolute path of the workspace to copy, set by the editor screen before each run.
     *
     * A plain field rather than a parameter so the download action stays a simple
     * `() -> Unit` in the UI. A stale value cannot leak between screens because every
     * run overwrites it first.
     */
    var activeWorkspaceRoot: String? = null

    private fun writeToDownloads(
        context: Context,
        folderName: String,
        relative: String,
        source: File
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(source))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folderName/${relative.substringBeforeLast('/', "")}")
            }
            val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            return resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(source).use { input -> input.copyTo(out) }
                true
            } ?: false
        }

        @Suppress("DEPRECATION")
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val target = File(downloads, "$folderName/$relative")
        target.parentFile?.mkdirs()
        return runCatching {
            FileOutputStream(target).use { out ->
                FileInputStream(source).use { input -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    private fun mimeOf(file: File): String = when (file.extension.lowercase()) {
        "pwn", "pawn", "p", "inc" -> "text/plain"
        "amx" -> "application/octet-stream"
        "sql" -> "application/sql"
        "json" -> "application/json"
        "cfg", "txt", "md" -> "text/plain"
        else -> "application/octet-stream"
    }

    /** Keeps the folder name valid on FAT and on Windows when the result is moved. */
    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "workspace" }
}
