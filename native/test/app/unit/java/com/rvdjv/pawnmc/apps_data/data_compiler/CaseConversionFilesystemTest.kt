package com.rvdjv.pawnmc.apps_data.data_compiler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Standalone filesystem tests for the Settings "Filesystem" option
 * (`Ignore case`), backed by [CaseConversion] through the [Compiler] facade.
 *
 * Every test builds its own temporary project tree, exercises the conversion,
 * and asserts both the visible result and the invariants the compiler relies
 * on — so the option can be validated without ever opening the app by hand.
 */
class CaseConversionFilesystemTest {

    /** Builds a mixed-case project: gamemode + includes + nested folders. */
    private fun createProject(rootName: String): File {
        val root = File(System.getProperty("java.io.tmpdir"), "$rootName-${System.nanoTime()}")
        val gamemodes = File(root, "Gamemodes")
        val library = File(gamemodes, "Library")
        val filterscripts = File(gamemodes, "Filterscripts")
        library.mkdirs()
        filterscripts.mkdirs()

        File(gamemodes, "Stock.pwn").writeText(
            "#include \"Warung.pwn\"\n" +
                "#include \"Library/Ekosistem.inc\"\n" +
                "#include <SampAddons/Util.INC>\n" +
                "main() { return 0; }\n"
        )
        File(gamemodes, "Warung.pwn").writeText(
            "#include \"library/Ekosistem.inc\"\nstock Warung() { return 1; }\n"
        )
        File(library, "Ekosistem.inc").writeText("stock Ekosistem() { return 2; }\n")
        File(library, "SampAddons").mkdirs()
        File(library, "SampAddons/Util.INC").writeText("stock Util() { return 3; }\n")
        File(filterscripts, "Mapping.pwn").writeText("public OnFilterScriptInit() { return 1; }\n")
        return root
    }

    @Test
    fun `converts names and include references to lowercase`() {
        val root = createProject("pawnmc-fs-convert")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            val result = Compiler.convertFolderToLowerCase(source.absolutePath)

            assertEquals(CaseConversion.ConversionStatus.CONVERTED, result.status)
            // The browsed file keeps its exact name.
            assertTrue(File(gamemodes, "Stock.pwn").isFile)
            // Everything else is lowercase.
            assertTrue(File(gamemodes, "warung.pwn").isFile)
            assertTrue(File(gamemodes, "library").isDirectory)
            assertTrue(File(gamemodes, "library/ekosistem.inc").isFile)
            assertTrue(File(gamemodes, "library/sampaddons").isDirectory)
            assertTrue(File(gamemodes, "library/sampaddons/util.inc").isFile)
            assertTrue(File(gamemodes, "filterscripts/mapping.pwn").isFile)
            assertFalse(File(gamemodes, "Warung.pwn").exists())
            assertFalse(File(gamemodes, "Library").exists())
            assertTrue(result.renamedEntries >= 5)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `include references are rewritten to the lowercase names`() {
        val root = createProject("pawnmc-fs-includes")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            Compiler.convertFolderToLowerCase(source.absolutePath)

            val content = source.readText()
            assertFalse("upper-case include must be gone", content.contains("Warung.pwn\""))
            assertTrue(content.contains("#include \"warung.pwn\""))
            assertTrue(content.contains("#include \"library/ekosistem.inc\""))
            assertTrue(content.contains("#include <sampaddons/util.inc>"))

            val warung = File(gamemodes, "warung.pwn")
            val warungContent = warung.readText()
            assertTrue(warungContent.contains("#include \"library/ekosistem.inc\""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `backup folder keeps the original mixed-case tree`() {
        val root = createProject("pawnmc-fs-backup")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            val result = Compiler.convertFolderToLowerCase(source.absolutePath)
            assertEquals(CaseConversion.ConversionStatus.CONVERTED, result.status)

            val backup = Compiler.backupDirFor(gamemodes)
            assertTrue(backup.isDirectory)
            assertEquals("Gamemodes.backup", backup.name)
            // The backup is the untouched copy, original casing included.
            assertTrue(File(backup, "Stock.pwn").isFile)
            assertTrue(File(backup, "Warung.pwn").isFile)
            assertTrue(File(backup, "Library/Ekosistem.inc").isFile)
            assertTrue(File(backup, "Library/SampAddons/Util.INC").isFile)
            assertTrue(File(backup, "Filterscripts/Mapping.pwn").isFile)

            val backupContent = File(backup, "Stock.pwn").readText()
            assertTrue("backup keeps the original include casing", backupContent.contains("Warung.pwn\""))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `a second run is a no-op while the backup folder exists`() {
        val root = createProject("pawnmc-fs-idempotent")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            val first = Compiler.convertFolderToLowerCase(source.absolutePath)
            assertEquals(CaseConversion.ConversionStatus.CONVERTED, first.status)

            val second = Compiler.convertFolderToLowerCase(source.absolutePath)
            assertEquals(CaseConversion.ConversionStatus.ALREADY_CONVERTED, second.status)
            // Nothing was renamed on the second pass.
            assertEquals(0, second.renamedEntries)
            assertEquals(0, second.rewrittenIncludes)
            // The lowercase tree survived untouched.
            assertTrue(File(gamemodes, "warung.pwn").isFile)
            assertTrue(File(gamemodes, "library/ekosistem.inc").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `needsConversion mirrors the backup folder state`() {
        val root = createProject("pawnmc-fs-needs")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            assertTrue(Compiler.needsConversion(gamemodes))
            Compiler.convertFolderToLowerCase(source.absolutePath)
            // With the backup present the folder is considered consistent; the
            // remembered backup path also short-circuits the probe.
            assertFalse(
                Compiler.needsConversion(gamemodes, Compiler.backupDirFor(gamemodes).absolutePath)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `refreshBackupForCompile keeps the snapshot current`() {
        val root = createProject("pawnmc-fs-refresh")
        try {
            val gamemodes = File(root, "Gamemodes")
            val source = File(gamemodes, "Stock.pwn")

            Compiler.convertFolderToLowerCase(source.absolutePath)
            val backup = Compiler.backupDirFor(gamemodes)

            // A file added after the conversion must appear in the refreshed snapshot.
            val added = File(gamemodes, "newmode.pwn")
            added.writeText("main() { return 9; }\n")
            assertTrue(Compiler.refreshBackupForCompile(gamemodes))
            assertTrue(File(backup, "newmode.pwn").isFile)

            // A file removed after the conversion must disappear from the snapshot.
            added.delete()
            assertTrue(Compiler.refreshBackupForCompile(gamemodes))
            assertFalse(File(backup, "newmode.pwn").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `an already lowercase folder reports nothing to do`() {
        val root = createProject("pawnmc-fs-lower")
        try {
            val dir = File(root, "flat")
            dir.mkdirs()
            File(dir, "mode.pwn").writeText("#include \"a.inc\"\n")
            File(dir, "a.inc").writeText("stock a() { return 1; }\n")

            val result = Compiler.convertFolderToLowerCase(File(dir, "mode.pwn").absolutePath)
            assertEquals(CaseConversion.ConversionStatus.NOT_NEEDED, result.status)
            assertEquals(0, result.renamedEntries)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `lowercaseIncludeReferences lowercases every static include`() {
        val input = """
            #include "Warung.pwn"
            #include 'Library/Ekosistem.inc'
            #include <SampAddons/Util.INC>
            // keep the comment text as-is: #include "NotARealPath.PWN"
            main() { return 0; }
        """.trimIndent()

        val output = Compiler.lowercaseIncludeReferences(input)
        assertTrue(output.contains("#include \"warung.pwn\""))
        assertTrue(output.contains("#include 'library/ekosistem.inc'"))
        assertTrue(output.contains("#include <sampaddons/util.inc>"))
        // Comments are not include directives; the text stays untouched.
        assertTrue(output.contains("NotARealPath.PWN"))
    }

    @Test
    fun `a missing folder fails cleanly`() {
        val missing = File(
            System.getProperty("java.io.tmpdir"),
            "pawnmc-fs-missing-${System.nanoTime()}/nowhere.pwn"
        )
        val result = Compiler.convertFolderToLowerCase(missing.absolutePath)
        assertEquals(CaseConversion.ConversionStatus.FAILED, result.status)
    }
}
