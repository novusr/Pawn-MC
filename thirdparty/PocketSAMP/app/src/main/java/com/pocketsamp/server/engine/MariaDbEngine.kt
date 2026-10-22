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
        return candidates.firstOrNull { isUsableBinary(it) }
    }

    private fun findMariadbdBinary(context: Context, mariadbDir: File): File? {
        val candidates = listOf(
            // A daemon installed from a downloaded bundle. It lives in a *private*
            // path that mirrors the package (filesDir/mariadb-native/arm64-v8a),
            // which is the only way exec() is allowed there since API 29; see
            // MariaDbDownloader.
            File(MariaDbDownloader.nativeDir(context), "bin/mariadbd"),
            File(mariadbDir, "bin/mariadbd"),
            File(mariadbDir, "bin/mysqld"),
            File(context.filesDir, "bin/mariadbd"),
            File(context.filesDir, "bin/mysqld"),
            File(context.applicationInfo.nativeLibraryDir, "libmariadbd.so"),
            File(context.applicationInfo.nativeLibraryDir, "mariadbd"),
            File(context.applicationInfo.nativeLibraryDir, "libmysqld.so"),
            File(context.applicationInfo.nativeLibraryDir, "mysqld")
        )
        return candidates.firstOrNull { isUsableBinary(it) }
    }

    /**
     * True when [file] is a real executable, not just an existing path.
     *
     * The old check was `it.exists() && (it.canExecute() || it.setExecutable(true))`.
     * A *directory* named `mariadbd` passes both (`File.canExecute()` is true for a
     * directory you own), so the daemon "was found" and the engine then tried to
     * execute a folder. Requiring a regular file and a non-zero length is what makes
     * a missing or truncated deployment report itself honestly instead.
     */
    private fun isUsableBinary(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (file.canExecute()) return true
        return runCatching { file.setExecutable(true, false) }.getOrDefault(false)
    }

    /** Binaries are shipped for 64-bit ARM only; QEMU syscalls of the guest are i386. */
    private fun isArm64Device(): Boolean =
        android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    /**
     * The install tree the daemon should be resolved from.
     *
     * A bundle installed from the Database tab lives in the private, package-mirrored
     * `mariadb-native/arm64-v8a` tree and brings its own `bin/`, `lib/` and `share/`.
     * The bundled asset tree (`filesDir/mariadb`) only wins when no downloaded daemon
     * is present, so an incomplete asset bundle can never shadow a working download.
     */
    private fun mariadbInstallRoot(): File {
        val downloaded = MariaDbDownloader.nativeDir(context)
        return if (MariaDbDownloader.downloadedDaemon(context) != null) downloaded
        else File(context.filesDir, "mariadb")
    }

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        // A downloaded bundle lives outside mariadb/, so its data directory, my.cnf
        // template and share/ tree are resolved from the install the daemon came from.
        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val dataDir = File(mariadbDir, "data")
        val tmpDir = File(mariadbDir, "tmp")
        val markerFile = File(mariadbDir, "data/.pawnmc-initialized")

        dataDir.mkdirs()
        tmpDir.mkdirs()

        if (!markerFile.isFile) {
            val installDb = findInstallDbBinary(context, installed)
            val mariadbd = findMariadbdBinary(context, installed)

            if (installDb == null && mariadbd == null) {
                // Say *why*, instead of one generic line that reads like a bug in the
                // engine. The asset bundle is the usual reason on a development build:
                // `mariadb_arm64.tar.gz` ships only share/ data unless a full server
                // build was packaged, and the daemon is 64-bit ARM only.
                val binDir = File(mariadbDir, "bin")
                if (!binDir.isDirectory) {
                    _dbLogs.emit("[MariaDB Info] The MariaDB asset bundle contains no bin/ directory.")
                    _dbLogs.emit("[MariaDB Info] Rebuild assets: mariadb_arm64.tar.gz must hold bin/mariadbd, bin/mariadb-install-db and lib/ for arm64-v8a.")
                } else if (!isArm64Device()) {
                    _dbLogs.emit("[MariaDB Info] MariaDB binaries are arm64-v8a only; this device reports ${android.os.Build.SUPPORTED_ABIS.joinToString(", ")}.")
                } else {
                    _dbLogs.emit("[MariaDB Info] MariaDB binary/installer not found in app storage or native libraries.")
                }
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
                    "--basedir=${installed.absolutePath}",
                    "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}"
                ).directory(installed).redirectErrorStream(true)
            } else {
                ProcessBuilder(
                    mariadbd!!.absolutePath,
                    "--initialize-insecure",
                    "--datadir=${dataDir.absolutePath}",
                    "--basedir=${installed.absolutePath}",
                    "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}"
                ).directory(installed).redirectErrorStream(true)
            }
            installBuilder.environment()["HOME"] = runtimeHome.absolutePath
            installBuilder.environment()["TMPDIR"] = tmpDir.absolutePath
            installBuilder.environment()["LD_LIBRARY_PATH"] = listOf(
                File(installed, "lib").absolutePath,
                File(installed, "lib/plugin").absolutePath,
                context.applicationInfo.nativeLibraryDir,
                System.getenv("LD_LIBRARY_PATH").orEmpty()
            ).filter { it.isNotBlank() }.joinToString(":")
            val result = installBuilder.start()
            val output = withContext(Dispatchers.IO) {
                result.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
            // MariaDB creates `mysql/global_priv.MAD` (new auth tables) or, on older
            // builds, `mysql/db.opt`. Any of the classic system tables proves the
            // datadir was really initialized; testing only those two file names
            // reported a successful install as a failure on other server versions.
            val systemDb = File(dataDir, "mysql")
            val initialized = File(dataDir, "mysql/global_priv.MAD").exists() ||
                File(dataDir, "mysql/db.opt").exists() ||
                File(dataDir, "mysql/user.MYD").exists() ||
                File(systemDb, "user.frm").exists()
            if (exitCode != 0 || !initialized) {
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

        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
        val mariadbdBinary = findMariadbdBinary(context, installed)
        val cnfFile = File(mariadbDir, "my.cnf")
        val dataDir = File(mariadbDir, "data").apply { mkdirs() }
        val tmpDir = File(mariadbDir, "tmp").apply { mkdirs() }
        val shareDir = File(installed, "share/mariadb")

        if (mariadbdBinary == null) {
            scope.launch {
                _dbLogs.emit("[MariaDB Info] MariaDB daemon not found in app storage or native libraries.")
                // A daemon that exists but cannot be made executable is almost always
                // the Android 10+ W^X rule: the file is in app storage, not in the
                // private package-mirrored path or nativeLibraryDir.
                val blocked = File(installed, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                    ?: File(mariadbDir, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                if (blocked != null) {
                    _dbLogs.emit("[MariaDB Info] Found ${blocked.absolutePath} but it is not executable (Android blocks exec() in app storage).")
                    _dbLogs.emit("[MariaDB Info] Install the bundle from the Database tab so it lands in ${MariaDbDownloader.nativeDir(context).absolutePath}.")
                }
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        val command = listOf(
            mariadbdBinary.absolutePath,
            "--defaults-file=${cnfFile.absolutePath}",
            "--basedir=${installed.absolutePath}",
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
                File(installed, "lib").absolutePath,
                File(installed, "lib/plugin").absolutePath,
                context.applicationInfo.nativeLibraryDir,
                System.getenv("LD_LIBRARY_PATH").orEmpty()
            ).filter { it.isNotBlank() }.joinToString(":"),
            "TMPDIR" to tmpDir.absolutePath,
            "HOME" to runtimeHome.absolutePath,
            "USER" to android.os.Process.myUid().toString(),
            "MYSQL_HOME" to installed.absolutePath
        )

        processRunner.start(
            command = command,
            // The daemon's own directory: a downloaded bundle keeps its data files
            // next to bin/ and lib/, so a relative lookup must not fall through to
            // the asset tree.
            workingDir = installed,
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
