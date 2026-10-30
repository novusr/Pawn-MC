package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration._DirectoryManager

class ThirdExtractor(private val context: Context) {

    companion object {
        private const val TAG = "ThirdExtractor"
        private const val PREF_NAME = "pocketsamp_assets"
        private const val KEY_INSTALLED_VERSION = "assets_installed_version"
        /** Bump when the runtime archive set changes. */
        private const val CURRENT_VERSION = 10
        /** The only MariaDB archive PocketSAMP uses. */
        private const val BUNDLE_ASSET = "mariadb_i386"

        fun getServerDirectory(context: Context): File {
            return _DirectoryManager.getServerDirectory(context)
        }
    }

    private fun t(key: String): String = Locales.text(Locales.current, key)

    suspend fun isExtracted(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_INSTALLED_VERSION, 0)
        val rootfsDir = File(context.filesDir, "rootfs_i386")
        val binDir = File(context.filesDir, "bin")
        val mariadbDir = File(context.filesDir, "mariadb")

        val marker = File(context.filesDir, "runtime.ready")
        installedVersion == CURRENT_VERSION &&
            marker.isFile &&
            File(binDir, "samp03svr").isFile &&
            File(rootfsDir, "lib").isDirectory &&
            File(mariadbDir, "bin/mariadbd").isFile
    }

    suspend fun extractAll(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir

        onProgress(t("log.x.runtime"))
        val rootfsDir = File(filesDir, "rootfs_i386")
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        extractArchiveAsset("rootfs_i386", rootfsDir)

        onProgress(t("log.x.samp"))
        val binDir = File(filesDir, "bin").apply { mkdirs() }
        extractArchiveAsset("samp_base", binDir)
        File(binDir, "samp03svr").setExecutable(true, false)
        File(binDir, "announce").setExecutable(true, false)

        val defaultDir = _DirectoryManager.getDefaultServerDirectory()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }

        onProgress(t("log.x.mariadb"))
        val mariadbDir = File(filesDir, "mariadb")
        if (!mariadbDir.exists()) mariadbDir.mkdirs()

        if (archiveExists(BUNDLE_ASSET)) {
            extractArchiveAsset(BUNDLE_ASSET, mariadbDir)
        } else {
            onProgress(t("log.x.bundle.missing").replace("{name}", BUNDLE_ASSET))
        }

        File(mariadbDir, "bin/mariadbd").let { if (it.exists()) it.setExecutable(true, false) }
        File(mariadbDir, "bin/mariadb-install-db").let { if (it.exists()) it.setExecutable(true, false) }

        val daemon = File(mariadbDir, "bin/mariadbd")
        val mariadbComplete = daemon.isFile && daemon.length() > 0
        if (!mariadbComplete) {
            onProgress(t("log.x.daemon.missing"))
        } else {
            onProgress(t("log.x.daemon.ready").replace("{kb}", (daemon.length() / 1024).toString()).replace("{kind}", ThirdLauncher.kindOf(daemon).name))
            if (ThirdLauncher.findQemu(context) == null) {
                onProgress(t("log.x.qemu.error"))
                onProgress(t("log.x.qemu.note"))
            }
        }

        onProgress(t("log.x.config"))
        val mariadbConfig = File(mariadbDir, "mysql_list.cnf")
        val renderedConfig = context.assets.open("mysql_list.cnf").bufferedReader().use { it.readText() }
        mariadbConfig.writeText(renderedConfig)

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INSTALLED_VERSION, CURRENT_VERSION).apply()
        // The marker is written only when every archive was present and unpacked.
        val allPresent = File(binDir, "samp03svr").isFile &&
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

    /** True when the archive for [baseName] has been downloaded into storage. */
    private fun archiveExists(baseName: String): Boolean =
        ThirdRuntime.archiveFile(context, baseName).let { it.isFile && it.length() > 0 }

    /** Unpacks one archive from `Documents/PawnMC/storage/` into [outputDir]. */
    private fun extractArchiveAsset(baseName: String, outputDir: File) {
        val stored = ThirdRuntime.archiveFile(context, baseName)
        if (!stored.isFile || stored.length() == 0L) {
            throw IllegalStateException(
                t("log.x.archive.notdownloaded").replace("{name}", baseName).replace("{path}", stored.absolutePath)
            )
        }
        Log.i(TAG, "Unpacking ${stored.absolutePath} into ${outputDir.absolutePath}")
        FileInputStream(stored).use { ThirdUnpacker.extract(it, outputDir) }
    }
}
