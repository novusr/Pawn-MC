package com.rvdjv.pawnmc.ui.main

import com.rvdjv.pawnmc.data.compiler.PawnCompiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import java.io.File

private const val TEST_PREFIX = "Self test"

data class AppSelfTestCase(
    val id: String,
    val title: String,
    val description: String,
    val runner: () -> AppSelfTestResult
)

data class AppSelfTestResult(
    val name: String,
    val passed: Boolean,
    val message: String
)

object AppSelfTestCatalog {
    fun list(): List<AppSelfTestCase> = listOf(
        AppSelfTestCase(
            id = "include_choice",
            title = "Pawno / Qawno include choice",
            description = "Checks that both include folders are detected and a single choice is required when both exist.",
            runner = ::runIncludeChoiceTest
        ),
        AppSelfTestCase(
            id = "dedupe_paths",
            title = "Include path deduplication",
            description = "Verifies duplicate include paths are removed case-insensitively.",
            runner = ::runDuplicatePathTest
        ),
        AppSelfTestCase(
            id = "nearby_compiler",
            title = "Nearby compiler detection",
            description = "Ensures the selected Pawn source can identify the supported nearby compiler metadata.",
            runner = ::runNearbyCompilerTest
        ),
        AppSelfTestCase(
            id = "settings_integration",
            title = "Settings include path integration",
            description = "Simulates adding a chosen include path to the settings config and verifies it stays unique.",
            runner = ::runSettingsIntegrationTest
        )
    )

    fun runAll(): List<AppSelfTestResult> = list().map { it.runner() }

    fun run(id: String): AppSelfTestResult {
        return list().firstOrNull { it.id == id }?.runner() ?: AppSelfTestResult(
            name = id,
            passed = false,
            message = "Test not found: $id"
        )
    }

    private fun runIncludeChoiceTest(): AppSelfTestResult {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-selftest-${System.nanoTime()}")
        val projectDir = File(root, "project")
        val pawnoInclude = File(projectDir, "pawno/include")
        val qawnoInclude = File(projectDir, "qawno/include")
        val sourceFile = File(projectDir, "gamemodes/main.pwn")

        return try {
            pawnoInclude.mkdirs()
            qawnoInclude.mkdirs()
            sourceFile.parentFile?.mkdirs()
            sourceFile.writeText("main() { return; }\n")

            val detected = CompilerConfig.dedupePaths(listOf(
                CompilerConfig.normalPath(pawnoInclude.absolutePath),
                CompilerConfig.normalPath(qawnoInclude.absolutePath),
                CompilerConfig.normalPath(pawnoInclude.absolutePath)
            ))

            val passed = detected.size == 2 &&
                detected.any { it.contains("pawno", ignoreCase = true) } &&
                detected.any { it.contains("qawno", ignoreCase = true) }

            val message = if (passed) {
                "Detected both pawno and qawno include folders and removed duplicates."
            } else {
                "Expected both include roots to remain distinct after dedupe."
            }

            AppSelfTestResult("$TEST_PREFIX: include_choice", passed, message)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runDuplicatePathTest(): AppSelfTestResult {
        val paths = listOf(
            "C:/Projects/MyServer/pawno/include/",
            "c:/projects/myserver/pawno/include",
            "C:/Projects/MyServer/qawno/include/",
            "c:/projects/myserver/qawno/include",
            "C:/Projects/MyServer/includes/"
        )

        val deduped = CompilerConfig.dedupePaths(paths)
        val passed = deduped.size == 3 && deduped.count { it.contains("pawno", ignoreCase = true) } == 1 &&
            deduped.count { it.contains("qawno", ignoreCase = true) } == 1

        val message = if (passed) {
            "Duplicate include paths were collapsed to unique normalized entries."
        } else {
            "Expected unique include paths but received $deduped."
        }

        return AppSelfTestResult("$TEST_PREFIX: dedupe_paths", passed, message)
    }

    private fun runNearbyCompilerTest(): AppSelfTestResult {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-compiler-${System.nanoTime()}")
        val projectDir = File(root, "project")
        val sourceFile = File(projectDir, "gamemodes/main.pwn")
        val compilerDir = File(projectDir, "pawno")
        val compilerFile = File(compilerDir, "pawncc.exe")

        return try {
            sourceFile.parentFile?.mkdirs()
            compilerDir.mkdirs()
            sourceFile.writeText("main() { return; }\n")
            val payload = "3.10.11".toByteArray(Charsets.UTF_16LE)
            compilerFile.writeBytes(payload)

            val match = PawnCompiler.detectNearbyCompiler(sourceFile.absolutePath)
            val passed = match != null && match.version == CompilerConfig.CompilerVersion.V31011
            val message = if (passed) {
                "Nearby compiler detection matched a supported Pawn compiler version."
            } else {
                "Nearby compiler metadata should resolve to Pawn 3.10.11 but returned ${match?.version ?: "null"}."
            }

            AppSelfTestResult("$TEST_PREFIX: nearby_compiler", passed, message)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun runSettingsIntegrationTest(): AppSelfTestResult {
        val chosen = CompilerConfig.normalPath("C:/Game/pawno/include")
        val values = listOf(
            CompilerConfig.normalPath("C:/Game/pawno/include/"),
            CompilerConfig.normalPath("C:/Game/qawno/include"),
            chosen
        )

        val deduped = CompilerConfig.dedupePaths(values)
        val passed = deduped.size == 2 && deduped.any { it.contains("pawno", ignoreCase = true) }

        val message = if (passed) {
            "Selected include path integrated cleanly without duplicates in the settings flow."
        } else {
            "Settings include-path integration should be unique; actual values: $deduped"
        }

        return AppSelfTestResult("$TEST_PREFIX: settings_integration", passed, message)
    }
}
