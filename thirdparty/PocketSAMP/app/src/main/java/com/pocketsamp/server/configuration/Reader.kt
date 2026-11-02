package com.pocketsamp.server.configuration

import java.io.File

/**
 * Reads a `server.cfg` into a [Configuration].
 */
object Reader {

    /** Keys the editor owns. Everything else in the file is left untouched. */
    private val knownKeys = setOf(
        "hostname", "port", "maxplayers", "rcon_password", "gamemode0",
        "filterscripts", "plugins", "announce", "query", "lanmode", "sleep"
    )

    fun read(file: File): Configuration {
        val configuration = Configuration()
        if (!file.isFile) return configuration

        file.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEachLine
            /* `#` is a comment; `echo` is an SA-MP directive that prints a banner. */
            if (trimmed.startsWith("#") || trimmed.startsWith("echo", ignoreCase = true)) return@forEachLine

            val parts = trimmed.split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) return@forEachLine

            val value = parts[1]
            when (parts[0].lowercase()) {
                "hostname" -> configuration.hostname = value
                "port" -> configuration.port = value.toIntOrNull()
                    ?.takeIf { it in 1..65535 }
                    ?: throw IllegalArgumentException("Invalid port in server.cfg: $value")
                "maxplayers" -> configuration.maxPlayers = value.toIntOrNull()
                    ?.takeIf { it in 1..1000 }
                    ?: throw IllegalArgumentException("Invalid maxplayers in server.cfg: $value")
                "rcon_password" -> configuration.rconPassword = value
                "gamemode0" -> configuration.gamemode = value
                "filterscripts" -> configuration.filterscripts = value
                "plugins" -> configuration.plugins = value
                "announce" -> configuration.announce = value == "1"
                "query" -> configuration.query = value == "1"
                "lanmode" -> configuration.lanmode = value == "1"
                "sleep" -> configuration.sleep = value.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid sleep value in server.cfg: $value")
            }
        }
        return configuration
    }

    /** True when [file] sets at least one key this app models. */
    fun hasKnownKeys(file: File): Boolean {
        if (!file.isFile) return false
        return file.readLines().any { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@any false
            val key = trimmed.split(Regex("\\s+"), limit = 2).first().lowercase()
            key in knownKeys
        }
    }
}
