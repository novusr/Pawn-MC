package com.pawnmc.terminal.core
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class TerminalLaunchScriptTest {
    @Test
    fun sandboxSourcesUbuntuEnvironmentBeforeLaunchingInteractiveShell() {
        val script = File("src/main/assets/terminal/sandbox.sh").readText()

        assertTrue(
            "Sandbox must source init.sh inside Ubuntu before starting the shell",
            script.contains(". \"\$LOCAL/bin/init\"; exec /bin/sh -i"),
        )
        assertTrue(
            "Interactive shell must inherit Ubuntu PATH",
            script.contains("exec /bin/sh -i"),
        )
        assertTrue(
            "shared memory must be made writable even when pre-created",
            script.contains("chmod 1777").and(script.contains("sandbox/tmp")),
        )
    }
}
