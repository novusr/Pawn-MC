package com.rvdjv.pawnmc.ui.editor

import com.rvdjv.pawnmc.data.pawn.PawnLanguageRegistry
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
        assertTrue(PawnLanguageRegistry.isKeyword("new"))
        assertTrue(PawnLanguageRegistry.isKeyword("stock"))
        assertTrue(PawnLanguageRegistry.isKeyword("public"))
        assertTrue(PawnLanguageRegistry.isKeyword("forward"))
        assertTrue(PawnLanguageRegistry.isKeyword("return"))

        assertTrue(PawnLanguageRegistry.isDirective("include"))
        assertTrue(PawnLanguageRegistry.isDirective("#include"))
        assertTrue(PawnLanguageRegistry.isDirective("define"))
        assertTrue(PawnLanguageRegistry.isDirective("#pragma"))

        assertTrue(PawnLanguageRegistry.isType("Float"))
        assertTrue(PawnLanguageRegistry.isType("bool"))

        assertTrue(PawnLanguageRegistry.isConstant("INVALID_PLAYER_ID"))
        assertTrue(PawnLanguageRegistry.isConstant("MAX_PLAYERS"))
    }

    @Test
    fun `verifies official SA-MP functions and callbacks`() {
        assertTrue(PawnLanguageRegistry.isFunction("OnGameModeInit"))
        assertTrue(PawnLanguageRegistry.isFunction("OnPlayerConnect"))
        assertTrue(PawnLanguageRegistry.isFunction("SendClientMessage"))
        assertTrue(PawnLanguageRegistry.isFunction("SetPlayerPos"))
        assertTrue(PawnLanguageRegistry.isFunction("format"))
        assertTrue(PawnLanguageRegistry.isFunction("strlen"))

        assertFalse(PawnLanguageRegistry.isFunction("nonExistentFakeFunction123"))
    }

    @Test
    fun `verifies autocompletion suggestions from registry`() {
        val sendCompletions = PawnLanguageRegistry.getCompletions("Send")
        assertTrue(sendCompletions.isNotEmpty())
        assertTrue(sendCompletions.any { it.name == "SendClientMessage" })
        assertTrue(sendCompletions.any { it.name == "SendClientMessageToAll" })

        val directiveCompletions = PawnLanguageRegistry.getCompletions("#inc")
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
