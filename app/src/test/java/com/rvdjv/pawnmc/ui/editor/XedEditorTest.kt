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
    fun `line column label uses a compact separator`() {
        assertEquals("-", LINE_COLUMN_SEPARATOR)
    }

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
    fun `verifies extended keywords and directives`() {
        listOf(
            "assert", "break", "case", "char", "const", "continue", "default",
            "defined", "do", "else", "emit", "__emit", "enum", "exit", "for",
            "forward", "goto", "if", "native", "new", "operator", "public", "return",
            "sizeof", "sleep", "state", "static", "stock", "switch", "tagof", "while"
        ).forEach { assertTrue("keyword $it", PawnRegistry.isKeyword(it)) }

        listOf("*begin", "*end", "*then").forEach {
            assertTrue("keyword $it", PawnRegistry.isKeyword(it))
        }

        listOf(
            "#assert", "#define", "#else", "#elseif", "#emit", "#endif", "#endinput",
            "#endscript", "#error", "#file", "#if", "#include", "#line", "#pragma",
            "#tryinclude", "#undef", "#warning"
        ).forEach { assertTrue("directive $it", PawnRegistry.isDirective(it)) }
    }

    @Test
    fun `verifies operator tokens`() {
        listOf(
            "*=", "/=", "%=", "+=", "-=", "<<=", ">>>=", ">>=", "&=", "^=", "|=",
            "||", "&&", "==", "!=", "<=", ">=", "<<", ">>>", ">>", "++", "--",
            "...", "..", "::"
        ).forEach { assertTrue("operator $it", PawnRegistry.isOperator(it)) }

        assertFalse(PawnRegistry.isOperator("=+"))
        assertFalse(PawnRegistry.isOperator(""))
    }

    @Test
    fun `verifies longest operator is matched first`() {
        assertEquals(">>>=", PawnRegistry.LONG_OPERATORS.first())
        assertEquals(4, PawnRegistry.LONG_OPERATORS.first().length)
        assertTrue(PawnRegistry.LONG_OPERATORS.contains(">>="))
        assertTrue(PawnRegistry.LONG_OPERATORS.none { it.length == 1 })
    }

    @Test
    fun `verifies operator heads cover every operator character`() {
        assertTrue(PawnRegistry.OPERATOR_HEADS.containsAll("+-*/%=<>!&|^~?:,;".toList()))
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
    fun `verifies include symbols resolve as functions`() {
        // Symbols that only exist in the include table.
        assertTrue(PawnRegistry.isFunction("CreatePlayerObject"))
        assertTrue(PawnRegistry.isFunction("SHA256_PassHash"))
        assertTrue(PawnRegistry.isFunction("GetPlayerVehicleSeat"))
        assertTrue(PawnRegistry.isFunction("deleteproperty"))
        assertTrue(PawnRegistry.isFunction("floatpower"))
        assertTrue(PawnRegistry.isFunction("uuencode"))
        assertTrue(PawnRegistry.isFunction("gettime"))
        assertTrue(PawnRegistry.isFunction("OnPlayerUpdate"))
        assertTrue(PawnRegistry.isFunction("OnVehicleSpawn"))
    }

    @Test
    fun `verifies autocompletion covers include natives`() {
        val actorCompletions = PawnRegistry.getCompletions("CreateActor")
        assertTrue(actorCompletions.any { it.name == "CreateActor" })

        val propCompletions = PawnRegistry.getCompletions("deletepro")
        assertTrue(propCompletions.any { it.name == "deleteproperty" })

        val updateCompletions = PawnRegistry.getCompletions("OnPlayerUp")
        assertTrue(updateCompletions.any { it.name == "OnPlayerUpdate" })
    }

    @Test
    fun `verifies autocompletion covers operators and keywords`() {
        assertTrue(PawnRegistry.getCompletions(">>>").any { it.name == ">>>" })
        assertTrue(PawnRegistry.getCompletions("==").any { it.name == "==" })
        assertTrue(PawnRegistry.getCompletions("swit").any { it.name == "switch" })
        assertTrue(PawnRegistry.getCompletions("defin").any { it.name == "defined" })
        assertTrue(PawnRegistry.getCompletions("#try").any { it.name == "#tryinclude" })
    }

    @Test
    fun `verifies empty prefix yields no completions`() {
        assertTrue(PawnRegistry.getCompletions("").isEmpty())
        assertTrue(PawnRegistry.getCompletions("   ").isEmpty())
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
