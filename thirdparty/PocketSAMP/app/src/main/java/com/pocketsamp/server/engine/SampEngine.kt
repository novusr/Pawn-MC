package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.RandomAccessFile

class SampEngine(private val context: Context) {

    companion object {
        private const val TAG = "SampEngine"
    }

    var onExit: ((exitCode: Int) -> Unit)? = null

    private val processRunner = ProcessRunner("SA-MP") { exitCode ->
        onExit?.invoke(exitCode)
    }
    private var logPollingJob: Job? = null

    private val _serverLogs = MutableSharedFlow<String>(
        replay = 100,
        extraBufferCapacity = 1000,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val serverLogs: SharedFlow<String> = _serverLogs.asSharedFlow()

    val isRunning: Boolean
        get() = processRunner.isRunning

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val filesDir = context.filesDir

        val qemuBinary = File(nativeLibDir, "libqemu-i386.so")
        val rootfsDir = File(filesDir, "rootfs_i386")
        val sampDir = com.pocketsamp.server.config.ServerDirectoryManager.getServerDirectory(context)
        val serverBinary = File(filesDir, "bin/samp03svr").takeIf { it.exists() }
            ?: File(filesDir, "samp_server/samp03svr").takeIf { it.exists() }
            ?: File(sampDir, "samp03svr")
        val tempDir = File(context.cacheDir, "samp_tmp").apply { mkdirs() }

        if (!qemuBinary.exists()) {
            scope.launch {
                _serverLogs.emit("[SA-MP Error] QEMU binary not found in $nativeLibDir")
            }
            return
        }

        if (!serverBinary.exists()) {
            scope.launch {
                _serverLogs.emit("[SA-MP Error] samp03svr binary not found in ${serverBinary.parent}")
            }
            return
        }

        val cfgFile = File(sampDir, "server.cfg")
        if (!cfgFile.exists()) {
            scope.launch {
                _serverLogs.emit("[SA-MP Error] server.cfg tidak ditemukan di: ${sampDir.absolutePath}")
                _serverLogs.emit("[SA-MP Info] Pastikan folder berisi gamemode SA-MP yang valid atau atur konfigurasi di tab Config.")
            }
            return
        }

        // Auto-tune mobile networking in server.cfg to eliminate query drops & high ping
        try {
            var cfgContent = cfgFile.readText()
            var modified = false

            // 1. Ensure lanmode 1: 'lanmode 0' triggers SA-MP's aggressive anti-flood query throttle, causing mobile launchers to report 'Offline (999ms)'
            if (cfgContent.contains(Regex("""(?i)^\s*lanmode\s+0""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*lanmode\s+0""", RegexOption.MULTILINE), "lanmode 1")
                modified = true
                scope.launch {
                    _serverLogs.emit("[Network Tuning] 'lanmode 1' diaktifkan untuk mencegah query drop pada launcher mobile.")
                }
            } else if (!cfgContent.contains(Regex("""(?i)^\s*lanmode""", RegexOption.MULTILINE))) {
                cfgContent = "lanmode 1\n$cfgContent"
                modified = true
            }

            // 2. Ensure query 1: allows client launchers to query ping and player info
            if (cfgContent.contains(Regex("""(?i)^\s*query\s+0""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*query\s+0""", RegexOption.MULTILINE), "query 1")
                modified = true
            } else if (!cfgContent.contains(Regex("""(?i)^\s*query""", RegexOption.MULTILINE))) {
                cfgContent = "query 1\n$cfgContent"
                modified = true
            }

            // 3. Ensure sleep 1: default sleep 5-10ms causes 300+ms ping in emulated loop; sleep 1 gives 10-20ms responsiveness
            if (cfgContent.contains(Regex("""(?i)^\s*sleep\s+([3-9]|\d{2,})""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*sleep\s+([3-9]|\d{2,})""", RegexOption.MULTILINE), "sleep 1")
                modified = true
            } else if (!cfgContent.contains(Regex("""(?i)^\s*sleep""", RegexOption.MULTILINE))) {
                cfgContent += "\nsleep 1\n"
                modified = true
            }

            // 4. Ensure ackslimit 3000 to prevent mobile packet burst drops
            if (!cfgContent.contains(Regex("""(?i)^\s*ackslimit""", RegexOption.MULTILINE))) {
                cfgContent += "\nackslimit 3000\n"
                modified = true
            }

            // 5. Ensure bind is removed so server listens on both loopback (127.0.0.1) and LAN
            if (cfgContent.contains(Regex("""(?i)^\s*bind\s+""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*bind\s+.*$""", RegexOption.MULTILINE), "# bind removed for localhost+LAN support")
                modified = true
            }

            if (modified) {
                cfgFile.writeText(cfgContent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Notice: could not auto-tune server.cfg: ${e.message}")
        }

        serverBinary.setExecutable(true, false)

        // Clear historical log file so the console only shows the current session
        val logFile = File(sampDir, "server_log.txt")
        if (logFile.exists()) {
            logFile.delete()
        }

        // Execute SA-MP server via QEMU user-mode translation directly (-L provides the x86 sysroot).
        // This eliminates PRoot ptrace overhead, glue rootfs bugs, and /tmp permission failures.
        val command = listOf(
            qemuBinary.absolutePath,
            "-L", rootfsDir.absolutePath,
            "-E", "LD_LIBRARY_PATH=/usr/lib/i386-linux-gnu:/lib/i386-linux-gnu:/usr/lib:/lib:./plugins:plugins",
            serverBinary.absolutePath
        )

        val env = mapOf(
            "TMPDIR" to tempDir.absolutePath,
            "LD_LIBRARY_PATH" to "${rootfsDir.absolutePath}/usr/lib/i386-linux-gnu:${rootfsDir.absolutePath}/lib/i386-linux-gnu:${sampDir.absolutePath}/plugins",
            "HOME" to sampDir.absolutePath,
            "USER" to "root"
        )

        processRunner.start(
            command = command,
            workingDir = sampDir,
            environment = env,
            scope = scope
        )

        // Relay stdout logs
        scope.launch(Dispatchers.IO) {
            processRunner.logs.collect { line ->
                _serverLogs.tryEmit(line)
            }
        }

        // Tail server_log.txt for detailed SA-MP Pawn logs
        startLogTailer(File(sampDir, "server_log.txt"), scope)
    }

    private fun startLogTailer(logFile: File, scope: CoroutineScope) {
        logPollingJob?.cancel()
        logPollingJob = scope.launch(Dispatchers.IO) {
            var attempts = 0
            while (!logFile.exists() && attempts < 20 && isActive) {
                delay(250)
                attempts++
            }

            if (!logFile.exists()) return@launch

            var filePointer = 0L
            while (isActive && isRunning) {
                try {
                    val fileLength = logFile.length()
                    if (fileLength > filePointer) {
                        RandomAccessFile(logFile, "r").use { raf ->
                            raf.seek(filePointer)
                            var line: String? = raf.readLine()
                            while (line != null) {
                                _serverLogs.tryEmit(line)
                                line = raf.readLine()
                            }
                            filePointer = raf.filePointer
                        }
                    } else if (fileLength < filePointer) {
                        filePointer = 0L
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Log tailer reading warning: ${e.message}")
                }
                delay(500)
            }
        }
    }

    fun stop() {
        logPollingJob?.cancel()
        processRunner.stop()
    }
}
