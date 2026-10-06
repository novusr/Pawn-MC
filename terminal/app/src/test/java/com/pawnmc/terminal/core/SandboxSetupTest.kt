package com.pawnmc.terminal.core

import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxSetupTest {
    @Test
    fun failureDetails_includeScriptOutputAndStackTrace() {
        val rawOutput = """
            [0;31mERROR: sandbox extraction failed[0m
            /data/local/tmp/proot: inaccessible or not found
            [2J[H
        """.trimIndent()

        val details = SandboxSetup.failureDetails(
            error = RuntimeException("The sandbox could not be extracted"),
            rawOutput = rawOutput,
        )

        assertTrue(details.contains("The sandbox could not be extracted"))
        assertTrue(details.contains("sandbox extraction failed"))
        assertTrue(details.contains("proot"))
        assertTrue(details.contains("Script output"))
    }
}
