package com.rvdjv.pawnmc.apps_data.data_config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            local_ru = "Версия компилятора"
            local_es = "Version del compilador"
            "".trimIndent()
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
            local_id = "Umum"
            local_en = "General"
            local_es = "General"
            local_ru = "Основные"

            [settings.language]
            local_id = "Bahasa"
            local_en = "Language"
            local_es = "Idioma"
            local_ru = "Язык"

            [settings.title]
            local_id = "Pengaturan"
            local_en = "Settings"
            local_es = "Configuracion"
            local_ru = "Настройки"
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
            localizer.get("settings.option.debug.level.3.desc", CompilerConfig.AppLanguage.ID)
        )
        assertEquals(
            "Most detailed debugging; optimization is disabled automatically.",
            localizer.get("settings.option.debug.level.3.desc", CompilerConfig.AppLanguage.EN)
        )
    }
}
