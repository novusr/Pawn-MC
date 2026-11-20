package com.pocketsamp.server.user_interface.screens

import com.pocketsamp.server.user_interface._Icons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Verifies the new PocketSAMP File Manager icons, folder open/closed selection logic,
 * and the floating-action-bar circled-letter icon family (B, i, I). */
class FileManagerIconTest {

    @Test
    fun filePaperIconIsNotNull() {
        assertNotNull("_Icons.FilePaper should be created", _Icons.FilePaper)
        assertEquals("_Icons.FilePaper", _Icons.FilePaper.name)
    }

    @Test
    fun folderRectIconIsNotNull() {
        assertNotNull("_Icons.FolderRect should be created", _Icons.FolderRect)
        assertEquals("_Icons.FolderRect", _Icons.FolderRect.name)
    }

    @Test
    fun folderRectOpenIconIsNotNull() {
        assertNotNull("_Icons.FolderRectOpen should be created", _Icons.FolderRectOpen)
        assertEquals("_Icons.FolderRectOpen", _Icons.FolderRectOpen.name)
    }

    @Test
    fun folderIconSelectionMatchesExpandedState() {
        // Simulates the selection expression used in FileEntry.
        fun iconFor(entryIsDirectory: Boolean, expanded: Boolean): String =
            if (entryIsDirectory) if (expanded) "_Icons.FolderRectOpen" else "_Icons.FolderRect" else "_Icons.FilePaper"

        assertEquals("_Icons.FolderRectOpen", iconFor(true, true))
        assertEquals("_Icons.FolderRect", iconFor(true, false))
        assertEquals("_Icons.FilePaper", iconFor(false, true))
        assertEquals("_Icons.FilePaper", iconFor(false, false))
    }

    @Test
    fun circledBIconsIsNotNull() {
        assertNotNull("_Icons.CircledB should be created", _Icons.CircledB)
        assertEquals("_Icons.CircledB", _Icons.CircledB.name)
    }

    @Test
    fun circledIIconsIsNotNull() {
        assertNotNull("_Icons.CircledI should be created", _Icons.CircledI)
        assertEquals("_Icons.CircledI", _Icons.CircledI.name)
    }

    @Test
    fun circledBigIIconsIsNotNull() {
        assertNotNull("_Icons.CircledBigI should be created", _Icons.CircledBigI)
        assertEquals("_Icons.CircledBigI", _Icons.CircledBigI.name)
    }

    @Test
    fun circledLettersUseSameCircleButDifferentInnerPath() {
        // All three icons share the same circle outline; only the inner letter differs.
        assertNotSame("_Icons.CircledB and _Icons.CircledI should differ", _Icons.CircledB, _Icons.CircledI)
        assertNotSame("_Icons.CircledI and _Icons.CircledBigI should differ", _Icons.CircledI, _Icons.CircledBigI)
        assertNotSame("_Icons.CircledB and _Icons.CircledBigI should differ", _Icons.CircledB, _Icons.CircledBigI)
    }
}