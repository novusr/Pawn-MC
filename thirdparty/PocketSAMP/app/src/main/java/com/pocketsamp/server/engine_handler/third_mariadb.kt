package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import com.pocketsamp.server.Locales
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class ThirdMariaDb(private val context: Context) {

    private fun t(key: String): String = Locales.text(Locales.current, key)

    companion object {
        private const val TAG = "MariaDb"
    }

    private val processRunner = ThirdProcess("MariaDB")

    private val _dbLogs = MutableSharedFlow<String>(
        replay = 50,
        extraBufferCapacity = 500,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val dbLogs: SharedFlow<String> = _dbLogs.asSharedFlow()

    val isRunning: Boolean
        get() = processRunner.isRunning

    private fun findMariadbdBinary(context: Context, mariadbDir: File): File? {
        val candidates = listOf(
            File(mariadbDir, "bin/mariadbd"),
            File(mariadbDir, "bin/mysqld"),
            File(context.filesDir, "bin/mariadbd"),
            File(context.filesDir, "bin/mysqld"),
            File(context.filesDir, "rootfs_i386/usr/sbin/mariadbd"),
            File(context.filesDir, "rootfs_i386/usr/sbin/mysqld"),
            File(context.filesDir, "rootfs_i386/usr/bin/mariadbd")
        )
        return firstRunnable(candidates)
    }

    /** First candidate that is an executable and runnable on this device. */
    private fun firstRunnable(candidates: List<File>): File? =
        candidates.firstOrNull { isUsableBinary(it) && isRunnableOnThisDevice(it) }

    /** True when [file] is a regular, non-empty executable. */
    private fun isUsableBinary(file: File): Boolean = ThirdLauncher.isExecutableFile(file)

    /** Whether the host can execute [binary]. */
    private fun isRunnableOnThisDevice(binary: File): Boolean {
        if (ThirdLauncher.needsQemu(binary)) return ThirdLauncher.findQemu(context) != null
        val kind = ThirdLauncher.kindOf(binary)
        return when (kind) {
            ThirdLauncher.Kind.AARCH64 -> android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            ThirdLauncher.Kind.ARM -> android.os.Build.SUPPORTED_ABIS.any { it.startsWith("armeabi") }
            else -> true
        }
    }

    /** i386 sysroot that QEMU uses with `-L`. */
    private fun rootfsDir(): File = File(context.filesDir, "rootfs_i386")

    /** The install tree the daemon is resolved from, `filesDir/mariadb`. */
    private fun mariadbInstallRoot(): File = File(context.filesDir, "mariadb")

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val dataDir = File(mariadbDir, "data")
        val tmpDir = File(mariadbDir, "tmp")
        val markerFile = File(mariadbDir, "data/.pawnmc-initialized")

        dataDir.mkdirs()
        tmpDir.mkdirs()

        if (!markerFile.isFile) {
            val mariadbd = findMariadbdBinary(context, installed)

            if (mariadbd == null) {
                val binDir = File(mariadbDir, "bin")
                if (!binDir.isDirectory) {
                    _dbLogs.emit(_dbLogs.emit("log.mdb.nobin"))
                    _dbLogs.emit(_dbLogs.emit("log.mdb.nobin.note"))
                } else {
                    _dbLogs.emit(_dbLogs.emit("log.mdb.daemon.notfound".replace("{dir}", binDir.absolutePath)))
                }
                _dbLogs.emit(_dbLogs.emit("log.mdb.optional"))
                return@withContext false
            }

            val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
            // `--initialize-insecure` needs no external helper, unlike the install script.
            val installerBinary = mariadbd
            val installerArgs = listOf(
                "--initialize-insecure",
                "--datadir=${dataDir.absolutePath}",
                "--basedir=${installed.absolutePath}",
                "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}"
            )
            val guestLibs = listOf(
                File(installed, "lib").absolutePath,
                File(installed, "lib/plugin").absolutePath
            ).joinToString(":")
            val installerCommand = ThirdLauncher.command(
                binary = installerBinary,
                arguments = installerArgs,
                qemu = ThirdLauncher.findQemu(context),
                rootfsDir = rootfsDir(),
                guestLibraryPath = guestLibs
            )
            if (installerCommand == null) {
                _dbLogs.emit(_dbLogs.emit("log.mdb.init.noqemu".replace("{name}", installerBinary.name)))
                return@withContext false
            }
            val installBuilder = ProcessBuilder(installerCommand)
                .directory(installed)
                .redirectErrorStream(true)
            installBuilder.environment()["HOME"] = runtimeHome.absolutePath
            installBuilder.environment()["TMPDIR"] = tmpDir.absolutePath
            installBuilder.environment()["LD_LIBRARY_PATH"] = if (ThirdLauncher.needsQemu(installerBinary)) {
                context.applicationInfo.nativeLibraryDir
            } else {
                listOf(
                    File(installed, "lib").absolutePath,
                    File(installed, "lib/plugin").absolutePath,
                    context.applicationInfo.nativeLibraryDir,
                    System.getenv("LD_LIBRARY_PATH").orEmpty()
                ).filter { it.isNotBlank() }.joinToString(":")
            }
            val result = installBuilder.start()
            val output = withContext(Dispatchers.IO) {
                result.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
            val systemDb = File(dataDir, "mysql")
            val initialized = File(dataDir, "mysql/global_priv.MAD").exists() ||
                File(dataDir, "mysql/db.opt").exists() ||
                File(dataDir, "mysql/user.MYD").exists() ||
                File(systemDb, "user.frm").exists()
            if (exitCode != 0 || !initialized) {
                _dbLogs.emit(_dbLogs.emit("log.mdb.init.failed".replace("{code}", exitCode.toString()).replace("{output}", output.takeLast(2000))))
                return@withContext false
            }
            markerFile.writeText("initialized")
            if (output.isNotBlank()) _dbLogs.emit(output.takeLast(2000))
        }

        _dbLogs.emit(_dbLogs.emit("log.mdb.initialized"))
        true
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
        val mariadbdBinary = findMariadbdBinary(context, installed)
        val cnfFile = File(mariadbDir, "mysql_list.cnf")
        val dataDir = File(mariadbDir, "data").apply { mkdirs() }
        val tmpDir = File(mariadbDir, "tmp").apply { mkdirs() }
        val shareDir = File(installed, "share/mariadb")

        if (mariadbdBinary == null) {
            scope.launch {
                _dbLogs.emit(_dbLogs.emit("log.mdb.daemon.missing".replace("{dir}", installed.absolutePath)))
                /*
                 * A daemon that exists but cannot be made executable is almost always
                 * the Android 10+ W^X rule: the file is in app storage and not marked
                 * as a native library. Anchor points at the asset bundle.
                 */
                val blocked = File(installed, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                if (blocked != null) {
                    _dbLogs.emit(_dbLogs.emit("log.mdb.notexecutable".replace("{path}", blocked.absolutePath)))
                }
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        val qemu = ThirdLauncher.findQemu(context)
        val guestLibs = listOf(
            File(installed, "lib").absolutePath,
            File(installed, "lib/plugin").absolutePath
        ).joinToString(":")

        val daemonArgs = listOf(
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

        val command = ThirdLauncher.command(
            binary = mariadbdBinary,
            arguments = daemonArgs,
            qemu = qemu,
            rootfsDir = rootfsDir(),
            guestLibraryPath = guestLibs
        )
        if (command == null) {
            scope.launch {
                _dbLogs.emit(_dbLogs.emit("log.mdb.start.noqemu".replace("{name}", mariadbdBinary.name)))
                _dbLogs.emit(_dbLogs.emit("log.mdb.start.noqemu.note"))
            }
            return
        }

        scope.launch {
            _dbLogs.emit(
                t("log.mdb.launching")
                    .replace("{name}", mariadbdBinary.name)
                    .replace("{kind}", ThirdLauncher.kindOf(mariadbdBinary).name)
                    .replace("{mode}", if (ThirdLauncher.needsQemu(mariadbdBinary)) "QEMU" else "native exec")
            )
        }

        val usesQemu = ThirdLauncher.needsQemu(mariadbdBinary)
        val env = mapOf(
            // The host needs its own libs only; guest libs travel through `-E`.
            "LD_LIBRARY_PATH" to if (usesQemu) {
                context.applicationInfo.nativeLibraryDir
            } else {
                listOf(
                    File(installed, "lib").absolutePath,
                    File(installed, "lib/plugin").absolutePath,
                    context.applicationInfo.nativeLibraryDir,
                    System.getenv("LD_LIBRARY_PATH").orEmpty()
                ).filter { it.isNotBlank() }.joinToString(":")
            },
            "TMPDIR" to tmpDir.absolutePath,
            "HOME" to runtimeHome.absolutePath,
            "USER" to android.os.Process.myUid().toString(),
            "MYSQL_HOME" to installed.absolutePath
        )

        processRunner.start(
            command = command,
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
