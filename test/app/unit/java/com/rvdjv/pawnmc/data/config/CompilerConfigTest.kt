package com.rvdjv.pawnmc.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompilerConfigTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun dedupePaths_ignoresCaseAndDuplicateSegments() {
        val input = listOf(
            "com.android/MyServer/pawno/include/",
            "com.android/myserver/pawno/include",
            "com.android/MyServer/qawno/include/",
            "com.android/myserver/qawno/include",
            "com.android/MyServer/includes/"
        )

        val result = CompilerConfig.dedupePaths(input)

        assertEquals(
            listOf(
                "com.android/MyServer/pawno/include/",
                "com.android/MyServer/qawno/include/",
                "com.android/MyServer/includes/"
            ),
            result
        )
    }

    @Test
    fun dedupePaths_prohibitsTwoSameLocationPaths() {
        val paths = listOf(
            "/storage/emulated/0/samp/pawno/include",
            "/storage/emulated/0/samp/pawno/include/",
            "/storage/emulated/0/SAMP/PAWNO/INCLUDE",
            "/storage/emulated/0/samp/gamemodes/../pawno/include",
            "/storage/emulated/0/samp/qawno/include"
        )
        val deduped = CompilerConfig.dedupePaths(paths)
        assertEquals(2, deduped.size)
        assertEquals("/storage/emulated/0/samp/pawno/include/", deduped[0])
        assertEquals("/storage/emulated/0/samp/qawno/include/", deduped[1])
    }

    @Test
    fun containsIncludePath_rejectsSameFolderRegardlessOfCaseAndTrailingSlash() {
        val existing = listOf("/storage/emulated/0/samp/jagung/")

        assertTrue(CompilerConfig.containsIncludePath(existing, "/storage/emulated/0/samp/jagung"))
        assertTrue(CompilerConfig.containsIncludePath(existing, "/storage/emulated/0/SAMP/Jagung/"))
        assertFalse(CompilerConfig.containsIncludePath(existing, "/storage/emulated/0/samp/jagung2/"))
    }

    @Test
    fun pruneMissingIncludePaths_dropsDeletedFoldersAndKeepsExistingOnes() {
        val root = temporaryFolder.newFolder("pawnmc_prune")
        val alive = File(root, "include").apply { mkdirs() }
        val dead = File(root, "gamemodes").apply { mkdirs() }

        val stored = listOf(
            CompilerConfig.normalPath(alive.absolutePath),
            CompilerConfig.normalPath(dead.absolutePath)
        )

        dead.delete()

        val survivors = CompilerConfig.pruneMissingIncludePaths(stored)

        assertEquals(listOf(CompilerConfig.normalPath(alive.absolutePath)), survivors)
    }

    @Test
    fun pruneMissingIncludePaths_keepsMissingFallbackIncludeDirectory() {
        val root = temporaryFolder.newFolder("pawnmc_prune_fallback")
        val fallback = File(root, "include")
        val missingOther = File(root, "gamemodes")

        val survivors = CompilerConfig.pruneMissingIncludePaths(
            listOf(
                CompilerConfig.normalPath(fallback.absolutePath),
                CompilerConfig.normalPath(missingOther.absolutePath)
            )
        )

        assertFalse(fallback.exists())
        assertEquals(listOf(CompilerConfig.normalPath(fallback.absolutePath)), survivors)
    }

    @Test
    fun pruneMissingIncludePaths_removesDuplicatesInStoredList() {
        val root = temporaryFolder.newFolder("pawnmc_prune_dupe")
        val alive = File(root, "include").apply { mkdirs() }
        val path = CompilerConfig.normalPath(alive.absolutePath)

        val survivors = CompilerConfig.pruneMissingIncludePaths(
            listOf(path, CompilerConfig.normalPath(alive.absolutePath).uppercase())
        )

        assertEquals(listOf(path), survivors)
    }
}
