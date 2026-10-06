package com.rvdjv.pawnmc.data.config
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
/**
 * Round-trip tests for the shared-storage JSON mirror of compiler preferences.
 *
 * Keys are the real `CompilerConfig.KEY_*` literals: the mirror reads and writes
 * exactly those, so a test that seeded a different vocabulary would only ever
 * exercise the defaults.
 */
class CompilerConfigFileTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @Test fun mirrorThenRestore_roundTripsEverySetting() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val written = InMemoryPreferences().apply(::seed)
        CompilerConfigFile.mirror(context, written)
        val restored = InMemoryPreferences()
        assertTrue(CompilerConfigFile.restore(context, restored))
        MIRRORED_KEYS.forEach { key -> assertEquals("key `$key` did not survive the mirror", written.all[key], restored.all[key]) }
    }
    @Test fun mirror_writesAJsonObjectWithSettingsUnderStableNames() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val prefs = InMemoryPreferences().apply(::seed)
        CompilerConfigFile.mirror(context, prefs)
        val json = JSONObject(CompilerConfigFile.file(context).readText())
        assertEquals(CompilerConfigFile.SCHEMA_VERSION, json.getInt(CompilerConfigFile.SCHEMA_KEY))
        assertEquals(CompilerConfig.DebugLevel.D2.value, json.getInt(CompilerConfig.KEY_DEBUG))
        assertEquals(CompilerConfig.STR_V31011, json.getString(CompilerConfig.KEY_COMPILER_VERSION))
        assertEquals(CompilerConfig.AppLanguage.ID.value, json.getString(CompilerConfig.KEY_APP_LANGUAGE))
        assertEquals(2, json.getJSONArray(CompilerConfig.KEY_INCLUDE_PATHS).length())
        assertTrue(json.isNull(CompilerConfig.KEY_EDITOR_BG_COLOR))
    }
    @Test fun mirror_roundTripsWorkspaceLayoutAndLastOpenedLocations() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val written = InMemoryPreferences().apply {
            seed(this)
            all[CompilerConfig.KEY_WORKSPACE_FOLDERS] = "/sdcard/projA/;/sdcard/projB/"
            all[CompilerConfig.KEY_LAST_FILE] = "/sdcard/projA/main.pwn"
            all[CompilerConfig.KEY_LAST_DIR] = "/sdcard/projA/"
            all[CompilerConfig.KEY_APP_LANGUAGE_SET] = true
        }
        CompilerConfigFile.mirror(context, written)
        val restored = InMemoryPreferences()
        assertTrue(CompilerConfigFile.restore(context, restored))
        assertEquals("/sdcard/projA/;/sdcard/projB/", restored.all[CompilerConfig.KEY_WORKSPACE_FOLDERS])
        assertEquals("/sdcard/projA/main.pwn", restored.all[CompilerConfig.KEY_LAST_FILE])
        assertEquals("/sdcard/projA/", restored.all[CompilerConfig.KEY_LAST_DIR])
        assertEquals(true, restored.all[CompilerConfig.KEY_APP_LANGUAGE_SET])
    }
    @Test fun mirror_survivesAbsentEnumSettingsWithoutThrowing() {
        // Regression: getString returning null used to hit the throwing
        // JSONObject.put(String, Object) overload, which aborted the whole write —
        // the file was never created and every reinstall restarted from defaults.
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val prefs = InMemoryPreferences()
        CompilerConfigFile.mirror(context, prefs)
        val json = JSONObject(CompilerConfigFile.file(context).readText())
        assertTrue(json.isNull(CompilerConfig.KEY_COMPILER_VERSION))
        assertTrue(json.isNull(CompilerConfig.KEY_APP_THEME))
        assertTrue(json.isNull(CompilerConfig.KEY_APP_LANGUAGE))
        assertEquals(CompilerConfigFile.SCHEMA_VERSION, json.getInt(CompilerConfigFile.SCHEMA_KEY))
    }
    @Test fun restore_carriesTheLanguageSetMarker() {
        // The marker that suppresses first-launch device-locale detection has to be
        // carried by the mirror, or a reinstall would overwrite the restored
        // language with the device locale in the same session.
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val json = "{\"schema\":2,\"app_language\":\"ru\",\"app_language_set\":true}"
        CompilerConfigFile.file(context).writeText(json)
        val restored = InMemoryPreferences()
        assertTrue(CompilerConfigFile.restore(context, restored))
        assertEquals("ru", restored.all[CompilerConfig.KEY_APP_LANGUAGE])
        assertEquals(true, restored.all[CompilerConfig.KEY_APP_LANGUAGE_SET])
    }
    @Test fun restore_leavesUntouchedKeysAloneWhenFileIsPartial() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val json = "{\"schema\":1,\"app_language\":\"ru\"}"
        CompilerConfigFile.file(context).writeText(json)
        val restored = InMemoryPreferences().apply { all[CompilerConfig.KEY_SEMICOLONS] = true }
        assertTrue(CompilerConfigFile.restore(context, restored))
        assertEquals("ru", restored.all[CompilerConfig.KEY_APP_LANGUAGE])
        assertEquals(true, restored.all[CompilerConfig.KEY_SEMICOLONS])
    }
    @Test fun restore_ignoresCorruptFileAndKeepsExistingValues() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        CompilerConfigFile.file(context).writeText("{ this is not json")
        val restored = InMemoryPreferences().apply { all[CompilerConfig.KEY_APP_LANGUAGE] = "en" }
        assertFalse(CompilerConfigFile.restore(context, restored))
        assertEquals("en", restored.all[CompilerConfig.KEY_APP_LANGUAGE])
    }
    @Test fun restore_distinguishesStoredFalseFromMissingKey() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val json = "{\"schema\":1,\"ignore_case\":false}"
        CompilerConfigFile.file(context).writeText(json)
        val restored = InMemoryPreferences().apply { all[CompilerConfig.KEY_IGNORE_CASE] = true }
        assertTrue(CompilerConfigFile.restore(context, restored))
        assertEquals(false, restored.all[CompilerConfig.KEY_IGNORE_CASE])
    }
    @Test fun file_usesUsersConfigFolderWhenSharedStorageIsAvailable() {
        val external = temporaryFolder.newFolder("external")
        val context = TestContext(temporaryFolder.newFolder("files"), external)
        val expected = File(File(File(external, AppStorage.PUBLIC_DIR_NAME), "config"), CompilerConfigFile.FILE_NAME)
        assertEquals(expected, CompilerConfigFile.file(context))
    }
    @Test fun file_fallsBackToPrivateRootWhenSharedStorageIsMissing() {
        val filesDir = temporaryFolder.newFolder("files-private")
        val context = TestContext(filesDir)
        val expected = File(File(File(filesDir, ".pawnmc"), "config"), CompilerConfigFile.FILE_NAME)
        assertEquals(expected, CompilerConfigFile.file(context))
    }
    @Test fun exists_isFalseBeforeTheFirstMirror() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        assertFalse(CompilerConfigFile.exists(context))
        CompilerConfigFile.mirror(context, InMemoryPreferences())
        assertTrue(CompilerConfigFile.exists(context))
    }
    @Test fun restoreIncludePaths_survivesAnEmptyArray() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val json = "{\"schema\":1,\"include_paths\":[]}"
        CompilerConfigFile.file(context).writeText(json)
        val prefs = InMemoryPreferences().apply { all[CompilerConfig.KEY_INCLUDE_PATHS] = "/keep/" }
        assertEquals(0, CompilerConfigFile.restoreIncludePaths(context, prefs))
        assertEquals("/keep/", prefs.all[CompilerConfig.KEY_INCLUDE_PATHS])
    }
    @Test fun restoreIncludePaths_rewritesStoredList() {
        val context = contextWithFilesDir(temporaryFolder.newFolder("files"))
        val json = "{\"schema\":1,\"include_paths\":[\"/a/\",\"/b/\"]}"
        CompilerConfigFile.file(context).writeText(json)
        val prefs = InMemoryPreferences().apply { all[CompilerConfig.KEY_INCLUDE_PATHS] = "/stale/" }
        assertEquals(2, CompilerConfigFile.restoreIncludePaths(context, prefs))
        assertEquals("/a/;/b/", prefs.all[CompilerConfig.KEY_INCLUDE_PATHS])
    }
    private fun contextWithFilesDir(directory: File): Context = TestContext(directory)
    private fun seed(prefs: InMemoryPreferences) {
        prefs.all[CompilerConfig.KEY_FORCED_MODE] = false
        prefs.all[CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO] = false
        prefs.all[CompilerConfig.KEY_DEBUG] = CompilerConfig.DebugLevel.D2.value
        prefs.all[CompilerConfig.KEY_OPTIMIZATION] = CompilerConfig.OptimizationLevel.O0.value
        prefs.all[CompilerConfig.KEY_IGNORE_CASE] = true
        prefs.all[CompilerConfig.KEY_EXPLAIN_OUTPUT] = true
        prefs.all[CompilerConfig.KEY_SEMICOLONS] = false
        prefs.all[CompilerConfig.KEY_PARENTHESES] = false
        prefs.all[CompilerConfig.KEY_CUSTOM_FLAGS] = "-v=0 -e=2"
        prefs.all[CompilerConfig.KEY_INCLUDE_PATHS] = "/includes/a/;/includes/b/"
        prefs.all[CompilerConfig.KEY_COMPILER_VERSION] = CompilerConfig.STR_V31011
        prefs.all[CompilerConfig.KEY_APP_THEME] = "dark"
        prefs.all[CompilerConfig.KEY_APP_LANGUAGE] = CompilerConfig.AppLanguage.ID.value
        prefs.all[CompilerConfig.KEY_APP_LANGUAGE_SET] = true
        prefs.all[CompilerConfig.KEY_EDITOR_BG_COLOR] = null
    }
    private companion object {
        val MIRRORED_KEYS = listOf(
            CompilerConfig.KEY_FORCED_MODE, CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO,
            CompilerConfig.KEY_DEBUG, CompilerConfig.KEY_OPTIMIZATION,
            CompilerConfig.KEY_IGNORE_CASE, CompilerConfig.KEY_EXPLAIN_OUTPUT,
            CompilerConfig.KEY_SEMICOLONS, CompilerConfig.KEY_PARENTHESES,
            CompilerConfig.KEY_CUSTOM_FLAGS, CompilerConfig.KEY_INCLUDE_PATHS,
            CompilerConfig.KEY_COMPILER_VERSION, CompilerConfig.KEY_APP_THEME,
            CompilerConfig.KEY_APP_LANGUAGE, CompilerConfig.KEY_APP_LANGUAGE_SET,
            CompilerConfig.KEY_WORKSPACE_FOLDERS, CompilerConfig.KEY_LAST_FILE,
            CompilerConfig.KEY_LAST_DIR, CompilerConfig.KEY_EDITOR_BG_COLOR,
        )
    }
}
private class InMemoryPreferences : SharedPreferences {
    val all = linkedMapOf<String, Any?>()
    override fun getAll(): MutableMap<String, *> = all
    override fun getString(key: String, defValue: String?): String? = all[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = @Suppress("UNCHECKED_CAST") (all[key] as? MutableSet<String>) ?: defValues
    override fun getInt(key: String, defValue: Int): Int = all[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = all[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = all[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = all[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = all.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
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
        override fun commit(): Boolean { apply(); return true }
        override fun apply() { removals.forEach(all::remove); all.putAll(pending) }
    }
}
private class TestContext(private val directory: File, private val externalDirectory: File? = null) : ContextWrapper(null) {
    override fun getFilesDir(): File = directory
    override fun getExternalFilesDir(type: String?): File? = externalDirectory
}
