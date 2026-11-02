package com.pocketsamp.server.configuration

import com.pocketsamp.server.user_interface.screens.createDestinationFor
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerConfigTest {

    @Test
    fun readerLoadsValuesFromTheSelectedConfigFile() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val configFile = File(directory, "server.cfg")
            configFile.writeText(
                """
                hostname My Test Server
                port 7799
                maxplayers 32
                rcon_password test-secret
                gamemode0 city 1
                filterscripts admin fs
                plugins mysql_static.so
                """.trimIndent()
            )

            val config = Reader.read(configFile)

            assertEquals("My Test Server", config.hostname)
            assertEquals(7799, config.port)
            assertEquals(32, config.maxPlayers)
            assertEquals("test-secret", config.rconPassword)
            // server.cfg stores the bare gamemode name, never the .pwn file on disk.
            assertEquals("city 1", config.gamemode)
            assertEquals("admin fs", config.filterscripts)
            assertEquals("mysql_static.so", config.plugins)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerUpdatesManagedValuesAndPreservesCommentsAndOtherDirectives() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val configFile = File(directory, "server.cfg")
            configFile.writeText(
                """
                # project settings
                hostname Old Name # keep this note
                port 7777
                custom_directive keep this value
                echo custom banner
                plugins old.so
                """.trimIndent()
            )
            val updated = Reader.read(configFile).copy(
                hostname = "New Name",
                port = 7799,
                plugins = "new.so"
            )

            Writer.write(updated, configFile)

            val result = configFile.readText()
            assertTrue(result.contains("# project settings"))
            assertTrue(result.contains("hostname New Name # keep this note"))
            assertTrue(result.contains("custom_directive keep this value"))
            assertTrue(result.contains("echo custom banner"))
            assertTrue(result.contains("port 7799"))
            assertTrue(result.contains("plugins new.so"))
            assertFalse(result.contains("hostname Old Name"))
            assertFalse(result.contains("port 7777"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun writerCreatesAMissingConfigFileFromTheRunnerDefaults() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val missingConfig = File(directory, "server.cfg")

            // A folder without server.cfg still gets a complete file from the shipped
            // defaults instead of being refused.
            Writer.write(Configuration(), missingConfig)

            assertTrue(missingConfig.exists())
            val written = Reader.read(missingConfig)
            assertEquals(Configuration.DEFAULT_HOSTNAME, written.hostname)
            assertEquals(Configuration.DEFAULT_PORT, written.port)
            assertEquals(Configuration.DEFAULT_MAX_PLAYERS, written.maxPlayers)
            assertEquals(Configuration.DEFAULT_RCON_PASSWORD, written.rconPassword)
            assertEquals("blank", written.gamemode)
            assertEquals("mapping", written.filterscripts)
            assertEquals(Configuration.DEFAULT_PLUGINS, written.plugins)
            // SA-MP wants the gamemode without its extension.
            assertTrue(missingConfig.readText().contains("gamemode0 blank"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readerFallsBackToTheRunnerDefaultsWhenTheFileIsMissing() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val config = Reader.readOrDefault(directory)

            assertEquals(Configuration.DEFAULT_HOSTNAME, config.hostname)
            assertEquals(7777, config.port)
            assertEquals(20, config.maxPlayers)
            assertEquals("rcon234", config.rconPassword)
            assertEquals("blank", config.gamemode)
            assertEquals("mapping", config.filterscripts)
            assertEquals("sscanf.so streamer.so", config.plugins)
            assertFalse(File(directory, "server.cfg").exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun ensureDefaultConfigWritesOnceAndKeepsAnExistingFile() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val created = Reader.ensureDefaultConfig(directory)
            val original = created.readText()

            created.writeText("hostname Custom\n")
            val second = Reader.ensureDefaultConfig(directory)

            assertEquals(created.absolutePath, second.absolutePath)
            assertEquals("hostname Custom\n", second.readText())
            assertTrue(original.contains("hostname ${Configuration.DEFAULT_HOSTNAME}"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun readerRejectsAnInvalidServerPortInsteadOfUsingAHiddenDefault() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val configFile = File(directory, "server.cfg")
            configFile.writeText("port 70000")

            try {
                Reader.read(configFile)
                throw AssertionError("Expected invalid port to be rejected")
            } catch (error: IllegalArgumentException) {
                assertTrue(error.message.orEmpty().contains("Invalid port"))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun gamemodeIsStoredWithoutThePawnFileExtension() {
        assertEquals("blank", Configuration.DEFAULT_GAMEMODE)
        assertEquals("mapping", Configuration.DEFAULT_FILTERSCRIPTS)
        assertFalse(Configuration.defaultFileContent().contains(".pwn"))
        assertTrue(Configuration.defaultFileContent().contains("gamemode0 blank"))
        assertTrue(Configuration.defaultFileContent().contains("filterscripts mapping"))
    }

    @Test
    fun createDestinationPrefersTheSelectedFolderOverTheOpenOne() {
        val directory = Files.createTempDirectory("pocketsamp-files").toFile()
        try {
            val root = File(directory, "PocketSAMP").apply { mkdirs() }
            val gamemodes = File(root, "gamemodes").apply { mkdirs() }
            val nested = File(gamemodes, "include").apply { mkdirs() }

            // A selected nested folder is the destination, even though the list is showing
            // the root: this is the "New file always lands in the parent" bug.
            assertEquals(nested, createDestinationFor(root, root, nested))
            // Nothing selected: the folder currently open is used.
            assertEquals(gamemodes, createDestinationFor(root, gamemodes, null))
            // A selected entry that is a file, or outside the root, is ignored.
            val file = File(root, "server.cfg").apply { writeText("hostname x") }
            assertEquals(root, createDestinationFor(root, root, file))
            assertEquals(root, createDestinationFor(root, root, directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
