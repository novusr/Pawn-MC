package com.rvdjv.pawnmc.apps_data.data_crash

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash writer producing a Windows-style `.dmp` (minidump) for every fatal error.
 *
 * ## Why a real minidump and not a renamed text file
 *
 * The extension means "open this with WinDbg / Visual Studio / `minidump_stackwalk`", so
 * the file has to be an actual `MDMP` container or those tools reject it. This writer
 * emits the subset of the format every reader supports:
 *
 *  - the 32-byte `MINIDUMP_HEADER` (`MDMP`, signature, stream count, stream directory);
 *  - `SystemInfoStream` - OS version, CPU architecture, page size, processor count;
 *  - `MiscInfoStream` - process id and how long the process had been up;
 *  - `ExceptionStream` - exception code for a Java VM failure;
 *  - `ThreadListStream` - every Java thread, with the crashing one listed first;
 *  - `ModuleListStream` - the native libraries actually mapped for this process;
 *  - `CommentStreamW` - the readable half of the report: device, threads, modules,
 *    stack trace, the activity journal tail and the log tail.
 *
 * Memory regions are deliberately not captured: the process is already dying and a full
 * Android heap dump is hundreds of megabytes. The address-space shape (threads, modules,
 * exception) is what locates a crash, and the journal says what led to it.
 *
 * ## Where it goes
 *
 * `Documents/PawnMC/crashreport/`. Before all-files access is granted the public folder
 * cannot be written, so the dump is written to app-private storage instead and
 * [CrashHandler] copies it out on the next launch.
 */
object Minidump {

    private const val TAG = "Minidump"

    /** Public folder name inside `Documents/PawnMC`. */
    const val FOLDER_NAME = ActivityJournal.FOLDER_NAME

    /** Private fallback folder under `files/`. */
    private const val PRIVATE_FOLDER = "crashreport"

    // ---- minidump stream types ---------------------------------------------
    private const val THREAD_LIST_STREAM = 3
    private const val MODULE_LIST_STREAM = 4
    private const val EXCEPTION_STREAM = 6
    private const val SYSTEM_INFO_STREAM = 7
    private const val MISC_INFO_STREAM = 15
    private const val COMMENT_W_STREAM = 0x7001

    // ---- MINIDUMP_MISC_INFO flags ------------------------------------------
    private const val MINIDUMP_MISC1_PROCESS_ID = 1
    private const val MINIDUMP_MISC1_PROCESS_TIMES = 2

    // ---- processor architectures -------------------------------------------
    private const val PROCESSOR_ARCHITECTURE_INTEL = 0
    private const val PROCESSOR_ARCHITECTURE_ARM = 5
    private const val PROCESSOR_ARCHITECTURE_AMD64 = 9
    private const val PROCESSOR_ARCHITECTURE_ARM64 = 12

    /** `0xE0000000 | 'J' 'V' 'M'` - the marker ART uses for a Java virtual machine fault. */
    private const val JAVA_VM_EXCEPTION_CODE = 0xE04A564D.toInt()

    /** Everything the writer needs, collected by [CrashHandler] before anything is written. */
    data class Input(
        /** Short id, also part of the file name so dump and text report pair up. */
        val crashId: String,
        val timestamp: Long,
        /** Which module crashed: `pawnmc`, `pocketsamp`, `editor`, … */
        val source: String,
        val threadName: String,
        val exceptionClass: String,
        val exceptionMessage: String?,
        val stackTrace: String,
        val deviceInfo: String,
        val journalTail: String,
        val logTail: String,
        /** Live threads, crashing thread first. */
        val threads: List<ThreadSnapshot>,
        val modules: List<ModuleSnapshot>,
        val processUptimeMs: Long,
        val reason: String
    )

    /** One live thread at crash time. */
    data class ThreadSnapshot(val id: Int, val name: String, val priority: Int, val isCurrent: Boolean)

    /** One loaded native library. */
    data class ModuleSnapshot(val path: String, val baseAddress: Long, val size: Long)

    /** Result of a write: where it landed and whether the user can reach it. */
    data class Output(val file: File, val isPublic: Boolean)

    /**
     * Writes the dump for [input]; returns `null` only when even app-private storage fails.
     * Never throws - a crash handler that throws loses the crash.
     */
    fun write(context: Context, input: Input): Output? = runCatching {
        val fileName = fileNameFor(input)
        val publicRoot = ActivityJournal.crashReportFolder(context)

        if (publicRoot != null) {
            val publicFile = File(publicRoot, fileName)
            val ok = runCatching {
                publicRoot.mkdirs()
                FileOutputStream(publicFile).use { it.write(build(input, isPublic = true)) }
                publicFile.isFile && publicFile.length() > 0L
            }.getOrElse {
                Log.w(TAG, "public dump write failed, using app-private storage", it)
                false
            }
            if (ok) return@runCatching Output(publicFile, isPublic = true)
        }

        val privateDir = File(context.filesDir, PRIVATE_FOLDER).apply { mkdirs() }
        val privateFile = File(privateDir, fileName)
        FileOutputStream(privateFile).use { it.write(build(input, isPublic = false)) }
        Output(privateFile, isPublic = false)
    }.onFailure { Log.e(TAG, "minidump could not be written", it) }.getOrNull()

    /** `crash_<yyyyMMdd_HHmmss>_<id>.dmp`, matching the text report of the same crash. */
    fun fileNameFor(input: Input): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(input.timestamp))
        return "crash_${stamp}_${input.crashId}.dmp"
    }

    /** Picks up dumps left in app-private storage once the public folder becomes writable. */
    fun migratePrivateDumps(context: Context): Int {
        val target = ActivityJournal.crashReportFolder(context) ?: return 0
        val privateDir = File(context.filesDir, PRIVATE_FOLDER)
        val files = privateDir.listFiles { file -> file.isFile && file.name.endsWith(".dmp") }
            ?: return 0
        var moved = 0
        files.forEach { source ->
            val destination = File(target, source.name)
            if (destination.exists()) {
                source.delete()
            } else if (runCatching { source.copyTo(destination, overwrite = false) }.isSuccess) {
                source.delete()
                moved++
            }
        }
        return moved
    }

    // ---------------------------------------------------------------------
    // Readable half
    // ---------------------------------------------------------------------

    private fun commentText(input: Input, isPublic: Boolean): String = buildString {
        appendLine("=========================================================")
        appendLine("                     PAWNMC CRASH DUMP")
        appendLine("=========================================================")
        appendLine("Report ID        : ${input.crashId}")
        appendLine("Time             : ${formatTime(input.timestamp)}")
        appendLine("Source           : ${input.source}")
        appendLine("Reason           : ${input.reason}")
        appendLine("Thread           : ${input.threadName}")
        appendLine("Exception        : ${input.exceptionClass}")
        appendLine("Message          : ${input.exceptionMessage ?: "(none)"}")
        appendLine("Process uptime   : ${input.processUptimeMs} ms")
        appendLine("Storage          : ${if (isPublic) "Documents/PawnMC/crashreport" else "app-private (copy pending)"}")
        appendLine("Journal session  : ${ActivityJournal.currentSessionId()}")
        appendLine("---------------------------------------------------------")
        appendLine("DEVICE")
        appendLine("---------------------------------------------------------")
        appendLine(input.deviceInfo.trimEnd())
        appendLine("---------------------------------------------------------")
        appendLine("THREADS (${input.threads.size})")
        appendLine("---------------------------------------------------------")
        input.threads.forEach { thread ->
            appendLine(
                "  #${thread.id}  prio=${thread.priority}  " +
                    (if (thread.isCurrent) "*CRASHING* " else "") + thread.name
            )
        }
        appendLine("---------------------------------------------------------")
        appendLine("MODULES (${input.modules.size})")
        appendLine("---------------------------------------------------------")
        input.modules.forEach { module ->
            appendLine(
                "  0x${java.lang.Long.toHexString(module.baseAddress)}" +
                    "  0x${java.lang.Long.toHexString(module.size)}  " +
                    module.path.substringAfterLast('/')
            )
        }
        appendLine("---------------------------------------------------------")
        appendLine("STACK TRACE")
        appendLine("---------------------------------------------------------")
        appendLine(input.stackTrace.trimEnd())
        appendLine("---------------------------------------------------------")
        appendLine("ACTIVITY JOURNAL (last ${input.journalTail.lines().size} lines)")
        appendLine("---------------------------------------------------------")
        appendLine(input.journalTail.ifBlank { "(journal unavailable)" }.trimEnd())
        appendLine("---------------------------------------------------------")
        appendLine("LOG TAIL")
        appendLine("---------------------------------------------------------")
        appendLine(input.logTail.ifBlank { "(no log lines)" }.trimEnd())
        appendLine("=========================================================")
    }

    // ---------------------------------------------------------------------
    // Binary layout
    // ---------------------------------------------------------------------

    /** One prepared stream: its type code and its bytes. */
    private class Stream(val type: Int, val payload: ByteArray)

    /**
     * Builds the whole file in memory.
     *
     * Written by hand rather than with a library: the module list has to come from
     * `/proc/self/maps` anyway, and hand-rolled offsets are easier to keep correct than
     * an extra native dependency on a process that is already dying.
     */
    private fun build(input: Input, isPublic: Boolean): ByteArray {
        val streams = listOf(
            Stream(SYSTEM_INFO_STREAM, systemInfoStream()),
            Stream(MISC_INFO_STREAM, miscInfoStream(input.processUptimeMs)),
            Stream(EXCEPTION_STREAM, exceptionStream(input)),
            Stream(THREAD_LIST_STREAM, threadListStream(input.threads)),
            Stream(MODULE_LIST_STREAM, moduleListStream(input.modules)),
            Stream(COMMENT_W_STREAM, commentStream(commentText(input, isPublic)))
        )

        // Header (32 bytes) + one 12-byte directory entry per stream, then the payloads
        // on 4-byte boundaries.
        val directoryOffset = 32
        var cursor = directoryOffset + streams.size * 12
        val placed = streams.map { stream ->
            val offset = (cursor + 3) and 3.inv()
            cursor = offset + stream.payload.size
            Placed(stream.type, stream.payload, offset)
        }

        val buffer = ByteBuffer.allocate(cursor).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("MDMP".toByteArray(Charsets.US_ASCII))
        buffer.putInt(0x0000A793) // MINIDUMP_SIGNATURE
        buffer.putInt(0) // version: 0000 = the format's first revision
        buffer.putInt(placed.size)
        buffer.putInt(directoryOffset)
        buffer.putInt(0) // CHECKSUM_UNUSED
        buffer.putLong(0) // time date stamp / flags
        placed.forEach { entry ->
            buffer.putInt(entry.type)
            buffer.putInt(entry.payload.size)
            buffer.putInt(entry.offset)
        }
        placed.forEach { entry ->
            buffer.position(entry.offset)
            buffer.put(entry.payload)
        }
        return buffer.array()
    }

    private class Placed(val type: Int, val payload: ByteArray, val offset: Int)

    private fun systemInfoStream(): ByteArray =
        ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(architecture().toShort())
            putShort(0) // processor level
            putShort(0) // processor revision
            put(0) // number of processors (in the CPU union)
            put(0) // product type
            putInt(Build.VERSION.SDK_INT) // major version
            putInt(0) // minor version
            putInt(0) // build number
            putInt(0) // platform id; Android has none, 0 is "unknown"
            putInt(0) // CSD version RVA
            putShort(0) // suite mask
            putShort(0) // reserved
            putInt(architecture())
            putInt(4096) // page size
            putLong(totalRamBytes())
            putInt(Runtime.getRuntime().availableProcessors())
            putLong(System.currentTimeMillis() / 1000L) // system time
        }.array()

    private fun miscInfoStream(uptimeMs: Long): ByteArray =
        ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(MINIDUMP_MISC1_PROCESS_ID or MINIDUMP_MISC1_PROCESS_TIMES)
            putInt(Process.myPid())
            putLong(0) // process create time is not exposed on Android
            putLong(uptimeMs)
            putLong(0) // user time
        }.array()

    private fun exceptionStream(input: Input): ByteArray =
        ByteBuffer.allocate(168).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(input.threads.firstOrNull { it.isCurrent }?.id ?: 0)
            putInt(0) // alignment
            putInt(JAVA_VM_EXCEPTION_CODE)
            putInt(0) // exception flags
            putLong(0) // nested exception record
            putLong(0) // exception address
            putInt(0) // number of parameters
            putInt(0) // alignment
            repeat(15) { putLong(0) } // ExceptionInformation[15]
            putInt(0) // context record data size (omitted)
            putInt(0) // context record RVA
        }.array()

    private fun threadListStream(threads: List<ThreadSnapshot>): ByteArray {
        val entrySize = 48
        val buffer = ByteBuffer.allocate(4 + threads.size * entrySize)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(threads.size)
        threads.forEach { thread ->
            buffer.putInt(thread.id)
            buffer.putInt(0) // suspend count
            buffer.putInt(thread.priority) // priority class
            buffer.putInt(0) // priority
            buffer.putLong(0) // thread environment block
            buffer.putLong(0) // stack start of memory range (no bytes captured)
            buffer.putInt(0) // stack memory data size
            buffer.putInt(0) // stack memory RVA
            buffer.putInt(0) // thread context size
            buffer.putInt(0) // thread context RVA
        }
        return buffer.array()
    }

    private fun moduleListStream(modules: List<ModuleSnapshot>): ByteArray {
        val entrySize = 108
        val buffer = ByteBuffer.allocate(4 + modules.size * entrySize)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(modules.size)
        modules.forEach { module ->
            buffer.putLong(module.baseAddress)
            buffer.putLong(module.size)
            buffer.putInt(0) // checksum
            buffer.putInt(0) // time date stamp
            buffer.putInt(0) // module name RVA (names live in the comment block)
            // VS_FIXEDFILEINFO, 13 dwords. Signature 0xFEEF04BD and structure version
            // 0x00010000 are what readers check for; the rest is optional detail.
            buffer.putInt(0xFEEF04BD.toInt()) // dwSignature
            buffer.putInt(0x00010000) // dwStrucVersion
            repeat(11) { buffer.putInt(0) }
            buffer.putInt(0) // CV record size
            buffer.putInt(0) // CV record RVA
            buffer.putInt(0) // misc record size
            buffer.putInt(0) // misc record RVA
            buffer.putLong(0) // reserved 0
            buffer.putLong(0) // reserved 1
        }
        return buffer.array()
    }

    /** `MINIDUMP_STRING`-style comment: UTF-16LE characters, no terminator needed. */
    private fun commentStream(text: String): ByteArray {
        val buffer = ByteBuffer.allocate(text.length * 2).order(ByteOrder.LITTLE_ENDIAN)
        text.forEach { buffer.putChar(it) }
        return buffer.array()
    }

    private fun architecture(): Int = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> PROCESSOR_ARCHITECTURE_ARM64
        "armeabi-v7a", "armeabi" -> PROCESSOR_ARCHITECTURE_ARM
        "x86_64" -> PROCESSOR_ARCHITECTURE_AMD64
        "x86" -> PROCESSOR_ARCHITECTURE_INTEL
        else -> PROCESSOR_ARCHITECTURE_ARM64
    }

    private fun totalRamBytes(): Long = runCatching {
        val context = com.rvdjv.pawnmc.PawnMCApplication.appContext?.get() ?: return 0L
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0L
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        info.totalMem
    }.getOrDefault(0L)

    /**
     * Native libraries mapped into this process, read from `/proc/self/maps`.
     *
     * Only `.so` files are kept: they are the frames a native crash walks through, and
     * the maps file can hold thousands of anonymous mappings that no reader wants.
     */
    fun readModules(): List<ModuleSnapshot> = runCatching {
        File("/proc/self/maps").readLines().mapNotNull { line ->
            val path = line.trim().substringAfterLast(' ')
            if (!path.endsWith(".so")) return@mapNotNull null
            val dash = line.indexOf('-')
            val space = line.indexOf(' ')
            if (dash <= 0 || space <= dash) return@mapNotNull null
            val start = line.substring(0, dash).toLongOrNull(16) ?: return@mapNotNull null
            val end = line.substring(dash + 1, space).toLongOrNull(16) ?: return@mapNotNull null
            ModuleSnapshot(path, start, (end - start).coerceAtLeast(1))
        }.distinctBy { it.path }
    }.getOrDefault(emptyList())

    /**
     * Every live thread, the crashing one first.
     *
     * Thread names and priorities are copied eagerly: after the handler returns, the
     * creators of these threads may already be gone.
     */
    fun snapshotThreads(current: Thread): List<ThreadSnapshot> {
        val live = runCatching { Thread.getAllStackTraces().keys }.getOrDefault(emptySet())
        val ordered = live.sortedBy { if (it === current) 0 else 1 }
        return ordered.map { thread ->
            ThreadSnapshot(
                id = thread.id.toInt(),
                name = thread.name,
                priority = thread.priority,
                isCurrent = thread === current
            )
        }
    }

    private fun formatTime(at: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(at))
}
