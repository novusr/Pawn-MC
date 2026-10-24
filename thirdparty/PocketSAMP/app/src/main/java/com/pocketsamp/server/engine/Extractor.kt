package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

import com.pocketsamp.server.config.DirectoryManager

class Extractor(private val context: Context) {

    companion object {
        private const val TAG = "Extractor"
        private const val PREF_NAME = "pocketsamp_assets"
        private const val KEY_INSTALLED_VERSION = "assets_installed_version"
        // Bumped when the asset set changes. 10 ships the complete i386 bundle
        // (daemon + all 13 shared libraries) and drops the arm64 one.
        private const val CURRENT_VERSION = 10
        /** The only MariaDB bundle PocketSAMP ships. */
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

        installedVersion == CURRENT_VERSION && rootfsDir.exists() && binDir.exists() && mariadbDir.exists()
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
            onProgress("No $BUNDLE_ASSET in assets; SQL stays disabled.")
        }

        // Ensure binaries have executable bit set if present
        File(mariadbDir, "bin/mariadbd").let { if (it.exists()) it.setExecutable(true, false) }
        File(mariadbDir, "bin/mariadb-install-db").let { if (it.exists()) it.setExecutable(true, false) }

        // Verify the deployment instead of trusting the extraction loop, and report the
        // one prerequisite a bundle cannot satisfy on its own.
        val daemon = File(mariadbDir, "bin/mariadbd")
        if (!daemon.isFile || daemon.length() == 0L) {
            onProgress("MariaDB daemon missing from the asset bundle (bin/mariadbd); SQL stays disabled.")
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
        onProgress("Initialization complete!")
    }

    /** True when [baseName] can be obtained from storage or from assets. */
    private fun archiveExists(baseName: String): Boolean =
        Downloader.archiveFile(context, baseName).let { it.isFile && it.length() > 0 } ||
            listOf("$baseName.tar.gz", "$baseName.tar", "$baseName.tgz", baseName)
                .any { name -> runCatching { context.assets.open(name).close() }.isSuccess }

    /**
     * Unpacks `baseName` from shared storage, falling back to the APK assets.
     *
     * `Documents/PawnMC/storage/` is preferred because the archives are downloaded
     * there (see [Downloader]) instead of being bundled; assets remain as a fallback
     * for builds that still embed them.
     */
    private fun extractArchiveAsset(baseName: String, outputDir: File) {
        val stored = Downloader.archiveFile(context, baseName)
        if (stored.isFile && stored.length() > 0) {
            Log.i(TAG, "Unpacking ${stored.absolutePath} into ${outputDir.absolutePath}")
            FileInputStream(stored).use { Unpacker.extract(it, outputDir) }
            return
        }

        // AAPT sometimes strips .gz during APK packaging, leaving .tar
        val candidates = listOf(
            "$baseName.tar.gz",
            "$baseName.tar",
            "$baseName.tgz",
            baseName
        )

        var openedStream: InputStream? = null
        var matchedName: String? = null
        for (candidate in candidates) {
            try {
                openedStream = context.assets.open(candidate)
                matchedName = candidate
                break
            } catch (ignored: Exception) {
            }
        }

        if (openedStream == null) {
            throw IllegalStateException(
                "Archive $baseName is in neither ${stored.parent} nor the APK assets (checked: $candidates)"
            )
        }

        Log.i(TAG, "Unpacking asset: $matchedName into ${outputDir.absolutePath}")

        openedStream.use { rawInput ->
            val buffered = BufferedInputStream(rawInput)
            buffered.mark(4)
            val b1 = buffered.read()
            val b2 = buffered.read()
            buffered.reset()

            // Check GZIP magic bytes: 0x1F, 0x8B
            val isGzip = (b1 == 0x1f && b2 == 0x8b)
            val tarStream: InputStream = if (isGzip) {
                GZIPInputStream(buffered)
            } else {
                buffered
            }

            TarArchiveInputStream(tarStream).use { tarInput ->
                var entry: TarArchiveEntry? = tarInput.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    val outputFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        if (entry.isSymbolicLink) {
                            try {
                                java.nio.file.Files.deleteIfExists(outputFile.toPath())
                                java.nio.file.Files.createSymbolicLink(
                                    outputFile.toPath(),
                                    java.nio.file.Paths.get(entry.linkName)
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Symlink creation skipped: ${entry.name} -> ${entry.linkName}")
                            }
                        } else {
                            FileOutputStream(outputFile).use { fos ->
                                var count: Int
                                while (tarInput.read(buffer).also { count = it } != -1) {
                                    fos.write(buffer, 0, count)
                                }
                            }
                            if ((entry.mode and 0x49) != 0) { // Executable bit (0111 octal)
                                outputFile.setExecutable(true, false)
                            }
                        }
                    }
                    entry = tarInput.nextEntry
                }
            }
        }
    }

    private fun copyAssetToFile(assetName: String, destination: File) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}
