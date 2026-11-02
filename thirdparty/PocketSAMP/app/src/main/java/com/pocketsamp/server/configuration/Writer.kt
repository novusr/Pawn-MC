package com.pocketsamp.server.configuration

import java.io.File

/**
 * Writes a [Configuration] back to `server.cfg`.
 */
object Writer {

    fun write(configuration: Configuration, file: File) {
        if (!file.isFile) {
            // A folder that has never been set up still gets a complete file, so the
            // Config screen never writes half a configuration next to nothing.
            file.parentFile?.mkdirs()
            file.writeText(Configuration.defaultFileContent())
        }
        val values = linkedMapOf(
            "hostname" to configuration.hostname,
            "port" to configuration.port.toString(),
            "maxplayers" to configuration.maxPlayers.toString(),
            "rcon_password" to configuration.rconPassword,
            "gamemode0" to Configuration.configGamemodeValue(configuration.gamemode),
            "filterscripts" to configuration.filterscripts,
            "plugins" to configuration.plugins,
            "announce" to flag(configuration.announce).toString(),
            "query" to flag(configuration.query).toString(),
            "lanmode" to flag(configuration.lanmode).toString(),
            "sleep" to configuration.sleep.toString()
        )
        val found = mutableSetOf<String>()
        val originalLines = file.readLines()
        val content = buildString {
            originalLines.forEach { line ->
                val trimmed = line.trimStart()
                val key = trimmed.split(Regex("\\s+"), limit = 2).first().lowercase()
                val value = values[key]
                if (value == null || trimmed.startsWith("#") || trimmed.startsWith("echo", ignoreCase = true)) {
                    appendLine(line)
                } else if (found.add(key)) {
                    val inlineComment = Regex("\\s+#.*$").find(line)?.value.orEmpty()
                    appendLine("$key $value$inlineComment")
                }
            }
            values.forEach { (key, value) ->
                if (key !in found && value.isNotBlank()) appendLine("$key $value")
            }
        }
        file.writeText(content)
    }

    /** SA-MP expects `1` and `0`, not booleans. */
    private fun flag(value: Boolean): Int = if (value) 1 else 0
}
