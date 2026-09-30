package com.rvdjv.pawnmc.data.compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Covers the "Ignore case" filesystem workflow that runs when a file is browsed:
 * back the folder up, lowercase every other file name, lowercase every static
 * include reference, and never repeat the work while the backup folder exists.
 *
 * Every test builds its own project folder, so no manual fixture is needed.
 */
class PawnCompilerIgnoreCaseWorkflowTest {

    /** Mixed case project used by most of the checks below. */
    private fun createProject(rootName: String): File {
        val root = File(System.getProperty("java.io.tmpdir"), "$rootName-${System.nanoTime()}")
        val gamemodes = File(root, "Gamemodes")
        val library = File(gamemodes, "Library")
        library.mkdirs()

        File(gamemodes, "Stock.pwn").writeText(
            "#include \"Warung.pwn\"\n#include \"Library/Ekosistem.inc\"\nmain() { return; }\n"
        )
        File(gamemodes, "Warung.pwn").writeText("#include \"Aaaa.inc\"\nstock Warung() { return 1; }\n")
        File(library, "Ekosistem.inc").writeText("stock Ekosistem() { return 2; }\n")
        return root
    }

    @Test
    fun `backs the whole folder up and lowercases every other name`() {
        val root = createProject("pawnmc-ignore-case")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            val result = PawnCompiler.convertFolderToLowerCase(source.absolutePath)

            assertEquals(PawnCompiler.ConversionStatus.CONVERTED, result.status)

            val backup = PawnCompiler.backupDirFor(gamemodes)
            assertTrue("backup folder must exist", backup.isDirectory)
            assertEquals("Gamemodes.backup", backup.name)
            assertTrue("backup keeps the original names", File(backup, "Stock.pwn").exists())
            assertTrue("backup keeps the original folder names", File(backup, "Library").isDirectory)
            assertTrue("backup keeps include contents", File(backup, "Warung.pwn").exists())

            // The browsed file keeps its exact name, everything else is lowercase.
            assertTrue(File(gamemodes, "Stock.pwn").exists())
            assertTrue(File(gamemodes, "warung.pwn").exists())
            assertTrue(File(gamemodes, "library").isDirectory)
            assertTrue(File(gamemodes, "library/ekosistem.inc").exists())
            assertFalse(File(gamemodes, "Warung.pwn").exists())
            assertTrue(result.renamedEntries >= 3)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `lowercases every static include reference including selected file`() {
        val root = createProject("pawnmc-ignore-case-include")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            PawnCompiler.convertFolderToLowerCase(source.absolutePath)

            val selectedText = File(gamemodes, "Stock.pwn").readText()
            assertTrue(selectedText.contains("#include \"warung.pwn\""))
            assertTrue(selectedText.contains("#include \"library/ekosistem.inc\""))

            val otherText = File(gamemodes, "warung.pwn").readText()
            assertTrue(otherText.contains("#include \"aaaa.inc\""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `lowercases include references without exception`() {
        val source = """
            #include "Aaaa"
            #include <Bbb>
            #include 'CcD'
            #include <system>
        """.trimIndent()

        val rewritten = PawnCompiler.lowercaseIncludeReferences(source)

        assertTrue(rewritten.contains("#include \"aaaa\""))
        assertTrue(rewritten.contains("#include <bbb>"))
        assertTrue(rewritten.contains("#include 'ccd'"))
        assertTrue(rewritten.contains("#include <system>"))
        assertEquals(source, PawnCompiler.lowercaseIncludeReferences(rewritten))
    }

    @Test
    fun `skips the instructions while the backup folder exists`() {
        val root = createProject("pawnmc-ignore-case-repeat")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")
            val first = PawnCompiler.convertFolderToLowerCase(source.absolutePath)
            val backup = PawnCompiler.backupDirFor(gamemodes)

            assertFalse(PawnCompiler.needsConversion(gamemodes, backup.absolutePath))

            val second = PawnCompiler.convertFolderToLowerCase(
                selectedFilePath = first.sourceFile,
                rememberedBackupDir = backup.absolutePath
            )
            assertEquals(PawnCompiler.ConversionStatus.ALREADY_CONVERTED, second.status)
            assertEquals(0, second.renamedEntries)

            // A new mixed case file inside an already converted folder must not
            // trigger the instructions again.
            File(gamemodes, "Extra.PWN").writeText("#include \"Warung.pwn\"\n")
            assertTrue(File(gamemodes, "Extra.PWN").exists())
            assertFalse(PawnCompiler.needsConversion(gamemodes, backup.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `runs the instructions again when the backup folder is gone`() {
        val root = createProject("pawnmc-ignore-case-rebuild")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")
            val backup = PawnCompiler.backupDirFor(gamemodes)
            PawnCompiler.convertFolderToLowerCase(source.absolutePath)

            backup.deleteRecursively()
            assertTrue(PawnCompiler.needsConversion(gamemodes, backup.absolutePath))

            val again = PawnCompiler.convertFolderToLowerCase(
                selectedFilePath = File(gamemodes, "Stock.pwn").absolutePath,
                rememberedBackupDir = backup.absolutePath
            )
            assertEquals(PawnCompiler.ConversionStatus.CONVERTED, again.status)
            assertTrue(PawnCompiler.backupDirFor(gamemodes).isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `keeps a colliding lowercase name instead of overwriting it`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-ignore-case-clash-${System.nanoTime()}")
        try {
            val gamemodes = File(root, "Gamemodes")
            gamemodes.mkdirs()
            File(gamemodes, "Main.pwn").writeText("main() { return; }\n")
            File(gamemodes, "Other.pwn").writeText("#include \"Helper.inc\"\n")
            File(gamemodes, "helper.inc").writeText("stock Helper() { return 1; }\n")

            val result = PawnCompiler.convertFolderToLowerCase(File(gamemodes, "Main.pwn").absolutePath)

            assertEquals(PawnCompiler.ConversionStatus.CONVERTED, result.status)
            assertTrue(File(gamemodes, "Main.pwn").exists())
            assertTrue("the differently cased file is kept under a unique name", File(gamemodes, "other.pwn").exists())
            assertTrue(File(gamemodes, "helper.inc").exists())
            assertTrue(File(gamemodes, "other.pwn").readText().contains("#include \"helper.inc\""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `resolves the browsed file again after the conversion`() {
        val root = createProject("pawnmc-ignore-case-source")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            val result = PawnCompiler.convertFolderToLowerCase(source.absolutePath)

            assertTrue(File(result.sourceFile).exists())
            assertEquals("Stock.pwn", File(result.sourceFile).name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `reports a folder without pawn files as nothing to convert`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-ignore-case-empty-${System.nanoTime()}")
        try {
            val folder = File(root, "Assets")
            folder.mkdirs()
            File(folder, "Readme.txt").writeText("nothing to convert here")

            assertFalse(PawnCompiler.needsConversion(folder, null))
            val result = PawnCompiler.convertFolderToLowerCase(File(folder, "missing.pwn").absolutePath)
            assertEquals(PawnCompiler.ConversionStatus.FAILED, result.status)
        } finally {
            root.deleteRecursively()
        }
    }
}
