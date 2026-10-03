package com.rvdjv.pawnmc.`interface`.editor

import com.rvdjv.pawnmc.data.pawn.PawnRegistry
import com.rvdjv.pawnmc.`interface`.editor.pawn.PawnSourceSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class XedEditorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

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
            "forward", "goto", "hook", "if", "native", "new", "operator", "public", "return",
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
    fun `native is a highlighted keyword with autocomplete`() {
        assertTrue(PawnRegistry.isKeyword("native"))
        assertTrue(PawnRegistry.getCompletions("nat").any { it.name == "native" })
    }

    @Test
    fun `hook is a highlighted keyword with autocomplete`() {
        assertTrue(PawnRegistry.isKeyword("hook"))
        assertTrue(PawnRegistry.getCompletions("ho").any { it.name == "hook" })
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
    fun `finds local function declarations and ignores comments strings and registry conflicts`() {
        val source = """
            // stock commentedOut() {}
            /* public alsoCommentedOut() {} */
            stock abc(value, const name[]) {
                return 1;
            }
            public Float:OnReady(playerid) {}
            forward OnLoaded();
            native localNative(value);
            stock strcat(custom[]) { return 1; }
            new sample[] = "stock insideString() {";
        """.trimIndent()

        val functions = PawnSourceSymbols.functions(source)

        assertEquals(listOf("abc", "OnReady", "OnLoaded", "localNative"), functions.map { it.name })
        assertTrue(functions.first().description.contains("stock abc(value, const name[])"))
        assertFalse(functions.any { it.name == "strcat" })
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

    @Test
    fun `workspace document stays dirty when newer edits arrive during save`() {
        val document = OpenDocument(tempFolder.newFile("workspace.pwn"))
        document.updateContent("saved snapshot")
        document.markSaved("saved snapshot")

        document.updateContent("newer unsaved edit")
        document.markSaved("saved snapshot")

        assertTrue(document.isDirty)
    }

    @Test
    fun `new Pawn file names default to pwn and reject paths or unsupported extensions`() {
        assertEquals("new_mode.pwn", normalizeNewPawnFileName("new_mode"))
        assertEquals("library.inc", normalizeNewPawnFileName("library.inc"))
        assertEquals(null, normalizeNewPawnFileName("../outside.pwn"))
        assertEquals(null, normalizeNewPawnFileName("notes.txt"))
        assertEquals(null, normalizeNewPawnFileName(" "))
    }
}
