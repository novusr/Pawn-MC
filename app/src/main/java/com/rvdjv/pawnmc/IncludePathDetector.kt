package com.rvdjv.pawnmc

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object IncludePathDetector {

    private var dismissed = false

    fun detect(
        activity: AppCompatActivity,
        filePath: String,
        config: CompilerConfig,
        onPathsAdded: (List<String>) -> Unit
    ) {
        if (dismissed) return

        val file = File(filePath)
        var currentDir = file.parentFile
        var levelsUp = 0
        val maxLevels = 4
        val possibleIncludes = listOf("pawno/include", "qawno/include", "include")

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val foundPaths = mutableListOf<String>()
            while (currentDir != null && levelsUp < maxLevels) {
                val dir = currentDir ?: break
                for (inc in possibleIncludes) {
                    val checkDir = File(dir, inc)
                    if (checkDir.exists() && checkDir.isDirectory) {
                        val absPath = checkDir.absolutePath
                        if (absPath !in foundPaths) {
                            foundPaths.add(absPath)
                        }
                    }
                }

                // stop at project root
                val rootIndicators = listOf("server.cfg", "config.json", "gamemodes", "filterscripts", "scriptfiles")
                if (rootIndicators.any { File(dir, it).exists() }) break

                currentDir = dir.parentFile
                levelsUp++
            }

            if (foundPaths.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val pathList = foundPaths.joinToString("\n") { "• $it" }
                    PawnDialog(activity)
                        .setIcon(R.drawable.ic_folder_open, R.color.accent_info)
                        .setTitle("Include Paths Detected")
                        .setMessage("Compilation failed due to a missing include file.\n\nThe following include paths were found:\n$pathList\n\nWould you like to add them to your compiler configuration?")
                        .setPositiveButton("Add Paths") {
                            it.dismiss()
                            config.includePaths = config.includePaths + foundPaths
                            onPathsAdded(foundPaths)
                        }
                        .setNegativeButton("Cancel") {
                            it.dismiss()
                            dismissed = true
                        }
                        .show()
                }
            }
        }
    }

    fun reset() {
        dismissed = false
    }
}
