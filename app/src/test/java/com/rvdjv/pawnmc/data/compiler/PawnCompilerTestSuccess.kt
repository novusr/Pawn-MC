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
    fun `matches detected compiler metadata by product version and size`() {
        assertTrue(CompilerConfig.CompilerVersion.V31011.matchesDetected("3.10.11", 19_000L, null))
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
