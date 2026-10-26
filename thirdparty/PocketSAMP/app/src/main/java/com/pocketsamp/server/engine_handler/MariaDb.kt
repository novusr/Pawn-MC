package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

class MariaDb(private val context: Context) {

    companion object {
        private const val TAG = "MariaDb"
    }

    private val processRunner = ProcessRun("MariaDB")

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
            /*
             * i386 build inside the shared rootfs: executed through QEMU.
             */
            File(context.filesDir, "rootfs_i386/usr/sbin/mariadbd"),
            File(context.filesDir, "rootfs_i386/usr/sbin/mysqld"),
            File(context.filesDir, "rootfs_i386/usr/bin/mariadbd")
        )
        return firstRunnable(candidates)
    }

    /**
     * First candidate that is executable *and* runnable here.
     *
     * The daemon is i386 now, so "runnable" means the QEMU translator was found; an
     * x86-64 native copy on the device would not be.
     */
    private fun firstRunnable(candidates: List<File>): File? =
        candidates.firstOrNull { isUsableBinary(it) && isRunnableOnThisDevice(it) }

    /**
     * True when [file] is a real executable, not just an existing path.
     *
     * The old check was `it.exists() && (it.canExecute() || it.setExecutable(true))`.
     * A *directory* named `mariadbd` passes both (`File.canExecute()` is true for a
     * directory you own), so the daemon "was found" and the engine then tried to
     * execute a folder. Requiring a regular file and a non-zero length is what makes
     * a missing or truncated deployment report itself honestly instead.
     */
    private fun isUsableBinary(file: File): Boolean = Launcher.isExecutableFile(file)

    /**
     * Whether the host can execute [binary].
     *
     * The daemon is i386, so this reduces to "was the QEMU translator found". A native
     * ARM64 or ARM binary is only accepted when the device actually reports that ABI.
     */
    private fun isRunnableOnThisDevice(binary: File): Boolean {
        if (Launcher.needsQemu(binary)) return Launcher.findQemu(context) != null
        val kind = Launcher.kindOf(binary)
        return when (kind) {
            Launcher.Kind.AARCH64 -> android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            Launcher.Kind.ARM -> android.os.Build.SUPPORTED_ABIS.any { it.startsWith("armeabi") }
            /*
             * A non-ELF or unrecognised file: let the exec attempt decide.
             */
            else -> true
        }
    }

    /** i386 sysroot that QEMU uses with `-L`, shared with [Samp]. */
    private fun rootfsDir(): File = File(context.filesDir, "rootfs_i386")

    /**
     * The install tree the daemon is resolved from.
     *
     * There is exactly one now: the i386 bundle extracted from assets into
     * `filesDir/mariadb`. Its `bin/`, `lib/` and `share/mariadb/` are all that QEMU
     * needs, so the data directory next to it is used unchanged.
     */
    private fun mariadbInstallRoot(): File = File(context.filesDir, "mariadb")

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        /*
         * A downloaded bundle lives outside mariadb/, so its data directory, my.cnf
         * template and share/ tree are resolved from the install the daemon came from.
         */
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
                    _dbLogs.emit("[MariaDB Info] The MariaDB bundle contains no bin/ directory.")
                    _dbLogs.emit("[MariaDB Info] It must hold bin/mariadbd, lib/ and share/mariadb/ for i386.")
                } else {
                    _dbLogs.emit("[MariaDB Info] bin/mariadbd not found in ${binDir.absolutePath}.")
                }
                _dbLogs.emit("[MariaDB Info] Database service is optional for SQLite/file gamemodes.")
                return@withContext false
            }

            val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
            /*
             * `mariadbd --initialize-insecure` is the only initializer used.
             *
             * The bundle also carries bin/mariadb-install-db, but that is a shell
             * script that shells out to `my_print_defaults`, a helper living in the
             * separate mariadb-client package. Running it produced
             * "FATAL ERROR: Could not find my_print_defaults" and a half-initialized
             * data directory. The daemon's own flag needs no external helper and is
             * what a packaged server uses anyway.
             */
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
            val installerCommand = Launcher.command(
                binary = installerBinary,
                arguments = installerArgs,
                qemu = Launcher.findQemu(context),
                rootfsDir = rootfsDir(),
                guestLibraryPath = guestLibs
            )
            if (installerCommand == null) {
                _dbLogs.emit("[MariaDB Info] ${installerBinary.name} is a 32-bit x86 binary but QEMU was not found; cannot initialize the data directory.")
                return@withContext false
            }
            val installBuilder = ProcessBuilder(installerCommand)
                .directory(installed)
                .redirectErrorStream(true)
            installBuilder.environment()["HOME"] = runtimeHome.absolutePath
            installBuilder.environment()["TMPDIR"] = tmpDir.absolutePath
            installBuilder.environment()["LD_LIBRARY_PATH"] = if (Launcher.needsQemu(installerBinary)) {
                /*
                 * Guest libs travel through `-E`; the host only needs its own libs.
                 */
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
            /*
             * MariaDB creates `mysql/global_priv.MAD` (new auth tables) or, on older
             * builds, `mysql/db.opt`. Any of the classic system tables proves the
             * datadir was really initialized; testing only those two file names
             * reported a successful install as a failure on other server versions.
             */
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
                _dbLogs.emit("[MariaDB Info] MariaDB daemon not found in ${installed.absolutePath}/bin.")
                /*
                 * A daemon that exists but cannot be made executable is almost always
                 * the Android 10+ W^X rule: the file is in app storage and not marked
                 * as a native library. Anchor points at the asset bundle.
                 */
                val blocked = File(installed, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                if (blocked != null) {
                    _dbLogs.emit("[MariaDB Info] Found ${blocked.absolutePath} but it is not executable.")
                }
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        /*
         * An i386 MariaDB build is a first-class option, not a fallback: it lets the
         * app run an ordinary Linux package that already links glibc, instead of a
         * bespoke Android build. The translator is shared with Samp.
         */
        val qemu = Launcher.findQemu(context)
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

        val command = Launcher.command(
            binary = mariadbdBinary,
            arguments = daemonArgs,
            qemu = qemu,
            rootfsDir = rootfsDir(),
            guestLibraryPath = guestLibs
        )
        if (command == null) {
            scope.launch {
                _dbLogs.emit("[MariaDB Info] ${mariadbdBinary.name} is a 32-bit x86 binary but no QEMU translator was found, so it cannot start.")
                _dbLogs.emit("[MariaDB Info] Ensure libqemu-i386.so is packaged in jniLibs/arm64-v8a; the SA-MP server needs it too.")
            }
            return
        }

        scope.launch {
            _dbLogs.emit("[MariaDB] Launching ${mariadbdBinary.name} (${Launcher.kindOf(mariadbdBinary)}) via ${if (Launcher.needsQemu(mariadbdBinary)) "QEMU" else "native exec"}.")
        }

        val usesQemu = Launcher.needsQemu(mariadbdBinary)
        val env = mapOf(
            /*
             * `LD_LIBRARY_PATH` here belongs to the *host* process. For a QEMU launch
             * the guest path is passed with `-E` inside the command instead, so the
             * arm64 bundle libs must not be offered to the translator (it would try
             * to load x86 objects as ARM).
             */
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
            /*
             * The daemon's own directory: a downloaded bundle keeps its data files
             * next to bin/ and lib/, so a relative lookup must not fall through to
             * the asset tree.
             */
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
