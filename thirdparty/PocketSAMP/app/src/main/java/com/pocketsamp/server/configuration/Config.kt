package com.pocketsamp.server.configuration

/**
 * One `server.cfg` file, as the SA-MP server understands it.
 */
data class Configuration(
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
)
