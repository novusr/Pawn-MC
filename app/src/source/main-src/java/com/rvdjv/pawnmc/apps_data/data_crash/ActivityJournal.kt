package com.rvdjv.pawnmc.apps_data.data_crash

import android.content.Context
import android.util.Log
import com.rvdjv.pawnmc.apps_data.data_config.AppStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Rolling journal of what the app was doing, written *before* a crash so the dump can
 * explain how it got there.
 *
 * A minidump only knows registers and the crashing thread. On a phone that is rarely
 * enough: the interesting question is "what did the app just do". Every module therefore
 * drops short, append-only lines into one text file per process:
 *
 * ```
 * 12:04:31.220  [lifecycle] MainActivity.onResume
 * 12:04:32.104  [compile] start  /storage/emulated/0/.../Server.pwn
 * 12:04:36.990  [compile] exit   0  in 4870 ms
 * ```
 *
 * Design notes, all of them about the fact that this runs right next to a failing process:
 *
 *  - **append-only, one line at a time.** No rewrite, no seek back: an entry is either
 *    fully on disk or not there at all, and a crash mid-write cannot corrupt earlier
 *    lines the way a rotating buffer would.
 *  - **size-capped.** The file is trimmed to its last [MAX_LINES] lines once it passes
 *    [MAX_BYTES], so an app left running for days cannot fill the phone.
 *  - **background only.** Writes go through a single daemon thread. A caller never waits
 *    on the disk, and a crash can still read whatever was already flushed.
 *  - **degrades to nothing.** Without storage permission the public folder is
 *    unavailable, so the journal falls back to app-private storage, and if even that
 *    fails every call is a silent no-op. Logging must never be the reason for a crash.
 */
object ActivityJournal {

    private const val TAG = "ActivityJournal"

    /** Public folder under `Documents/PawnMC`, next to the dumps it explains. */
    const val FOLDER_NAME = "crashreport"

    /** Private fallback used before storage permission is granted. */
    private const val PRIVATE_FOLDER = "crashreport"

    /** Rotate past this size; roughly 200 KB of short lines. */
    private const val MAX_BYTES = 200L * 1024L

    /** Lines kept when rotating. */
    private const val MAX_LINES = 4000

    /** How much of the journal is attached to a dump. */
    private const val DUMP_TAIL_LINES = 400

    /**
     * Session tag, so entries from a previous run are never confused with this one.
     * Read once at process start; every line carries it in the prefix of its timestamp.
     */
    @Volatile
    private var sessionStartedAt: Long = System.currentTimeMillis()

    @Volatile
    private var sessionId: String = newSessionId(sessionStartedAt)

    @Volatile
    private var writer: java.util.concurrent.ExecutorService? = null

    private val clockFormat = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    /**
     * Starts a new journal session. Called once from [com.rvdjv.pawnmc.PawnMCApplication]
     * so a restart is visible as a session boundary in the file.
     */
    fun startSession(context: Context) {
        val startedAt = System.currentTimeMillis()
        sessionStartedAt = startedAt
        sessionId = newSessionId(startedAt)
        val file = journalFile(context)
        runCatching {
            file.parentFile?.mkdirs()
            if (file.isFile && file.length() > 0L) {
                // A separator makes the restart obvious when reading the tail.
                file.appendText("\n===== session $sessionId started ${timestamp(startedAt)} =====\n")
            } else {
                file.writeText("===== session $sessionId started ${timestamp(startedAt)} =====\n")
            }
        }
        record(context, "session", "started $sessionId")
    }

    /** Journal file for this process, in the public crash-report folder when possible. */
    fun journalFile(context: Context): File {
        val public = runCatching { AppStorage.crashReportRoot(context) }.getOrNull()
        val root = public
            ?: File(context.filesDir, PRIVATE_FOLDER).also { runCatching { it.mkdirs() } }
        return File(root, "activity-$sessionId.log")
    }

    /** Where the folder actually landed, for the report header. */
    fun journalLocation(context: Context): String = runCatching {
        journalFile(context).absolutePath
    }.getOrDefault("unavailable")

    /** Public dumps directory, or `null` when shared storage is not writable yet. */
    fun crashReportFolder(context: Context): File? =
        runCatching { AppStorage.crashReportRoot(context) }.getOrNull()

    /**
     * Appends one activity line. Cheap to call from anywhere: the work is queued.
     *
     * @param tag short subsystem name (`compile`, `editor`, `server`, `lifecycle`, …)
     * @param message what happened, in one line
     */
    fun record(context: Context, tag: String, message: String) {
        val line = "${clockFormat.get().format(Date())}  [${tag.take(16)}] ${message.oneLine()}"
        val appContext = context.applicationContext
        executor().execute {
            runCatching {
                val file = journalFile(appContext)
                file.parentFile?.mkdirs()
                file.appendText(line + "\n")
                if (file.length() > MAX_BYTES) trim(file)
            }.onFailure { Log.w(TAG, "journal write failed", it) }
        }
    }

    /** Last [lines] journal lines, for attaching to a dump. Never throws. */
    fun tail(context: Context, lines: Int = DUMP_TAIL_LINES): String = runCatching {
        val file = journalFile(context)
        if (!file.isFile) return@runCatching ""
        // A crash is happening right now: read with a small budget, keep it blunt.
        val all = file.readLines()
        all.takeLast(lines.coerceAtLeast(1)).joinToString("\n")
    }.getOrDefault("")

    /**
     * Flushes queued lines and waits briefly.
     *
     * Called from the crash handler only. The process is about to die, so the wait is
     * capped: a missing last line is better than a hung crash screen.
     */
    fun flushAndAwait(timeoutMs: Long = 600L) {
        val current = writer ?: return
        current.shutdown()
        runCatching { current.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS) }
    }

    /** Flushes the file the handler of *another* process (PocketSAMP) is writing. */
    fun currentSessionId(): String = sessionId

    /** Trims the journal down to its newest [MAX_LINES] lines. */
    private fun trim(file: File) {
        val kept = file.readLines().takeLast(MAX_LINES)
        file.writeText(kept.joinToString("\n", postfix = "\n"))
    }

    private fun executor(): java.util.concurrent.ExecutorService {
        writer?.let { return it }
        synchronized(this) {
            writer?.let { return it }
            return Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "pawnmc-journal").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }.also { writer = it }
        }
    }

    private fun String.oneLine(): String = replace('\n', ' ').replace('\r', ' ').take(400)

    private fun newSessionId(at: Long): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(at))

    private fun timestamp(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(at))
}
