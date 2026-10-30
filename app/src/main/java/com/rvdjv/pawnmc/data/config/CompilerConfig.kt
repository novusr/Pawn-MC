package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.Locale

/**
 * compiler configuration options.
 */
class CompilerConfig private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Application context for data-layer helpers that need one at call time. */
    fun applicationContextHolder(): Context = appContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun registerExternalConfigListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (!suppressMirror) refreshPropertiesFromPreferences()
    }

    /** True while the mirror is being written through the setters. */
    @Volatile
    private var suppressMirror: Boolean = false

    init {
        restoreFromFile()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        if (!CompilerConfigFile.exists(appContext)) {
            mirrorToFile()
        }
    }

    /** Repopulates the prefs from the JSON mirror after a reinstall. */
    fun restoreFromFile() {
        if (!CompilerConfigFile.restore(appContext, prefs)) return

        suppressMirror = true
        try {
            n_debug_level = n_debug_level
            n_optimization_level = n_optimization_level
            n_ignore_case = n_ignore_case
            n_explain_output = n_explain_output
            n_mandatory_semicolons = n_mandatory_semicolons
            n_mandatory_parentheses = n_mandatory_parentheses
            n_custom_flags = n_custom_flags
            n_include_paths = n_include_paths
            n_compiler_version = n_compiler_version
            n_app_theme = n_app_theme
            n_app_language = n_app_language
            n_editor_background_color = n_editor_background_color
            n_workspace_folders = n_workspace_folders
            n_last_selected_file_path = n_last_selected_file_path
            n_last_opened_dir_path = n_last_opened_dir_path
        } finally {
            suppressMirror = false
        }
        mirrorToFile()
    }

    /** Refreshes Compose state holders after a JSON reload without writing them back. */
    private fun refreshPropertiesFromPreferences() {
        suppressMirror = true
        try {
            refreshPropertiesFromPreferencesInternal()
        } finally {
            suppressMirror = false
        }
    }

    private fun refreshPropertiesFromPreferencesInternal() {
        n_debug_level = n_debug_level
        n_optimization_level = n_optimization_level
        n_ignore_case = n_ignore_case
        n_explain_output = n_explain_output
        n_mandatory_semicolons = n_mandatory_semicolons
        n_mandatory_parentheses = n_mandatory_parentheses
        n_custom_flags = n_custom_flags
        n_include_paths = n_include_paths
        n_compiler_version = n_compiler_version
        n_app_theme = n_app_theme
        n_app_language = n_app_language
        n_editor_background_color = n_editor_background_color
        n_last_selected_file_path = n_last_selected_file_path
        n_last_opened_dir_path = n_last_opened_dir_path
    }

    /** Rewrites the JSON file from the current prefs. */
    fun mirrorToFile() {
        if (suppressMirror) return
        CompilerConfigFile.mirror(appContext, prefs)
    }

    //
    // [debug]
    //
    var n_debug_level: DebugLevel
        get() = DebugLevel.fromValue(prefs.getInt(KEY_DEBUG, DebugLevel.D0.value))
        set(value) {
            prefs.edit { putInt(KEY_DEBUG, value.value) }
            mirrorToFile()
        }

    var n_optimization_level: OptimizationLevel
        get() = OptimizationLevel.fromValue(prefs.getInt(KEY_OPTIMIZATION, OptimizationLevel.O0.value))
        set(value) {
            prefs.edit { putInt(KEY_OPTIMIZATION, value.value) }
            mirrorToFile()
        }

    var n_ignore_case: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_CASE, false)
        set(value) {
            prefs.edit { putBoolean(KEY_IGNORE_CASE, value) }
            mirrorToFile()
        }

    var n_explain_output: Boolean
        get() = prefs.getBoolean(KEY_EXPLAIN_OUTPUT, true)
        set(value) {
            prefs.edit { putBoolean(KEY_EXPLAIN_OUTPUT, value) }
            mirrorToFile()
        }

    //
    // [code style]
    //
    var n_mandatory_semicolons: Boolean
        get() = prefs.getBoolean(KEY_SEMICOLONS, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SEMICOLONS, value) }
            mirrorToFile()
        }
    var n_mandatory_parentheses: Boolean
        get() = prefs.getBoolean(KEY_PARENTHESES, true)
        set(value) {
            prefs.edit { putBoolean(KEY_PARENTHESES, value) }
            mirrorToFile()
        }

    //
    // [custom]
    //
    var n_custom_flags: String
        get() = prefs.getString(KEY_CUSTOM_FLAGS, "") ?: ""
        set(value) {
            prefs.edit { putString(KEY_CUSTOM_FLAGS, value) }
            mirrorToFile()
        }

    //
    // [include paths]
    //
    var n_include_paths: List<String>
        get() {
            val stored = prefs.getString(KEY_INCLUDE_PATHS, "") ?: ""
            if (stored.isEmpty()) return emptyList()
            return dedupePaths(stored.split(";"))
        }
        set(value) {
            prefs.edit {
                putString(KEY_INCLUDE_PATHS, dedupePaths(value).joinToString(";"))
            }
            mirrorToFile()
        }
    //
    // [compiler version]
    //
    var n_compiler_version: CompilerVersion
        get() = CompilerVersion.fromValue(
            prefs.getString(KEY_COMPILER_VERSION, CompilerVersion.V3107.value) ?: CompilerVersion.V3107.value
        )
        set(value) {
            prefs.edit { putString(KEY_COMPILER_VERSION, value.value) }
            mirrorToFile()
        }

    var n_last_selected_file_path: String?
        get() = prefs.getString(KEY_LAST_FILE, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_FILE, value) }
            mirrorToFile()
        }

    /**
     * Folder that already went through the ignore-case conversion.
     *
     * The conversion leaves a `<folder>.backup` copy next to the working folder,
     * so the backup folder is the marker that tells the app the conversion for
     * this folder is already done and must not run again.
     */
    var n_case_insensitive_backup_dir: String?
        get() = prefs.getString(KEY_CASE_BACKUP_DIR, null)
        set(value) = prefs.edit { putString(KEY_CASE_BACKUP_DIR, value) }

    /** Forgets the stored backup folder, e.g. when it was deleted manually. */
    fun clearCaseInsensitiveBackupDir() {
        prefs.edit { remove(KEY_CASE_BACKUP_DIR) }
    }

    /**
     * Marks "Ignore case" as having been switched on by the automatic
     * `cannot read from file` detection instead of by the user.
     *
     * The flag travels with [n_ignore_case]: the compile that runs after the
     * automatic switch-on consumes it and drops both the flag and the switch, so
     * a user-set "Ignore case" stays untouched while an automatic one behaves
     * like a one-shot instruction. It is deliberately not mirrored to the JSON
     * file — it only has to outlive a single failed compile.
     */
    var n_ignore_case_auto: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_CASE_AUTO, false)
        set(value) = prefs.edit { putBoolean(KEY_IGNORE_CASE_AUTO, value) }

    /**
     * Marks "Explain output" as having been switched on by the automatic
     * consecutive-failure detection instead of by the user.
     *
     * The flag travels with [n_explain_output]: the compile that runs after the
     * automatic switch-on consumes it and drops both the flag and the switch, so
     * a user-set "Explain output" stays untouched while an automatic one behaves
     * like a one-shot instruction. It is deliberately not mirrored to the JSON
     * file — it only has to outlive a single failed compile.
     */
    var n_explain_auto: Boolean
        get() = prefs.getBoolean(KEY_EXPLAIN_AUTO, false)
        set(value) = prefs.edit { putBoolean(KEY_EXPLAIN_AUTO, value) }

    var lastSelectedFilePath: String?
        get() = n_last_selected_file_path
        set(value) { n_last_selected_file_path = value }

    var n_last_opened_dir_path: String?
        get() = prefs.getString(KEY_LAST_DIR, null)
        set(value) {
            prefs.edit { putString(KEY_LAST_DIR, value) }
            mirrorToFile()
        }

    var lastOpenedDirPath: String?
        get() = n_last_opened_dir_path
        set(value) { n_last_opened_dir_path = value }

    var n_detected_compiler_product_version: String?
        get() = prefs.getString(KEY_DETECTED_PRODUCT_VERSION, null)
        set(value) = prefs.edit { putString(KEY_DETECTED_PRODUCT_VERSION, value) }

    var n_detected_compiler_size_bytes: Long
        get() = prefs.getLong(KEY_DETECTED_SIZE_BYTES, 0L)
        set(value) = prefs.edit { putLong(KEY_DETECTED_SIZE_BYTES, value) }

    var n_detected_compiler_md5: String?
        get() = prefs.getString(KEY_DETECTED_MD5, null)
        set(value) = prefs.edit { putString(KEY_DETECTED_MD5, value) }

    var n_forced_compiler_mode: Boolean
        get() = prefs.getBoolean(KEY_FORCED_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCED_MODE, value) }

    var n_forced_include_path_auto: Boolean
        get() = prefs.getBoolean(KEY_FORCED_INCLUDE_PATH_AUTO, false)
        set(value) = prefs.edit { putBoolean(KEY_FORCED_INCLUDE_PATH_AUTO, value) }

    /**
     * Settles the compiler version for [sourcePath] before a compile.
     *
     * Forced mode keeps the selected version; otherwise a recognised nearby
     * compiler wins over the bundled default.
     */
    fun repinCompilerModeFor(
        sourcePath: String?,
        detectedVersion: CompilerConfig.CompilerVersion? = null
    ): CompilerVersion {
        if (n_forced_compiler_mode) return n_compiler_version
        if (sourcePath.isNullOrBlank()) return n_compiler_version

        val n_resolved = detectedVersion ?: return n_compiler_version
        if (n_resolved != n_compiler_version) {
            n_compiler_version = n_resolved
        }
        return n_resolved
    }

    var n_app_theme: AppTheme
        get() = AppTheme.fromValue(prefs.getString(KEY_APP_THEME, AppTheme.SYSTEM.value) ?: AppTheme.SYSTEM.value)
        set(value) {
            prefs.edit { putString(KEY_APP_THEME, value.value) }
            mirrorToFile()
        }

    var n_app_language: AppLanguage
        get() = AppLanguage.fromValue(prefs.getString(KEY_APP_LANGUAGE, AppLanguage.EN.value) ?: AppLanguage.EN.value)
        set(value) {
            prefs.edit(commit = true) { putString(KEY_APP_LANGUAGE, value.value) }
            mirrorToFile()
        }

    /** Sets the app language from the device locale once, on a fresh install. */
    fun applyDeviceLocaleOnFirstLaunch(context: Context) {
        if (prefs.getBoolean(KEY_APP_LANGUAGE_SET, false)) return
        val deviceLocale = try {
            context.resources.configuration.locales[0]
        } catch (_: Exception) {
            Locale.getDefault()
        }
        val detected = when (deviceLocale?.language?.lowercase()) {
            "id", "in" -> AppLanguage.ID
            "es" -> AppLanguage.ES
            "ru" -> AppLanguage.RU
            "tl", "fil" -> AppLanguage.TL
            else -> AppLanguage.EN
        }
        prefs.edit(commit = true) {
            putString(KEY_APP_LANGUAGE, detected.value)
            putBoolean(KEY_APP_LANGUAGE_SET, true)
        }
        mirrorToFile()
    }

    /** Records that the language is user-chosen, so detection never runs again. */
    fun markAppLanguageSet() {
        prefs.edit { putBoolean(KEY_APP_LANGUAGE_SET, true) }
        mirrorToFile()
    }

    //
    // [workspace]
    //

    /** Workspace folders kept open between runs, in open order. */
    var n_workspace_folders: List<String>
        get() = (prefs.getString(KEY_WORKSPACE_FOLDERS, "") ?: "")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) {
            prefs.edit {
                putString(KEY_WORKSPACE_FOLDERS, value.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";"))
            }
            mirrorToFile()
        }

    /** Forgets the remembered workspace folders, e.g. when the user closes every workspace. */
    fun clearWorkspaceFolders() {
        prefs.edit { remove(KEY_WORKSPACE_FOLDERS) }
    }

    //
    // [editor appearance]
    //

    /** Custom editor canvas colour as `#RRGGBB`, or `null` to follow the theme. */
    var n_editor_background_color: String?
        get() = prefs.getString(KEY_EDITOR_BG_COLOR, null)?.let(::normalizeEditorBackgroundColor)
        set(value) {
            prefs.edit {
                val normalized = normalizeEditorBackgroundColor(value)
                if (normalized == null) remove(KEY_EDITOR_BG_COLOR) else putString(KEY_EDITOR_BG_COLOR, normalized)
            }
            mirrorToFile()
        }

    /** Normalises a background colour to uppercase `#RRGGBB`, or null when invalid. */
    fun normalizeEditorBackgroundColor(raw: String?): String? {
        if (raw == null) return null
        val digits = raw.trim().removePrefix("#")
        if (digits.isEmpty()) return null
        if (digits.length != 6 || digits.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) {
            return null
        }
        return "#" + digits.uppercase()
    }

    /**
     * Removes stored include paths that no longer point at an existing folder and
     * rewrites the stored value when something was dropped.
     *
     * Called once on app start (see `MainActivity.onCreate`).
     *
     * @return the cleaned list
     */
    fun pruneMissingIncludePaths(): List<String> {
        val stored = n_include_paths
        val survivors = pruneMissingIncludePaths(stored)
        if (survivors != stored) {
            n_include_paths = survivors
        }
        return survivors
    }

    /**
     * Build compiler options list from current configuration.
     */
    fun buildOptions(compilerVersion: CompilerVersion = n_compiler_version): List<String> {
        return buildOptionsFor(
            debugLevel = n_debug_level,
            optimizationLevel = n_optimization_level,
            mandatorySemicolons = n_mandatory_semicolons,
            mandatoryParentheses = n_mandatory_parentheses,
            includePaths = n_include_paths,
            customFlags = n_custom_flags,
            compilerVersion = compilerVersion
        )
    }

    enum class DebugLevel(val value: Int, val label: String, val description: String) {
        D0(0, "Disabled (-d0)", "No debug symbols and no runtime validation. (Default)"),
        D1(1, "Runtime Validation (-d1)", "Enables array bounds checking without debug symbols."),
        D2(2, "Full Debugging (-d2)", "Complete debug information and runtime checks."),
        D3(3, "Maximum Debug (-d3)", "Most detailed debug data, disables optimization.");

        companion object {
            fun fromValue(value: Int) = entries.find { it.value == value } ?: D0
        }
    }

    enum class OptimizationLevel(val value: Int, val label: String, val description: String) {
        O0(0, "Disabled (-O0)", "No optimization; safest but largest output. (Default)"),
        O1(1, "Balanced (-O1)", "Default optimization pass for stable behavior."),
        O2(2, "Aggressive (-O2)", "Maximum optimization for speed and size.");

        companion object {
            fun fromValue(value: Int) = entries.find { it.value == value } ?: O0
        }
    }

    enum class AppTheme(val value: String, val label: String, val description: String) {
        SYSTEM("system", "System Default", "Follow the Android system theme."),
        LIGHT("light", "Light", "Always use the light theme."),
        DARK("dark", "Dark", "Always use the dark theme.");

        companion object {
            fun fromValue(value: String?) = entries.find { it.value == value } ?: SYSTEM
        }
    }

    enum class AppLanguage(val value: String, val label: String, val description: String) {
        ID("id", "Indonesia", "Bahasa Indonesia"),
        EN("en", "English (EN/US)", "English language"),
        ES("es", "Espanol (AR)", "Espanol rioplatense (Argentina)"),
        RU("ru", "Russkiy (RU)", "Russkiy yazyk"),
        TL("tl", "Tagalog (PH)", "Wikang Tagalog / Filipino");

        /** BCP-47 tag used when the Android context has to be localised. */
        fun localeTag(): String = when (this) {
            ID -> "id"
            ES -> "es-AR"
            RU -> "ru"
            TL -> "fil"
            EN -> "en"
        }

        companion object {
            fun fromValue(value: String?): AppLanguage = when {
                value.equals("in", ignoreCase = true) -> ID
                value.equals("es-ar", ignoreCase = true) || value.equals("es_ar", ignoreCase = true) -> ES
                value.equals("ru-ru", ignoreCase = true) || value.equals("ru_ru", ignoreCase = true) -> RU
                // Filipino is the national standard; Tagalog is the everyday name. Both
                // spellings map to one entry so a config written by either resolves.
                value.equals("fil", ignoreCase = true) ||
                    value.equals("fil-ph", ignoreCase = true) ||
                    value.equals("fil_ph", ignoreCase = true) ||
                    value.equals("tagalog", ignoreCase = true) -> TL
                else -> entries.find { it.value.equals(value, ignoreCase = true) } ?: ID
            }
        }
    }

    enum class CompilerVersion(
        val value: String,
        val libraryName: String,
        val label: String,
        val description: String,
        val expectedMd5: String? = null,
        val sizeToleranceBytes: Long = 4_096L,
        val fallbackEquivalent: CompilerVersion? = null
    ) {
        V3107(STR_V3107, STR_PAWN_3107, STR_VERSION_3107, "Stable", "a48e04d28e8cb77e0361ecb4dced2501", 4_096L, null),
        V31011(STR_V31011, STR_PAWN_31011, STR_VERSION_31011, "Newer", "9044b9ef65658c79851b4e2e249e5c75", 4_096L, null);

        fun other(): CompilerVersion = if (this == V3107) V31011 else V3107

        fun matchesDetected(
            productVersion: String?,
            sizeBytes: Long?,
            md5: String?
        ): Boolean {
            val normalizedProduct = productVersion?.trim()?.removeSuffix("\u0000")
            val productMatches = normalizedProduct != null && normalizedProduct.equals(value, ignoreCase = true)
            val sizeMatches = sizeBytes == null || sizeMatchesExpected(sizeBytes)
            val md5Matches = !md5.isNullOrBlank() && md5.equals(expectedMd5, ignoreCase = true)

            if (productMatches && sizeMatches) return true
            if (md5Matches) return true
            if (productMatches) return true

            return false
        }

        fun nearestSupportedEquivalent(productVersion: String?, sizeBytes: Long?, md5: String?): CompilerVersion? {
            if (productVersion != null) {
                val versionText = productVersion.trim()
                when {
                    versionText.startsWith("3.10.10", ignoreCase = true) || versionText.startsWith("3.10.9", ignoreCase = true) -> return V31011
                    versionText.startsWith("3.10.8", ignoreCase = true) -> return V3107
                }
            }

            if (md5 != null) {
                when {
                    md5.equals("ce6bd3ae0fb9fba27bb8189adf0c0fab", ignoreCase = true) -> return V31011
                    md5.equals("f2d87592200cbfb3346a949ca5d1a2bd", ignoreCase = true) -> return V31011
                    md5.equals("f28ab0d8b1ccbcc448b26e3255f4abb8", ignoreCase = true) -> return V3107
                }
            }

            if (sizeBytes != null) {
                val size = sizeBytes.toDouble()
                if (size <= 20_000L) return V31011
                if (size >= 28_000L) return V3107
            }

            return null
        }

        private fun sizeMatchesExpected(sizeBytes: Long): Boolean {
            val expected = when (this) {
                V3107 -> 28_672L
                V31011 -> 18_944L
            }
            return kotlin.math.abs(expected - sizeBytes) <= sizeToleranceBytes ||
                kotlin.math.abs(expected - sizeBytes) <= expected * 0.20
        }

        companion object {
            fun fromValue(value: String) = entries.find { it.value == value } ?: V3107
        }
    }

    companion object {
        /** Version strings for the two bundled Pawn compilers. */
        const val STR_V3107 = "3.10.7"
        const val STR_V31011 = "3.10.11"

        /** Human readable labels shown in Settings. */
        const val STR_VERSION_3107 = "Pawn 3.10.7"
        const val STR_VERSION_31011 = "Pawn 3.10.11"

        /** Names of the native libraries built from the compiler submodules. */
        const val STR_PAWN_3107 = "pawnc3107"
        const val STR_PAWN_31011 = "pawnc31011"

        //
        // Preference keys
        //
        internal const val KEY_DEBUG            = "debug_level"
        internal const val KEY_OPTIMIZATION      = "optimization_level"
        internal const val KEY_IGNORE_CASE       = "ignore_case"
        internal const val KEY_EXPLAIN_OUTPUT    = "explain_output"
        private const val PREFS_NAME           = "compiler_config"
        internal const val KEY_LAST_DIR         = "last_open_dir"
        internal const val KEY_LAST_FILE        = "last_sel_file"
        private const val KEY_CASE_BACKUP_DIR  = "case_insensitive_backup_dir"
        private const val KEY_IGNORE_CASE_AUTO = "ignore_case_auto"
        private const val KEY_EXPLAIN_AUTO = "explain_output_auto"
        internal const val KEY_SEMICOLONS       = "semicolons"
        internal const val KEY_PARENTHESES      = "parentheses"
        internal const val KEY_CUSTOM_FLAGS     = "custom_flags"
        internal const val KEY_INCLUDE_PATHS    = "include_paths"
        internal const val KEY_COMPILER_VERSION = "compiler_version"
        internal const val KEY_DETECTED_PRODUCT_VERSION = "detected_compiler_product_version"
        private const val KEY_DETECTED_SIZE_BYTES = "detected_compiler_size_bytes"
        private const val KEY_DETECTED_MD5 = "detected_compiler_md5"

        /** Compiler version handling, mirrored like the other preferences. */
        internal const val KEY_FORCED_MODE = "forced_compiler_mode"
        internal const val KEY_FORCED_INCLUDE_PATH_AUTO = "forced_include_path_auto"
        internal const val KEY_APP_THEME = "app_theme"
        internal const val KEY_APP_LANGUAGE = "app_language"
        // internal: the JSON mirror has to carry this marker across a reinstall, or
        // `applyDeviceLocaleOnFirstLaunch` would overwrite the restored language with
        // the device locale as if the install were brand new.
        internal const val KEY_APP_LANGUAGE_SET = "app_language_set"
        internal const val KEY_WORKSPACE_FOLDERS = "workspace_folders"
        internal const val KEY_EDITOR_BG_COLOR = "editor_background_color"

        fun buildOptionsFor(
            debugLevel: DebugLevel = DebugLevel.D0,
            optimizationLevel: OptimizationLevel = OptimizationLevel.O0,
            mandatorySemicolons: Boolean = true,
            mandatoryParentheses: Boolean = true,
            includePaths: List<String> = emptyList(),
            customFlags: String = "",
            compilerVersion: CompilerVersion = CompilerVersion.V3107
        ): List<String> {
            val options = mutableListOf<String>()
            options.add("-d=${debugLevel.value}")
            val effectiveOptimization = effectiveOptimizationLevel(compilerVersion, optimizationLevel)
            options.add("-O=${effectiveOptimization.value}")

            if (mandatorySemicolons) { options.add("-;+") }
            if (mandatoryParentheses) { options.add("-(+") }

            for (path in dedupePaths(includePaths)) {
                val normalized = normalPath(path)
                if (normalized.isNotBlank()) { options.add("-i=$normalized") }
            }

            // Behind the scenes the compiled options are written to the native layer
            // with `-C+` (compact bytecode encoding) and `-v0` (quiet verbosity) and
            // warning 217 (loose indentation) silenced. A user-supplied custom flag
            // that explicitly names the same option wins: the behind-the-scenes
            // default is dropped and the user's spelling is kept verbatim.
            val custom = customFlags.trim()
            val customOptions = if (custom.isNotEmpty()) {
                custom.split("\\s+".toRegex()).filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            if (customOptions.none { isCompactEncodingFlag(it) }) { options.add("-C+") }
            if (customOptions.none { isVerbosityFlag(it) }) { options.add("-v0") }
            if (customOptions.none { isWarning217Flag(it) }) { options.add("-w217") }
            if (customOptions.isNotEmpty()) {
                options.addAll(
                    if (compilerVersion == CompilerVersion.V3107) {
                        customOptions.filterNot { isO2OptimizationFlag(it) }
                    } else {
                        customOptions
                    }
                )
            }
            return options
        }

        /** Matches `-C+` / `-C-` (compact encoding toggle) in any spelling. */
        internal fun isCompactEncodingFlag(option: String): Boolean =
            option.matches(Regex("(?i)^-C[+-]$"))

        /** Matches `-v0`, `-v=0`, `-v 0`-style verbosity flags with any level. */
        internal fun isVerbosityFlag(option: String): Boolean =
            option.matches(Regex("(?i)^-v(=\\d+|\\d+)$"))

        /**
         * Matches the loose-indentation warning silencer in all its variants
         * (`-w217` / `-w=217`) and every other `-w<num>` warning switch, so a
         * user warning list is never double-declared by the built-in `-w217`.
         */
        internal fun isWarning217Flag(option: String): Boolean =
            option.matches(Regex("(?i)^-w=?217$"))

        fun optimizationLevelsFor(compilerVersion: CompilerVersion): List<OptimizationLevel> =
            if (compilerVersion == CompilerVersion.V3107) {
                OptimizationLevel.entries.filterNot { it == OptimizationLevel.O2 }
            } else {
                OptimizationLevel.entries
            }

        fun effectiveOptimizationLevel(
            compilerVersion: CompilerVersion,
            requestedLevel: OptimizationLevel
        ): OptimizationLevel =
            if (compilerVersion == CompilerVersion.V3107 && requestedLevel == OptimizationLevel.O2) {
                OptimizationLevel.O1
            } else {
                requestedLevel
            }

        internal fun isO2OptimizationFlag(option: String): Boolean =
            option.equals("-O2", ignoreCase = true) || option.equals("-O=2", ignoreCase = true)

        internal fun isOptimizationFlag(option: String): Boolean =
            option.matches(Regex("(?i)^-O=?\\d+$"))

        fun dedupePaths(paths: Iterable<String>): List<String> {
            val unique = linkedSetOf<String>()
            val result = mutableListOf<String>()

            for (path in paths) {
                val normalized = normalizeIncludePathInput(path)
                if (normalized.isBlank()) continue

                val key = includePathKey(normalized)
                if (key in unique) continue

                val canonicalKey = runCatching {
                    val file = java.io.File(normalized)
                    if (file.exists()) includePathKey(file.canonicalPath) else null
                }.getOrNull()

                if (canonicalKey != null && canonicalKey in unique) continue

                unique += key
                if (canonicalKey != null) {
                    unique += canonicalKey
                }
                result += normalized
            }

            return result
        }

        fun normalizeIncludePath(path: String): String = normalPath(path)

        /** Source extensions PawnMC accepts. */
        private val SCRIPT_EXTENSIONS = listOf(".pawn", ".pwn", ".p", ".inc")

        /** True when [path] ends with one of [SCRIPT_EXTENSIONS]. */
        fun hasScriptExtension(path: String): Boolean {
            val lowered = path.trim().replace('\\', '/').trimEnd('/').lowercase()
            if (lowered.isEmpty()) return false
            return SCRIPT_EXTENSIONS.any { lowered.endsWith(it) && lowered.length > it.length }
        }

        /** Removes a trailing script extension from [path]. */
        fun stripScriptExtension(path: String): String {
            val original = path.trim()
            val withoutTrailingSlash = original.replace('\\', '/').trimEnd('/')
            if (!hasScriptExtension(withoutTrailingSlash)) return original

            val lowered = withoutTrailingSlash.lowercase()
            val extension = SCRIPT_EXTENSIONS.firstOrNull { lowered.endsWith(it) } ?: return original
            return withoutTrailingSlash.dropLast(extension.length)
        }

        /** Normalises an include path and strips a trailing script extension. */
        fun normalizeIncludePathInput(path: String): String = normalPath(stripScriptExtension(path))

        /** Resolves the file handed to the compiler, keeping an existing file as-is. */
        fun resolveCompilePath(path: String): String {
            val trimmed = path.trim()
            if (trimmed.isEmpty()) return trimmed
            if (!hasScriptExtension(trimmed)) return trimmed
            val exists = runCatching { java.io.File(trimmed).exists() }.getOrDefault(false)
            if (exists) return trimmed
            return stripScriptExtension(trimmed)
        }

        /** Case-insensitive key used to compare two include paths. */
        fun includePathKey(path: String): String = normalPath(normalizePathSegments(path)).lowercase()

        private fun normalizePathSegments(path: String): String {
            val normalized = path.trim().replace('\\', '/')
            val prefix = when {
                normalized.startsWith("/") -> "/"
                normalized.length >= 3 && normalized[1] == ':' && normalized[2] == '/' -> normalized.substring(0, 3)
                else -> ""
            }
            val segments = normalized.removePrefix(prefix).split('/')
            val stack = mutableListOf<String>()

            for (segment in segments) {
                when (segment) {
                    "", "." -> Unit
                    ".." -> if (stack.isNotEmpty() && stack.last() != "..") {
                        stack.removeAt(stack.lastIndex)
                    } else if (prefix.isEmpty()) {
                        stack += segment
                    }
                    else -> stack += segment
                }
            }

            return prefix + stack.joinToString("/")
        }

        /** True when [candidate] already resolves to a folder in [existing]. */
        fun containsIncludePath(existing: Iterable<String>, candidate: String): Boolean {
            val key = includePathKey(candidate)
            if (key.isBlank() || key == "/") return false

            for (path in existing) {
                if (includePathKey(path) == key) return true
            }

            val candidateFile = java.io.File(normalPath(candidate))
            if (!candidateFile.exists()) return false

            val canonicalKey = runCatching {
                CompilerConfig.includePathKey(candidateFile.canonicalPath)
            }.getOrNull() ?: return false

            for (path in existing) {
                val file = java.io.File(normalPath(path))
                if (!file.exists()) continue
                val otherCanonical = runCatching {
                    CompilerConfig.includePathKey(file.canonicalPath)
                }.getOrNull() ?: continue
                if (otherCanonical == canonicalKey) return true
            }
            return false
        }

        /** Drops include paths whose folder no longer exists, then re-dedupes. */
        fun pruneMissingIncludePaths(existing: Iterable<String>): List<String> {
            val survivors = mutableListOf<String>()

            for (path in existing) {
                val normalized = normalizeIncludePathInput(path)
                if (normalized.isBlank() || normalized == "/") continue

                val file = java.io.File(normalized)
                val usable = runCatching { file.isDirectory }.getOrDefault(false)
                val isFallbackInclude = file.name.equals("include", ignoreCase = true) &&
                    file.parentFile?.name?.equals("pawno", ignoreCase = true) != true &&
                    file.parentFile?.name?.equals("qawno", ignoreCase = true) != true
                if (!usable && !isFallbackInclude) continue

                if (containsIncludePath(survivors, normalized)) continue

                survivors += normalized
            }

            return survivors
        }

        fun normalPath(path: String): String {
            val trimmed = path.trim().replace('\\', '/')
            if (trimmed.isEmpty()) return ""
            val cleanSlashes = trimmed.replace(Regex("/+"), "/")
            val withoutTrailingSlash = cleanSlashes.trimEnd('/')
            return if (withoutTrailingSlash.isEmpty()) "/" else "$withoutTrailingSlash/"
        }

        @Volatile
        private var instance: CompilerConfig? = null

        fun getInstance(): CompilerConfig = instance
            ?: throw IllegalStateException("CompilerConfig is not initialized. Call getInstance(context) first.")

        fun getInstanceOrNull(): CompilerConfig? = instance

        @Synchronized
        fun getInstance(context: Context): CompilerConfig {
            return instance ?: CompilerConfig(context).also { instance = it }
        }

        /** Re-read the user-editable mirror file into the live preferences. */
        fun reloadFromFile(context: Context): Boolean {
            val current = getInstance(context)
            val loaded = CompilerConfigFile.restore(context, current.prefs)
            if (loaded) current.refreshPropertiesFromPreferences()
            return loaded
        }
    }
}
