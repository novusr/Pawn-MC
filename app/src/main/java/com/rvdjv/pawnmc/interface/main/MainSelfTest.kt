package com.rvdjv.pawnmc.`interface`.main

import com.rvdjv.pawnmc.data.compiler.CaseConversion
import com.rvdjv.pawnmc.data.compiler.Compiler
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
        ),
        AppSelfTestCase(
            id = "ignore_case_workflow",
            title = "Ignore case filesystem workflow",
            description = "Creates a mixed case project, verifies the .backup copy, the lowercased names, " +
                "the lowercased #include references, and that a second run is skipped while the backup exists.",
            runner = ::runIgnoreCaseWorkflowTest
        )
    )

    fun runAll(): List<AppSelfTestResult> = list().map { it.runner() }

    fun run(id: String): AppSelfTestResult {
        return list().firstOrNull { it.id == id }?.runner?.invoke() ?: AppSelfTestResult(
            name = id,
            passed = false,
            message = "Test not found: $id"
        )
    }

    private fun runDuplicatePathTest(): AppSelfTestResult {
        val paths = listOf(
            "com.android/MyServer/pawno/include/",
            "com.android/myserver/pawno/include",
            "com.android/MyServer/qawno/include/",
            "com.android/myserver/qawno/include",
            "com.android/MyServer/includes/"
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
            val payload = CompilerConfig.STR_V31011.toByteArray(Charsets.UTF_16LE)
            compilerFile.writeBytes(payload)

            val match = Compiler.detectNearbyCompiler(sourceFile.absolutePath)
            val passed = match != null && match.version == CompilerConfig.CompilerVersion.V31011
            val message = if (passed) {
                "Nearby compiler detection matched a supported Pawn compiler version."
            } else {
                "Nearby compiler metadata should resolve to ${CompilerConfig.STR_VERSION_31011} but returned ${match?.version ?: "null"}."
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

        // The add flow must reject a folder that is already registered, matching what the
        // Settings manual add, folder picker, and auto-discovery all do.
        val known = listOf(
            CompilerConfig.normalPath("C:/Game/pawno/include/"),
            CompilerConfig.normalPath("C:/Game/qawno/include")
        )
        val rejectedDuplicate = CompilerConfig.containsIncludePath(known, chosen)

        val deduped = CompilerConfig.dedupePaths(values)
        val passed = rejectedDuplicate && deduped.size == 2 && deduped.any { it.contains("pawno", ignoreCase = true) }

        val message = if (passed) {
            "Selected include path integrated cleanly without duplicates in the settings flow."
        } else {
            "Settings include-path integration should reject duplicates and stay unique; " +
                "duplicate rejected=$rejectedDuplicate, actual values: $deduped"
        }

        return AppSelfTestResult("$TEST_PREFIX: settings_integration", passed, message)
    }

        /**
                 * Replays the whole "Ignore case" workflow on files the test creates itself:
                 * browse a file, back the folder up, lowercase every other name, lowercase every
                 * static include reference, and finally confirm the instructions are not repeated
                 * while the backup folder is still there.
                 */
                private fun runIgnoreCaseWorkflowTest(): AppSelfTestResult {
                val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-ignore-case-${System.nanoTime()}")
                val gamemodes = File(root, "Gamemodes")
                val sourceFile = File(gamemodes, "Stock.pwn")
                val nestedDir = File(gamemodes, "Library")
                val includeFile = File(gamemodes, "Warung.pwn")
                val incFile = File(nestedDir, "Ekosistem.inc")

                val checks = mutableListOf<Triple<String, Boolean, String>>()

                fun check(label: String, passed: Boolean, detail: String) {
                    checks += Triple(label, passed, detail)
                }

                return try {
                    gamemodes.mkdirs()
                    nestedDir.mkdirs()
                    sourceFile.writeText("#include \"Warung.pwn\"\n#include \"Library/Ekosistem.inc\"\nmain() { return; }\n")
                    includeFile.writeText("#include \"Aaaa.inc\"\nstock Warung() { return 1; }\n")
                    incFile.writeText("stock Ekosistem() { return 2; }\n")

                    val backupDir = Compiler.backupDirFor(gamemodes)
                    val firstRun = Compiler.convertFolderToLowerCase(sourceFile.absolutePath)
                    check(
                        "first run converts",
                        firstRun.status == CaseConversion.ConversionStatus.CONVERTED,
                        "status=${firstRun.status}"
                    )
                    check(
                        "backup folder created",
                        backupDir.isDirectory && File(backupDir, "Stock.pwn").exists(),
                        "backup=${backupDir.absolutePath}"
                    )
                    check(
                        "selected file keeps its name",
                        File(gamemodes, "Stock.pwn").exists(),
                        "Stock.pwn missing in working folder"
                    )
                    check(
                        "other files lowercased",
                        File(gamemodes, "warung.pwn").exists() &&
                            File(gamemodes, "library/ekosistem.inc").exists() &&
                            File(gamemodes, "library").isDirectory,
                        "expected warung.pwn, library/ekosistem.inc"
                    )
                    val sourceText = File(gamemodes, "Stock.pwn").readText()
                    check(
                        "includes lowercased",
                        sourceText.contains("#include \"warung.pwn\"") &&
                            sourceText.contains("#include \"library/ekosistem.inc\""),
                        "source=$sourceText"
                    )
                    val includeText = File(gamemodes, "warung.pwn").readText()
                    check(
                        "nested includes lowercased",
                        includeText.contains("#include \"aaaa.inc\""),
                        "warung.pwn=$includeText"
                    )

                    // Second browse with the backup folder present: the instructions must be skipped.
                    val needsConversion = Compiler.needsConversion(gamemodes, backupDir.absolutePath)
                    val secondRun = Compiler.convertFolderToLowerCase(
                        selectedFilePath = firstRun.sourceFile,
                        rememberedBackupDir = backupDir.absolutePath
                    )
                    check(
                        "second run skipped",
                        !needsConversion && secondRun.status == CaseConversion.ConversionStatus.ALREADY_CONVERTED,
                        "needs=$needsConversion status=${secondRun.status}"
                    )

                    // Deleting the backup folder makes the instructions run again.
                    backupDir.deleteRecursively()
                    check(
                        "runs again without backup",
                        Compiler.needsConversion(gamemodes, backupDir.absolutePath),
                        "conversion should be required again"
                    )

                    val failed = checks.firstOrNull { !it.second }
                    val message = if (failed == null) {
                        "Ignore case workflow verified: backup created, names and includes lowercased, repeat run skipped."
                    } else {
                        "Ignore case workflow failed at '${failed.first}': ${failed.third}"
                    }
                    AppSelfTestResult("$TEST_PREFIX: ignore_case_workflow", failed == null, message)
                } catch (e: Exception) {
                    AppSelfTestResult(
                        "$TEST_PREFIX: ignore_case_workflow",
                        false,
                        "Ignore case workflow threw ${e.javaClass.simpleName}: ${e.message}"
                    )
                } finally {
                    root.deleteRecursively()
                }
            }
}
