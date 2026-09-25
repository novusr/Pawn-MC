package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
