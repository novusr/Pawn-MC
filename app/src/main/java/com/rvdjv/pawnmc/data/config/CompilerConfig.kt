package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * compiler configuration options.
 */
class CompilerConfig private constructor(context: Context) {

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * True while [restoreFromFile] / [mirrorToFile] are writing through the setters.
     *
     * The recovery path drives itself by assigning to these same properties, so without
     * the flag it would mirror on every single assignment and restore its own restore.
     */
    @Volatile
    private var suppressMirror: Boolean = false

    init {
        // The mirror runs while the prefs object is already usable but before any
        // getter has been called, so `attachBaseContext` still sees the restored
        // language on the very first launch after a reinstall.
        restoreFromFile()
    }

    /**
     * Repopulates the prefs from the JSON mirror.
     *
     * This is the path that makes a manual reinstall behave like an update: Android
     * wipes SharedPreferences, the app starts with defaults, and this reads the file
     * back out of `filesDir` — which the installer leaves alone — and assigns every
     * value through the normal setters.
     */
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
        } finally {
            suppressMirror = false
        }
        mirrorToFile()
    }

    /**
     * Rewrites the JSON file from the current prefs.
     *
     * Called after every settings change, which is why it only serialises a dozen
     * scalars: cheap enough to stay synchronous with the write that triggered it.
     */
    fun mirrorToFile() {
        if (suppressMirror) return
        CompilerConfigFile.mirror(appContext, prefs)
    }

    //
    // [debug]
    //
    var n_debug_level: DebugLevel
        get() = DebugLevel.fromValue(prefs.getInt(KEY_DEBUG, DebugLevel.D3.value))
        set(value) {
            prefs.edit { putInt(KEY_DEBUG, value.value) }
            mirrorToFile()
        }

    var n_optimization_level: OptimizationLevel
        get() = OptimizationLevel.fromValue(prefs.getInt(KEY_OPTIMIZATION, OptimizationLevel.O1.value))
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
        get() = prefs.getBoolean(KEY_EXPLAIN_OUTPUT, false)
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
        set(value) = prefs.edit { putString(KEY_LAST_FILE, value) }

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

    var lastSelectedFilePath: String?
        get() = n_last_selected_file_path
        set(value) { n_last_selected_file_path = value }

    var n_last_opened_dir_path: String?
        get() = prefs.getString(KEY_LAST_DIR, null)
        set(value) = prefs.edit { putString(KEY_LAST_DIR, value) }

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

    //
    // [xed workspace]
    //

    /**
     * Workspace folders the Xed editor keeps open between runs, in the order they
     * were opened; the first one comes back in front.
     *
     * The main screen cannot pick a folder, only a single file, and a file picked
     * there is treated as temporary. Storing the folders here is what makes a
     * workspace folder chosen inside the Xed editor itself survive leaving the
     * editor, restarting the app or a reboot, instead of being thrown away with the
     * temporary single-file selection.
     *
     * Stored as one `;` separated string rather than a string set, because a set has
     * no order and would come back in an arbitrary one.
     */
    var n_xed_workspace_folders: List<String>
        get() = (prefs.getString(KEY_XED_WORKSPACE_FOLDERS, "") ?: "")
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        set(value) = prefs.edit {
            putString(KEY_XED_WORKSPACE_FOLDERS, value.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";"))
        }

    /** Forgets the remembered workspace folders, e.g. when the user resets Xed. */
    fun clearXedWorkspaceFolders() {
        prefs.edit { remove(KEY_XED_WORKSPACE_FOLDERS) }
    }

    //
    // [xed editor appearance]
    //

    /**
     * Custom Xed editor canvas colour as `#RRGGBB`, or `null` when the editor
     * should follow the app theme (the default behaviour).
     *
     * Stored as a string because SharedPreferences has no colour type, and the
     * value is validated on read so a hand-edited or stale preference can never
     * hand an invalid colour to the editor.
     */
    var n_editor_background_color: String?
        get() = prefs.getString(KEY_EDITOR_BG_COLOR, null)?.let(::normalizeEditorBackgroundColor)
        set(value) {
            prefs.edit {
                val normalized = normalizeEditorBackgroundColor(value)
                if (normalized == null) remove(KEY_EDITOR_BG_COLOR) else putString(KEY_EDITOR_BG_COLOR, normalized)
            }
            mirrorToFile()
        }

    /**
     * Normalises a user supplied editor background colour to uppercase `#RRGGBB`.
     *
     * Returns `null` for anything that is not exactly six hexadecimal digits, so
     * both the writer and the reader treat an invalid value as "no custom colour"
     * instead of propagating a broken value into the editor scheme. A blank
     * string means "clear the override", which is why it is distinct from a
     * malformed value here but collapses to the same stored result.
     */
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
        D0(0, "Disabled (-d0)", "No debug symbols and no runtime validation."),
        D1(1, "Runtime Validation (-d1)", "Enables array bounds checking without debug symbols. (Default)"),
        D2(2, "Full Debugging (-d2)", "Complete debug information and runtime checks."),
        D3(3, "Maximum Debug (-d3)", "Most detailed debug data, disables optimization.");

        companion object {
            fun fromValue(value: Int) = entries.find { it.value == value } ?: D1
        }
    }

    enum class OptimizationLevel(val value: Int, val label: String, val description: String) {
        O0(0, "Disabled (-O0)", "No optimization; safest but largest output."),
        O1(1, "Balanced (-O1)", "Default optimization pass for stable behavior. (Default)"),
        O2(2, "Aggressive (-O2)", "Maximum optimization for speed and size.");

        companion object {
            fun fromValue(value: Int) = entries.find { it.value == value } ?: O1
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
        RU("ru", "Russkiy (RU)", "Russkiy yazyk");

        /** BCP-47 tag used when the Android context has to be localised. */
        fun localeTag(): String = when (this) {
            ID -> "id"
            ES -> "es-AR"
            RU -> "ru"
            EN -> "en"
        }

        companion object {
            fun fromValue(value: String?): AppLanguage = when {
                value.equals("in", ignoreCase = true) -> ID
                value.equals("es-ar", ignoreCase = true) || value.equals("es_ar", ignoreCase = true) -> ES
                value.equals("ru-ru", ignoreCase = true) || value.equals("ru_ru", ignoreCase = true) -> RU
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
        /**
         * Version strings for the two bundled Pawn compilers.
         *
         * Every place that mentions a Pawn release refers to these constants
         * instead of repeating the literal, so a version bump only has to happen
         * here and the enum, the settings label and the library name can never
         * drift apart.
         */
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
        // `internal`, not `private`: the JSON mirror cannot live in this class without
        // turning every getter into a size-probe, so it lives in `CompilerConfigFile`
        // and reads and writes these very keys. Keeping the literals here, next to the
        // getters that use them, is what stops the prefs and the mirror from drifting
        // apart. The non-mirrored keys below stay private because nothing outside reads
        // them.
        //
        internal const val KEY_DEBUG            = "debug_level"
        internal const val KEY_OPTIMIZATION      = "optimization_level"
        internal const val KEY_IGNORE_CASE       = "ignore_case"
        internal const val KEY_EXPLAIN_OUTPUT    = "explain_output"
        private const val PREFS_NAME           = "compiler_config"
        private const val KEY_LAST_DIR         = "last_open_dir"
        private const val KEY_LAST_FILE        = "last_sel_file"
        private const val KEY_CASE_BACKUP_DIR  = "case_insensitive_backup_dir"
        internal const val KEY_SEMICOLONS       = "semicolons"
        internal const val KEY_PARENTHESES      = "parentheses"
        internal const val KEY_CUSTOM_FLAGS     = "custom_flags"
        internal const val KEY_INCLUDE_PATHS    = "include_paths"
        internal const val KEY_COMPILER_VERSION = "compiler_version"
        private const val KEY_DETECTED_PRODUCT_VERSION = "detected_compiler_product_version"
        private const val KEY_DETECTED_SIZE_BYTES = "detected_compiler_size_bytes"
        private const val KEY_DETECTED_MD5 = "detected_compiler_md5"
        private const val KEY_FORCED_MODE = "forced_compiler_mode"
        private const val KEY_FORCED_INCLUDE_PATH_AUTO = "forced_include_path_auto"
        internal const val KEY_APP_THEME = "app_theme"
        internal const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_XED_WORKSPACE_FOLDERS = "xed_workspace_folders"
        internal const val KEY_EDITOR_BG_COLOR = "editor_background_color"

        fun buildOptionsFor(
            debugLevel: DebugLevel = DebugLevel.D3,
            optimizationLevel: OptimizationLevel = OptimizationLevel.O1,
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

            val custom = customFlags.trim()
            if (custom.isNotEmpty()) {
                val customOptions = custom.split("\\s+".toRegex()).filter { it.isNotBlank() }
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

        /**
         * Source extensions PawnMC accepts. Only these four are treated as script
         * extensions, so folders that legitimately contain a dot (for example
         * `my.project/include`) keep working untouched.
         */
        private val SCRIPT_EXTENSIONS = listOf(".pawn", ".pwn", ".p", ".inc")

        /**
         * True when [path] ends with one of [SCRIPT_EXTENSIONS].
         *
         * The check is anchored to the end of the whole path, so only a real file
         * extension counts and never a dot that belongs to a parent folder.
         */
        fun hasScriptExtension(path: String): Boolean {
            val lowered = path.trim().replace('\\', '/').trimEnd('/').lowercase()
            if (lowered.isEmpty()) return false
            return SCRIPT_EXTENSIONS.any { lowered.endsWith(it) && lowered.length > it.length }
        }

        /**
         * Removes a trailing script extension from [path].
         *
         * `.../gamemodes/main.pwn` becomes `.../gamemodes/main`, while
         * `.../my.project/include` is returned unchanged because `.project` is not a
         * script extension.
         */
        fun stripScriptExtension(path: String): String {
            val original = path.trim()
            val withoutTrailingSlash = original.replace('\\', '/').trimEnd('/')
            if (!hasScriptExtension(withoutTrailingSlash)) return original

            val lowered = withoutTrailingSlash.lowercase()
            val extension = SCRIPT_EXTENSIONS.firstOrNull { lowered.endsWith(it) } ?: return original
            return withoutTrailingSlash.dropLast(extension.length)
        }

        /**
         * Normalises an include path exactly like [normalPath], then removes a trailing
         * script extension so a path typed as `.../gamemodes/main.pwn` is stored as the
         * folder `.../gamemodes/main` instead of being rejected as a non-existent path.
         */
        fun normalizeIncludePathInput(path: String): String = normalPath(stripScriptExtension(path))

        /**
         * Resolves the file handed to the compiler.
         *
         * When the selected path still points at a real file it is kept as-is, because
         * the compiler needs the extension to recognise the source. Only when it does not
         * exist (a mistyped selection, or a path carrying an extension) is the script
         * extension removed, and the compilation is never cancelled by this cleanup.
         */
        fun resolveCompilePath(path: String): String {
            val trimmed = path.trim()
            if (trimmed.isEmpty()) return trimmed
            if (!hasScriptExtension(trimmed)) return trimmed
            val exists = runCatching { java.io.File(trimmed).exists() }.getOrDefault(false)
            if (exists) return trimmed
            return stripScriptExtension(trimmed)
        }

        /**
         * Case-insensitive, separator-normalised key used to decide whether two include
         * paths point at the same folder.
         *
         * This is a pure string operation: it never touches the filesystem, so it stays
         * cheap enough to run on every incoming candidate before any real work happens.
         */
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

        /**
         * Cheap duplicate check used by every include path entry point (Settings manual
         * add/edit, folder picker, and [Compiler] auto-discovery).
         *
         * Returns true when [candidate] resolves to a folder already present in
         * [existing] (same path, different case, or same canonical location through a
         * symlink/relative alias), meaning the caller should skip it entirely instead of
         * re-scanning and re-filtering the whole list.
         */
        fun containsIncludePath(existing: Iterable<String>, candidate: String): Boolean {
            val key = includePathKey(candidate)
            if (key.isBlank() || key == "/") return false

            for (path in existing) {
                if (includePathKey(path) == key) return true
            }

            // Second pass: only pay for canonicalisation when the cheap comparison
            // failed. This keeps the common "new path" case filesystem-free.
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

        /**
         * Drops stored include paths whose folder no longer exists, except the stable
         * root `include` fallback which must remain available even before it is created.
         * Also re-dedupes, guarding against a list that went stale through a manual edit
         * of the stored value.
         *
         * @return the surviving paths, already normalised and unique
         */
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

        fun getInstance(context: Context): CompilerConfig {
            return instance ?: synchronized(this) {
                instance ?: CompilerConfig(context).also { instance = it }
            }
        }
    }
}
