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
                "--net_buffer_length=16K"
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
                    writer.append("USE mysql;\nSET @auth_root_socket=NULL;\n")
                    for (sqlFile in bootstrapSqlFiles) {
                        sqlFile.bufferedReader().use { reader -> reader.copyTo(writer) }
                        writer.append('\n')
                    }
                }
            } catch (e: IOException) {
                result.destroy()
                val exitCode = withContext(Dispatchers.IO) { result.waitFor() }
                val output = bootstrapLog.takeIf { it.isFile }?.readText().orEmpty()
                _dbLogs.emit(
                    t("log.mdb.init.failed")
                        .replace("{code}", exitCode.toString())
                        .replace("{output}", (output + "\n" + e.message.orEmpty()).takeLast(2000))
                )
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
                _dbLogs.emit(t("log.mdb.init.failed".replace("{code}", exitCode.toString()).replace("{output}", output.takeLast(2000))))
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
