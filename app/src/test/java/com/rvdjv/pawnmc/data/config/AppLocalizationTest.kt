package com.rvdjv.pawnmc.data.config

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocalizationTest {
    @Test
    fun `language values should resolve to supported options`() {
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("id"))
        assertEquals(CompilerConfig.AppLanguage.EN, CompilerConfig.AppLanguage.fromValue("en"))
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("in"))
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("IN"))
        assertEquals(CompilerConfig.AppLanguage.ID, CompilerConfig.AppLanguage.fromValue("unknown"))
        assertEquals(CompilerConfig.AppLanguage.ES, CompilerConfig.AppLanguage.fromValue("es"))
        assertEquals(CompilerConfig.AppLanguage.ES, CompilerConfig.AppLanguage.fromValue("ES"))
        assertEquals(CompilerConfig.AppLanguage.ES, CompilerConfig.AppLanguage.fromValue("es-AR"))
        assertEquals(CompilerConfig.AppLanguage.ES, CompilerConfig.AppLanguage.fromValue("es_AR"))
        assertEquals(CompilerConfig.AppLanguage.RU, CompilerConfig.AppLanguage.fromValue("ru"))
        assertEquals(CompilerConfig.AppLanguage.RU, CompilerConfig.AppLanguage.fromValue("RU"))
        assertEquals(CompilerConfig.AppLanguage.RU, CompilerConfig.AppLanguage.fromValue("ru-RU"))
        assertEquals(CompilerConfig.AppLanguage.RU, CompilerConfig.AppLanguage.fromValue("ru_RU"))
    }

    @Test
    fun `every language maps to a BCP-47 tag`() {
        assertEquals("id", CompilerConfig.AppLanguage.ID.localeTag())
        assertEquals("en", CompilerConfig.AppLanguage.EN.localeTag())
        assertEquals("es-AR", CompilerConfig.AppLanguage.ES.localeTag())
        assertEquals("ru", CompilerConfig.AppLanguage.RU.localeTag())
    }

    @Test
    fun `raw localization entries should flatten TOML tables into dotted language keys`() {
        val parsed = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            id = "Umum"
            en = "General"
            es = "General"

            [settings.theme]
            id = "Tema"
            en = "Theme"
            es = "Tema"
            """.trimIndent()
        )

        assertEquals("Umum", parsed["settings.general.id"])
        assertEquals("General", parsed["settings.general.en"])
        assertEquals("General", parsed["settings.general.es"])
        assertEquals("Tema", parsed["settings.theme.id"])
        assertEquals("Theme", parsed["settings.theme.en"])
        assertEquals("Tema", parsed["settings.theme.es"])
    }

    @Test
    fun `localization translates English words to Indonesian when ID is selected`() {
        val data = """
            [settings.general]
            id = "Umum"
            en = "General"

            [settings.language]
            id = "Bahasa"
            en = "Language"

            [settings.title]
            id = "Pengaturan"
            en = "Settings"
        """.trimIndent()

        val parsed = AppLocalization.parseLocalizationData(data)
        val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
        constructor.isAccessible = true
        val localizer = constructor.newInstance(parsed)

        // Key-based translation
        assertEquals("Umum", localizer.get("settings.general", CompilerConfig.AppLanguage.ID))
        assertEquals("General", localizer.get("settings.general", CompilerConfig.AppLanguage.EN))

        // Phrase / word-based translation
        assertEquals("Umum", localizer.get("General", CompilerConfig.AppLanguage.ID))
        assertEquals("Bahasa", localizer.get("Language", CompilerConfig.AppLanguage.ID))
        assertEquals("Pengaturan", localizer.get("Settings", CompilerConfig.AppLanguage.ID))
        assertEquals("Settings", localizer.get("Pengaturan", CompilerConfig.AppLanguage.EN))
    }

    @Test
    fun `localization translates English words to Spanish when ES is selected`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            id = "Umum"
            en = "General"
            es = "General"

            [settings.language]
            id = "Bahasa"
            en = "Language"
            es = "Idioma"

            [settings.title]
            id = "Pengaturan"
            en = "Settings"
            es = "Configuracion"
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        // Key-based translation
        assertEquals("General", localizer.get("settings.general", CompilerConfig.AppLanguage.ES))

        // Phrase / word-based translation
        assertEquals("Idioma", localizer.get("Language", CompilerConfig.AppLanguage.ES))
        assertEquals("Configuracion", localizer.get("Settings", CompilerConfig.AppLanguage.ES))

        // English still recovers from both Indonesian and Spanish source text
        assertEquals("Settings", localizer.get("Pengaturan", CompilerConfig.AppLanguage.EN))
        assertEquals("Settings", localizer.get("Configuracion", CompilerConfig.AppLanguage.EN))
    }

    @Test
    fun `localization translates English words to Russian when RU is selected`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            id = "Umum"
            en = "General"
            es = "General"
            ru = "Основные"

            [settings.language]
            id = "Bahasa"
            en = "Language"
            es = "Idioma"
            ru = "Язык"

            [settings.title]
            id = "Pengaturan"
            en = "Settings"
            es = "Configuracion"
            ru = "Настройки"
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        // Key-based translation
        assertEquals("Основные", localizer.get("settings.general", CompilerConfig.AppLanguage.RU))

        // Phrase / word-based translation
        assertEquals("Язык", localizer.get("Language", CompilerConfig.AppLanguage.RU))
        assertEquals("Настройки", localizer.get("Settings", CompilerConfig.AppLanguage.RU))

        // English recovers from Russian source text as well
        assertEquals("Settings", localizer.get("Настройки", CompilerConfig.AppLanguage.EN))
    }

    @Test
    fun `settings option descriptions resolve for both selected languages`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.option.debug.level.3.desc]
            id = "Debug paling terperinci; optimisasi otomatis dinonaktifkan."
            en = "Most detailed debugging; optimization is disabled automatically."
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        assertEquals(
            "Debug paling terperinci; optimisasi otomatis dinonaktifkan.",
            localizer.get("settings.option.debug.level.3.desc", CompilerConfig.AppLanguage.ID)
        )
        assertEquals(
            "Most detailed debugging; optimization is disabled automatically.",
            localizer.get("settings.option.debug.level.3.desc", CompilerConfig.AppLanguage.EN)
        )
    }
}
