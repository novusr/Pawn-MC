package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class MariaDbEngine(private val context: Context) {

    companion object {
        private const val TAG = "MariaDbEngine"
    }

    private val processRunner = ProcessRunner("MariaDB")

    private val _dbLogs = MutableSharedFlow<String>(
        replay = 50,
        extraBufferCapacity = 500,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val dbLogs: SharedFlow<String> = _dbLogs.asSharedFlow()

    val isRunning: Boolean
        get() = processRunner.isRunning

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        val mariadbDir = File(context.filesDir, "mariadb")
        val dataDir = File(mariadbDir, "data")
        val mysqlDir = File(dataDir, "mysql")
        val tmpDir = File(mariadbDir, "tmp")

        dataDir.mkdirs()
        mysqlDir.mkdirs()
        tmpDir.mkdirs()

        _dbLogs.emit("[MariaDB] Local database directory initialized.")
        true
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val mariadbdBinary = File(nativeLibDir, "libmariadbd.so")
        val mariadbDir = File(context.filesDir, "mariadb")
        val cnfFile = File(mariadbDir, "my.cnf")
        val dataDir = File(mariadbDir, "data").apply { mkdirs() }
        val tmpDir = File(mariadbDir, "tmp").apply { mkdirs() }
        val shareDir = File(mariadbDir, "share/mariadb")

        if (!mariadbdBinary.exists()) {
            scope.launch {
                _dbLogs.emit("[MariaDB Error] Binary not found: ${mariadbdBinary.absolutePath}")
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        // Execute natively from nativeLibraryDir (full execution permissions from Android OS)
        val command = listOf(
            mariadbdBinary.absolutePath,
            "--defaults-file=${cnfFile.absolutePath}",
            "--datadir=${dataDir.absolutePath}",
            "--socket=${socketFile.absolutePath}",
            "--pid-file=${pidFile.absolutePath}",
            "--tmpdir=${tmpDir.absolutePath}",
            "--lc-messages-dir=${shareDir.absolutePath}",
            "--skip-grant-tables"
        )

        val env = mapOf(
            "LD_LIBRARY_PATH" to nativeLibDir,
            "TMPDIR" to tmpDir.absolutePath,
            "HOME" to mariadbDir.absolutePath,
            "USER" to "root"
        )

        processRunner.start(
            command = command,
            workingDir = mariadbDir,
            environment = env,
            scope = scope
        )

        scope.launch(Dispatchers.IO) {
            processRunner.logs.collect { line ->
                _dbLogs.tryEmit(line)
            }
        }
    }

    fun stop() {
        processRunner.stop()
    }
}
