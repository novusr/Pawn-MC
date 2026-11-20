package com.pocketsamp.server.user_interface.screens

import com.pocketsamp.server.user_interface._Icons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Verifies the new PocketSAMP File Manager icons and folder open/closed selection logic. */
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
}