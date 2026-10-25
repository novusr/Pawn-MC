package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

import com.pocketsamp.server.config.DirectoryManager

class Extractor(private val context: Context) {

    companion object {
        private const val TAG = "Extractor"
        private const val PREF_NAME = "pocketsamp_assets"
        private const val KEY_INSTALLED_VERSION = "assets_installed_version"
        // Bumped when the runtime archive set changes. 10 ships the complete i386 bundle
        // (daemon + all 13 shared libraries) and drops the arm64 one.
        private const val CURRENT_VERSION = 10
        /** The only MariaDB archive PocketSAMP uses. */
        private const val BUNDLE_ASSET = "mariadb_i386"

        fun getServerDirectory(context: Context): File {
            return DirectoryManager.getServerDirectory(context)
        }
    }

    suspend fun isExtracted(): Boolean = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val installedVersion = prefs.getInt(KEY_INSTALLED_VERSION, 0)
        val rootfsDir = File(context.filesDir, "rootfs_i386")
        val binDir = File(context.filesDir, "bin")
        val mariadbDir = File(context.filesDir, "mariadb")

        // Checking that the directories merely exist is not enough now that the
        // archives are downloaded on demand: `extractAll` creates them before it has
        // anything to put inside. Each tree must contain its key file, and the marker
        // written at the end of a successful run proves the set was complete.
        val marker = File(context.filesDir, "runtime.ready")
        installedVersion == CURRENT_VERSION &&
            marker.isFile &&
            File(binDir, "samp03svr").isFile &&
            File(rootfsDir, "lib").isDirectory &&
            File(mariadbDir, "bin/mariadbd").isFile
    }

    suspend fun extractAll(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir

        // 1. Extract rootfs_i386 (x86 C-Runtime sysroot for QEMU)
        onProgress("Extracting x86 C-Runtime...")
        val rootfsDir = File(filesDir, "rootfs_i386")
        if (!rootfsDir.exists()) rootfsDir.mkdirs()
        extractArchiveAsset("rootfs_i386", rootfsDir)

        // 2. Extract engine binaries to internal protected storage (context.filesDir/bin)
        onProgress("Deploying SA-MP server binary...")
        val binDir = File(filesDir, "bin").apply { mkdirs() }
        extractArchiveAsset("samp_base", binDir)
        File(binDir, "samp03svr").setExecutable(true, false)
        File(binDir, "announce").setExecutable(true, false)

        // Ensure default public server folder exists for user convenience (/storage/emulated/0/PocketSAMP)
        val defaultDir = DirectoryManager.getDefaultServerDirectory()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }

        // 3. Extract the MariaDB engine.
        //
        // Only the i386 bundle exists now: PocketSAMP runs MariaDB through the same
        // qemu-i386 translator as the SA-MP server, so the binary is an ordinary
        // Debian build that links the glibc already present in rootfs_i386. There is
        // no native arm64 path any more, which also means no bespoke Android build is
        // needed to keep the database working.
        onProgress("Deploying MariaDB engine (i386 via QEMU)...")
        val mariadbDir = File(filesDir, "mariadb")
        if (!mariadbDir.exists()) mariadbDir.mkdirs()

        if (archiveExists(BUNDLE_ASSET)) {
            extractArchiveAsset(BUNDLE_ASSET, mariadbDir)
        } else {
            onProgress("$BUNDLE_ASSET.tar.gz was not downloaded; SQL stays disabled.")
        }

        // Ensure binaries have executable bit set if present
        File(mariadbDir, "bin/mariadbd").let { if (it.exists()) it.setExecutable(true, false) }
        File(mariadbDir, "bin/mariadb-install-db").let { if (it.exists()) it.setExecutable(true, false) }

        // Verify the deployment instead of trusting the extraction loop, and report the
        // one prerequisite no archive can satisfy on its own.
        val daemon = File(mariadbDir, "bin/mariadbd")
        val mariadbComplete = daemon.isFile && daemon.length() > 0
        if (!mariadbComplete) {
            onProgress("MariaDB archive has no bin/mariadbd; SQL stays disabled.")
        } else {
            onProgress("MariaDB daemon ready: ${daemon.length() / 1024} KB, ${Launcher.kindOf(daemon).name}.")
            if (Launcher.findQemu(context) == null) {
                onProgress("ERROR: no QEMU translator (libqemu-i386.so). Both the SA-MP server and")
                onProgress("MariaDB are 32-bit x86 binaries; without it neither can start.")
            }
        }

        // 4. Install MariaDB configuration.
        //
        // The template uses paths relative to the install root, so it is copied
        // verbatim: rewriting absolute paths here only worked while the template still
        // hard-coded an application id, and that id was never PawnMC's.
        onProgress("Configuring low-memory database...")
        val mariadbConfig = File(mariadbDir, "my.cnf")
        val renderedConfig = context.assets.open("my.cnf").bufferedReader().use { it.readText() }
        mariadbConfig.writeText(renderedConfig)

        // Save extraction state
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INSTALLED_VERSION, CURRENT_VERSION).apply()
        // Written only when every archive was present AND unpacked. A partial run must
        // not leave it behind: `isExtracted` treats the marker as proof of completeness,
        // so writing it after skipping MariaDB would make the next launch believe the
        // database was installed and never retry it once the download completed.
        val allPresent = File(binDir, "samp03svr").isFile &&
            File(rootfsDir, "lib").isDirectory &&
            mariadbComplete
        if (allPresent) {
            File(filesDir, "runtime.ready").writeText(CURRENT_VERSION.toString())
            onProgress("Initialization complete!")
        } else {
            File(filesDir, "runtime.ready").delete()
            onProgress("Runtime is incomplete; the missing archives will be retried on the next start.")
        }
    }

    /** True when the archive for [baseName] has been downloaded into storage. */
    private fun archiveExists(baseName: String): Boolean =
        Downloader.archiveFile(context, baseName).let { it.isFile && it.length() > 0 }

    /**
     * Unpacks one archive from `Documents/PawnMC/storage/` into [outputDir].
     *
     * Each of the three archives is unpacked independently, into its own destination,
     * so one missing or corrupt file cannot take the others down: the server can still
     * start when only `samp_base` arrived. Extraction itself is [Unpacker], which
     * handles gzip detection, symlinks and the executable bit.
     */
    private fun extractArchiveAsset(baseName: String, outputDir: File) {
        val stored = Downloader.archiveFile(context, baseName)
        if (!stored.isFile || stored.length() == 0L) {
            throw IllegalStateException(
                "Archive $baseName has not been downloaded; expected ${stored.absolutePath}"
            )
        }
        Log.i(TAG, "Unpacking ${stored.absolutePath} into ${outputDir.absolutePath}")
        FileInputStream(stored).use { Unpacker.extract(it, outputDir) }
    }
}
