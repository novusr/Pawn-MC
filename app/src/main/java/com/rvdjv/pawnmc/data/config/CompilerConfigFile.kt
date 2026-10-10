package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The `.json` mirror of every setting PawnMC keeps.
 *
 * SharedPreferences alone could not carry settings across a manual reinstall: the keys
 * live inside the APK's private prefs area, which the installer clears. This file lives
 * in `PawnMC/config/` on the app's shared directory ([AppStorage.dataRoot]) whenever
 * external storage is available, so the user can read it, back it up, or hand-edit it
 * between two installs, and the app finds it again on the next launch and writes it back
 * into the prefs ("restore"). When shared storage is unavailable the same file is kept
 * in the app-private `files/.pawnmc` root instead, so the feature degrades to invisible
 * rather than to broken. Every write goes to both places at once ("mirror"), which means
 * the JSON is never a hand-made export the user has to remember to run.
 *
 * The file is keyed by `CompilerConfig.KEY_*`, i.e. the very names the prefs use. That is
 * deliberate: `restore` is a plain "read these keys out of the file and put them back",
 * with no second vocabulary to keep in sync, and the same `KEY_*` constants are what both
 * halves of the mirror are written against.
 *
 * The JSON is the source of truth only for *restoring*. The running app keeps reading
 * SharedPreferences, so a corrupt or hand-edited file can never break a session; the
 * worst it does is seed the prefs with a value one launch earlier.
 */
internal object CompilerConfigFile {

    /** File name inside [AppStorage.configRoot] (or the private fallback). */
    const val FILE_NAME: String = "compiler_config.json"

    /**
     * `schema` value this build writes and understands.
     *
     * The name is spelled out here rather than taken from `CompilerConfig.KEY_*`, because
     * `schema` is a property of the file format and not of the prefs it mirrors.
     */
    const val SCHEMA_KEY: String = "schema"

    private const val SCHEMA_VERSION = 1

    /**
     * The mirror on the user-visible app directory, or the private fallback when shared
     * storage is not mounted.
     */
    fun file(context: Context): File = File(AppStorage.configRoot(context), FILE_NAME)

    /**
     * Loads the JSON file and writes its values into [prefs].
     *
     * Missing keys are left untouched, so a partial or older file only restores what it
     * actually contains. A malformed file is ignored: keeping the prefs as they are is
     * always better than refusing to start over a settings mirror.
     *
     * @return true when the file existed and parsed, i.e. something may have been restored
     */
    fun restore(context: Context, prefs: SharedPreferences): Boolean {
        val source = file(context)
        if (!source.isFile) return false

        val root = runCatching { JSONObject(source.readText()) }.getOrNull() ?: return false

        runCatching {
            val editor = prefs.edit()

            // Enum / integer settings are stored as the value that is actually shown,
            // in case the labels ever drift away from the numeric codes.
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

            // Compiler version handling is a preference like any other, so it is restored
            // with the rest instead of resetting to "automatic" on every reinstall.
            root.optBoolean(CompilerConfig.KEY_FORCED_MODE, null)?.let { editor.putBoolean(CompilerConfig.KEY_FORCED_MODE, it) }
            root.optBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, null)?.let { editor.putBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, it) }

            // A JSON null means "no custom colour" (i.e. follow the theme), which has to
            // remove the key rather than store the literal string.
            if (root.has(CompilerConfig.KEY_EDITOR_BG_COLOR)) {
                val color = root.optStringOrNull(CompilerConfig.KEY_EDITOR_BG_COLOR)
                if (color == null) editor.remove(CompilerConfig.KEY_EDITOR_BG_COLOR)
                else editor.putString(CompilerConfig.KEY_EDITOR_BG_COLOR, color)
            }

            root.optJSONArray(CompilerConfig.KEY_INCLUDE_PATHS)
                ?.toStringList()
                ?.let { editor.putString(CompilerConfig.KEY_INCLUDE_PATHS, it.joinToString(";")) }

            editor.apply()
        }

        return true
    }

    /**
     * Writes the current [prefs] out as the JSON mirror.
     *
     * Reads the same keys the getters read instead of taking the config object, so the
     * file is a faithful dump of what the next launch will actually see, including a
     * value the user changed while the app was running.
     *
     * Only anchored content is written: the last-opened folder/file, the detected
     * compiler metadata and the case-conversion backup marker describe a machine and a
     * moment, not a preference, and carrying them to another install would open a path
     * that need not exist there.
     */
    fun mirror(context: Context, prefs: SharedPreferences) = runCatching {
        val root = JSONObject()

        root.put(SCHEMA_KEY, SCHEMA_VERSION)
        root.put(CompilerConfig.KEY_DEBUG, prefs.getInt(CompilerConfig.KEY_DEBUG, CompilerConfig.DebugLevel.D3.value))
        root.put(
            CompilerConfig.KEY_OPTIMIZATION,
            prefs.getInt(CompilerConfig.KEY_OPTIMIZATION, CompilerConfig.OptimizationLevel.O1.value),
        )
        root.put(CompilerConfig.KEY_IGNORE_CASE, prefs.getBoolean(CompilerConfig.KEY_IGNORE_CASE, false))
        root.put(CompilerConfig.KEY_EXPLAIN_OUTPUT, prefs.getBoolean(CompilerConfig.KEY_EXPLAIN_OUTPUT, false))
        root.put(CompilerConfig.KEY_SEMICOLONS, prefs.getBoolean(CompilerConfig.KEY_SEMICOLONS, true))
        root.put(CompilerConfig.KEY_PARENTHESES, prefs.getBoolean(CompilerConfig.KEY_PARENTHESES, true))
        root.put(CompilerConfig.KEY_CUSTOM_FLAGS, prefs.getString(CompilerConfig.KEY_CUSTOM_FLAGS, "") ?: "")
        root.put(CompilerConfig.KEY_COMPILER_VERSION, prefs.getString(CompilerConfig.KEY_COMPILER_VERSION, null))
        root.put(CompilerConfig.KEY_APP_THEME, prefs.getString(CompilerConfig.KEY_APP_THEME, null))
        root.put(CompilerConfig.KEY_APP_LANGUAGE, prefs.getString(CompilerConfig.KEY_APP_LANGUAGE, null))
        root.put(CompilerConfig.KEY_FORCED_MODE, prefs.getBoolean(CompilerConfig.KEY_FORCED_MODE, false))
        root.put(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, prefs.getBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, false))

        val color = prefs.getString(CompilerConfig.KEY_EDITOR_BG_COLOR, null)
        if (color == null) root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, color)

        val includePaths = JSONArray()
        (prefs.getString(CompilerConfig.KEY_INCLUDE_PATHS, "") ?: "")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { includePaths.put(it) }
        root.put(CompilerConfig.KEY_INCLUDE_PATHS, includePaths)

        file(context).writeText(root.toString(2))
    }

    /**
     * Copies the JSON include paths back into the prefs.
     *
     * Kept separate from `restore` because include paths are the one setting a running
     * app also rewrites on its own (`pruneMissingIncludePaths`), so the settings screen
     * needs a way to re-apply the file without discarding the compiler options it has
     * already restored.
     */
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

/**
 * `optBoolean` with a real "absent" answer.
 *
 * `JSONObject.optBoolean(name, default)` cannot distinguish a stored `false` from a
 * missing key when the default is `false`, which is exactly the bug that would silently
 * turn "user disabled this" into "key not present" and skip the restore.
 */
private fun JSONObject.optBoolean(name: String, fallback: Boolean?): Boolean? {
    if (!has(name) || isNull(name)) return fallback
    return optBoolean(name)
}

/**
 * `optString` with a real "absent" answer, and without the `String`/`Nothing?` clash.
 *
 * `JSONObject.optString(name, default)` is a Java method whose Kotlin signature declares a
 * non-null `String` default, so passing `null` makes Kotlin infer the whole expression as
 * `Nothing?` and warn at the assignment. This returns `String?` explicitly instead, which
 * is the truth: the key may hold JSON `null`, a string, or nothing at all.
 */
private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name, "")
}

