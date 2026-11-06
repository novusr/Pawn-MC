package com.rvdjv.pawnmc.apps_data.data_syntax

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped symbol table.
 *
 * `InternDat` is `internal`, so this test reads the TOML the app bundles and
 * checks it the same way the parser does: no placeholder text, a description for
 * every symbol, and a value for every one of the requested `server.cfg` names.
 */
class InternTomlTest {

    @Test
    fun `item and include descriptions load every supported language`() {
        val parsed = InternDat.parse(
            """
            [item.KEYWORD]
            "stock" = { local_en = "English item", local_id = "Item Indonesia", local_es = "Item espanol", local_ru = "Элемент по-русски", local_tl = "Item sa Tagalog" }

            [native."a_samp.inc"]
            "SendClientMessage" = { local_en = "English native", local_id = "Native Indonesia", local_es = "Native espanol", local_ru = "Функция по-русски", local_tl = "Native sa Tagalog" }
            """.trimIndent()
        )

        val item = parsed.items.single()
        assertEquals("Item espanol", item.descriptionFor(CompilerConfig.AppLanguage.ES))
        assertEquals("Элемент по-русски", item.descriptionFor(CompilerConfig.AppLanguage.RU))
        assertEquals("Item sa Tagalog", item.descriptionFor(CompilerConfig.AppLanguage.TL))

        val symbol = parsed.symbols.single()
        assertEquals("Native espanol", symbol.descriptionEs)
        assertEquals("Функция по-русски", symbol.descriptionRu)
        assertEquals("Native sa Tagalog", symbol.descriptionTl)
    }

    private val toml: String by lazy {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "native/data/_internal.toml").isFile }
            ?: File(System.getProperty("user.dir").orEmpty())
        File(root, "native/data/_internal.toml").readText()
    }

    @Test
    fun `every bundled internal description has all supported languages`() {
        val rows = toml.lineSequence()
            .map(String::trim)
            .filter { it.startsWith("\"") && it.contains("= {") }
            .toList()
        val missing = rows.flatMap { row ->
            // The shipped rows carry the bare language code; the `local_` prefix
            // is accepted for older data. A language counts as present when
            // either spelling carries a non-blank value.
            listOf("en", "id", "es", "ru", "tl")
                .filter { language ->
                    Regex("(?:local_)?$language\\s*=\\s*\"([^\"]*)\"")
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        .isNullOrBlank()
                }
                .map { language -> "local_$language: $row" }
        }

        assertTrue("expected all bundled descriptions to include five languages", rows.size >= 750)
        assertTrue("descriptions missing a language: $missing", missing.isEmpty())
    }

    /** `"Name" = { local_en = "...", local_id = "..." }` rows of one table. */
    private fun rowsUnder(prefix: String): List<Triple<String, String, String>> {
        val out = mutableListOf<Triple<String, String, String>>()
        var active = false
        for (raw in toml.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("[")) {
                active = line.startsWith("[$prefix.")
                continue
            }
            if (!active || !line.startsWith("\"")) continue
            val name = line.substring(1, line.indexOf('"', 1))
            val en = Regex("(?:local_)?en\\s*=\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty()
            val id = Regex("(?:local_)?id\\s*=\\s*\"([^\"]*)\"").find(line)?.groupValues?.get(1).orEmpty()
            out += Triple(name, en, id)
        }
        return out
    }

    @Test
    fun `the table parses into natives and forwards`() {
        val natives = rowsUnder("native")
        val forwards = rowsUnder("forward")
        assertTrue("no natives parsed", natives.size > 400)
        assertTrue("no forwards parsed", forwards.size > 40)
        assertTrue(natives.any { it.first == "CreateVehicle" })
        assertTrue(natives.any { it.first == "ShowPlayerDialog" })
        assertTrue(forwards.any { it.first == "OnGameModeInit" })
    }

    @Test
    fun `no symbol keeps the generic placeholder description`() {
        val placeholders = (rowsUnder("native") + rowsUnder("forward")).filter { (_, en, _) ->
            en.contains("include set", ignoreCase = true) ||
                en.contains("Native provided", ignoreCase = true) ||
                en.isBlank()
        }
        assertTrue(
            "placeholder descriptions left: ${placeholders.map { it.first }}",
            placeholders.isEmpty()
        )
    }

    @Test
    fun `every symbol is described for both languages`() {
        val missing = (rowsUnder("native") + rowsUnder("forward"))
            .filter { (_, en, id) -> en.isBlank() || id.isBlank() }
            .map { it.first }
        assertTrue("undescribed: $missing", missing.isEmpty())
    }

    @Test
    fun `descriptions say what the symbol does`() {
        // A generic blurb is short and repetitive; a real one names a concrete
        // behaviour. Anything below this length is treated as a stub.
        val stubs = (rowsUnder("native") + rowsUnder("forward"))
            .filter { (_, en, _) -> en.trim().length < 18 }
            .map { it.first }
        assertTrue("stub descriptions: $stubs", stubs.isEmpty())
    }

    @Test
    fun `the requested server cfg directives are all present`() {
        val requested = listOf(
            "announce", "maxplayers", "port", "bind", "lanmode", "query", "logqueries",
            "rcon_port", "rcon_maxadmins", "rcon_bind", "output", "mtu", "timestamp",
            "logtimeformat", "password", "hostname", "mapname", "language", "weburl",
            "rcon_password", "gravity", "weather", "tirepopping", "gamemodetext",
            "filterscripts", "plugins", "nosign", "anticheat", "instagib", "myriad",
            "chatlogging", "messageholelimit", "playertimeout", "db_logging",
            "db_log_queries", "onfoot_rate", "incar_rate", "maxplayerperip",
            "stream_rate", "stream_distance", "sleep", "connseedtime", "conncookies",
            "cookielogging", "useartwork", "artpath", "artwork", "version"
        )
        val missing = requested.filterNot { ServerConfig.isDirective(it) }
        assertTrue("missing from ServerConfig: $missing", missing.isEmpty())
        assertTrue((0..15).all { ServerConfig.isDirective("gamemode$it") })
        assertFalse(ServerConfig.isDirective("gamemode16"))
    }
}
