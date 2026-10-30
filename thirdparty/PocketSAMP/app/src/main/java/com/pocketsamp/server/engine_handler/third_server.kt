package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration._DirectoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.RandomAccessFile

class Server(private val context: Context) {

    companion object {
        private const val TAG = "Server"
    }

    var onExit: ((exitCode: Int) -> Unit)? = null

    private fun t(key: String): String = Locales.text(Locales.current, key)

    private val processRunner = ThirdProcess("SA-MP") { exitCode ->
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

    private fun findQemuBinary(context: Context): File? = ThirdLauncher.findQemu(context)

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val filesDir = context.filesDir

        val qemuBinary = findQemuBinary(context)
        val rootfsDir = File(filesDir, "rootfs_i386")
        val sampDir = _DirectoryManager.getServerDirectory(context)
        val serverBinary = File(filesDir, "bin/samp03svr").takeIf { it.exists() }
            ?: File(filesDir, "samp_server/samp03svr").takeIf { it.exists() }
            ?: File(sampDir, "samp03svr")
        val tempDir = File(context.cacheDir, "samp_tmp").apply { mkdirs() }

        val isX86Host = android.os.Build.SUPPORTED_ABIS.any { it.contains("x86") }
        val ldLinuxCandidates = listOf(
            File(rootfsDir, "lib/ld-linux.so.2"),
            File(rootfsDir, "lib/i386-linux-gnu/ld-linux.so.2"),
            File("/lib/ld-linux.so.2")
        )
        val ldLinux = ldLinuxCandidates.firstOrNull { it.exists() }
        val useNativeX86 = isX86Host && (ldLinux != null || serverBinary.canExecute())

        if (qemuBinary == null && !useNativeX86) {
            scope.launch {
                _serverLogs.emit(t("log.qemu.missing").replace("{dir}", nativeLibDir))
                _serverLogs.emit(t("log.qemu.required"))
            }
            return
        }

        if (!serverBinary.exists()) {
            scope.launch {
                _serverLogs.emit(t("log.binary.missing").replace("{dir}", serverBinary.parent.orEmpty()))
            }
            return
        }

        val cfgFile = File(sampDir, "server.cfg")
        if (!cfgFile.exists()) {
            scope.launch {
                _serverLogs.emit(t("log.cfg.missing").replace("{dir}", sampDir.absolutePath))
                _serverLogs.emit(t("log.cfg.hint"))
            }
            return
        }

        // Auto-tune mobile networking in server.cfg to avoid query drops and high ping.
        try {
            var cfgContent = cfgFile.readText()
            var modified = false

            if (cfgContent.contains(Regex("""(?i)^\s*lanmode\s+0""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*lanmode\s+0""", RegexOption.MULTILINE), "lanmode 1")
                modified = true
                scope.launch {
                    _serverLogs.emit(t("log.tuning.lanmode"))
                }
            } else if (!cfgContent.contains(Regex("""(?i)^\s*lanmode""", RegexOption.MULTILINE))) {
                cfgContent = "lanmode 1\n$cfgContent"
                modified = true
            }

            // query 1 lets launchers read ping and player info.
            if (cfgContent.contains(Regex("""(?i)^\s*query\s+0""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*query\s+0""", RegexOption.MULTILINE), "query 1")
                modified = true
            } else if (!cfgContent.contains(Regex("""(?i)^\s*query""", RegexOption.MULTILINE))) {
                cfgContent = "query 1\n$cfgContent"
                modified = true
            }

            // sleep 1 keeps the emulated loop responsive.
            if (cfgContent.contains(Regex("""(?i)^\s*sleep\s+([3-9]|\d{2,})""", RegexOption.MULTILINE))) {
                cfgContent = cfgContent.replace(Regex("""(?i)^\s*sleep\s+([3-9]|\d{2,})""", RegexOption.MULTILINE), "sleep 1")
                modified = true
            } else if (!cfgContent.contains(Regex("""(?i)^\s*sleep""", RegexOption.MULTILINE))) {
                cfgContent += "\nsleep 1\n"
                modified = true
            }

            // ackslimit 3000 absorbs mobile packet bursts.
            if (!cfgContent.contains(Regex("""(?i)^\s*ackslimit""", RegexOption.MULTILINE))) {
                cfgContent += "\nackslimit 3000\n"
                modified = true
            }

            // No bind line, so the server listens on loopback and LAN.
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

        /*
         * Clear historical log file so the console only shows the current session
         */
        val logFile = File(sampDir, "server_log.txt")
        if (logFile.exists()) {
            logFile.delete()
        }

        /*
         * Execute SA-MP server via QEMU user-mode translation directly (-L provides the x86 sysroot),
         * or natively on x86/x86_64 hosts via ld-linux.so.2.
         */
        val command = if (qemuBinary != null) {
            listOf(
                qemuBinary.absolutePath,
                "-L", rootfsDir.absolutePath,
                "-E", "LD_LIBRARY_PATH=/usr/lib/i386-linux-gnu:/lib/i386-linux-gnu:/usr/lib:/lib:./plugins:plugins",
                serverBinary.absolutePath
            )
        } else if (ldLinux != null) {
            ldLinux.setExecutable(true, false)
            listOf(
                ldLinux.absolutePath,
                "--library-path",
                "${rootfsDir.absolutePath}/usr/lib/i386-linux-gnu:${rootfsDir.absolutePath}/lib/i386-linux-gnu:${sampDir.absolutePath}/plugins:./plugins",
                serverBinary.absolutePath
            )
        } else {
            listOf(serverBinary.absolutePath)
        }

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

        /*
         * Relay stdout logs
         */
        scope.launch(Dispatchers.IO) {
            processRunner.logs.collect { line ->
                _serverLogs.tryEmit(line)
            }
        }

        /*
         * Tail server_log.txt for detailed SA-MP Pawn logs
         */
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
