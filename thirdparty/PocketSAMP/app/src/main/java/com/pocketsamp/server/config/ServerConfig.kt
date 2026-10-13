package com.pocketsamp.server.config

import java.io.File

data class ServerConfig(
    var hostname: String = "PocketSAMP Mobile Server",
    var port: Int = 7777,
    var maxPlayers: Int = 50,
    var rconPassword: String = "admin123",
    var gamemode: String = "bare 1",
    var filterscripts: String = "",
    var plugins: String = "",
    var announce: Boolean = false,
    var query: Boolean = true,
    var lanmode: Boolean = true,
    var sleep: Int = 1
) {
    companion object {
        fun loadFromFile(file: File): ServerConfig {
            val config = ServerConfig()
            if (!file.exists()) return config

            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("echo")) return@forEachLine

                val parts = trimmed.split(Regex("\\s+"), limit = 2)
                if (parts.size >= 2) {
                    val key = parts[0].lowercase()
                    val value = parts[1]
                    when (key) {
                        "hostname" -> config.hostname = value
                        "port" -> config.port = value.toIntOrNull() ?: 7777
                        "maxplayers" -> config.maxPlayers = value.toIntOrNull() ?: 50
                        "rcon_password" -> config.rconPassword = value
                        "gamemode0" -> config.gamemode = value
                        "filterscripts" -> config.filterscripts = value
                        "plugins" -> config.plugins = value
                        "announce" -> config.announce = value == "1"
                        "query" -> config.query = value == "1"
                        "lanmode" -> config.lanmode = value == "1"
                        "sleep" -> config.sleep = value.toIntOrNull() ?: 1
                    }
                }
            }
            return config
        }

        fun saveToFile(config: ServerConfig, file: File) {
            val content = buildString {
                appendLine("echo Executing Server Config...")
                appendLine("lanmode ${if (config.lanmode) 1 else 0}")
                appendLine("rcon_password ${config.rconPassword}")
                appendLine("maxplayers ${config.maxPlayers}")
                appendLine("port ${config.port}")
                appendLine("hostname ${config.hostname}")
                appendLine("gamemode0 ${config.gamemode}")
                if (config.filterscripts.isNotBlank()) {
                    appendLine("filterscripts ${config.filterscripts}")
                }
                if (config.plugins.isNotBlank()) {
                    appendLine("plugins ${config.plugins}")
                }
                appendLine("announce ${if (config.announce) 1 else 0}")
                appendLine("query ${if (config.query) 1 else 0}")
                appendLine("sleep ${config.sleep}")
                appendLine("ackslimit 3000")
                appendLine("weburl www.sa-mp.com")
                appendLine("maxnpc 0")
                appendLine("onfoot_rate 40")
                appendLine("incar_rate 40")
                appendLine("weapon_rate 40")
                appendLine("stream_distance 300.0")
                appendLine("stream_rate 1000")
            }
            file.writeText(content)
        }
    }
}
