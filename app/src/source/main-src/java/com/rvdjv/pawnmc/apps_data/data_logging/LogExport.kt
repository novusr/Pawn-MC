package com.rvdjv.pawnmc.apps_data.data_logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds the enriched compile log and manages device-safe clipboard copies.
 *
 * A downloaded (or copied) log starts with a header block — timestamp, compiler
 * version, app version, device info, compile info — and then the raw compiler
 * output follows. The on-screen log in the main interface is untouched: only
 * exports see the header.
 */
object _log_export {

    /** Line window the clipboard is filled in, so no device truncates silently. */
    private const val CLIP_CHUNK_LINES = 100

    data class HeaderInfo(
        val compilerVersion: String?,
        val appVersion: String?,
        val sourcePath: String?,
        val exitCode: Int?,
        val durationLabel: String?,
        val lineCount: Int? = null
    )

    /** Formats the header block shown at the very top of a `.log` export. */
    fun buildHeader(info: HeaderInfo, timestamp: Long = System.currentTimeMillis()): String = buildString {
        val stamp = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
        appendLine("[LOGGING $stamp]")
        appendLine("Compiler Version: ${info.compilerVersion ?: "unknown"}")
        appendLine("Apps Version: ${info.appVersion ?: "unknown"}")
        appendLine("Device Info:")
        appendLine("    Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
        appendLine("    Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("    ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("File Compilation Info:")
        appendLine("    Source: ${info.sourcePath?.substringAfterLast('/') ?: "unknown"}")
        appendLine("    Exit code: ${info.exitCode?.toString() ?: "unknown"}")
        appendLine("    Compilation time: ${info.durationLabel ?: "unknown"}")
        appendLine("File Log Info:")
        appendLine("    Lines: ${info.lineCount ?: "-"}")
        appendLine("    Exported: $stamp")
        appendLine("    Format: .log (PawnMC compile log)")
        appendLine()
    }

    /** Loads the most recent compile row so its metadata can feed the header. */
    fun latestEntry(context: Context): _compiler_store.Entry? =
        _compiler_store.recentEntries(context, 1).firstOrNull()

    /**
     * Copies [text] to the clipboard in bounded chunks.
     *
     * Devices clip very long strings silently; splitting the copy into windows of
     * [CLIP_CHUNK_LINES] lines and appending each one keeps the full log intact in
     * the clipboard without relying on an OS-level maximum.
     */
    fun copyChunked(context: Context, text: String, label: String = "PawnMC Compilation Result") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val lines = text.lines()
        if (lines.size <= CLIP_CHUNK_LINES) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            return
        }
        // One ClipData item per window: a single ClipData carrying every chunk
        // stays under the device cap while preserving the order.
        val clip = ClipData.newPlainText(label, "")
        var index = 0
        while (index < lines.size) {
            val window = lines.subList(index, minOf(index + CLIP_CHUNK_LINES, lines.size))
            clip.addItem(ClipData.Item(window.joinToString("\n")))
            index += CLIP_CHUNK_LINES
        }
        clipboard.setPrimaryClip(clip)
    }

    /** Writes the enriched `.log` file into the app's logs folder; returns the file. */
    fun writeLogFile(
        context: Context,
        outputText: String,
        info: HeaderInfo
    ): File? {
        val latest = latestEntry(context)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val base = info.sourcePath?.substringAfterLast('/')?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() } ?: "pawnmc"
        val file = File(AppStorage.logsRoot(context), "${base}_$stamp.log")
        return runCatching {
            file.parentFile?.mkdirs()
            val header = latest?.let {
                buildHeader(
                    HeaderInfo(
                        compilerVersion = info.compilerVersion ?: it.compilerVersion,
                        appVersion = info.appVersion,
                        sourcePath = info.sourcePath ?: it.sourcePath,
                        exitCode = info.exitCode ?: it.exitCode,
                        durationLabel = info.durationLabel ?: it.durationLabel(),
                        lineCount = info.lineCount ?: outputText.lines().size
                    ),
                    timestamp = it.timestamp
                )
            } ?: buildHeader(info.copy(lineCount = info.lineCount ?: outputText.lines().size))
            file.writeText(header + outputText)
            file
        }.getOrNull()
    }
}

private fun _compiler_store.Entry.durationLabel(): String = when {
    durationMs >= 1000 -> String.format(Locale.getDefault(), "%.2f seconds", durationMs / 1000.0)
    else -> "$durationMs ms"
}
