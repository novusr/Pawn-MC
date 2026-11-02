package com.pocketsamp.server.configuration

/**
 * One `server.cfg` file, as the SA-MP server understands it.
 *
 * The defaults are the values PocketSAMP/PawnMC ships with, so a folder without a
 * `server.cfg` still has a complete, runnable configuration to show and to write back.
 */
data class Configuration(
    var hostname: String = DEFAULT_HOSTNAME,
    var port: Int = DEFAULT_PORT,
    var maxPlayers: Int = DEFAULT_MAX_PLAYERS,
    var rconPassword: String = DEFAULT_RCON_PASSWORD,
    var gamemode: String = DEFAULT_GAMEMODE,
    var filterscripts: String = DEFAULT_FILTERSCRIPTS,
    var plugins: String = DEFAULT_PLUGINS,
    var announce: Boolean = false,
    var query: Boolean = true,
    var lanmode: Boolean = true,
    var sleep: Int = 1
) {
    companion object {
        const val DEFAULT_HOSTNAME = "PocketSAMP/PawnMC Runner"
        const val DEFAULT_PORT = 7777
        const val DEFAULT_MAX_PLAYERS = 20
        const val DEFAULT_RCON_PASSWORD = "rcon234"
        const val DEFAULT_GAMEMODE = "blank.pwn"
        const val DEFAULT_FILTERSCRIPTS = "mapping.pwn"
        const val DEFAULT_PLUGINS = "sscanf.so streamer.so"

        /**
         * The text written when no `server.cfg` exists yet.
         *
         * SA-MP wants the gamemode without its extension and followed by the repeat
         * count, while the editor shows the file name the user picked, so the first
         * line is normalised when the file is rendered instead of stored.
         */
        fun defaultFileContent(): String = buildString {
            appendLine("echo Executing Server Config...")
            appendLine("lanmode 1")
            appendLine("rcon_password $DEFAULT_RCON_PASSWORD")
            appendLine("maxplayers $DEFAULT_MAX_PLAYERS")
            appendLine("port $DEFAULT_PORT")
            appendLine("hostname $DEFAULT_HOSTNAME")
            appendLine("gamemode0 ${configGamemodeValue(DEFAULT_GAMEMODE)}")
            appendLine("filterscripts $DEFAULT_FILTERSCRIPTS")
            appendLine("plugins $DEFAULT_PLUGINS")
            appendLine("announce 0")
            appendLine("query 1")
            appendLine("sleep 1")
            append("ackslimit 3000")
        }

        /** `blank.pwn` -> `blank`, keeping any repeat count the caller already wrote. */
        fun configGamemodeValue(editorValue: String): String {
            val value = editorValue.trim()
            if (value.isEmpty()) return DEFAULT_GAMEMODE.substringBeforeLast('.')
            val name = value.substringBefore(' ')
            val repeat = value.drop(name.length).trim()
            val withoutExtension = name.substringBeforeLast('.', name)
            return if (repeat.isEmpty()) withoutExtension else "$withoutExtension $repeat"
        }
    }
}
