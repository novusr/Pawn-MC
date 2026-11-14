package com.pocketsamp.server

import android.content.Context
import android.util.Log
import com.pocketsamp.server.crash_handler.Report
import com.pocketsamp.server.crash_handler.Reporter
import com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal
import com.rvdjv.pawnmc.apps_data.data_crash.CrashHandler
import java.io.File
import java.io.PrintWriter

/**
 * Routes PocketSAMP's crash reports into PawnMC's crash folder.
 *
 * The runtime keeps its own uncaught-exception handler (it owns the `:crash` dialog and
 * the restart flow), so it cannot simply be replaced. What it lacked was a *file* that
 * another tool could read: its reports went to `files/crash_reports/crash_*.txt`, an
 * app-private directory invisible to the user and to every dump viewer.
 *
 * With this reporter installed, a crash raised anywhere in the process - PocketSAMP
 * screens, the server service, the Xed editor - is written by [CrashHandler] as
 * `Documents/PawnMC/crashreport/crash_<time>_<id>.dmp` plus the matching text report.
 * The handler is process-wide, so the same file also records which module was in front
 * (`source`).
 *
 * Lives in the `newer` source set on purpose: it names PocketSAMP types, and only the
 * flavor that links the runtime may see them. It is registered from
 * [registerNewerPocketSampHook], which the shared gateway resolves reflectively.
 */
internal object PocketSampCrashBridge : Reporter {

    private const val TAG = "PocketSampCrash"

    /** Folder of the last reports, resolved from the application context at install. */
    @Volatile
    private var folder: File? = null

    /** Called once from the hook so the history list knows where to look. */
    fun attach(context: Context) {
        folder = ActivityJournal.crashReportFolder(context)
    }

    override fun write(context: Context, report: Report): Map<String, File>? {
        // The writer needs the live thread object to name the crashing thread and to
        // snapshot every other one; PocketSAMP calls this from inside its own handler,
        // which is itself running on that thread.
        val captured = CrashReportException(report)
        val written = runCatching {
            CrashHandler.record(
                context = context,
                thread = Thread.currentThread(),
                throwable = captured,
                source = "pocketsamp",
                reason = "uncaught exception in [${report.threadName}]",
                deviceInfo = captured.deviceInfo()
            )
        }.onFailure { Log.e(TAG, "shared crash writer failed", it) }.getOrNull()
            ?: return null

        // The folder can become available after install (storage permission granted
        // later), so it is refreshed here rather than trusted from startup. Only a public
        // report counts: a private fallback must not become the history's source, or the
        // folder would keep pointing at the copy this list is meant to replace.
        if (written.dumpIsPublic) {
            folder = written.textReport?.parentFile ?: written.dump?.parentFile ?: folder
        }
        ActivityJournal.record(context, "crash", "pocketsamp report ${report.id} -> ${written.dump?.name}")

        return buildMap {
            written.textReport?.let { put("report", it) }
            written.dump?.let { put("dump", it) }
        }
    }

    override fun listReportFiles(): List<File> = CrashHandler.listReportFiles(folder)

    override fun listDumpFiles(): List<File> = CrashHandler.listDumpFiles(folder)
}

/**
 * Carries a captured [Report] into the shared writer.
 *
 * The original throwable is already gone by the time PocketSAMP's handler formats its
 * report, so the stack trace travels as text; this exception only has to present that
 * text and the class name to [CrashHandler], which is what the dump's comment block and
 * the text report quote verbatim.
 */
internal class CrashReportException(report: Report) :
    Throwable("${report.exceptionClass}: ${report.message ?: "(no message)"}") {

    private val captured = report

    /** The trace already exists as text; there is no live frame to attach. */
    override val stackTrace: Array<StackTraceElement>
        get() = emptyArray()

    override fun printStackTrace(writer: PrintWriter) {
        writer.print(captured.stackTrace)
    }

    /** Skips the ART-side frame capture: the report text is the trace. */
    override fun fillInStackTrace(): Throwable = this

    /** The device line the runtime captured, quoted verbatim in the dump comment. */
    fun deviceInfo(): String = buildString {
        appendLine(captured.deviceInfo)
        appendLine("Reported at    : ${captured.formattedDate}")
        append("Runtime thread : ${captured.threadName}")
    }
}
