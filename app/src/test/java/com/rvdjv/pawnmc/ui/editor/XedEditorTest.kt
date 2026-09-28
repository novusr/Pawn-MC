package com.rvdjv.pawnmc.ui.editor

import com.rvdjv.pawnmc.data.pawn.PawnRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class XedEditorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `verifies Pawn keywords and directives in registry`() {
        assertTrue(PawnRegistry.isKeyword("new"))
        assertTrue(PawnRegistry.isKeyword("stock"))
        assertTrue(PawnRegistry.isKeyword("public"))
        assertTrue(PawnRegistry.isKeyword("forward"))
        assertTrue(PawnRegistry.isKeyword("return"))

        assertTrue(PawnRegistry.isDirective("include"))
        assertTrue(PawnRegistry.isDirective("#include"))
        assertTrue(PawnRegistry.isDirective("define"))
        assertTrue(PawnRegistry.isDirective("#pragma"))

        assertTrue(PawnRegistry.isType("Float"))
        assertTrue(PawnRegistry.isType("bool"))

        assertTrue(PawnRegistry.isConstant("INVALID_PLAYER_ID"))
        assertTrue(PawnRegistry.isConstant("MAX_PLAYERS"))
    }

    @Test
    fun `verifies official SA-MP functions and callbacks`() {
        assertTrue(PawnRegistry.isFunction("OnGameModeInit"))
        assertTrue(PawnRegistry.isFunction("OnPlayerConnect"))
        assertTrue(PawnRegistry.isFunction("SendClientMessage"))
        assertTrue(PawnRegistry.isFunction("SetPlayerPos"))
        assertTrue(PawnRegistry.isFunction("format"))
        assertTrue(PawnRegistry.isFunction("strlen"))

        assertFalse(PawnRegistry.isFunction("nonExistentFakeFunction123"))
    }

    @Test
    fun `verifies autocompletion suggestions from registry`() {
        val sendCompletions = PawnRegistry.getCompletions("Send")
        assertTrue(sendCompletions.isNotEmpty())
        assertTrue(sendCompletions.any { it.name == "SendClientMessage" })
        assertTrue(sendCompletions.any { it.name == "SendClientMessageToAll" })

        val directiveCompletions = PawnRegistry.getCompletions("#inc")
        assertTrue(directiveCompletions.any { it.name == "#include" })
    }

    @Test
    fun `verifies file reading and direct save in XedEditorViewModel`() {
        val testFile = tempFolder.newFile("main.pwn")
        testFile.writeText("#include <a_samp>\n\nmain() {\n    print(\"Hello World\");\n}\n")

        val viewModel = XedEditorViewModel(testFile.absolutePath)
        assertEquals("main.pwn", viewModel.fileName)
        assertFalse(viewModel.hasUnsavedChanges)

        viewModel.onContentChanged("#include <a_samp>\n\nmain() {\n    print(\"Updated World\");\n}\n")
        assertTrue(viewModel.hasUnsavedChanges)

        val updatedContent = "#include <a_samp>\n\nmain() {\n    print(\"Saved Directly\");\n}\n"
        runBlocking {
            val success = viewModel.saveFileSync(updatedContent)
            assertTrue(success)
        }

        val diskContent = testFile.readText()
        assertEquals(updatedContent, diskContent)
        assertFalse(viewModel.hasUnsavedChanges)
    }
}
