package com.rvdjv.pawnmc.apps_data.data_compiler

import com.rvdjv.pawnmc.apps_data.data_config.CompilerConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the shipped compiler-message table.
 *
 * The codes come from `errmsg[]`, `fatalmsg[]` and `warnmsg[]` in the Pawn compiler
 * (`source/compiler/sc5.c`): 001-093 are errors, 100-111 are fatal, 200-239 are
 * warnings. [Explainer] looks a code up with its leading zeros stripped first and
 * then padded to three digits, so both spellings have to work.
 */
class ExplanationTableTest {

    private val explainer get() = Explainer

    private val toml: String by lazy {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "native/data/_explainer.toml").isFile }
            ?: File(System.getProperty("user.dir").orEmpty())
        File(root, "native/data/_explainer.toml").readText()
    }

    private val parsed: Map<String, String> by lazy { Explanations.parse(toml) }

    /** Every code the compiler can emit, in order. */
    private val compilerCodes: List<Int> = buildList {
        for (i in 1..93) add(i)
        for (i in 100..111) add(i)
        for (i in 200..239) add(i)
    }

    private fun padded(code: Int) = code.toString().padStart(3, '0')

    @Test
    fun `every compiler code is explained`() {
        val missing = compilerCodes.filter { parsed["${padded(it)}.en"].isNullOrBlank() }
        assertTrue("codes without an explanation: $missing", missing.isEmpty())
    }

    @Test
    fun `every code is explained in Indonesian as well`() {
        val missing = compilerCodes.filter { parsed["${padded(it)}.id"].isNullOrBlank() }
        assertTrue("codes without an Indonesian text: $missing", missing.isEmpty())
    }

    @Test
    fun `every compiler code has each supported localization`() {
        val languages = listOf(
            CompilerConfig.AppLanguage.ES,
            CompilerConfig.AppLanguage.RU,
            CompilerConfig.AppLanguage.TL,
        )
        val missing = languages.flatMap { language ->
            compilerCodes.filter { parsed["${padded(it)}.${language.value}"].isNullOrBlank() }
                .map { "${padded(it)}.${language.value}" }
        }
        assertTrue("codes without a localized explanation: $missing", missing.isEmpty())
    }

    @Test
    fun `existing Spanish Russian and Tagalog explanations remain localized`() {
        val translations = mapOf(
            CompilerConfig.AppLanguage.ES to "Símbolo no definido",
            CompilerConfig.AppLanguage.RU to "не определён",
            CompilerConfig.AppLanguage.TL to "Hindi defined ang symbol",
        )
        translations.forEach { (language, expected) ->
            val explanation = Explanations.resolve(parsed, "017", language).orEmpty()
            assertTrue(
                "missing ${language.value} explanation",
                explanation.contains(expected),
            )
        }
    }

    @Test
    fun `symbol warning descriptions stay neutral until the source symbol is applied`() {
        val text = parsed["203.en"].orEmpty()
        assertTrue(text.contains("symbol is never used"))
        // The table may only carry the `%s` placeholder, never a hard-coded symbol:
        // the Explainer fills it from the quoted symbol of the compiler line, so a
        // baked-in name would leak one message's symbol into every other line.
        val rendered = Explainer.explanationText(
            "warning 203: symbol is never used: \"myVar\"", text
        )
        assertFalse(rendered.contains("%s"))
        assertTrue(rendered.contains("myVar"))
        // And with no quoted symbol on the line the placeholder is dropped, not shown raw.
        val bare = Explainer.explanationText("warning 203", text)
        assertFalse(bare.contains("%s"))
    }

    @Test
    fun `lookup works with and without leading zeros`() {
        // Explainer probes the stripped form first, then the padded one.
        for (code in listOf("013", "017", "100", "217")) {
            assertNotNull("stripped $code", parsed["${code.trimStart('0')}.en"])
            assertNotNull("padded $code", parsed["$code.en"])
        }
        assertTrue(Explanations.resolve(parsed, "13", CompilerConfig.AppLanguage.EN).orEmpty().isNotBlank())
        assertTrue(Explanations.resolve(parsed, "013", CompilerConfig.AppLanguage.ID).orEmpty().isNotBlank())
    }

    @Test
    fun `explanations state the cause instead of only repeating the code`() {
        // The old table had a handful of vague lines; every entry now has to carry
        // more than the bare "Error NNN: <message>" prefix.
        val thin = compilerCodes.filter { code ->
            val text = parsed["${padded(code)}.en"].orEmpty()
            text.length < 80
        }
        assertTrue("explanations too short: ${thin.map { padded(it) }}", thin.isEmpty())
    }

    @Test
    fun `the messages that were previously wrong are now correct`() {
        // 061 is "#elseif" after "#else", not a recursive include.
        assertTrue(parsed["061.en"].orEmpty().contains("#elseif"))
        // 100 is a read failure, 101 the matching write failure.
        assertTrue(parsed["100.en"].orEmpty().contains("read"))
        assertTrue(parsed["101.en"].orEmpty().contains("write"))
        // 217 is loose indentation, which PawnMC silences with -w217.
        assertTrue(parsed["217.en"].orEmpty().contains("indentation"))
        assertTrue(parsed["217.en"].orEmpty().contains("-w217"))
    }

    @Test
    fun `fatal codes say the build stops`() {
        val missing = (100..111).filter { code ->
            !parsed["${padded(code)}.en"].orEmpty().contains("stops", ignoreCase = true)
        }
        assertTrue("fatal codes without a stop notice: $missing", missing.isEmpty())
    }
}
