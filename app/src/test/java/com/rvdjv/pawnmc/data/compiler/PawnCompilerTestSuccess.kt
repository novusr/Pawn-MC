package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
