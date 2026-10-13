package com.pocketsamp.server.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

import com.pocketsamp.server.config.ServerDirectoryManager

class AssetExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AssetExtractor"
        private const val PREF_NAME = "pocketsamp_assets"
        private const val KEY_INSTALLED_VERSION = "assets_installed_version"
        private const val CURRENT_VERSION = 6

        fun getServerDirectory(context: Context): File {
            return ServerDirectoryManager.getServerDirectory(context)
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
        val defaultDir = ServerDirectoryManager.getDefaultServerDirectory()
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }

        // 3. Extract mariadb_arm64
        onProgress("Deploying MariaDB ARM64 engine...")
        val mariadbDir = File(filesDir, "mariadb")
        if (!mariadbDir.exists()) mariadbDir.mkdirs()
        extractArchiveAsset("mariadb_arm64", mariadbDir)

        // Ensure binaries have executable bit set if present
        File(mariadbDir, "bin/mariadbd").let { if (it.exists()) it.setExecutable(true, false) }
        File(mariadbDir, "bin/mariadb-install-db").let { if (it.exists()) it.setExecutable(true, false) }

        // 4. Copy my.cnf template
        onProgress("Configuring low-memory database...")
        copyAssetToFile("my.cnf", File(mariadbDir, "my.cnf"))

        // Save extraction state
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_INSTALLED_VERSION, CURRENT_VERSION).apply()
        onProgress("Initialization complete!")
    }

    private fun extractArchiveAsset(baseName: String, outputDir: File) {
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
            throw IllegalStateException("Asset not found for $baseName (checked: $candidates)")
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
                var entry: TarArchiveEntry? = tarInput.nextTarEntry
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
                    entry = tarInput.nextTarEntry
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
