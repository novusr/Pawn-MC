package com.rvdjv.pawnmc.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocalizationTest {
    @Test
    fun `language values should resolve to supported options`() {
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("id"))
        assertEquals(CompilerConfig.AppLanguage.EN, CompilerConfig.AppLanguage.fromValue("en"))
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("unknown"))
    }

    @Test
    fun `raw localization entries should parse both key value and hex tagged format`() {
        val parsed = AppLocalization.parseLocalizationData(
            """
            0x01:settings.general.id:0x02:Umum
            0x01:settings.general.en:0x02:General
            settings.theme.id=Tema
            settings.theme.en=Theme
            """.trimIndent()
        )

        assertEquals("Umum", parsed["settings.general.id"])
        assertEquals("General", parsed["settings.general.en"])
        assertEquals("Tema", parsed["settings.theme.id"])
        assertEquals("Theme", parsed["settings.theme.en"])
    }
}
