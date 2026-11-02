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
                "gamemode0" -> configuration.gamemode = editorGamemodeValue(value)
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

    /**
     * Reads [dir]/server.cfg, or the built-in defaults when the file is missing or
     * unreadable. Callers that must show a configuration for a folder which has not
     * been set up yet use this instead of [read].
     */
    fun readOrDefault(dir: File): Configuration {
        val file = File(dir, "server.cfg")
        return if (file.isFile) runCatching { read(file) }.getOrDefault(Configuration()) else Configuration()
    }

    /** Writes the default `server.cfg` into [dir] unless one is already there. */
    fun ensureDefaultConfig(dir: File): File {
        val file = File(dir, "server.cfg")
        if (!file.isFile) {
            file.parentFile?.mkdirs()
            file.writeText(Configuration.defaultFileContent())
        }
        return file
    }

    /** `blank` -> `blank.pwn`: the editor shows a Pawn file name, SA-MP does not. */
    fun editorGamemodeValue(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return Configuration.DEFAULT_GAMEMODE
        val name = trimmed.substringBefore(' ')
        val repeat = trimmed.drop(name.length).trim()
        val withExtension = if (name.substringAfterLast('.', "").isEmpty()) "$name.pwn" else name
        return if (repeat.isEmpty()) withExtension else "$withExtension $repeat"
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
