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

    private fun findInstallDbBinary(context: Context, mariadbDir: File): File? {
        val candidates = listOf(
            File(mariadbDir, "bin/mariadb-install-db"),
            File(mariadbDir, "bin/mysql_install_db"),
            File(context.filesDir, "bin/mariadb-install-db"),
            File(context.filesDir, "bin/mysql_install_db"),
            File(context.applicationInfo.nativeLibraryDir, "libmariadb-install-db.so"),
            File(context.applicationInfo.nativeLibraryDir, "mariadb-install-db")
        )
        return candidates.firstOrNull { it.exists() && (it.canExecute() || it.setExecutable(true, false)) }
    }

    private fun findMariadbdBinary(context: Context, mariadbDir: File): File? {
        val candidates = listOf(
            File(mariadbDir, "bin/mariadbd"),
            File(mariadbDir, "bin/mysqld"),
            File(context.filesDir, "bin/mariadbd"),
            File(context.filesDir, "bin/mysqld"),
            File(context.applicationInfo.nativeLibraryDir, "libmariadbd.so"),
            File(context.applicationInfo.nativeLibraryDir, "mariadbd"),
            File(context.applicationInfo.nativeLibraryDir, "libmysqld.so"),
            File(context.applicationInfo.nativeLibraryDir, "mysqld")
        )
        return candidates.firstOrNull { it.exists() && (it.canExecute() || it.setExecutable(true, false)) }
    }

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        val mariadbDir = File(context.filesDir, "mariadb")
        val dataDir = File(mariadbDir, "data")
        val tmpDir = File(mariadbDir, "tmp")
        val markerFile = File(mariadbDir, "data/.pawnmc-initialized")

        dataDir.mkdirs()
        tmpDir.mkdirs()

        if (!markerFile.isFile) {
            val installDb = findInstallDbBinary(context, mariadbDir)
            val mariadbd = findMariadbdBinary(context, mariadbDir)

            if (installDb == null && mariadbd == null) {
                _dbLogs.emit("[MariaDB Info] MariaDB binary/installer not found in app storage or native libraries.")
                _dbLogs.emit("[MariaDB Info] Database service is optional for SQLite/file gamemodes.")
                return@withContext false
            }

            val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
            val installBuilder = if (installDb != null) {
                ProcessBuilder(
                    installDb.absolutePath,
                    "--datadir=${dataDir.absolutePath}",
                    "--auth-root-authentication-method=normal",
                    "--skip-test-db",
                    "--basedir=${mariadbDir.absolutePath}",
                    "--lc-messages-dir=${File(mariadbDir, "share/mariadb").absolutePath}"
                ).directory(mariadbDir).redirectErrorStream(true)
            } else {
                ProcessBuilder(
                    mariadbd!!.absolutePath,
                    "--initialize-insecure",
                    "--datadir=${dataDir.absolutePath}",
                    "--basedir=${mariadbDir.absolutePath}",
                    "--lc-messages-dir=${File(mariadbDir, "share/mariadb").absolutePath}"
                ).directory(mariadbDir).redirectErrorStream(true)
            }
            installBuilder.environment()["HOME"] = runtimeHome.absolutePath
            installBuilder.environment()["TMPDIR"] = tmpDir.absolutePath
            installBuilder.environment()["LD_LIBRARY_PATH"] = listOf(
                File(mariadbDir, "lib").absolutePath,
                File(mariadbDir, "lib/plugin").absolutePath,
                context.applicationInfo.nativeLibraryDir,
                System.getenv("LD_LIBRARY_PATH").orEmpty()
            ).filter { it.isNotBlank() }.joinToString(":")
            val result = installBuilder.start()
            val output = withContext(Dispatchers.IO) {
                result.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
            if (exitCode != 0 || !File(dataDir, "mysql/global_priv.MAD").exists() && !File(dataDir, "mysql/db.opt").exists()) {
                _dbLogs.emit("[MariaDB Error] Database initialization failed (exit $exitCode): ${output.takeLast(2000)}")
                return@withContext false
            }
            markerFile.writeText("initialized")
            if (output.isNotBlank()) _dbLogs.emit(output.takeLast(2000))
        }

        _dbLogs.emit("[MariaDB] Local database initialized.")
        true
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val mariadbDir = File(context.filesDir, "mariadb")
        val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
        val mariadbdBinary = findMariadbdBinary(context, mariadbDir)
        val cnfFile = File(mariadbDir, "my.cnf")
        val dataDir = File(mariadbDir, "data").apply { mkdirs() }
        val tmpDir = File(mariadbDir, "tmp").apply { mkdirs() }
        val shareDir = File(mariadbDir, "share/mariadb")

        if (mariadbdBinary == null || !mariadbdBinary.exists() || !mariadbdBinary.canExecute()) {
            scope.launch {
                _dbLogs.emit("[MariaDB Info] MariaDB daemon not found in app storage or native libraries.")
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        val command = listOf(
            mariadbdBinary.absolutePath,
            "--defaults-file=${cnfFile.absolutePath}",
            "--datadir=${dataDir.absolutePath}",
            "--socket=${socketFile.absolutePath}",
            "--pid-file=${pidFile.absolutePath}",
            "--tmpdir=${tmpDir.absolutePath}",
            "--lc-messages-dir=${shareDir.absolutePath}",
            "--bind-address=127.0.0.1",
            "--port=3306",
            "--user=${android.os.Process.myUid()}"
        )

        val env = mapOf(
            "LD_LIBRARY_PATH" to listOf(
                File(mariadbDir, "lib").absolutePath,
                File(mariadbDir, "lib/plugin").absolutePath,
                context.applicationInfo.nativeLibraryDir,
                System.getenv("LD_LIBRARY_PATH").orEmpty()
            ).filter { it.isNotBlank() }.joinToString(":"),
            "TMPDIR" to tmpDir.absolutePath,
            "HOME" to runtimeHome.absolutePath,
            "USER" to android.os.Process.myUid().toString(),
            "MYSQL_HOME" to mariadbDir.absolutePath
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
