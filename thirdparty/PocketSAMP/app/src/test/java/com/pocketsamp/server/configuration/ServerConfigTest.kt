package com.pocketsamp.server.configuration

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
                plugins mysql.so
                """.trimIndent()
            )

            val config = Reader.read(configFile)

            assertEquals("My Test Server", config.hostname)
            assertEquals(7799, config.port)
            assertEquals(32, config.maxPlayers)
            assertEquals("test-secret", config.rconPassword)
            assertEquals("city 1", config.gamemode)
            assertEquals("admin fs", config.filterscripts)
            assertEquals("mysql.so", config.plugins)
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
    fun writerRefusesToCreateAMissingConfigFile() {
        val directory = Files.createTempDirectory("pocketsamp-config").toFile()
        try {
            val missingConfig = File(directory, "server.cfg")

            try {
                Writer.write(Configuration(), missingConfig)
                throw AssertionError("Expected missing config to be rejected")
            } catch (_: IllegalArgumentException) {
                assertFalse(missingConfig.exists())
            }
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
}
