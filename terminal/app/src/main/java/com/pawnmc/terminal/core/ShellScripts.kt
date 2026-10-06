package com.pawnmc.terminal.core

import android.content.Context
import java.io.File

/**
 * The `sandbox.sh`, `setup.sh`, `init.sh` and `utils.sh` scripts.
 *
 * They ship as assets because the shell needs them inside the container, where the APK
 * is not readable. Each is copied into `files/local/bin` and refreshed when its APK asset
 * changes, so setup fixes are applied to existing installs without clearing app data.
 * User edits to these managed scripts are overwritten when the corresponding asset changes.
 *
 * `sandbox.sh` requires the exec bit, which is why the copy is not done by Gradle:
 * assets lose their mode, and only the app layer can restore it.
 */
internal object ShellScripts {

    /** Script name -> asset path. The asset extension is `.sh`, the copied name is not. */
    private val SCRIPTS = listOf("setup", "sandbox", "init", "utils")

    fun install(context: Context) {
        SCRIPTS.forEach { install(context, it) }
    }

    /**
     * Materialises one script and returns its path.
     *
     * @param name script name without the `.sh` suffix, e.g. `sandbox`.
     */
    fun install(context: Context, name: String): File {
        val destination = File(TerminalPaths.binDir(context), name)
        val assetPath = "terminal/$name.sh"
        val assetText = context.assets.open(assetPath).bufferedReader().use { it.readText() }
        val currentText = destination.takeIf { it.isFile }
            ?.bufferedReader()
            ?.use { it.readText() }
        if (currentText != assetText) {
            destination.parentFile?.mkdirs()
            destination.writeText(assetText)
        }
        // Both proot and the shell need this to be execable; setExecutable is a no-op on
        // filesystems that do not model the bit, hence the unchecked result.
        destination.setExecutable(true, false)
        return destination
    }

    fun sandboxScript(context: Context): File = install(context, "sandbox")
}
