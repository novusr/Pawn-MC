package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The `.json` mirror of every setting PawnMC keeps.
 */
internal object CompilerConfigFile {

    /** File name inside [AppStorage.configRoot] (or the private fallback). */
    const val FILE_NAME: String = "compiler_config.json"

    /** `schema` key, a property of the file format rather than of the prefs. */
    const val SCHEMA_KEY: String = "schema"

    internal const val SCHEMA_VERSION = 2
    /** True when the JSON mirror already exists in any readable location. */
    fun exists(context: Context): Boolean = candidateFiles(context).any { it.isFile }

    /** Mirror locations in order of preference: Documents, app files, private root. */
    fun candidateFiles(context: Context): List<File> {
        val locations = mutableListOf<File>()
        AppStorage.documentsRoot(context)?.let { locations += File(File(it, "config"), FILE_NAME) }
        runCatching { context.getExternalFilesDir(null) }.getOrNull()
            ?.let { locations += File(File(File(it, AppStorage.PUBLIC_DIR_NAME), "config"), FILE_NAME) }
        locations += File(File(AppStorage.privateRoot(context), "config"), FILE_NAME)
        return locations
    }

    /**
     * The primary mirror location — the one this build writes to.
     *
     * Kept for callers that must know the concrete path (tests, diagnostics);
     * reading code goes through [candidateFiles] so a pre-migration file is found.
     */
    fun file(context: Context): File = candidateFiles(context).first()

    /**
     * The primary mirror path with its parent directories created.
     *
     * `Documents/PawnMC/config` exists only conceptually until the first write, so
     * every writer (and every test that seeds a file) goes through this instead of
     * calling `file(...).writeText(...)` into a directory nobody has made yet.
     */
    fun materializeFile(context: Context): File {
        val target = file(context)
        target.parentFile?.let { parent -> if (!parent.isDirectory) runCatching { parent.mkdirs() } }
        return target
    }

    /** Loads the JSON file and writes its values into [prefs]; false when absent. */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        val files = candidateFiles(context)
        val source = files.firstOrNull { it.isFile } ?: return false
        return restoreFile(source, prefs, files)
    }

    /** Loads an explicitly selected JSON mirror and applies its values to preferences. */
    fun restoreFile(source: File, prefs: SharedPreferences): Boolean =
        restoreFile(source, prefs, listOf(source))

    /** Resets invalid values in [root] to their defaults; true when anything changed. */
    fun validateAndCorrect(root: JSONObject): Boolean {
        var corrected = false

        // 1. schema: integer
        if (root.has(SCHEMA_KEY)) {
            val schemaVal = root.opt(SCHEMA_KEY)
            if (schemaVal !is Number || (schemaVal is Double && schemaVal % 1.0 != 0.0)) {
                root.put(SCHEMA_KEY, SCHEMA_VERSION)
                corrected = true
            }
        }

        // 2. debug_level: 0..3
        if (root.has(CompilerConfig.KEY_DEBUG)) {
            val debugVal = root.opt(CompilerConfig.KEY_DEBUG)
            val intVal = (debugVal as? Number)?.toInt()
            if (debugVal !is Number || intVal == null || intVal !in 0..3) {
                root.put(CompilerConfig.KEY_DEBUG, CompilerConfig.DebugLevel.D0.value)
                corrected = true
            }
        }

        // 3. optimization_level: 0..2
        if (root.has(CompilerConfig.KEY_OPTIMIZATION)) {
            val optVal = root.opt(CompilerConfig.KEY_OPTIMIZATION)
            val intVal = (optVal as? Number)?.toInt()
            if (optVal !is Number || intVal == null || intVal !in 0..2) {
                root.put(CompilerConfig.KEY_OPTIMIZATION, CompilerConfig.OptimizationLevel.O0.value)
                corrected = true
            }
        }

        // Helper for strictly validating boolean fields
        fun validateBoolean(key: String, defaultVal: Boolean) {
            if (root.has(key)) {
                val value = root.opt(key)
                if (value !is Boolean) {
                    root.put(key, defaultVal)
                    corrected = true
                }
            }
        }

        validateBoolean(CompilerConfig.KEY_IGNORE_CASE, false)
        validateBoolean(CompilerConfig.KEY_EXPLAIN_OUTPUT, true)
        validateBoolean(CompilerConfig.KEY_SEMICOLONS, true)
        validateBoolean(CompilerConfig.KEY_PARENTHESES, true)
        validateBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, false)
        validateBoolean(CompilerConfig.KEY_FORCED_MODE, false)
        validateBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, false)

        // 4. custom_flags: String
        if (root.has(CompilerConfig.KEY_CUSTOM_FLAGS)) {
            val value = root.opt(CompilerConfig.KEY_CUSTOM_FLAGS)
            if (value != null && value != JSONObject.NULL && value !is String) {
                root.put(CompilerConfig.KEY_CUSTOM_FLAGS, "")
                corrected = true
            }
        }

        // 5. compiler_version: "3.10.7" | "3.10.11" | null
        if (root.has(CompilerConfig.KEY_COMPILER_VERSION)) {
            val value = root.opt(CompilerConfig.KEY_COMPILER_VERSION)
            if (value != null && value != JSONObject.NULL) {
                val str = value as? String
                if (str == null || (str != CompilerConfig.STR_V3107 && str != CompilerConfig.STR_V31011)) {
                    root.put(CompilerConfig.KEY_COMPILER_VERSION, JSONObject.NULL)
                    corrected = true
                }
            }
        }

        // 6. app_theme: "system" | "light" | "dark" | null
        if (root.has(CompilerConfig.KEY_APP_THEME)) {
            val value = root.opt(CompilerConfig.KEY_APP_THEME)
            if (value != null && value != JSONObject.NULL) {
                val str = value as? String
                val allowedThemes = setOf("system", "light", "dark")
                if (str == null || str.lowercase() !in allowedThemes) {
                    root.put(CompilerConfig.KEY_APP_THEME, JSONObject.NULL)
                    corrected = true
                }
            }
        }

        // 7. app_language: "id" | "en" | "es" | "ru" | "tl" | "in" | "es-ar" | "es_ar"
        //    | "ru-ru" | "ru_ru" | "fil" | "fil-ph" | "tagalog" | null
        if (root.has(CompilerConfig.KEY_APP_LANGUAGE)) {
            val value = root.opt(CompilerConfig.KEY_APP_LANGUAGE)
            if (value != null && value != JSONObject.NULL) {
                val str = value as? String
                val allowedLangs = setOf(
                    "id", "en", "es", "ru", "tl",
                    "in", "es-ar", "es_ar", "ru-ru", "ru_ru",
                    "fil", "fil-ph", "fil_ph", "tagalog"
                )
                if (str == null || str.lowercase() !in allowedLangs) {
                    root.put(CompilerConfig.KEY_APP_LANGUAGE, JSONObject.NULL)
                    corrected = true
                }
            }
        }

        // 8. editor_background_color: null or hex color #RRGGBB / #AARRGGBB
        if (root.has(CompilerConfig.KEY_EDITOR_BG_COLOR)) {
            val value = root.opt(CompilerConfig.KEY_EDITOR_BG_COLOR)
            if (value != null && value != JSONObject.NULL) {
                val str = value as? String
                val hexRegex = Regex("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$")
                if (str == null || !str.matches(hexRegex)) {
                    root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, JSONObject.NULL)
                    corrected = true
                }
            }
        }

        // 9. include_paths: JSONArray of Strings
        if (root.has(CompilerConfig.KEY_INCLUDE_PATHS)) {
            val value = root.opt(CompilerConfig.KEY_INCLUDE_PATHS)
            if (value !is JSONArray) {
                root.put(CompilerConfig.KEY_INCLUDE_PATHS, JSONArray())
                corrected = true
            } else {
                val sanitizedArray = JSONArray()
                var arrayModified = false
                for (i in 0 until value.length()) {
                    val item = value.opt(i)
                    if (item is String) {
                        sanitizedArray.put(item)
                    } else {
                        arrayModified = true
                    }
                }
                if (arrayModified) {
                    root.put(CompilerConfig.KEY_INCLUDE_PATHS, sanitizedArray)
                    corrected = true
                }
            }
        }

        // 10. string or null paths
        listOf(
            CompilerConfig.KEY_WORKSPACE_FOLDERS,
            CompilerConfig.KEY_LAST_FILE,
            CompilerConfig.KEY_LAST_DIR
        ).forEach { key ->
            if (root.has(key)) {
                val value = root.opt(key)
                if (value != null && value != JSONObject.NULL && value !is String) {
                    root.put(key, JSONObject.NULL)
                    corrected = true
                }
            }
        }

        return corrected
    }

    /** Validates [file], resetting invalid settings and rewriting the corrected file. */
    fun validateAndCorrect(file: File): Boolean {
        if (!file.isFile) return false
        val content = runCatching { file.readText() }.getOrNull() ?: return false
        val root = runCatching { JSONObject(content) }.getOrNull() ?: return false
        val modified = validateAndCorrect(root)
        if (modified) {
            runCatching { file.writeText(root.toString(2)) }
        }
        return modified
    }

    private fun restoreFile(
        source: File,
        prefs: SharedPreferences,
        files: List<File>
    ): Boolean {
        if (!source.isFile) return false
        val root = runCatching { JSONObject(source.readText()) }.getOrNull() ?: return false

        val wasCorrected = validateAndCorrect(root)
        if (wasCorrected) {
            runCatching { source.writeText(root.toString(2)) }
        }

        val restored = runCatching {
            val editor = prefs.edit()

            root.optInt(CompilerConfig.KEY_DEBUG, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
                ?.let { editor.putInt(CompilerConfig.KEY_DEBUG, it) }

            root.optInt(CompilerConfig.KEY_OPTIMIZATION, Int.MIN_VALUE)
                .takeIf { it != Int.MIN_VALUE }
                ?.let { editor.putInt(CompilerConfig.KEY_OPTIMIZATION, it) }

            root.optBoolean(CompilerConfig.KEY_IGNORE_CASE, null)?.let { editor.putBoolean(CompilerConfig.KEY_IGNORE_CASE, it) }
            root.optBoolean(CompilerConfig.KEY_EXPLAIN_OUTPUT, null)?.let { editor.putBoolean(CompilerConfig.KEY_EXPLAIN_OUTPUT, it) }
            root.optBoolean(CompilerConfig.KEY_SEMICOLONS, null)?.let { editor.putBoolean(CompilerConfig.KEY_SEMICOLONS, it) }
            root.optBoolean(CompilerConfig.KEY_PARENTHESES, null)?.let { editor.putBoolean(CompilerConfig.KEY_PARENTHESES, it) }

            root.optStringOrNull(CompilerConfig.KEY_CUSTOM_FLAGS)?.let { editor.putString(CompilerConfig.KEY_CUSTOM_FLAGS, it) }
            root.optStringOrNull(CompilerConfig.KEY_COMPILER_VERSION)?.let { editor.putString(CompilerConfig.KEY_COMPILER_VERSION, it) }
            root.optStringOrNull(CompilerConfig.KEY_APP_THEME)?.let { editor.putString(CompilerConfig.KEY_APP_THEME, it) }
            root.optStringOrNull(CompilerConfig.KEY_APP_LANGUAGE)?.let { editor.putString(CompilerConfig.KEY_APP_LANGUAGE, it) }

            root.optBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, null)?.let {
                editor.putBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, it)
            }

            root.optStringOrNull(CompilerConfig.KEY_WORKSPACE_FOLDERS)?.let {
                editor.putString(CompilerConfig.KEY_WORKSPACE_FOLDERS, it)
            }
            root.optStringOrNull(CompilerConfig.KEY_LAST_FILE)?.let {
                editor.putString(CompilerConfig.KEY_LAST_FILE, it)
            }
            root.optStringOrNull(CompilerConfig.KEY_LAST_DIR)?.let {
                editor.putString(CompilerConfig.KEY_LAST_DIR, it)
            }

            root.optBoolean(CompilerConfig.KEY_FORCED_MODE, null)?.let { editor.putBoolean(CompilerConfig.KEY_FORCED_MODE, it) }
            root.optBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, null)?.let { editor.putBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, it) }

            if (root.has(CompilerConfig.KEY_EDITOR_BG_COLOR)) {
                val color = root.optStringOrNull(CompilerConfig.KEY_EDITOR_BG_COLOR)
                if (color == null) editor.remove(CompilerConfig.KEY_EDITOR_BG_COLOR)
                else editor.putString(CompilerConfig.KEY_EDITOR_BG_COLOR, color)
            }

            root.optJSONArray(CompilerConfig.KEY_INCLUDE_PATHS)
                ?.toStringList()
                ?.let { editor.putString(CompilerConfig.KEY_INCLUDE_PATHS, it.joinToString(";")) }

            editor.commit()
        }.getOrDefault(false)
        if (!restored) return false
        return true
    }

    /** Writes the current [prefs] out as the JSON mirror. */
    fun mirror(context: Context, prefs: SharedPreferences) = runCatching {
        val root = JSONObject()

        root.put(SCHEMA_KEY, SCHEMA_VERSION)
        root.put(CompilerConfig.KEY_DEBUG, prefs.getInt(CompilerConfig.KEY_DEBUG, CompilerConfig.DebugLevel.D0.value))
        root.put(
            CompilerConfig.KEY_OPTIMIZATION,
            prefs.getInt(CompilerConfig.KEY_OPTIMIZATION, CompilerConfig.OptimizationLevel.O0.value),
        )
        root.put(CompilerConfig.KEY_IGNORE_CASE, prefs.getBoolean(CompilerConfig.KEY_IGNORE_CASE, false))
        root.put(CompilerConfig.KEY_EXPLAIN_OUTPUT, prefs.getBoolean(CompilerConfig.KEY_EXPLAIN_OUTPUT, true))
        root.put(CompilerConfig.KEY_SEMICOLONS, prefs.getBoolean(CompilerConfig.KEY_SEMICOLONS, true))
        root.put(CompilerConfig.KEY_PARENTHESES, prefs.getBoolean(CompilerConfig.KEY_PARENTHESES, true))
        root.put(CompilerConfig.KEY_CUSTOM_FLAGS, prefs.getString(CompilerConfig.KEY_CUSTOM_FLAGS, "") ?: "")

        root.put(CompilerConfig.KEY_COMPILER_VERSION, prefs.getString(CompilerConfig.KEY_COMPILER_VERSION, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_THEME, prefs.getString(CompilerConfig.KEY_APP_THEME, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_LANGUAGE, prefs.getString(CompilerConfig.KEY_APP_LANGUAGE, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_LANGUAGE_SET, prefs.getBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, false))
        root.put(CompilerConfig.KEY_FORCED_MODE, prefs.getBoolean(CompilerConfig.KEY_FORCED_MODE, false))
        root.put(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, prefs.getBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, false))

        val color = prefs.getString(CompilerConfig.KEY_EDITOR_BG_COLOR, null)
        if (color == null) root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, color)

        val workspaceFolders = prefs.getString(CompilerConfig.KEY_WORKSPACE_FOLDERS, null)
        if (workspaceFolders == null) root.put(CompilerConfig.KEY_WORKSPACE_FOLDERS, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_WORKSPACE_FOLDERS, workspaceFolders)

        val lastFile = prefs.getString(CompilerConfig.KEY_LAST_FILE, null)
        if (lastFile == null) root.put(CompilerConfig.KEY_LAST_FILE, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_LAST_FILE, lastFile)

        val lastDir = prefs.getString(CompilerConfig.KEY_LAST_DIR, null)
        if (lastDir == null) root.put(CompilerConfig.KEY_LAST_DIR, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_LAST_DIR, lastDir)

        val includePaths = JSONArray()
        (prefs.getString(CompilerConfig.KEY_INCLUDE_PATHS, "") ?: "")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { includePaths.put(it) }
        root.put(CompilerConfig.KEY_INCLUDE_PATHS, includePaths)

        val target = materializeFile(context)
        target.writeText(root.toString(2))
    }

    /** Copies the JSON include paths back into the prefs. */
    fun restoreIncludePaths(context: Context, prefs: SharedPreferences): Int {
        val source = file(context)
        if (!source.isFile) return 0
        val root = runCatching { JSONObject(source.readText()) }.getOrNull() ?: return 0
        val paths = root.optJSONArray(CompilerConfig.KEY_INCLUDE_PATHS)?.toStringList() ?: return 0
        if (paths.isEmpty()) return 0

        prefs.edit { putString(CompilerConfig.KEY_INCLUDE_PATHS, paths.joinToString(";")) }
        return paths.size
    }

    private fun JSONArray.toStringList(): List<String> {
        val result = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index, "").trim()
            if (value.isNotEmpty()) result += value
        }
        return result
    }
}

/** `optBoolean` that returns null for a missing key. */
private fun JSONObject.optBoolean(name: String, fallback: Boolean?): Boolean? {
    if (!has(name) || isNull(name)) return fallback
    return optBoolean(name)
}

/** `optString` that returns null for a missing key or JSON null. */
private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name, "")
}

