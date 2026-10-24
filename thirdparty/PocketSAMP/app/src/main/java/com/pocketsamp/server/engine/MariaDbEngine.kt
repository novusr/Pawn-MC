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
            File(mariadbDir, "bin/install-db"),
            File(context.filesDir, "bin/mariadb-install-db"),
            File(context.filesDir, "bin/mysql_install_db"),
            // i386 build shipped inside the shared rootfs: executed through QEMU.
            File(context.filesDir, "rootfs_i386/usr/bin/mariadb-install-db"),
            File(context.filesDir, "rootfs_i386/usr/sbin/mariadb-install-db")
        )
        return firstRunnable(candidates)
    }

    private fun findMariadbdBinary(context: Context, mariadbDir: File): File? {
        val candidates = listOf(
            File(mariadbDir, "bin/mariadbd"),
            File(mariadbDir, "bin/mysqld"),
            File(context.filesDir, "bin/mariadbd"),
            File(context.filesDir, "bin/mysqld"),
            // i386 build inside the shared rootfs: executed through QEMU.
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
    private fun isUsableBinary(file: File): Boolean = BinaryLauncher.isExecutableFile(file)

    /**
     * Whether the host can execute [binary].
     *
     * The daemon is i386, so this reduces to "was the QEMU translator found". A native
     * ARM64 or ARM binary is only accepted when the device actually reports that ABI.
     */
    private fun isRunnableOnThisDevice(binary: File): Boolean {
        if (BinaryLauncher.needsQemu(binary)) return BinaryLauncher.findQemu(context) != null
        val kind = BinaryLauncher.kindOf(binary)
        return when (kind) {
            BinaryLauncher.Kind.AARCH64 -> android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
            BinaryLauncher.Kind.ARM -> android.os.Build.SUPPORTED_ABIS.any { it.startsWith("armeabi") }
            // A non-ELF or unrecognised file: let the exec attempt decide.
            else -> true
        }
    }

    /** i386 sysroot that QEMU uses with `-L`, shared with [SampEngine]. */
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
                // engine. There is exactly one bundle now, so the diagnosis is short.
                val binDir = File(mariadbDir, "bin")
                if (!binDir.isDirectory) {
                    _dbLogs.emit("[MariaDB Info] The MariaDB asset bundle contains no bin/ directory.")
                    _dbLogs.emit("[MariaDB Info] The bundle must hold bin/mariadbd, bin/mariadb-install-db, lib/ and share/mariadb/ for i386.")
                } else {
                    _dbLogs.emit("[MariaDB Info] MariaDB binary/installer not found in app storage.")
                }
                _dbLogs.emit("[MariaDB Info] Database service is optional for SQLite/file gamemodes.")
                return@withContext false
            }

            val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
            // The installer is launched through the same ELF-aware path as the daemon:
            // an i386 `mariadb-install-db` would otherwise be executed directly and
            // fail with ENOEXEC on an ARM device.
            val installerBinary = installDb ?: mariadbd!!
            val installerArgs = if (installDb != null) {
                listOf(
                    "--datadir=${dataDir.absolutePath}",
                    "--auth-root-authentication-method=normal",
                    "--skip-test-db",
                    "--basedir=${installed.absolutePath}",
                    "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}"
                )
            } else {
                listOf(
                    "--initialize-insecure",
                    "--datadir=${dataDir.absolutePath}",
                    "--basedir=${installed.absolutePath}",
                    "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}"
                )
            }
            val guestLibs = listOf(
                File(installed, "lib").absolutePath,
                File(installed, "lib/plugin").absolutePath
            ).joinToString(":")
            val installerCommand = BinaryLauncher.command(
                binary = installerBinary,
                arguments = installerArgs,
                qemu = BinaryLauncher.findQemu(context),
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
            installBuilder.environment()["LD_LIBRARY_PATH"] = if (BinaryLauncher.needsQemu(installerBinary)) {
                // Guest libs travel through `-E`; the host only needs its own libs.
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
                _dbLogs.emit("[MariaDB Info] MariaDB daemon not found in ${installed.absolutePath}/bin.")
                // A daemon that exists but cannot be made executable is almost always
                // the Android 10+ W^X rule: the file is in app storage and not marked
                // as a native library. Anchor points at the asset bundle.
                val blocked = File(installed, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                if (blocked != null) {
                    _dbLogs.emit("[MariaDB Info] Found ${blocked.absolutePath} but it is not executable.")
                }
            }
            return
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

        // An i386 MariaDB build is a first-class option, not a fallback: it lets the
        // app run an ordinary Linux package that already links glibc, instead of a
        // bespoke Android build. The translator is shared with SampEngine.
        val qemu = BinaryLauncher.findQemu(context)
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

        val command = BinaryLauncher.command(
            binary = mariadbdBinary,
            arguments = daemonArgs,
            qemu = qemu,
            rootfsDir = rootfsDir(),
            guestLibraryPath = guestLibs
        )
        if (command == null) {
            scope.launch {
                _dbLogs.emit("[MariaDB Info] ${mariadbdBinary.name} is a 32-bit x86 binary but QEMU (libqemu-i386.so) was not found.")
                _dbLogs.emit("[MariaDB Info] Add libqemu-i386.so to jniLibs/arm64-v8a, the same one SA-MP uses.")
            }
            return
        }

        scope.launch {
            _dbLogs.emit("[MariaDB] Launching ${mariadbdBinary.name} (${BinaryLauncher.kindOf(mariadbdBinary)}) via ${if (BinaryLauncher.needsQemu(mariadbdBinary)) "QEMU" else "native exec"}.")
        }

        val usesQemu = BinaryLauncher.needsQemu(mariadbdBinary)
        val env = mapOf(
            // `LD_LIBRARY_PATH` here belongs to the *host* process. For a QEMU launch
            // the guest path is passed with `-E` inside the command instead, so the
            // arm64 bundle libs must not be offered to the translator (it would try
            // to load x86 objects as ARM).
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
