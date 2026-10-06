package com.pawnmc.terminal.shell
import org.junit.Assert.assertArrayEquals
import org.junit.Test
class TerminalServiceTest {
    @Test
    fun sandboxScriptIsPassedAsTheShellFileArgument() {
        val scriptPath = "/data/user/0/com.example/files/local/bin/sandbox"

        assertArrayEquals(
            arrayOf("/system/bin/sh", "-c", "exec \"$1\"", "pawnmc-terminal", scriptPath),
            sandboxShellArguments(scriptPath),
        )
    }
}
