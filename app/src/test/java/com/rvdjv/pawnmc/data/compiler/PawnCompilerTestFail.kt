package com.rvdjv.pawnmc.data.compiler

import com.rvdjv.pawnmc.data.config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PawnCompilerFailTest {
    @Test
    fun `detects error count from compiler output`() {
        val output = """
            Exit code: 1
            source.pawn(10) : warning 217: "foo" is assigned a value but never used
            source.pawn(20) : error 017: undefined symbol "bar"
            source.pawn(21) : error 017: undefined symbol "bar"
            source.pawn(22) : error 017: undefined symbol "bar"
            source.pawn(23) : error 017: undefined symbol "bar"
            source.pawn(24) : error 017: undefined symbol "bar"
            6 Errors.
        """.trimIndent()

        assertEquals(6, PawnCompiler.extractErrorCount(output))
        assertTrue(PawnCompiler.shouldRetryWithFallback(output, 5))
    }

    @Test
    fun `returns the other compiler version`() {
        assertEquals(CompilerConfig.CompilerVersion.V31011, CompilerConfig.CompilerVersion.V3107.other())
        assertEquals(CompilerConfig.CompilerVersion.V3107, CompilerConfig.CompilerVersion.V31011.other())
    }
}
