package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import com.rvdjv.pawnmc.data.update.UpdateManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Round-trip test for the `.json` settings mirror.
 *
 * The file is written from, and read back into, a plain [SharedPreferences] stand-in so
 * the test covers the real serialisation without needing an Android device. The two
 * things that matter and are asserted here are that *every* mirrored key survives the
 * round trip — a setting silently dropped by the writer would only ever show up as a
 * user reporting that one preference reset after an update — and that an absent key is
 * distinguishable from a stored `false`.
 */
class CompilerConfigFileTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun mirrorThenRestore_roundTripsEverySetting() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val written = InMemoryPreferences()
        seed(written)

        CompilerConfigFile.mirror(context, written)

        val restored = InMemoryPreferences()
        assertTrue(CompilerConfigFile.restore(context, restored))

        for (key in MIRRORED_KEYS) {
            assertEquals("key `$key` did not survive the mirror", written.all[key], restored.all[key])
        }
    }

    @Test
    fun mirror_writesAJsonObjectWithTheSettingsUnderStableNames() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val prefs = InMemoryPreferences()
        seed(prefs)

        CompilerConfigFile.mirror(context, prefs)

        val json = JSONObject(CompilerConfigFile.file(context).readText())
        assertEquals(1, json.getInt(CompilerConfigFile.SCHEMA_KEY))
        assertEquals(CompilerConfig.DebugLevel.D2.value, json.getInt(CompilerConfig.KEY_DEBUG))
        assertEquals(CompilerConfig.STR_V31011, json.getString(CompilerConfig.KEY_COMPILER_VERSION))
        assertEquals(CompilerConfig.AppLanguage.ID.value, json.getString(CompilerConfig.KEY_APP_LANGUAGE))
        assertEquals(2, json.getJSONArray(CompilerConfig.KEY_INCLUDE_PATHS).length())
        assertTrue(
            "a cleared colour must be stored as JSON null, not as the string \"null\"",
            json.isNull(CompilerConfig.KEY_EDITOR_BG_COLOR),
        )
    }

    @Test
    fun restore_leavesUntouchedKeysAloneWhenTheFileIsPartial() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val file = CompilerConfigFile.file(context)
        file.writeText("""{"schema":1,"app_language":"ru"}""")

        val restored = InMemoryPreferences().apply { all["${PREFIX}semicolons"] = true }

        assertTrue(CompilerConfigFile.restore(context, restored))
        assertEquals("ru", restored.all["${PREFIX}app_language"])
        assertEquals(true, restored.all["${PREFIX}semicolons"])
    }

    @Test
    fun restore_ignoresACorruptFileAndKeepsExistingValues() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        CompilerConfigFile.file(context).writeText("{ this is not json")

        val restored = InMemoryPreferences().apply { all["${PREFIX}app_language"] = "en" }

        assertFalse(CompilerConfigFile.restore(context, restored))
        assertEquals("en", restored.all["${PREFIX}app_language"])
    }

    @Test
    fun restore_distinguishesStoredFalseFromAMissingKey() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        CompilerConfigFile.file(context).writeText("""{"schema":1,"ignore_case":false}""")

        val restored = InMemoryPreferences().apply { all["${PREFIX}ignore_case"] = true }
        assertTrue(CompilerConfigFile.restore(context, restored))

        assertEquals(false, restored.all["${PREFIX}ignore_case"])
    }

    @Test
    fun file_livesInTheHiddenDirectoryTheUpdateManagerOwns() {
        val filesDir = temporaryFolder.newFolder("files")
        val context = contextWithFilesDir(filesDir)

        assertEquals(UpdateManager.hiddenBaseDir(context), CompilerConfigFile.file(context).parentFile)
    }

    @Test
    fun restoreIncludePaths_survivesAnEmptyArray() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        CompilerConfigFile.file(context).writeText("""{"schema":1,"include_paths":[]}""")

        val prefs = InMemoryPreferences().apply { all["${PREFIX}include_paths"] = "/keep/" }

        assertEquals(0, CompilerConfigFile.restoreIncludePaths(context, prefs))
        assertEquals("/keep/", prefs.all["${PREFIX}include_paths"])
    }

    @Test
    fun restoreIncludePaths_rewritesTheStoredList() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        CompilerConfigFile.file(context)
            .writeText("""{"schema":1,"include_paths":["/a/","/b/"]}""")

        val prefs = InMemoryPreferences().apply { all["${PREFIX}include_paths"] = "/stale/" }

        assertEquals(2, CompilerConfigFile.restoreIncludePaths(context, prefs))
        assertEquals("/a/;/b/", prefs.all["${PREFIX}include_paths"])
    }

    private fun seed(prefs: InMemoryPreferences) {
        prefs.all["${PREFIX}debug_level"] = CompilerConfig.DebugLevel.D2.value
        prefs.all["${PREFIX}optimization_level"] = CompilerConfig.OptimizationLevel.O0.value
        prefs.all["${PREFIX}ignore_case"] = true
        prefs.all["${PREFIX}explain_output"] = true
        prefs.all["${PREFIX}semicolons"] = false
        prefs.all["${PREFIX}parentheses"] = false
        prefs.all["${PREFIX}custom_flags"] = "-v=0 -e=2"
        prefs.all["${PREFIX}include_paths"] = "/first/;/second/"
        prefs.all["${PREFIX}compiler_version"] = CompilerConfig.STR_V31011
        prefs.all["${PREFIX}app_theme"] = CompilerConfig.AppTheme.DARK.value
        prefs.all["${PREFIX}app_language"] = CompilerConfig.AppLanguage.ID.value

        // Left unset on purpose: a cleared colour has to round-trip as "absent", which
        // is the case the JSON `null` in the writer exists for.
        prefs.all.remove("${PREFIX}editor_background_color")
    }

    private fun contextWithFilesDir(filesDir: File): Context =
        TestContext(filesDir)

    private companion object {
        /** Mirrors `CompilerConfig.PREFS_NAME`; the config file writes these plain keys. */
        const val PREFIX = "compiler_config."

        val MIRRORED_KEYS = listOf(
            "${PREFIX}debug_level",
            "${PREFIX}optimization_level",
            "${PREFIX}ignore_case",
            "${PREFIX}explain_output",
            "${PREFIX}semicolons",
            "${PREFIX}parentheses",
            "${PREFIX}custom_flags",
            "${PREFIX}include_paths",
            "${PREFIX}compiler_version",
            "${PREFIX}app_theme",
            "${PREFIX}app_language",
            "${PREFIX}editor_background_color",
        )
    }
}

/**
 * Minimal [SharedPreferences] backed by a mutable map.
 *
 * `androidx.test` is not on the unit-test classpath and the mirror code only ever calls
 * `get*`/`edit`, so a hand-rolled implementation is both smaller and clearer than
 * Robolectric for what this test needs.
 */
private class InMemoryPreferences : SharedPreferences {
    val all = linkedMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = all

    override fun getString(key: String, defValue: String?): String? = all[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST") (all[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String, defValue: Int): Int = all[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = all[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = all[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = all[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = all.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = linkedMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removals += key }
        override fun clear() = apply { removals += all.keys }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            removals.forEach { all.remove(it) }
            all.putAll(pending)
        }
    }
}

/**
 * A [Context] whose only meaningful answer is the files directory.
 *
 * The mirror code reaches the app directory through [UpdateManager.hiddenBaseDir], which
 * touches nothing else on the context, so overriding `getFilesDir` is enough and an
 * entire mock framework is not needed. `AbstractMethodError` from the other overrides
 * never happens because nothing else on the context is called.
 */
private class TestContext(private val directory: File) : Context() {
    override fun getFilesDir(): File = directory
}
