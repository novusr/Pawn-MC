package com.rvdjv.pawnmc.apps_data.data_config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the remembered-folder persistence used by the Xed workspace panel and
 * the PocketSAMP File Manager.
 *
 * The restore pass must recreate a recorded folder that vanished (a reinstall,
 * a storage hiccup) instead of silently pruning it — the path was a user
 * decision, and losing it emptied the workspace panel down to just blank.pwn.
 */
class PathStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `restoreExisting keeps only paths that are still on disk`() {
        val alive = tempFolder.newFolder("alive")
        val dead = File(tempFolder.root, "gone")

        val restored = PathStore.restoreExisting(listOf(alive.absolutePath, dead.absolutePath))

        assertEquals(listOf(alive.absolutePath), restored)
    }

    @Test
    fun `restoreRecreating brings a vanished folder back`() {
        val lost = File(tempFolder.root, "workspace/gamemodes")
        val alive = tempFolder.newFolder("scriptfiles")

        val restored = PathStore.restoreRecreating(listOf(lost.absolutePath, alive.absolutePath))

        assertEquals(listOf(lost.absolutePath, alive.absolutePath), restored)
        assertTrue("missing folder must be recreated", lost.isDirectory)
    }

    @Test
    fun `restoreRecreating drops paths that cannot be recreated`() {
        // A file blocks mkdir at its own path, so the entry cannot come back.
        val blocked = tempFolder.newFile("blocked")
        val alive = tempFolder.newFolder("alive")

        val restored = PathStore.restoreRecreating(listOf(blocked.absolutePath, alive.absolutePath))

        assertEquals(listOf(alive.absolutePath), restored)
        assertFalse(restored.contains(blocked.absolutePath))
    }

    @Test
    fun `restoreRecreating deduplicates and skips blank entries`() {
        val alive = tempFolder.newFolder("once")

        val restored = PathStore.restoreRecreating(
            listOf("", alive.absolutePath, "  ", alive.absolutePath)
        )

        assertEquals(listOf(alive.absolutePath), restored)
    }
}
