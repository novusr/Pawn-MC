package com.rvdjv.pawnmc.apps_data.data_config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppLocalizationTest {
    @Test
    fun `language values should resolve to supported options`() {
        assertEquals(ContentConfig.AppLanguage.ID, ContentConfig.AppLanguage.fromValue("id"))
        assertEquals(ContentConfig.AppLanguage.EN, ContentConfig.AppLanguage.fromValue("en"))
        assertEquals(ContentConfig.AppLanguage.ID, ContentConfig.AppLanguage.fromValue("in"))
        assertEquals(ContentConfig.AppLanguage.ID, ContentConfig.AppLanguage.fromValue("IN"))
        assertEquals(ContentConfig.AppLanguage.ID, ContentConfig.AppLanguage.fromValue("unknown"))
        assertEquals(ContentConfig.AppLanguage.ES, ContentConfig.AppLanguage.fromValue("es"))
        assertEquals(ContentConfig.AppLanguage.ES, ContentConfig.AppLanguage.fromValue("ES"))
        assertEquals(ContentConfig.AppLanguage.ES, ContentConfig.AppLanguage.fromValue("es-AR"))
        assertEquals(ContentConfig.AppLanguage.ES, ContentConfig.AppLanguage.fromValue("es_AR"))
        assertEquals(ContentConfig.AppLanguage.RU, ContentConfig.AppLanguage.fromValue("ru"))
        assertEquals(ContentConfig.AppLanguage.RU, ContentConfig.AppLanguage.fromValue("RU"))
        assertEquals(ContentConfig.AppLanguage.RU, ContentConfig.AppLanguage.fromValue("ru-RU"))
        assertEquals(ContentConfig.AppLanguage.RU, ContentConfig.AppLanguage.fromValue("ru_RU"))
    }

    @Test
    fun `every language maps to a BCP-47 tag`() {
        assertEquals("id", ContentConfig.AppLanguage.ID.localeTag())
        assertEquals("en", ContentConfig.AppLanguage.EN.localeTag())
        assertEquals("es-AR", ContentConfig.AppLanguage.ES.localeTag())
        assertEquals("ru", ContentConfig.AppLanguage.RU.localeTag())
    }

    @Test
    fun `raw localization entries should flatten TOML tables into dotted language keys`() {
        val parsed = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            local_id = "Umum"
            local_en = "General"
            local_es = "General"

            [settings.theme]
            local_id = "Tema"
            local_en = "Theme"
            local_es = "Tema"
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
    fun `localization reads explicit local field names only`() {
        val parsed = AppLocalization.parseLocalizationData(
            """
            [settings.compiler.version]
            local_id = "Versi Compiler"
            local_en = "Compiler Version"
            local_tl = "Bersyon ng Compiler"
            local_ru = "Ð’ÐµÑ€ÑÐ¸Ñ ÐºÐ¾Ð¼Ð¿Ð¸Ð»ÑÑ‚Ð¾Ñ€Ð°"
            local_es = "Version del compilador"
            """.trimIndent()
        )
        assertEquals("Versi Compiler", parsed["settings.compiler.version.id"])
        assertEquals("Compiler Version", parsed["settings.compiler.version.en"])
        assertFalse(parsed.containsKey("settings.compiler.version.local_en"))
    }

    @Test
    fun `localization translates English words to Indonesian when ID is selected`() {
        val data = """
            [settings.general]
            local_id = "Umum"
            local_en = "General"

            [settings.language]
            local_id = "Bahasa"
            local_en = "Language"

            [settings.title]
            local_id = "Pengaturan"
            local_en = "Settings"
        """.trimIndent()

        val parsed = AppLocalization.parseLocalizationData(data)
        val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
        constructor.isAccessible = true
        val localizer = constructor.newInstance(parsed)

        // Key-based translation
        assertEquals("Umum", localizer.get("settings.general", ContentConfig.AppLanguage.ID))
        assertEquals("General", localizer.get("settings.general", ContentConfig.AppLanguage.EN))

        // Phrase / word-based translation
        assertEquals("Umum", localizer.get("General", ContentConfig.AppLanguage.ID))
        assertEquals("Bahasa", localizer.get("Language", ContentConfig.AppLanguage.ID))
        assertEquals("Pengaturan", localizer.get("Settings", ContentConfig.AppLanguage.ID))
        assertEquals("Settings", localizer.get("Pengaturan", ContentConfig.AppLanguage.EN))
    }

    @Test
    fun `localization translates English words to Spanish when ES is selected`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            local_id = "Umum"
            local_en = "General"
            local_es = "General"

            [settings.language]
            local_id = "Bahasa"
            local_en = "Language"
            local_es = "Idioma"

            [settings.title]
            local_id = "Pengaturan"
            local_en = "Settings"
            local_es = "Configuracion"
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        // Key-based translation
        assertEquals("General", localizer.get("settings.general", ContentConfig.AppLanguage.ES))

        // Phrase / word-based translation
        assertEquals("Idioma", localizer.get("Language", ContentConfig.AppLanguage.ES))
        assertEquals("Configuracion", localizer.get("Settings", ContentConfig.AppLanguage.ES))

        // English still recovers from both Indonesian and Spanish source text
        assertEquals("Settings", localizer.get("Pengaturan", ContentConfig.AppLanguage.EN))
        assertEquals("Settings", localizer.get("Configuracion", ContentConfig.AppLanguage.EN))
    }

    @Test
    fun `localization translates English words to Russian when RU is selected`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.general]
            local_id = "Umum"
            local_en = "General"
            local_es = "General"
            local_ru = "Osnovnye"

            [settings.language]
            local_id = "Bahasa"
            local_en = "Language"
            local_es = "Idioma"
            local_ru = "Yazyk"

            [settings.title]
            local_id = "Pengaturan"
            local_en = "Settings"
            local_es = "Configuracion"
            local_ru = "konfiguratsiya"
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        // Key-based translation preserves the supplied localized value.
        assertEquals("Osnovnye", localizer.get("settings.general", ContentConfig.AppLanguage.RU))

        // Phrase / word-based translation
        assertEquals("Yazyk", localizer.get("Language", ContentConfig.AppLanguage.RU))
        assertEquals("konfiguratsiya", localizer.get("Settings", ContentConfig.AppLanguage.RU))

        // English recovers from the configured Russian source text as well.
        assertEquals("Settings", localizer.get("konfiguratsiya", ContentConfig.AppLanguage.EN))
    }

    @Test
    fun `settings option descriptions resolve for both selected languages`() {
        val localizer = AppLocalization.parseLocalizationData(
            """
            [settings.option.debug.level.3.desc]
            local_id = "Debug paling terperinci; optimisasi otomatis dinonaktifkan."
            local_en = "Most detailed debugging; optimization is disabled automatically."
            """.trimIndent()
        ).let { parsed ->
            val constructor = AppLocalization::class.java.getDeclaredConstructor(Map::class.java)
            constructor.isAccessible = true
            constructor.newInstance(parsed)
        }

        assertEquals(
            "Debug paling terperinci; optimisasi otomatis dinonaktifkan.",
            localizer.get("settings.option.debug.level.3.desc", ContentConfig.AppLanguage.ID)
        )
        assertEquals(
            "Most detailed debugging; optimization is disabled automatically.",
            localizer.get("settings.option.debug.level.3.desc", ContentConfig.AppLanguage.EN)
        )
    }
}
