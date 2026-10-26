package com.pocketsamp.server.configuration

import java.io.File

/**
 * Reads a `server.cfg` into a [Configuration].
 *
 * The parser is deliberately forgiving. A real SA-MP config is hand-edited, so it
 * contains comments, blank lines, `echo` banners and keys this app does not model.
 * Anything it cannot interpret is skipped rather than rejected: losing a setting is
 * recoverable, refusing to open the server is not.
 */
object ConfigurationReader {

    /** Keys the editor owns. Everything else in the file is left untouched. */
    private val knownKeys = setOf(
        "hostname", "port", "maxplayers", "rcon_password", "gamemode0",
        "filterscripts", "plugins", "announce", "query", "lanmode", "sleep"
    )

    fun read(file: File): Configuration {
        val configuration = Configuration()
        if (!file.exists()) return configuration

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine
            /* `#` is a comment; `echo` is an SA-MP directive that prints a banner. */
            if (trimmed.startsWith("#") || trimmed.startsWith("echo")) return@forEachLine

            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) return@forEachLine

            val value = parts[1]
            when (parts[0].lowercase()) {
                "hostname" -> configuration.hostname = value
                "port" -> configuration.port = value.toIntOrNull() ?: 7777
                "maxplayers" -> configuration.maxPlayers = value.toIntOrNull() ?: 50
                "rcon_password" -> configuration.rconPassword = value
                "gamemode0" -> configuration.gamemode = value
                "filterscripts" -> configuration.filterscripts = value
                "plugins" -> configuration.plugins = value
                "announce" -> configuration.announce = value == "1"
                "query" -> configuration.query = value == "1"
                "lanmode" -> configuration.lanmode = value == "1"
                "sleep" -> configuration.sleep = value.toIntOrNull() ?: 1
            }
        }
        return configuration
    }

    /** True when [file] sets at least one key this app models. */
    fun hasKnownKeys(file: File): Boolean {
        if (!file.exists()) return false
        return file.readLines().any { line ->
            val key = line.trim().substringBefore(' ').lowercase()
            key in knownKeys
        }
    }
}
