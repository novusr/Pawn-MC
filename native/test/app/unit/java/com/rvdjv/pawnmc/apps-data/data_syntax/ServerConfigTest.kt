package com.rvdjv.pawnmc.apps-data.data_syntax

import com.rvdjv.pawnmc.apps-data.data_config.CompilerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The `.cfg` vocabulary used by the Xed editor highlighter and completion popup. */
class ServerConfigTest {

    private val requiredDirectives = listOf(
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

    @Test
    fun `every SA-MP directive is known and documented`() {
        requiredDirectives.forEach { directive ->
            assertTrue("missing directive: $directive", ServerConfig.isDirective(directive))
            assertTrue("no value hint: $directive", ServerConfig.valueHintFor(directive) != null)
            assertTrue(
                "no description: $directive",
                ServerConfig.descriptionFor(directive, CompilerConfig.AppLanguage.EN).isNotBlank()
            )
            assertTrue(
                "no Indonesian description: $directive",
                ServerConfig.descriptionFor(directive, CompilerConfig.AppLanguage.ID).isNotBlank()
            )
        }
    }

    @Test
    fun `descriptions are specific rather than a generic placeholder`() {
        // A real description names a concrete effect; the old placeholder text did not.
        requiredDirectives.forEach { directive ->
            val text = ServerConfig.descriptionFor(directive, CompilerConfig.AppLanguage.EN)
            assertFalse(
                "$directive still has placeholder text",
                text.contains("include set", ignoreCase = true) || text.length < 20
            )
        }
    }

    @Test
    fun `gamemode slots go from zero to fifteen`() {
        for (index in 0..15) {
            assertTrue("gamemode$index", ServerConfig.isDirective("gamemode$index"))
        }
        assertFalse(ServerConfig.isDirective("gamemode16"))
        assertFalse(ServerConfig.isDirective("gamemode"))
        assertTrue(ServerConfig.isListDirective("filterscripts"))
        assertFalse(ServerConfig.isListDirective("hostname"))
    }

    @Test
    fun `completion is prefix based`() {
        assertEquals(listOf("port"), ServerConfig.completions("po"))
        assertTrue(ServerConfig.completions("gamemode").containsAll(listOf("gamemode0", "gamemode15")))
        assertTrue(ServerConfig.completions("zzz").isEmpty())
    }

    @Test
    fun `directiveAt finds the key of a value`() {
        assertEquals("hostname", ServerConfig.directiveAt("hostname My Server", 12))
        assertEquals("maxplayers", ServerConfig.directiveAt("maxplayers = 32", 12))
        assertEquals("rcon_password", ServerConfig.directiveAt("   rcon_password secret", 18))
        assertNull(ServerConfig.directiveAt("hostname", 4))
        assertNull(ServerConfig.directiveAt("not_a_directive value", 16))
    }

    @Test
    fun `indonesian descriptions differ from english`() {
        val english = ServerConfig.descriptionFor("maxplayers", CompilerConfig.AppLanguage.EN)
        val indonesian = ServerConfig.descriptionFor("maxplayers", CompilerConfig.AppLanguage.ID)
        assertTrue(english.isNotBlank())
        assertTrue(indonesian.isNotBlank())
        assertTrue(english != indonesian)
    }
}
