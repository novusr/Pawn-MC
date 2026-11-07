package com.rvdjv.pawnmc.apps_data.data_compiler

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Compiler version selection: MD5 / product-version / size matching, the
 * qawno + pawno path scenarios, and how forced | automatic mode changes the
 * result.
 */
class CompilerVersionSelectionTest {

    /** The MD5s and sizes pinned in the version table. */
    @Test
    fun `matchesDetected honours md5 product version and size`() {
        val v3107 = CompilerConfig.CompilerVersion.V3107
        val v31011 = CompilerConfig.CompilerVersion.V31011

        // Exact MD5 wins even with no other metadata.
        assertTrue(v3107.matchesDetected(null, null, "a48e04d28e8cb77e0361ecb4dced2501"))
        assertTrue(v31011.matchesDetected(null, null, "9044b9ef65658c79851b4e2e249e5c75"))
        // Wrong MD5, wrong product version, absurd size: no match.
        assertFalse(v3107.matchesDetected("9.9.9", 1L, "deadbeef"))
        // Product version string alone is enough.
        assertTrue(v31011.matchesDetected("3.10.11", null, null))
        // Size tolerance covers the pinned expected sizes.
        assertTrue(v3107.matchesDetected(null, 28_672L, null))
        assertTrue(v31011.matchesDetected(null, 18_944L, null))
    }

    @Test
    fun `nearestSupportedEquivalent maps near versions and unknown md5s`() {
        // 3.10.9 / 3.10.10 map forward to 3.10.11; 3.10.8 maps back to 3.10.7.
        assertEquals(
            CompilerConfig.CompilerVersion.V31011,
            CompilerConfig.CompilerVersion.V3107.nearestSupportedEquivalent("3.10.10", null, null)
        )
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            CompilerConfig.CompilerVersion.V31011.nearestSupportedEquivalent("3.10.8", null, null)
        )
        // Known third-party pawncc MD5s.
        assertEquals(
            CompilerConfig.CompilerVersion.V31011,
            CompilerConfig.CompilerVersion.V3107.nearestSupportedEquivalent(
                null, null, "ce6bd3ae0fb9fba27bb8189adf0c0fab"
            )
        )
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            CompilerConfig.CompilerVersion.V31011.nearestSupportedEquivalent(
                null, null, "f28ab0d8b1ccbcc448b26e3255f4abb8"
            )
        )
        // Nothing to go on: null.
        assertNull(
            CompilerConfig.CompilerVersion.V3107.nearestSupportedEquivalent(null, null, null)
        )
    }

    @Test
    fun `forced mode pins the selected version regardless of detection`() {
        // Forced + selected 3.10.11 with a detected 3.10.7 nearby: stay at 3.10.11.
        assertEquals(
            CompilerConfig.CompilerVersion.V31011,
            Runner.compilerVersionForMode(
                forcedMode = true,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = CompilerConfig.CompilerVersion.V3107
            )
        )
        // Forced + selected 3.10.7 with a detected 3.10.11 nearby: stay at 3.10.7.
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            Runner.compilerVersionForMode(
                forcedMode = true,
                selectedVersion = CompilerConfig.CompilerVersion.V3107,
                detectedVersion = CompilerConfig.CompilerVersion.V31011
            )
        )
        // Forced mode never downgrades: even when nothing was detected the
        // selection holds.
        assertEquals(
            CompilerConfig.CompilerVersion.V31011,
            Runner.compilerVersionForMode(
                forcedMode = true,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = null
            )
        )
    }

    @Test
    fun `auto mode prefers the detected version and falls back to the default`() {
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            Runner.compilerVersionForMode(
                forcedMode = false,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = CompilerConfig.CompilerVersion.V3107
            )
        )
        assertEquals(
            CompilerConfig.CompilerVersion.V3107,
            Runner.compilerVersionForMode(
                forcedMode = false,
                selectedVersion = CompilerConfig.CompilerVersion.V31011,
                detectedVersion = null
            )
        )
    }

    @Test
    fun `detection finds pawncc in pawno and qawno folders`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-select-${System.nanoTime()}")
        val source = File(File(root, "gamemodes"), "mode.pwn").apply {
            parentFile.mkdirs()
            writeText("main() {}")
        }

        val content = "fake-pawncc-binary-3.10.11"
        // Case 1: pawncc.exe inside pawno/.
        val pawno = File(root, "pawno").apply { mkdirs() }
        File(pawno, "pawncc.exe").writeText(content)
        val match1 = Compiler.detectNearbyCompiler(source.absolutePath)
        assertTrue(match1 != null && match1.version == CompilerConfig.CompilerVersion.V31011)
        File(pawno, "pawncc.exe").delete()

        // Case 2: pawncc inside qawno/ (no pawno anywhere).
        val qawno = File(root, "qawno").apply { mkdirs() }
        File(qawno, "pawncc").writeText(content)
        val match2 = Compiler.detectNearbyCompiler(source.absolutePath)
        assertTrue(match2 != null && match2.version == CompilerConfig.CompilerVersion.V31011)
        File(qawno, "pawncc").delete()

        // Case 3: no compiler anywhere: no match.
        val none = Compiler.detectNearbyCompiler(source.absolutePath)
        assertNull(none)
        root.deleteRecursively()
    }

    @Test
    fun `addressof recovery is one shot and transient`() {
        Compiler.resetSessionState()
        val output = "error 017: undefined symbol: \"__addressof\""
        // Auto mode: no recovery claim at all.
        assertFalse(Compiler.claimAddressOfRecovery(output, forcedMode = false, CompilerConfig.CompilerVersion.V3107))
        // Forced 3.10.11: the newer compiler already knows __addressof, no claim.
        assertFalse(Compiler.claimAddressOfRecovery(output, forcedMode = true, CompilerConfig.CompilerVersion.V31011))
        // Forced 3.10.7: exactly one recovery, then the flag is spent.
        assertTrue(Compiler.claimAddressOfRecovery(output, forcedMode = true, CompilerConfig.CompilerVersion.V3107))
        assertFalse(Compiler.claimAddressOfRecovery(output, forcedMode = true, CompilerConfig.CompilerVersion.V3107))
        Compiler.resetSessionState()
        // A new session can claim it again.
        assertTrue(Compiler.claimAddressOfRecovery(output, forcedMode = true, CompilerConfig.CompilerVersion.V3107))
    }
}
