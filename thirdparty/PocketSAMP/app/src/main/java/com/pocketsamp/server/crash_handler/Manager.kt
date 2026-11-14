package com.pocketsamp.server.crash_handler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object Manager {

    private const val TAG = "Manager"
    private const val DIR_NAME = "crash_reports"
    private const val DELIMITER = "===POCKETSAMP_CRASH_FIELD==="

    /**
     * Optional external writer, installed by the host application.
     *
     * Upstream PocketSAMP is a standalone app and owns its crash folder; embedded in
     * PawnMC it is one module of a bigger process, and a private `files/` directory is a
     * report no user or tool can find. When a reporter is installed it writes the dump
     * and the report into the host's public folder and the private copy is skipped.
     *
     * Deliberately a one-method seam rather than a dependency on the host: PocketSAMP
     * stays buildable (and its unit tests runnable) on its own.
     */
    interface Reporter {
        /**
         * Writes [report]; returns the files it produced keyed by kind, or `null` to
         * fall back to the built-in private storage.
         */
        fun write(context: Context, report: Report): Map<String, File>?

        /** Text reports in the host folder, for the history list. */
        fun listReportFiles(): List<File>

        /** Minidumps in the host folder, paired with reports by crash id. */
        fun listDumpFiles(): List<File>
    }

    @Volatile
    private var reporter: Reporter? = null

    /** Installs the host writer; idempotent, so the runtime can call it on every start. */
    fun installReporter(external: Reporter?) {
        reporter = external
    }

    private val _crashesFlow = MutableStateFlow<List<Report>>(emptyList())
    val crashesFlow: StateFlow<List<Report>> = _crashesFlow.asStateFlow()

    private fun getCrashDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveCrash(context: Context, report: Report): File {
        // Host first: when PawnMC supplied a writer, the `.dmp` and its text report land
        // in Documents/PawnMC/crashreport, where the user can reach them.
        val external = reporter?.write(context, report)?.get("report")
        if (external != null) {
            refreshCrashes(context)
            return external
        }
        return try {
            val dir = getCrashDir(context)
            val fileName = "crash_${report.timestamp}_${report.id}.txt"
            val file = File(dir, fileName)

            val payload = buildString {
                append(report.id).append("\n").append(DELIMITER).append("\n")
                append(report.timestamp).append("\n").append(DELIMITER).append("\n")
                append(report.formattedDate).append("\n").append(DELIMITER).append("\n")
                append(report.exceptionClass).append("\n").append(DELIMITER).append("\n")
                append(report.message ?: "No message provided").append("\n").append(DELIMITER).append("\n")
                append(report.threadName).append("\n").append(DELIMITER).append("\n")
                append(report.deviceInfo).append("\n").append(DELIMITER).append("\n")
                append(report.stackTrace)
            }

            file.writeText(payload)
            refreshCrashes(context)
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash report to disk", e)
            File(context.cacheDir, "crash_fallback.txt")
        }
    }

    /**
     * Every crash report this process can see, newest first.
     *
     * Two sources are merged: the host folder (`Documents/PawnMC/crashreport`, where the
     * dumps live) and the original private folder, so reports written before this change
     * - or before storage permission was granted - are still listed. A report present in
     * both is listed once.
     */
    fun getAllCrashes(context: Context): List<Report> {
        val files = buildList {
            addAll(publicReportFiles())
            val dir = getCrashDir(context)
            addAll(dir.listFiles { f -> f.isFile && f.name.startsWith("crash_") } ?: emptyArray())
        }.distinctBy { it.name }

        val reports = files.mapNotNull { file ->
            try {
                val content = file.readText()
                val parts = content.split("\n$DELIMITER\n")
                if (parts.size >= 8) {
                    Report(
                        id = parts[0].trim(),
                        timestamp = parts[1].trim().toLongOrNull() ?: file.lastModified(),
                        formattedDate = parts[2].trim(),
                        exceptionClass = parts[3].trim(),
                        message = parts[4].trim().takeIf { it != "No message provided" },
                        threadName = parts[5].trim(),
                        deviceInfo = parts[6].trim(),
                        stackTrace = parts[7].trim()
                    )
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Corrupt crash file: ${file.name}", e)
                null
            }
        }

        return reports.sortedByDescending { it.timestamp }
    }

    /**
     * Text reports in the host's public folder, read through the reporter seam where the
     * host exposes a lister; empty when the host has none (standalone PocketSAMP).
     */
    private fun publicReportFiles(): List<File> =
        runCatching { reporter?.listReportFiles() }.getOrNull() ?: emptyList()

    /**
     * The `.dmp` written next to [report], or `null` when the host did not write one.
     *
     * The pair shares its base name (`crash_<time>_<id>`), so the lookup is a directory
     * listing rather than a field stored in the report file - which keeps older reports
     * valid.
     */
    fun dumpFor(report: Report): File? = runCatching {
        // The host names the dump `crash_<yyyyMMdd_HHmmss>_<id>.dmp`; the text report
        // carries the same id, so matching on it finds the pair regardless of the stamp.
        reporter?.listDumpFiles()
            ?.firstOrNull { it.name.contains(report.id) && it.name.endsWith(".dmp") }
    }.getOrNull()

    fun refreshCrashes(context: Context) {
        _crashesFlow.value = getAllCrashes(context)
    }

    fun deleteCrash(context: Context, id: String): Boolean {
        val dir = getCrashDir(context)
        // Both locations, and both kinds: the private copy, the public report and the
        // public `.dmp` that belongs to the same crash id.
        val publicFiles = publicReportFiles() + runCatching { reporter?.listDumpFiles() }
            .getOrNull().orEmpty()
        val files = (dir.listFiles { f -> f.name.contains(id) }?.toList() ?: emptyList()) +
            publicFiles.filter { it.name.contains(id) }
        var deleted = false
        files.forEach {
            if (it.delete()) deleted = true
        }
        refreshCrashes(context)
        return deleted
    }

    fun clearAllCrashes(context: Context) {
        val dir = getCrashDir(context)
        dir.listFiles()?.forEach { it.delete() }
        runCatching { publicReportFiles().forEach { it.delete() } }
        runCatching { reporter?.listDumpFiles()?.forEach { it.delete() } }
        _crashesFlow.value = emptyList()
    }

    fun formatForClipboard(report: Report): String {
        return buildString {
            appendLine("=========================================")
            appendLine("           POCKETSAMP CRASH REPORT       ")
            appendLine("=========================================")
            appendLine("Report ID    : ${report.id}")
            appendLine("Date & Time  : ${report.formattedDate}")
            appendLine("Thread       : ${report.threadName}")
            appendLine("Exception    : ${report.exceptionClass}")
            appendLine("Message      : ${report.message ?: "(none)"}")
            appendLine("-----------------------------------------")
            appendLine("DEVICE INFORMATION:")
            appendLine(report.deviceInfo)
            appendLine("-----------------------------------------")
            appendLine("FULL STACK TRACE:")
            appendLine(report.stackTrace)
            appendLine("=========================================")
        }
    }
}
