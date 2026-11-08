package com.rvdjv.pawnmc.apps_data.data_config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContentConfigTest {

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

        val result = ContentConfig.dedupePaths(input)

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
        val deduped = ContentConfig.dedupePaths(paths)
        assertEquals(2, deduped.size)
        assertEquals("/storage/emulated/0/samp/pawno/include/", deduped[0])
        assertEquals("/storage/emulated/0/samp/qawno/include/", deduped[1])
    }

    @Test
    fun containsIncludePath_rejectsSameFolderRegardlessOfCaseAndTrailingSlash() {
        val existing = listOf("/storage/emulated/0/samp/jagung/")

        assertTrue(ContentConfig.containsIncludePath(existing, "/storage/emulated/0/samp/jagung"))
        assertTrue(ContentConfig.containsIncludePath(existing, "/storage/emulated/0/SAMP/Jagung/"))
        assertFalse(ContentConfig.containsIncludePath(existing, "/storage/emulated/0/samp/jagung2/"))
    }

    @Test
    fun pruneMissingIncludePaths_removesDuplicatesInStoredList() {
        val root = temporaryFolder.newFolder("pawnmc_prune_dupe")
        val alive = File(root, "include").apply { mkdirs() }
        val path = ContentConfig.normalPath(alive.absolutePath)

        val survivors = ContentConfig.pruneMissingIncludePaths(
            listOf(path, ContentConfig.normalPath(alive.absolutePath).uppercase())
        )

        assertEquals(listOf(path), survivors)
    }

    @Test
    fun pruneMissingIncludePaths_dropsDeletedFoldersAndKeepsExistingOnes() {
        val root = temporaryFolder.newFolder("pawnmc_prune")
        val alive = File(root, "include").apply { mkdirs() }
        val dead = File(root, "gamemodes").apply { mkdirs() }

        val stored = listOf(
            ContentConfig.normalPath(alive.absolutePath),
            ContentConfig.normalPath(dead.absolutePath)
        )

        dead.delete()

        val survivors = ContentConfig.pruneMissingIncludePaths(stored)

        assertEquals(listOf(ContentConfig.normalPath(alive.absolutePath)), survivors)
    }

    @Test
    fun pruneMissingIncludePaths_keepsMissingFallbackIncludeDirectory() {
        val root = temporaryFolder.newFolder("pawnmc_prune_fallback")
        val fallback = File(root, "include")
        val missingOther = File(root, "gamemodes")

        val survivors = ContentConfig.pruneMissingIncludePaths(
            listOf(
                ContentConfig.normalPath(fallback.absolutePath),
                ContentConfig.normalPath(missingOther.absolutePath)
            )
        )

        assertFalse(fallback.exists())
        assertEquals(listOf(ContentConfig.normalPath(fallback.absolutePath)), survivors)
    }

    @Test
    fun isCompactEncodingFlag_matchesEverySpellingOfTheToggle() {
        assertTrue(ContentConfig.isCompactEncodingFlag("-C+"))
        assertTrue(ContentConfig.isCompactEncodingFlag("-C-"))
        assertFalse(ContentConfig.isCompactEncodingFlag("-C"))
        assertFalse(ContentConfig.isCompactEncodingFlag("-C++"))
    }

    @Test
    fun isVerbosityFlag_matchesBothSpellingsOfTheLevel() {
        assertTrue(ContentConfig.isVerbosityFlag("-v0"))
        assertTrue(ContentConfig.isVerbosityFlag("-v=0"))
        assertTrue(ContentConfig.isVerbosityFlag("-v2"))
        assertTrue(ContentConfig.isVerbosityFlag("-v=2"))
        assertFalse(ContentConfig.isVerbosityFlag("-v"))
        assertFalse(ContentConfig.isVerbosityFlag("-verbosity"))
    }

    @Test
    fun isWarning217Flag_matchesOnlyTheLooseIndentationSilencer() {
        assertTrue(ContentConfig.isWarning217Flag("-w217"))
        assertTrue(ContentConfig.isWarning217Flag("-w=217"))
        assertFalse(ContentConfig.isWarning217Flag("-w218"))
        assertFalse(ContentConfig.isWarning217Flag("-w219"))
        assertFalse(ContentConfig.isWarning217Flag("-w17"))
        assertFalse(ContentConfig.isWarning217Flag("-w"))
    }

    @Test
    fun isWarning200Flag_matchesOnlyTheSymbolTruncatedSilencer() {
        assertTrue(ContentConfig.isWarning200Flag("-w200"))
        assertTrue(ContentConfig.isWarning200Flag("-w=200"))
        assertFalse(ContentConfig.isWarning200Flag("-w201"))
        assertFalse(ContentConfig.isWarning200Flag("-w202"))
        assertFalse(ContentConfig.isWarning200Flag("-w17"))
        assertFalse(ContentConfig.isWarning200Flag("-w"))
    }
}
