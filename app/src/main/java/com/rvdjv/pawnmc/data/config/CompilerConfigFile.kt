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

    internal const val SCHEMA_VERSION = 2
    /**
     * True when the JSON mirror file already exists in any of the locations this
     * build can read.
     *
     * Used by [com.rvdjv.pawnmc.data.config.CompilerConfig] to decide whether a
     * first launch has to seed the file with the defaults: without this check a
     * user who never opens Settings would reinstall into a device that has never
     * written anything down.
     */
    fun exists(context: Context): Boolean = candidateFiles(context).any { it.isFile }

    /**
     * The mirror the app reads and writes, in order of preference:
     *
     * 1. `Documents/PawnMC/config/compiler_config.json` — the only location that
     *    survives an uninstall, so this is where a fresh install looks first;
     * 2. `Android/data/<pkg>/files/PawnMC/config/…` — what older builds wrote;
     *    reading it is what carries an existing user's settings into the new
     *    location on the next launch without them doing anything;
     * 3. `files/.pawnmc/config/…` — private fallback when shared storage is
     *    unavailable.
     *
     * [restore] reads the first file that parses, and [mirror] rewrites the
     * primary; after that first successful write the app-scoped copy is stale but
     * harmless, and it stops being consulted once the primary exists.
     */
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
        val source = candidateFiles(context).firstOrNull { it.isFile } ?: return false

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

            // The "language already chosen" marker travels with the language itself:
            // a fresh install that restores a language must also suppress the
            // device-locale auto-detection, or the detection would overwrite the
            // restored value in the same session.
            root.optBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, null)?.let {
                editor.putBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, it)
            }

            // Session layout: which workspace folders were open, which single file was
            // last selected in the main screen, and which folder the browser last
            // showed. These are the settings the Xed editor reopens on launch, so
            // losing them is what makes a reinstall feel like starting from zero.
            root.optStringOrNull(CompilerConfig.KEY_WORKSPACE_FOLDERS)?.let {
                editor.putString(CompilerConfig.KEY_WORKSPACE_FOLDERS, it)
            }
            root.optStringOrNull(CompilerConfig.KEY_LAST_FILE)?.let {
                editor.putString(CompilerConfig.KEY_LAST_FILE, it)
            }
            root.optStringOrNull(CompilerConfig.KEY_LAST_DIR)?.let {
                editor.putString(CompilerConfig.KEY_LAST_DIR, it)
            }

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
     * Only anchored content is written: the detected compiler metadata, the
     * case-conversion backup marker and the ignore-case one-shot flag describe a
     * machine and a moment, not a preference, and carrying them to another install
     * would open a path that need not exist there. Everything the next launch is
     * meant to reopen — including the workspace layout and the last-opened
     * locations — goes through this file.
     */
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

        // Enum settings are written explicitly: passing a null String straight to
        // put() would throw (org.json rejects null values through that overload) and
        // abort the entire write, which is exactly the bug that made a fresh install
        // mirror nothing at all — the file never existed, so every reinstall
        // restarted from defaults. JSONObject.NULL is the "absent" spelling JSON
        // round-trips correctly.
        root.put(CompilerConfig.KEY_COMPILER_VERSION, prefs.getString(CompilerConfig.KEY_COMPILER_VERSION, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_THEME, prefs.getString(CompilerConfig.KEY_APP_THEME, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_LANGUAGE, prefs.getString(CompilerConfig.KEY_APP_LANGUAGE, null) ?: JSONObject.NULL)
        root.put(CompilerConfig.KEY_APP_LANGUAGE_SET, prefs.getBoolean(CompilerConfig.KEY_APP_LANGUAGE_SET, false))
        root.put(CompilerConfig.KEY_FORCED_MODE, prefs.getBoolean(CompilerConfig.KEY_FORCED_MODE, false))
        root.put(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, prefs.getBoolean(CompilerConfig.KEY_FORCED_INCLUDE_PATH_AUTO, false))

        val color = prefs.getString(CompilerConfig.KEY_EDITOR_BG_COLOR, null)
        if (color == null) root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, JSONObject.NULL)
        else root.put(CompilerConfig.KEY_EDITOR_BG_COLOR, color)

        // The workspace list is only written when the key actually exists in the
        // prefs: "no workspaces ever opened" and "workspaces were closed" would
        // otherwise be indistinguishable from "this install never stored one", and
        // writing an empty string for the third case would resurrect an empty list
        // over a value the user set by hand.
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

    /**
     * Copies the JSON include paths back into the prefs.
     *
     * Kept separate from `restore` because include paths are the one setting a running
     * app also rewrites on its own (`pruneMissingIncludePaths`), so the settings screen
     * needs a way to re-apply the file without discarding the compiler options it has
     * already restored.
     */
    fun restoreIncludePaths(context: Context, prefs: SharedPreferences): Int {
        val source = candidateFiles(context).firstOrNull { it.isFile } ?: return 0

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

