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
        compilerOutput: String,
        config: CompilerConfig,
        onPathsAdded: (List<String>) -> Unit
    ) {
        if (dismissed) return

        val regex = Regex("cannot read from file:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        val matchResult = regex.find(compilerOutput)
        val missingFileName = matchResult?.groups?.get(1)?.value ?: return
        val fileToFind = if (!missingFileName.endsWith(".inc") && !missingFileName.endsWith(".pwn")) {
            "$missingFileName.inc"
        } else missingFileName

        val file = File(filePath)
        var currentDir = file.parentFile
        var levelsUp = 0
        val maxLevels = 8
        val possibleIncludes = listOf("pawno/include", "qawno/include", "include", "includes", "lib", "dependencies")

        activity.lifecycleScope.launch(Dispatchers.IO) {
            val foundPaths = mutableListOf<String>()
            var hitRoot = false

            while (currentDir != null && levelsUp < maxLevels && !hitRoot) {
                val dir = currentDir ?: break
                for (inc in possibleIncludes) {
                    val checkDir = File(dir, inc)
                    if (checkDir.exists() && checkDir.isDirectory) {
                        val potentialFile = File(checkDir, fileToFind)
                        val potentialFileWithoutExt = File(checkDir, missingFileName)
                        
                        if (potentialFile.exists() || potentialFileWithoutExt.exists()) {
                            val absPath = checkDir.absolutePath
                            if (absPath !in foundPaths && !config.includePaths.contains(absPath)) {
                                foundPaths.add(absPath)
                            }
                        }
                    }
                }

                val rootIndicators = listOf("server.cfg", "config.json", "gamemodes", "plugins")
                if (rootIndicators.any { File(dir, it).exists() }) {
                    hitRoot = true
                }

                currentDir = dir.parentFile
                levelsUp++
            }

            if (foundPaths.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (activity.isFinishing || activity.isDestroyed) return@withContext

                    val pathList = foundPaths.joinToString("\n") { "• $it" }
                    PawnDialog(activity)
                        .setIcon(R.drawable.ic_folder_open, R.color.accent_info)
                        .setTitle("Include Paths Detected")
                        .setMessage("Compilation failed due to a missing include file: \"$missingFileName\".\n\nWe found it in these directories:\n$pathList\n\nWould you like to add them to your compiler configuration?")
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
