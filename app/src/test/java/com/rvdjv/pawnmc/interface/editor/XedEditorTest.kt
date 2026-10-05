package com.rvdjv.pawnmc.`interface`.editor

import com.rvdjv.pawnmc.data.pawn.PawnRegistry
import com.rvdjv.pawnmc.data.pawn.InternDat
import com.rvdjv.pawnmc.`interface`.editor.pawn.PawnSourceSymbols
import com.rvdjv.pawnmc.data.config.CompilerConfig
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
import java.io.File

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
        viewModel.onContentChanged(updatedContent)
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
    fun `editor view model builds without a context so tests need no Android runtime`() {
        val viewModel = XedEditorViewModel("")
        assertFalse(viewModel.workspace.isWorkspaceOpen)
    }

    @Test
    fun `workspace folders stay open for the life of the session and respect the limit`() {
        val session = XedWorkspaceViewModel()
        assertFalse(session.isWorkspaceOpen)

        val first = tempFolder.newFolder("gamemodes")
        val second = tempFolder.newFolder("scriptfiles")
        val third = tempFolder.newFolder("pawno")
        val fourth = tempFolder.newFolder("filterscripts")

        listOf(first, second, third).forEach { session.openWorkspace(it) }
        assertEquals(3, session.openWorkspaces.size)
        assertEquals(third.absolutePath, session.activeWorkspace?.root?.absolutePath)

        // The fourth folder is refused, and the refusal is reported instead of
        // silently dropping a folder the user picked.
        session.openWorkspace(fourth)
        assertEquals(3, session.openWorkspaces.size)
        assertEquals(MAX_OPEN_WORKSPACES, session.openWorkspaces.size)
        assertTrue(session.statusMessage?.contains("close one first") == true)

        // Reopening an already open folder just brings it to the front, no duplicate.
        session.openWorkspace(first)
        assertEquals(3, session.openWorkspaces.size)
        assertEquals(first.absolutePath, session.activeWorkspace?.root?.absolutePath)

        // Closing it again frees a slot for the folder that was refused.
        session.closeWorkspace(session.activeWorkspace!!)
        assertEquals(2, session.openWorkspaces.size)
        session.openWorkspace(fourth)
        assertEquals(3, session.openWorkspaces.size)
        assertTrue(session.openWorkspaces.any { it.root.absolutePath == fourth.absolutePath })
    }

    @Test
    fun `workspace folder must be a folder and missing folders are pruned`() {
        val session = XedWorkspaceViewModel()
        val notAFolder = tempFolder.newFile("main.pwn")

        session.openWorkspace(notAFolder)
        assertFalse(session.isWorkspaceOpen)
        assertTrue(session.statusMessage?.startsWith("Not a folder") == true)

        val gone = tempFolder.newFolder("removed")
        session.openWorkspace(gone)
        assertTrue(session.isWorkspaceOpen)

        assertTrue(gone.deleteRecursively())
        assertEquals(1, session.closeMissingWorkspaces())
        assertFalse(session.isWorkspaceOpen)
        assertEquals(null, session.activeWorkspace)
    }

    @Test
    fun `closing every workspace leaves an empty session`() {
        val session = XedWorkspaceViewModel()
        session.openWorkspace(tempFolder.newFolder("gamemodes"))
        session.openWorkspace(tempFolder.newFolder("scriptfiles"))
        assertEquals(2, session.openWorkspaces.size)

        session.closeAllWorkspaces()
        assertFalse(session.isWorkspaceOpen)
        assertEquals(0, session.openWorkspaces.size)
        assertEquals(null, session.activeWorkspace)
    }

    @Test
    fun `workspace tree lists folders before files and collapses folders`() {
        val root = tempFolder.newFolder("tree")
        File(root, "zulu.pwn").writeText("zulu")
        File(root, "alpha.pwn").writeText("alpha")
        File(root, "sub").mkdirs()
        File(root, "sub/inner.pwn").writeText("inner")
        File(root, "notes.txt").writeText("plain notes")

        val session = XedWorkspaceViewModel()
        session.openWorkspace(root)

        // The scan runs on Dispatchers.IO, so wait for it rather than racing it.
        val workspace = session.activeWorkspace!!
        val tree = waitForTree(session, workspace)
        val names = tree.map { it.file.name }

        assertEquals(listOf("sub", "alpha.pwn", "notes.txt", "zulu.pwn"), names)

        val sub = tree.first()
        assertTrue(sub.isDirectory)
        assertEquals(listOf("inner.pwn"), sub.children.map { it.file.name })

        workspace.toggleFolder(sub)
        assertFalse(workspace.isExpanded(sub))

        workspace.toggleFolder(sub)
        assertTrue(workspace.isExpanded(sub))
    }

    /** Polls until the background scan of [workspace] has landed. */
    private fun waitForTree(
        session: XedWorkspaceViewModel,
        workspace: Workspace
    ): List<WorkspaceNode> {
        repeat(200) {
            if (workspace.tree.isNotEmpty()) return workspace.tree
            Thread.sleep(10)
        }
        session.refreshTree(workspace)
        repeat(200) {
            if (workspace.tree.isNotEmpty()) return workspace.tree
            Thread.sleep(10)
        }
        return workspace.tree
    }

    @Test
    fun `new Pawn file names default to pwn and reject paths or unsupported extensions`() {
        assertEquals("new_mode.pwn", normalizeNewPawnFileName("new_mode"))
        assertEquals("library.inc", normalizeNewPawnFileName("library.inc"))
        assertEquals(null, normalizeNewPawnFileName("../outside.pwn"))
        assertEquals(null, normalizeNewPawnFileName("notes.txt"))
        assertEquals(null, normalizeNewPawnFileName(" "))
    }

    @Test
    fun `terminal tokenizer supports quoted paths without shell expansion`() {
        assertEquals(
            listOf("pawncc", "gamemodes/test mode.pwn", "-d=3"),
            XedTerminalCommandParser.tokenize("pawncc \"gamemodes/test mode.pwn\" -d=3")
        )
        assertEquals(null, XedTerminalCommandParser.tokenize("pawncc \"unfinished path.pwn"))
        assertEquals(listOf("help"), XedTerminalCommandParser.suggestions("h", "mode.pwn"))
        assertEquals(listOf("pawncc \"mode.pwn\""), XedTerminalCommandParser.suggestions("pawncc ", "mode.pwn"))
        assertEquals(listOf("clear"), XedTerminalCommandParser.suggestions("cle", "mode.pwn"))
        assertEquals(listOf("celar"), XedTerminalCommandParser.suggestions("cel", "mode.pwn"))
        assertEquals(
            listOf("switch 3.10.7", "switch 3.10.11"),
            XedTerminalCommandParser.suggestions("switch 3", "mode.pwn")
        )
    }

    @Test
    fun `pawncc source is confined to workspace and Pawn files`() {
        val workspace = tempFolder.newFolder("workspace")
        val source = File(workspace, "mode.pwn").apply { writeText("main() {}") }
        val outside = tempFolder.newFile("outside.pwn")

        assertEquals(
            source.canonicalFile,
            XedTerminalCommandParser.pawnccInvocation(listOf("mode.pwn", "-d=3"), workspace)?.source
        )
        assertEquals(null, XedTerminalCommandParser.pawnccInvocation(emptyList(), workspace))
        assertEquals(null, XedTerminalCommandParser.pawnccInvocation(listOf("-d=3"), workspace))
        assertEquals(null, XedTerminalCommandParser.pawnccInvocation(listOf("../${outside.name}"), workspace))
        assertEquals(null, XedTerminalCommandParser.pawnccInvocation(listOf("notes.txt"), workspace))
    }

    @Test
    fun `simulation data preserves localized Markdown docs`() {
        val entries = XedSimulationData.parse(
            "0x01:terminal.docs.id:0x02:# MC Developer Portal\\nDokumentasi\n" +
                "0x01:terminal.docs.en:0x02:# MC Developer Portal\\nDocumentation"
        )

        assertEquals("# MC Developer Portal\nDokumentasi", entries["terminal.docs.id"])
        assertEquals("# MC Developer Portal\nDocumentation", entries["terminal.docs.en"])
    }

    @Test
    fun `internal completion descriptions localize and preserve signatures`() {
        val dataset = InternDat.parse(
            """
                0x01:description.id:0x02:Declares a local or global variable:0x03:Mendeklarasikan variabel lokal atau global
                0x01:description.id:0x02:Prints a plain string:0x03:Mencetak teks biasa
                0x01:description.es:0x02:Declares a local or global variable:0x03:Declara una variable local o global
                0x01:description.es:0x02:Prints a plain string:0x03:Imprime un texto simple
                0x01:item|KEYWORD|new:0x02:Declares a local or global variable
                0x01:item|FUNCTION|print:0x02:Prints a plain string: print(const string[])
                0x01:item|KEYWORD|stock:0x02:Description without translation
            """.trimIndent()
        )

        val keyword = dataset.items.first { it.name == "new" }
        val function = dataset.items.first { it.name == "print" }
        val untranslated = dataset.items.first { it.name == "stock" }

        assertEquals("Mendeklarasikan variabel lokal atau global", keyword.descriptionFor(CompilerConfig.AppLanguage.ID))
        assertEquals("Declares a local or global variable", keyword.descriptionFor(CompilerConfig.AppLanguage.EN))
        assertEquals("Mencetak teks biasa: print(const string[])", function.descriptionFor(CompilerConfig.AppLanguage.ID))
        assertEquals("Description without translation", untranslated.descriptionFor(CompilerConfig.AppLanguage.ID))
        assertEquals("Declara una variable local o global", keyword.descriptionFor(CompilerConfig.AppLanguage.ES))
        assertEquals("Imprime un texto simple: print(const string[])", function.descriptionFor(CompilerConfig.AppLanguage.ES))
        assertEquals("Description without translation", untranslated.descriptionFor(CompilerConfig.AppLanguage.ES))
    }
}
