package com.rvdjv.pawnmc.data.config

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * compiler configuration options.
 */
class CompilerConfig private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    //
    // [debug]
    //
    var n_debug_level: DebugLevel
        get() = DebugLevel.fromValue(prefs.getInt(KEY_DEBUG, DebugLevel.D3.value))
        set(value) = prefs.edit { putInt(KEY_DEBUG, value.value) }

    var n_optimization_level: OptimizationLevel
        get() = OptimizationLevel.fromValue(prefs.getInt(KEY_OPTIMIZATION, OptimizationLevel.O1.value))
        set(value) = prefs.edit { putInt(KEY_OPTIMIZATION, value.value) }

    var n_ignore_case: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_CASE, false)
        set(value) = prefs.edit { putBoolean(KEY_IGNORE_CASE, value) }

    var n_explain_output: Boolean
        get() = prefs.getBoolean(KEY_EXPLAIN_OUTPUT, false)
        set(value) = prefs.edit { putBoolean(KEY_EXPLAIN_OUTPUT, value) }

    //
    // [code style]
    //
    var n_mandatory_semicolons: Boolean
        get() = prefs.getBoolean(KEY_SEMICOLONS, true)
        set(value) = prefs.edit { putBoolean(KEY_SEMICOLONS, value) }
    var n_mandatory_parentheses: Boolean
        get() = prefs.getBoolean(KEY_PARENTHESES, true)
        set(value) = prefs.edit { putBoolean(KEY_PARENTHESES, value) }

    //
    // [custom]
    //
    var n_custom_flags: String
        get() = prefs.getString(KEY_CUSTOM_FLAGS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_FLAGS, value) }

    //
    // [include paths]
    //
    var n_include_paths: List<String>
        get() {
            val stored = prefs.getString(KEY_INCLUDE_PATHS, "") ?: ""
            if (stored.isEmpty()) return emptyList()
            return stored.split(";").map { normalizeIncludePath(it) }.filter { it.isNotBlank() }
        }
        set(value) = prefs.edit {
            putString(KEY_INCLUDE_PATHS, value.map { normalizeIncludePath(it) }.filter { it.isNotBlank() }.distinct().joinToString(";"))
        }

    //
    // [compiler version]
    //
    var n_compiler_version: CompilerVersion
        get() = CompilerVersion.fromValue(
            prefs.getString(KEY_COMPILER_VERSION, CompilerVersion.V3107.value) ?: CompilerVersion.V3107.value
        )
        set(value) = prefs.edit { putString(KEY_COMPILER_VERSION, value.value) }

    var n_last_selected_file_path: String?
        get() = prefs.getString(KEY_LAST_FILE, null)
        set(value) = prefs.edit { putString(KEY_LAST_FILE, value) }

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
        set(value) = prefs.edit { putString(KEY_APP_THEME, value.value) }

    /**
     * Build compiler options list from current configuration.
     */
    fun buildOptions(): List<String> {
        return buildOptionsFor(
            debugLevel = n_debug_level,
            optimizationLevel = n_optimization_level,
            mandatorySemicolons = n_mandatory_semicolons,
            mandatoryParentheses = n_mandatory_parentheses,
            includePaths = n_include_paths,
            customFlags = n_custom_flags
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

    enum class CompilerVersion(
        val value: String,
        val libraryName: String,
        val label: String,
        val description: String,
        val expectedMd5: String? = null,
        val sizeToleranceBytes: Long = 4_096L,
        val fallbackEquivalent: CompilerVersion? = null
    ) {
        V3107("3.10.7", "pawnc3107", "Pawn 3.10.7", "Stable", "a48e04d28e8cb77e0361ecb4dced2501", 4_096L, null),
        V31011("3.10.11", "pawnc31011", "Pawn 3.10.11", "Newer", "9044b9ef65658c79851b4e2e249e5c75", 4_096L, null);

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
                if (size <= 20_000L) return V3107
                if (size >= 28_000L) return V31011
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
        private const val KEY_DEBUG            = "debug_level"
        private const val KEY_OPTIMIZATION      = "optimization_level"
        private const val KEY_IGNORE_CASE       = "ignore_case"
        private const val KEY_EXPLAIN_OUTPUT    = "explain_output"
        private const val PREFS_NAME           = "compiler_config"
        private const val KEY_LAST_DIR         = "last_open_dir"
        private const val KEY_LAST_FILE        = "last_sel_file"
        private const val KEY_SEMICOLONS       = "semicolons"
        private const val KEY_PARENTHESES      = "parentheses"
        private const val KEY_CUSTOM_FLAGS     = "custom_flags"
        private const val KEY_INCLUDE_PATHS    = "include_paths"
        private const val KEY_COMPILER_VERSION = "compiler_version"
        private const val KEY_DETECTED_PRODUCT_VERSION = "detected_compiler_product_version"
        private const val KEY_DETECTED_SIZE_BYTES = "detected_compiler_size_bytes"
        private const val KEY_DETECTED_MD5 = "detected_compiler_md5"
        private const val KEY_FORCED_MODE = "forced_compiler_mode"
        private const val KEY_FORCED_INCLUDE_PATH_AUTO = "forced_include_path_auto"
        private const val KEY_APP_THEME = "app_theme"

        fun buildOptionsFor(
            debugLevel: DebugLevel = DebugLevel.D3,
            optimizationLevel: OptimizationLevel = OptimizationLevel.O1,
            mandatorySemicolons: Boolean = true,
            mandatoryParentheses: Boolean = true,
            includePaths: List<String> = emptyList(),
            customFlags: String = ""
        ): List<String> {
            val options = mutableListOf<String>()
            options.add("-d=${debugLevel.value}")
            options.add("-O=${optimizationLevel.value}")

            if (mandatorySemicolons) { options.add("-;+") }
            if (mandatoryParentheses) { options.add("-(+") }

            for (path in includePaths) {
                val normalized = normalizeIncludePath(path)
                if (normalized.isNotBlank()) { options.add("-i=$normalized") }
            }

            val custom = customFlags.trim()
            if (custom.isNotEmpty()) { options.addAll(custom.split("\\s+".toRegex()).filter { it.isNotBlank() }) }
            return options
        }

        fun normalizeIncludePath(path: String): String {
            val trimmed = path.trim().replace('\\', '/')
            if (trimmed.isEmpty()) return ""
            val withoutTrailingSlash = trimmed.trimEnd('/')
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
