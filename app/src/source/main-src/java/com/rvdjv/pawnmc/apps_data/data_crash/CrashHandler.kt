package com.rvdjv.pawnmc.apps_data.data_crash

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import com.rvdjv.pawnmc.apps_data.data_logging._pocket_store
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The one crash handler for the whole PawnMC process.
 *
 * Installed from [com.rvdjv.pawnmc.PawnMCApplication] so it exists before *any* screen,
 * compiler job or embedded PocketSAMP runtime runs. When anything throws on any thread:
 *
 * 1. the exception is logged, so `logcat` keeps working as before;
 * 2. the activity journal is flushed (it is otherwise written on a background thread);
 * 3. a `.dmp` is written to `Documents/PawnMC/crashreport/` together with a matching
 *    `crash_….txt` report - the text file is what the in-app crash screen reads;
 * 4. the process dies.
 *
 * PocketSAMP's own handler (upstream code in its `crash_handler` package) still exists
 * and still owns the runtime UI. It delegates the *file* side to [record], so a crash
 * raised from the PocketSAMP screens lands in the same folder, with the same header
 * format, instead of a private per-module directory nobody can find.
 *
 * `source` distinguishes the origin (`pawnmc`, `pocketsamp`, …) in both the file name and
 * the dump comment, so a report can be traced back to the module that failed.
 */
object CrashHandler {

    private const val TAG = "PawnMCcrash"

    /** Default source tag when a caller does not name its module. */
    const val SOURCE_PAWNMC = "pawnmc"

    /** Delimiter of the text report, kept stable so old reports still parse. */
    private const val DELIMITER = "===PAWNMC_CRASH_FIELD==="

    @Volatile
    private var installed = false

    /** Application context captured at install, for helpers that take no context. */
    @Volatile
    private var appContext: Context? = null

    /** Time the process started, for the uptime line in the dump. */
    @Volatile
    var processStartedAt: Long = System.currentTimeMillis()

    /**
     * Installs the handler once. Safe to call from every entry point; the second call is
     * a no-op, which matters because the embedded runtime also tries to install its own.
     */
    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val hostContext = context.applicationContext
            appContext = hostContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                handle(hostContext, thread, throwable, SOURCE_PAWNMC, chainTo = previous)
            }
            installed = true
            Log.i(TAG, "Crash handler installed")
        }
    }

    /**
     * Handles one fatal throwable and returns where the report landed.
     *
     * Called by [install] and by the PocketSAMP adapter. Never throws itself: everything
     * is wrapped, because a throw here loses the only record of the original failure.
     */
    fun handle(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        source: String = SOURCE_PAWNMC,
        chainTo: Thread.UncaughtExceptionHandler? = null
    ): Record? {
        var record: Record? = null
        try {
            ActivityJournal.record(context, "crash", "$source ${throwable.javaClass.simpleName}: ${throwable.message}")
            ActivityJournal.flushAndAwait()
            record = record(context, thread, throwable, source)
        } catch (error: Throwable) {
            Log.e(TAG, "Crash handler failed while writing the report", error)
        } finally {
            // The previous handler is the one that prints to logcat and, in upstream
            // PocketSAMP, opens its own crash dialog, so it still runs.
            chainTo?.uncaughtException(thread, throwable)
        }
        return record
    }

    /** Writes the dump and the paired text report. Returns what was written. */
    fun record(
        context: Context,
        thread: Thread,
        throwable: Throwable,
        source: String = SOURCE_PAWNMC,
        reason: String = "uncaught exception",
        deviceInfo: String? = null
    ): Record {
        val timestamp = System.currentTimeMillis()
        val crashId = UUID.randomUUID().toString().substring(0, 8)
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()
        val deviceInfoText = deviceInfo ?: deviceInfo(context)
        val journalTail = ActivityJournal.tail(context)
        val logTail = runCatching { _pocket_store.recentEntries(context, 120) }
            .getOrElse { emptyList() }
            .joinToString("\n") { entry -> formatLogEntry(entry) }

        val dump = Minidump.write(
            context,
            Minidump.Input(
                crashId = crashId,
                timestamp = timestamp,
                source = source,
                threadName = thread.name,
                exceptionClass = throwable.javaClass.name,
                exceptionMessage = throwable.message,
                stackTrace = stackTrace,
                deviceInfo = deviceInfoText,
                journalTail = journalTail,
                logTail = logTail,
                threads = Minidump.snapshotThreads(thread),
                modules = Minidump.readModules(),
                processUptimeMs = (timestamp - processStartedAt).coerceAtLeast(0),
                reason = reason
            )
        )

        val textReport = writeTextReport(
            context = context,
            crashId = crashId,
            timestamp = timestamp,
            source = source,
            thread = thread,
            throwable = throwable,
            deviceInfo = deviceInfoText,
            stackTrace = stackTrace,
            dumpPath = dump?.file?.absolutePath,
            dumpIsPublic = dump?.isPublic == true,
            reason = reason
        )

        Log.e(
            TAG,
            "Crash $crashId from $source on [${thread.name}]: ${throwable.javaClass.name}. " +
                "Dump: ${dump?.file?.absolutePath ?: "not written"}"
        )
        // Recorded itself, so the next crash's journal explains that this one happened
        // even when the report screen was never opened.
        ActivityJournal.record(context, "crash", "report $crashId written source=$source")

        return Record(crashId, textReport, dump?.file, dump?.isPublic == true)
    }

    /** Paths of one written crash, for callers that want to surface them. */
    data class Record(
        val crashId: String,
        val textReport: File?,
        val dump: File?,
        val dumpIsPublic: Boolean
    )

    // ---------------------------------------------------------------------
    // Text report
    // ---------------------------------------------------------------------

    /**
     * The `crash_<time>_<id>.txt` file.
     *
     * Same field order and delimiter as before this change, so reports written by older
     * builds stay readable, plus an eight field with the dump location.
     */
    private fun writeTextReport(
        context: Context,
        crashId: String,
        timestamp: Long,
        source: String,
        thread: Thread,
        throwable: Throwable,
        deviceInfo: String,
        stackTrace: String,
        dumpPath: String?,
        dumpIsPublic: Boolean,
        reason: String
    ): File? = runCatching {
        val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
        val header = buildString {
            append("Source: $source\nReason: $reason\n")
            if (dumpPath != null) {
                append("Dump: $dumpPath\n")
                append("DumpStorage: ${if (dumpIsPublic) "public" else "private"}\n")
            } else {
                append("Dump: (not written)\n")
            }
            append("Journal: ${ActivityJournal.journalLocation(context)}\n")
        }
        val payload = buildString {
            append(crashId).append("\n").append(DELIMITER).append("\n")
            append(timestamp).append("\n").append(DELIMITER).append("\n")
            append(formattedDate).append("\n").append(DELIMITER).append("\n")
            append(throwable.javaClass.name).append("\n").append(DELIMITER).append("\n")
            append(throwable.message ?: "No message provided").append("\n").append(DELIMITER).append("\n")
            append(thread.name).append("\n").append(DELIMITER).append("\n")
            append(deviceInfo).append(DELIMITER).append("\n")
            append(stackTrace).append(DELIMITER).append("\n")
            append(header)
        }
        val name = "crash_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(timestamp))}_$crashId.txt"
        val root = ActivityJournal.crashReportFolder(context)
            ?: File(context.filesDir, "crashreport").apply { mkdirs() }
        File(root, name).apply { writeText(payload) }
    }.onFailure { Log.e(TAG, "text report could not be written", it) }.getOrNull()

    /**
     * Crash reports (`.txt`) in the shared folder, newest first.
     *
     * Used by the PocketSAMP crash history, which has no context of its own to resolve
     * the folder: the bridge hands it this list.
     */
    fun listReportFiles(folder: File? = null): List<File> = listCrashFiles(folder, ".txt")

    /** Minidumps in the shared folder; paired with text reports by crash id. */
    fun listDumpFiles(folder: File? = null): List<File> = listCrashFiles(folder, ".dmp")

    private fun listCrashFiles(folder: File?, suffix: String): List<File> {
        val resolved = folder ?: runCatching {
            appContext?.get()?.let { ActivityJournal.crashReportFolder(it) }
        }.getOrNull() ?: return emptyList()
        return resolved.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(suffix)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Parses the original text report back, so the in-app screens keep working.
     *
     * Eight fields is the current format; seven is the pre-dump one. Both are accepted
     * because a phone upgrading mid-crash can still hold old reports in its folder.
     */
    fun parseTextReport(file: File): ParsedReport? = runCatching {
        val parts = file.readText().split("\n$DELIMITER\n")
        if (parts.size < 7) return@runCatching null
        // The dump location lives in the trailing "extra" block, when the report has
        // one; older reports end at the stack trace.
        val dumpLine = parts.drop(7)
            ?.lineSequence()
            ?.firstOrNull { it.startsWith("Dump: ") }
            ?.removePrefix("Dump: ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it != "(not written)" }
        ParsedReport(
            id = parts[0].trim(),
            timestamp = parts[1].trim().toLongOrNull() ?: file.lastModified(),
            formattedDate = parts[2].trim(),
            exceptionClass = parts[3].trim(),
            message = parts[4].trim().takeIf { it != "No message provided" },
            threadName = parts[5].trim(),
            deviceInfo = parts[6].trim(),
            stackTrace = parts[7].trim(),
            dumpPath = dumpLine
        )
    }.onFailure { Log.w(TAG, "unreadable crash report ${file.name}", it) }.getOrNull()

    /** One parsed text report, with the dump it points at (if any). */
    data class ParsedReport(
        val id: String,
        val timestamp: Long,
        val formattedDate: String,
        val exceptionClass: String,
        val message: String?,
        val threadName: String,
        val deviceInfo: String,
        val stackTrace: String,
        val dumpPath: String?
    )

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /** Device line shown in the report; the same fields the compiler log header uses. */
    fun deviceInfo(context: Context): String = buildString {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 0L
        }
        appendLine("App Version      : $versionName (Build $versionCode)")
        appendLine("Device Model     : ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
        appendLine("Android OS       : Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Build ID         : ${Build.DISPLAY}")
        appendLine("Supported ABIs   : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine("Process          : ${Process.myPid()} (${currentProcessName(context)})")
        append("Storage Root     : ${runCatching { AppStorage.dataRoot(context).absolutePath }.getOrDefault("unavailable")}")
    }

    private fun currentProcessName(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            File("/proc/self/cmdline").readText().trimEnd('\u0000')
        }
    }.getOrDefault("unknown")

    private fun formatLogEntry(entry: _pocket_store.Entry): String {
        val stamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestamp))
        return "$stamp [${entry.level}] ${entry.message}"
    }

    /** Leaves the process, after the report is on disk. */
    fun terminate() {
        Process.killProcess(Process.myPid())
        System.exit(10)
    }
}
