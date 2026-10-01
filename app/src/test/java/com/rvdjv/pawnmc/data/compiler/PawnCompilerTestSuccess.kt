package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.ui.main.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PawnCompilerSuccessTest {
    @Test
    fun `testing success build`() {
        val output = """
            Exit code: 0
            source.pawn(10) : warning 217: "foo" is assigned a value but never used
        """.trimIndent()

        assertEquals(0, PawnCompiler.extractErrorCount(output))
        assertFalse(PawnCompiler.shouldRetryWithFallback(output, 5))
    }

    @Test
    fun `returns the other compiler version`() {
        assertEquals(CompilerConfig.CompilerVersion.V31011, CompilerConfig.CompilerVersion.V3107.other())
        assertEquals(CompilerConfig.CompilerVersion.V3107, CompilerConfig.CompilerVersion.V31011.other())
    }

    @Test
    fun `forced compiler keeps the configured version instead of detection or fallback`() {
        assertEquals(
            CompilerConfig.CompilerVersion.V31011,
            PawnCompiler.compilerVersionForMode(
                forcedMode = true,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = CompilerConfig.CompilerVersion.V3107
            )
        )
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            PawnCompiler.compilerVersionForMode(
                forcedMode = false,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = null
            )
        )
    }

    @Test
    fun `matches detected compiler metadata by product version and size`() {
        assertTrue(CompilerConfig.CompilerVersion.V31011.matchesDetected(CompilerConfig.STR_V31011, 19_000L, null))
        assertTrue(CompilerConfig.CompilerVersion.V3107.matchesDetected(null, 28_000L, "a48e04d28e8cb77e0361ecb4dced2501"))
    }

    @Test
    fun `build options include optimization level`() {
        val options = CompilerConfig.buildOptionsFor(
            optimizationLevel = CompilerConfig.OptimizationLevel.O2,
            mandatorySemicolons = true,
            mandatoryParentheses = true,
            customFlags = "-v=0"
        )

        assertTrue(options.contains("-O=2"))
        assertTrue(options.contains("-v=0"))
        assertTrue(options.contains("-;+"))
    }

    @Test
    fun `adds sscanf compatibility define as final compiler argument for pawn 3 10 7`() {
        val options = listOf("-d=3", "-i=/project/include", "-i=/project/gamemodes", "-v=0")

        val options3107 = PawnCompiler.compilerOptionsForVersion(
            options,
            CompilerConfig.CompilerVersion.V3107
        )
        assertEquals(
            options + "SSCANF_NO_NICE_FEATURES=1",
            options3107
        )

        val options31011 = PawnCompiler.compilerOptionsForVersion(
            options,
            CompilerConfig.CompilerVersion.V31011
        )
        assertEquals(options, options31011)

        val compilerArguments = PawnCompiler.compilerArgumentsForVersion(
            "/project/main.pwn",
            options,
            CompilerConfig.CompilerVersion.V3107
        )
        assertEquals("pawncc", compilerArguments.first())
        assertEquals("/project/main.pwn", compilerArguments[1])
        assertEquals("SSCANF_NO_NICE_FEATURES=1", compilerArguments.last())
    }

    @Test
    fun `sscanf compatibility define is moved to the end without duplicates`() {
        val options = listOf("-i=/project/include", "SSCANF_NO_NICE_FEATURES=1", "-v=0", "SSCANF_NO_NICE_FEATURES=1")

        assertEquals(
            listOf("-i=/project/include", "-v=0", "SSCANF_NO_NICE_FEATURES=1"),
            PawnCompiler.compilerOptionsForVersion(options, CompilerConfig.CompilerVersion.V3107)
        )
    }

    @Test
    fun `case-insensitive workspace lowercases filenames and include statements`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-ignore-case-${System.nanoTime()}")
        val originalDir = File(root, "Gamemodes")
        val includeFile = File(originalDir, "Aaa.inc")
        val sourceFile = File(originalDir, "Main.PWN")

        originalDir.mkdirs()
        includeFile.writeText("stock foo() { return 1; }\n")
        sourceFile.writeText("#include \"Aaa.inc\"\n#include \"Other.Inc\"\n")

        try {
            val result = PawnCompiler.convertFolderToLowerCase(sourceFile.absolutePath)
            val workingDir = result.workingDir

            assertEquals(PawnCompiler.ConversionStatus.CONVERTED, result.status)
            assertTrue(File(workingDir, "Main.PWN").exists())
            assertTrue(File(workingDir, "aaa.inc").exists())
            assertTrue(File(workingDir, "Main.PWN").readText().contains("#include \"aaa.inc\""))
            assertTrue(File(workingDir, "Main.PWN").readText().contains("#include \"other.inc\""))
            assertTrue(PawnCompiler.backupDirFor(workingDir).isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `discovers only include roots that exist on disk`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-include-discovery-${System.nanoTime()}")
        val sourceFile = File(root, "gamemodes/main.pwn")
        sourceFile.parentFile?.mkdirs()
        sourceFile.writeText("main() { return; }\n")
        File(root, "qawno/include").mkdirs()

        try {
            val paths = PawnCompiler.discoverRelevantIncludePaths(sourceFile.absolutePath)
            assertTrue(paths.any { it.contains("gamemodes", ignoreCase = true) })
            assertTrue(paths.any { it.contains("qawno/include", ignoreCase = true) })
            // Absent conventional folders must not be registered.
            assertFalse(paths.any { it.contains("pawno/include", ignoreCase = true) })
            assertEquals("No two include paths can have the same location", paths.size, paths.map { CompilerConfig.normalPath(it).lowercase() }.distinct().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `skips include roots that are already registered`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-include-known-${System.nanoTime()}")
        val sourceFile = File(root, "gamemodes/main.pwn")
        sourceFile.parentFile?.mkdirs()
        sourceFile.writeText("main() { return; }\n")
        File(root, "qawno/include").mkdirs()

        try {
            val known = listOf(CompilerConfig.normalPath(File(root, "qawno/include").absolutePath))
            val paths = PawnCompiler.discoverRelevantIncludePaths(sourceFile.absolutePath, known)

            assertTrue(paths.any { it.contains("gamemodes", ignoreCase = true) })
            assertFalse(paths.any { it.contains("qawno/include", ignoreCase = true) })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `creates temporary pawn file with hello world snippet`() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "pawnmc-${System.nanoTime()}")
        require(tempDir.mkdirs()) { "Unable to create temp dir for test" }

        val tempFile = MainViewModel.createTemporaryPawnFile(tempDir)

        try {
            assertTrue(tempFile.exists())
            assertEquals("main.pwn", tempFile.name)
            val content = tempFile.readText()
            assertTrue(content.contains("native printf"))
            assertTrue(content.contains("printf(\"Hello, World!\");"))
        } finally {
            tempFile.delete()
            tempDir.deleteRecursively()
        }
    }
}
