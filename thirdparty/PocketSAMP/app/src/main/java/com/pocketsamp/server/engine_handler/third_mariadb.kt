package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import com.pocketsamp.server.Locales
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.IOException

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

    private fun findMariadbdBinary(mariadbDir: File): File? {
        val candidates = listOf(
            File(mariadbDir, "bin/mariadbd"),
            File(mariadbDir, "bin/mysqld")
        )
        return firstRunnable(candidates)
    }

    /** First Android ARM64 daemon that can be started by the system linker. */
    private fun firstRunnable(candidates: List<File>): File? =
        candidates.firstOrNull { isUsableBinary(it) && isRunnableOnThisDevice(it) }

    /** Native ARM binaries are opened by Android's linker, so they need to be readable. */
    private fun isUsableBinary(file: File): Boolean =
        if (ThirdLauncher.kindOf(file) == ThirdLauncher.Kind.AARCH64) {
            file.isFile && file.length() > 0L && file.canRead()
        } else {
            ThirdLauncher.isExecutableFile(file)
        }

    /** Whether the host can execute [binary]. */
    private fun isRunnableOnThisDevice(binary: File): Boolean {
        return ThirdLauncher.kindOf(binary) == ThirdLauncher.Kind.AARCH64 &&
            android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
    }

    /** Legacy SA-MP sysroot; MariaDB never executes through QEMU. */
    private fun rootfsDir(): File = File(context.filesDir, "rootfs_i386")

    /** The install tree the daemon is resolved from, `filesDir/mariadb`. */
    private fun mariadbInstallRoot(): File = File(context.filesDir, "mariadb")

    /**
     * Classifies why the bundled daemon cannot run on this device.
     *
     * `BROKEN` — the file is not a valid ELF at all (truncated extraction,
     * placeholder, wrong content): re-extracting is the fix.
     * `WRONG_ARCH` — a valid ELF whose machine the host cannot execute (an
     * ARM64 daemon on an x86_64 emulator, for instance): the archive is fine,
     * MariaDB is simply unavailable on this device and stays optional.
     */
    private fun daemonFailure(mariadbd: File): String = when {
        !mariadbd.isFile || mariadbd.length() < 1_000_000L -> "BROKEN"
        ThirdLauncher.kindOf(mariadbd) == ThirdLauncher.Kind.AARCH64 ->
            if (android.os.Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) "NONE" else "WRONG_ARCH"
        else -> "BROKEN"
    }

    private fun versionParts(value: String): List<Int>? {
        val match = Regex("""(\d+)\.(\d+)\.(\d+)""").find(value) ?: return null
        val parts = match.groupValues.drop(1).map { part -> part.toIntOrNull() }
        return if (parts.all { it != null }) parts.filterNotNull() else null
    }

    private fun compareVersions(left: List<Int>, right: List<Int>): Int {
        for (index in 0..2) {
            val comparison = left[index].compareTo(right[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    suspend fun initializeIfNeeded(scope: CoroutineScope): Boolean = withContext(Dispatchers.IO) {
        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val dataDir = File(mariadbDir, "data")
        val tmpDir = File(mariadbDir, "tmp")
        val markerFile = File(mariadbDir, "data/.pawnmc-initialized")

        dataDir.mkdirs()
        tmpDir.mkdirs()

        val bundleVersionFile = File(installed, "share/mariadb/pawnmc-version.txt")
        if (!bundleVersionFile.isFile) {
            _dbLogs.emit(t("log.mdb.version.missing"))
            return@withContext false
        }
        val bundleVersionText = try {
            bundleVersionFile.readText().trim()
        } catch (e: IOException) {
            _dbLogs.emit(t("log.mdb.version.read.failed").replace("{reason}", e.message.orEmpty()))
            return@withContext false
        }
        val bundleVersion = versionParts(bundleVersionText)
        if (bundleVersion == null) {
            _dbLogs.emit(t("log.mdb.version.unknown").replace("{version}", bundleVersionText))
            return@withContext false
        }

        val systemDbDir = File(dataDir, "mysql")
        if (systemDbDir.isDirectory) {
            val versionFiles = listOf(
                File(dataDir, "mysql_upgrade_info"),
                markerFile
            )
            var recordedVersionText: String? = null
            for (file in versionFiles) {
                if (!file.isFile) continue
                val value = try {
                    file.readText().trim()
                } catch (e: IOException) {
                    _dbLogs.emit(t("log.mdb.version.read.failed").replace("{reason}", e.message.orEmpty()))
                    return@withContext false
                }
                if (versionParts(value) != null) {
                    recordedVersionText = value
                    break
                }
            }
            val existingVersionText = recordedVersionText
            val recordedVersion = existingVersionText?.let(::versionParts)
            if (recordedVersion == null) {
                _dbLogs.emit(t("log.mdb.version.unknown.existing"))
                return@withContext false
            }
            if (compareVersions(recordedVersion, bundleVersion) > 0) {
                _dbLogs.emit(
                    t("log.mdb.version.downgrade")
                        .replace("{existing}", existingVersionText.orEmpty())
                        .replace("{bundle}", bundleVersionText)
                )
                return@withContext false
            }
        }

        if (!markerFile.isFile) {
            val mariadbd = findMariadbdBinary(installed)

            if (mariadbd == null) {
                val binDir = File(mariadbDir, "bin")
                if (!binDir.isDirectory) {
                    _dbLogs.emit(t("log.mdb.nobin"))
                    _dbLogs.emit(t("log.mdb.nobin.note"))
                } else {
                    _dbLogs.emit(t("log.mdb.daemon.notfound".replace("{dir}", binDir.absolutePath)))
                }
                _dbLogs.emit(t("log.mdb.optional"))
                return@withContext false
            }

            when (daemonFailure(mariadbd)) {
                /*
                 * A daemon that is not a valid ARM64 ELF cannot be helped by any
                 * launcher prefix; running it would only print the linker banner
                 * and exit 0. Report the real state instead of a bare banner.
                 */
                "BROKEN" -> {
                    _dbLogs.emit(
                        t("log.mdb.daemon.invalid")
                            .replace("{name}", mariadbd.name)
                            .replace("{size}", (mariadbd.length() / 1024).toString())
                            .replace("{kind}", ThirdLauncher.kindOf(mariadbd).name)
                    )
                    _dbLogs.emit(t("log.mdb.optional"))
                    return@withContext false
                }
                /*
                 * A valid ELF of a foreign architecture (ARM64 daemon on an
                 * x86_64 emulator, for example): the archive is fine, this
                 * device just cannot execute it natively. MariaDB stays
                 * optional — the SA-MP server runs without it.
                 */
                "WRONG_ARCH" -> {
                    _dbLogs.emit(
                        t("log.mdb.daemon.wrongarch")
                            .replace("{kind}", ThirdLauncher.kindOf(mariadbd).name)
                            .replace("{abis}", android.os.Build.SUPPORTED_ABIS.take(2).joinToString(" / "))
                    )
                    _dbLogs.emit(t("log.mdb.optional"))
                    return@withContext false
                }
            }

            val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
            val bootstrapLog = File(mariadbDir, "bootstrap.log")
            val bootstrapSqlFiles = listOf(
                "mariadb_system_tables.sql",
                "mariadb_performance_tables.sql",
                "mariadb_system_tables_data.sql",
                "fill_help_tables.sql",
                "maria_add_gis_sp_bootstrap.sql",
                "mariadb_test_db.sql"
            ).map { name -> File(installed, "share/mariadb/$name") }
            val missingSqlFile = bootstrapSqlFiles.firstOrNull { !it.isFile }
            if (missingSqlFile != null) {
                _dbLogs.emit(
                    t("log.mdb.bootstrap.sql.missing")
                        .replace("{path}", missingSqlFile.absolutePath)
                )
                return@withContext false
            }
            File(installed, "lib/plugin").mkdirs()
            val installerArgs = listOf(
                "--no-defaults",
                "--bootstrap",
                "--datadir=${dataDir.absolutePath}",
                "--basedir=${installed.absolutePath}",
                "--lc-messages-dir=${File(installed, "share/mariadb").absolutePath}",
                "--plugin-dir=${File(installed, "lib/plugin").absolutePath}",
                "--log-warnings=0",
                "--max_allowed_packet=8M",
                "--net_buffer_length=16K",
                // With --no-defaults the cnf is not read, so the bootstrap used to
                // start with the built-in 128M InnoDB buffer pool — memory a phone
                // under pressure does not have, and exactly the kind of spike that
                // gets the whole process group OOM-killed mid-initialize.
                "--innodb_buffer_pool_size=16M",
                "--innodb_log_buffer_size=2M",
                "--performance_schema=OFF",
                "--skip-grant-tables"
            )
            val installerCommand = ThirdLauncher.command(
                binary = mariadbd,
                arguments = installerArgs,
                qemu = null,
                rootfsDir = rootfsDir(),
                guestLibraryPath = File(installed, "lib").absolutePath
            )
            if (installerCommand == null) {
                _dbLogs.emit(t("log.mdb.runtime.invalid").replace("{name}", mariadbd.name))
                return@withContext false
            }
            val installBuilder = ProcessBuilder(installerCommand)
                .directory(installed)
                .redirectErrorStream(true)
                .redirectOutput(bootstrapLog)
            installBuilder.environment()["HOME"] = runtimeHome.absolutePath
            installBuilder.environment()["TMPDIR"] = tmpDir.absolutePath
            installBuilder.environment()["LD_LIBRARY_PATH"] = listOf(
                File(installed, "lib").absolutePath,
                File(installed, "lib/plugin").absolutePath,
                context.applicationInfo.nativeLibraryDir,
                System.getenv("LD_LIBRARY_PATH").orEmpty()
            ).filter { it.isNotBlank() }.joinToString(":")
            val result = installBuilder.start()
            try {
                result.outputStream.bufferedWriter().use { writer ->
                    // No leading `USE mysql;`: the system-table script creates the
                    // mysql database itself, and selecting it beforehand failed with
                    // `ERROR 1049 Unknown database 'mysql'` — the abort the console
                    // showed before a single table existed.
                    for (sqlFile in bootstrapSqlFiles) {
                        sqlFile.bufferedReader().use { reader -> reader.copyTo(writer) }
                        writer.append('\n')
                    }
                }
            } catch (e: IOException) {
                /*
                 * Writing into a dead bootstrap process surfaces as
                 * `write failed: EPIPE (Broken pipe)`. The plain IOException
                 * text confused users because it appeared *above* the MariaDB
                 * banner with no context; wrap it in the db log stream so it
                 * is readable as what it is: the initializer exited early.
                 */
                result.destroy()
                val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
                val output = bootstrapLog.takeIf { it.isFile }?.readText().orEmpty()
                _dbLogs.emit(
                    t("log.mdb.init.brokenpipe")
                        .replace("{code}", exitCode.toString())
                        .replace("{reason}", e.message.orEmpty())
                )
                _dbLogs.emit(
                    t("log.mdb.init.failed")
                        .replace("{code}", exitCode.toString())
                        .replace("{output}", (output + "\n" + e.message.orEmpty()).takeLast(2000))
                )
                // A broken bootstrap is never fatal for the server: MariaDB is an
                // optional companion. Say so and continue instead of wedging the
                // startup sequence in a half-initialized state.
                _dbLogs.emit(t("log.mdb.optional"))
                return@withContext false
            }
            val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
            val output = bootstrapLog.takeIf { it.isFile }?.readText().orEmpty()
            val systemDb = File(dataDir, "mysql")
            val initialized = File(dataDir, "mysql/global_priv.MAD").exists() ||
                File(dataDir, "mysql/db.opt").exists() ||
                File(dataDir, "mysql/user.MYD").exists() ||
                File(systemDb, "user.frm").exists()
            if (exitCode != 0 || !initialized) {
                /*
                 * `/system/bin/linker64` printing its banner ("This is
                 * /system/bin/linker64, the helper program for dynamic
                 * executables") means the launcher prefix ran the linker without
                 * a binary it accepted — the daemon never started. Say so
                 * explicitly instead of dumping the raw banner.
                 */
                val rawOutput = output.takeLast(2000)
                val friendly = if (rawOutput.contains("helper program for dynamic executables")) {
                    t("log.mdb.init.linkerbanner")
                } else {
                    rawOutput
                }
                _dbLogs.emit(
                    t("log.mdb.init.failed")
                        .replace("{code}", exitCode.toString())
                        .replace("{output}", friendly)
                )
                // Same graceful path: an aborted bootstrap leaves the daemon
                // unusable for now, not the server. The SA-MP run continues.
                _dbLogs.emit(t("log.mdb.optional"))
                return@withContext false
            }
            bootstrapLog.delete()
            markerFile.writeText(bundleVersionText)
            if (output.isNotBlank()) _dbLogs.emit(output.takeLast(2000))
        }

        _dbLogs.emit(t("log.mdb.initialized"))
        true
    }

    fun start(scope: CoroutineScope) {
        if (isRunning) return

        val installed = mariadbInstallRoot()
        val mariadbDir = File(context.filesDir, "mariadb")
        val runtimeHome = File(mariadbDir, "runtime-home").apply { mkdirs() }
        val mariadbdBinary = findMariadbdBinary(installed)
        val cnfFile = File(mariadbDir, "mysql_list.cnf")
        val dataDir = File(mariadbDir, "data").apply { mkdirs() }
        val tmpDir = File(mariadbDir, "tmp").apply { mkdirs() }
        val shareDir = File(installed, "share/mariadb")

        if (mariadbdBinary == null) {
            scope.launch {
                _dbLogs.emit(t("log.mdb.daemon.missing".replace("{dir}", installed.absolutePath)))
                val blocked = File(installed, "bin/mariadbd").takeIf { it.isFile && it.length() > 0 }
                if (blocked != null) {
                    _dbLogs.emit(t("log.mdb.notexecutable".replace("{path}", blocked.absolutePath)))
                }
            }
            return
        }

        when (daemonFailure(mariadbdBinary)) {
            "BROKEN" -> {
                scope.launch {
                    _dbLogs.emit(
                        t("log.mdb.daemon.invalid")
                            .replace("{name}", mariadbdBinary.name)
                            .replace("{size}", (mariadbdBinary.length() / 1024).toString())
                            .replace("{kind}", ThirdLauncher.kindOf(mariadbdBinary).name)
                    )
                }
                return
            }
            "WRONG_ARCH" -> {
                scope.launch {
                    _dbLogs.emit(
                        t("log.mdb.daemon.wrongarch")
                            .replace("{kind}", ThirdLauncher.kindOf(mariadbdBinary).name)
                            .replace("{abis}", android.os.Build.SUPPORTED_ABIS.take(2).joinToString(" / "))
                    )
                }
                return
            }
        }

        val socketFile = File(tmpDir, "mysql.sock")
        val pidFile = File(tmpDir, "mysql.pid")

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
            qemu = null,
            rootfsDir = rootfsDir(),
            guestLibraryPath = guestLibs
        )
        if (command == null) {
            scope.launch {
                _dbLogs.emit(t("log.mdb.runtime.invalid").replace("{name}", mariadbdBinary.name))
            }
            return
        }

        scope.launch {
            _dbLogs.emit(
                t("log.mdb.launching")
                    .replace("{name}", mariadbdBinary.name)
                    .replace("{kind}", ThirdLauncher.kindOf(mariadbdBinary).name)
                    .replace("{mode}", "Android linker")
            )
        }

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
