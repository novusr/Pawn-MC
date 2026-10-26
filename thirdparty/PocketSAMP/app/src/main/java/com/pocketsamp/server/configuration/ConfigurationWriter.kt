package com.pocketsamp.server.configuration

import java.io.File

/**
 * Writes a [Configuration] back to `server.cfg`.
 *
 * The key order matches the file SA-MP ships with, and the keys this app does not
 * edit (`ackslimit`, `stream_rate`, the rate limits) are emitted with their stock
 * values. Writing them explicitly keeps the file complete: a server started from a
 * minimal config would otherwise fall back to different defaults than a user
 * expects after opening the editor once.
 */
object ConfigurationWriter {

    fun write(configuration: Configuration, file: File) {
        val content = buildString {
            appendLine("echo Executing Server Config...")
            appendLine("lanmode ${flag(configuration.lanmode)}")
            appendLine("rcon_password ${configuration.rconPassword}")
            appendLine("maxplayers ${configuration.maxPlayers}")
            appendLine("port ${configuration.port}")
            appendLine("hostname ${configuration.hostname}")
            appendLine("gamemode0 ${configuration.gamemode}")
            if (configuration.filterscripts.isNotBlank()) {
                appendLine("filterscripts ${configuration.filterscripts}")
            }
            if (configuration.plugins.isNotBlank()) {
                appendLine("plugins ${configuration.plugins}")
            }
            appendLine("announce ${flag(configuration.announce)}")
            appendLine("query ${flag(configuration.query)}")
            appendLine("sleep ${configuration.sleep}")
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

    /** SA-MP expects `1` and `0`, not booleans. */
    private fun flag(value: Boolean): Int = if (value) 1 else 0
}
