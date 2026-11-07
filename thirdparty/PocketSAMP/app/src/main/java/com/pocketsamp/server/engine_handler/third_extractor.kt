package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration._DirectoryManager
import com.pocketsamp.server.configuration.Reader as _Reader

class ThirdExtractor(private val context: Context) {

    companion object {
        private const val TAG = "ThirdExtractor"
        private const val PREF_NAME = "pocketsamp_assets"
        private const val KEY_INSTALLED_VERSION = "assets_installed_version"
        /** Bump when the archive set changes. */
        private const val CURRENT_VERSION = 13
        /** The only MariaDB archive PocketSAMP uses. */
        private const val MARIADB_ARCHIVE = ThirdRuntime.MARIADB_ARCHIVE

        fun getServerDirectory(context: Context): File {
            return _DirectoryManager.getServerDirectory(context)
        }
    }

    private fun t(key: String): String = Locales.text(Locales.current, key)

    suspend fun isExtracted(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_INSTALLED_VERSION, 0)
        val rootfsDir = File(context.filesDir, "rootfs_i386")
        val mariadbDir = File(context.filesDir, "mariadb")

        val marker = File(context.filesDir, "runtime.ready")
        // The samp template now lives in Downloads/.Pawn-MC/template (the shared
        // template location moved out of Documents, see _DirectoryManager).
        val templateDir = _DirectoryManager.templateDirectory()
        installedVersion == CURRENT_VERSION &&
            marker.isFile &&
            File(templateDir, "samp03svr").isFile &&
            File(rootfsDir, "lib").isDirectory &&
            File(mariadbDir, "bin/mariadbd").let {
                it.isFile && ThirdLauncher.kindOf(it) == ThirdLauncher.Kind.AARCH64
            }
    }

    suspend fun extractAll(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir

        onProgress(t("log.x.runtime"))
        val rootfsDir = File(filesDir, "rootfs_i386")
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        extractRuntimeArchive(ThirdRuntime.ROOTFS_ARCHIVE, rootfsDir)

        onProgress(t("log.x.samp"))
        // r_samp.tar.gz is unpacked into the shared storage template area
        // (Downloads/.Pawn-MC/template/), so the base gamemode project is
        // user-visible and reusable; the File Manager defaults there when no
        // folder has been picked yet.
        val templateDir = _DirectoryManager.templateDirectory().apply { mkdirs() }
        extractRuntimeArchive(ThirdRuntime.SAMP_ARCHIVE, templateDir)
        File(templateDir, "samp03svr").setExecutable(true, false)
        // Shared-storage writes (FUSE) can come out truncated; a short samp03svr
        // would later fail with 'Exec format error' at launch. Re-extract just
        // this binary once when it does not read as an ELF.
        val extractedServer = File(templateDir, "samp03svr")
        if (extractedServer.isFile &&
            ThirdLauncher.kindOf(extractedServer) != ThirdLauncher.Kind.I386
        ) {
            Log.w(TAG, "Template samp03svr is not a valid i386 ELF (${extractedServer.length()} bytes); re-extracting")
            runCatching { extractRuntimeArchive(ThirdRuntime.SAMP_ARCHIVE, templateDir) }
            extractedServer.setExecutable(true, false)
        }

        onProgress(t("log.x.mariadb"))
        val mariadbDir = File(filesDir, "mariadb")
        if (!mariadbDir.exists()) mariadbDir.mkdirs()

        if (archiveExists(MARIADB_ARCHIVE)) {
            extractRuntimeArchive(MARIADB_ARCHIVE, mariadbDir)
        } else {
            onProgress(t("log.x.bundle.missing").replace("{name}", MARIADB_ARCHIVE))
        }

        File(mariadbDir, "bin/mariadbd").let { if (it.exists()) it.setExecutable(true, false) }

        /*
         * Host-side ecosystem binaries (QEMU translator, MariaDB support .so) ship
         * in their own archive because they cannot be built on the device. They are
         * unpacked into filesDir so `ThirdLauncher.findQemu` picks the translator up
         * from `filesDir/arm64-v8a/` when the APK's jniLibs copy is missing.
         */
        if (archiveExists(ThirdRuntime.ECOSYSTEM_ARCHIVE)) {
            extractRuntimeArchive(ThirdRuntime.ECOSYSTEM_ARCHIVE, filesDir)
            File(filesDir, "arm64-v8a").listFiles()?.forEach { library ->
                if (library.isFile) library.setExecutable(true, false)
            }
        } else {
            onProgress(t("log.x.bundle.missing").replace("{name}", ThirdRuntime.ECOSYSTEM_ARCHIVE))
        }

        val daemon = File(mariadbDir, "bin/mariadbd")
        val mariadbComplete = daemon.isFile &&
            daemon.length() > 0L &&
            ThirdLauncher.kindOf(daemon) == ThirdLauncher.Kind.AARCH64
        if (!mariadbComplete) {
            onProgress(t("log.x.daemon.missing"))
        } else {
            onProgress(t("log.x.daemon.ready").replace("{kb}", (daemon.length() / 1024).toString()).replace("{kind}", ThirdLauncher.kindOf(daemon).name))
        }
        if (ThirdLauncher.findQemu(context) == null) {
            onProgress(t("log.x.qemu.error"))
            onProgress(t("log.x.qemu.note"))
        }

        onProgress(t("log.x.config"))
        val mariadbConfig = File(mariadbDir, "mysql_list.cnf")
        val renderedConfig = context.assets.open("mysql_list.cnf").bufferedReader().use { it.readText() }
        mariadbConfig.writeText(renderedConfig)

        // Ship a starter server.cfg in the template folder so a base project
        // extracted from r_samp.tar.gz starts without manual setup.
        if (!File(templateDir, "server.cfg").isFile) {
            runCatching { _Reader.ensureDefaultConfig(templateDir) }
        }

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INSTALLED_VERSION, CURRENT_VERSION).apply()
        // The marker is written only when every archive was present and unpacked.
        val allPresent = File(templateDir, "samp03svr").isFile &&
            File(rootfsDir, "lib").isDirectory &&
            mariadbComplete
        if (allPresent) {
            File(filesDir, "runtime.ready").writeText(CURRENT_VERSION.toString())
            onProgress(t("log.x.complete"))
        } else {
            File(filesDir, "runtime.ready").delete()
            onProgress(t("log.x.incomplete"))
        }
    }

    /** Checks whether [archiveName] has been downloaded. */
    private fun archiveExists(archiveName: String): Boolean =
        ThirdRuntime.archiveFile(context, archiveName).let { it.isFile && it.length() > 0 }

    /** Extracts [archiveName] into [outputDir]. */
    private fun extractRuntimeArchive(archiveName: String, outputDir: File) {
        val stored = ThirdRuntime.archiveFile(context, archiveName)
        if (!stored.isFile || stored.length() == 0L) {
            throw IllegalStateException(
                t("log.x.archive.notdownloaded").replace("{name}", archiveName).replace("{path}", stored.absolutePath)
            )
        }
        Log.i(TAG, "Unpacking ${stored.absolutePath} into ${outputDir.absolutePath}")
        FileInputStream(stored).use { ThirdUnpacker.extract(it, outputDir) }
    }
}
