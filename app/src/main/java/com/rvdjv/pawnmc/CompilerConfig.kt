package com.rvdjv.pawnmc

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * compiler configuration options.
 */
class CompilerConfig private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // debug
    var debugLevel: DebugLevel
        get() = DebugLevel.fromValue(prefs.getInt(KEY_DEBUG, DebugLevel.D3.value))
        set(value) = prefs.edit { putInt(KEY_DEBUG, value.value) }

    // code style
    var mandatorySemicolons: Boolean
        get() = prefs.getBoolean(KEY_SEMICOLONS, true)
        set(value) = prefs.edit { putBoolean(KEY_SEMICOLONS, value) }

    var mandatoryParentheses: Boolean
        get() = prefs.getBoolean(KEY_PARENTHESES, true)
        set(value) = prefs.edit { putBoolean(KEY_PARENTHESES, value) }

    // samp compatibility
    var sampCompatibility: Boolean
        get() = prefs.getBoolean(KEY_SAMP_COMPAT, false)
        set(value) = prefs.edit { putBoolean(KEY_SAMP_COMPAT, value) }

    // custom
    var customFlags: String
        get() = prefs.getString(KEY_CUSTOM_FLAGS, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CUSTOM_FLAGS, value) }

    // include paths
    var includePaths: List<String>
        get() {
            val stored = prefs.getString(KEY_INCLUDE_PATHS, "") ?: ""
            return if (stored.isEmpty()) emptyList() else stored.split(";")
        }
        set(value) = prefs.edit { putString(KEY_INCLUDE_PATHS, value.joinToString(";")) }

    // compiler version
    var compilerVersion: CompilerVersion
        get() = CompilerVersion.fromValue(
            prefs.getString(KEY_COMPILER_VERSION, CompilerVersion.V3107.value) ?: CompilerVersion.V3107.value
        )
        set(value) = prefs.edit { putString(KEY_COMPILER_VERSION, value.value) }

    var lastSelectedFilePath: String?
        get() = prefs.getString(KEY_LAST_FILE, null)
        set(value) = prefs.edit { putString(KEY_LAST_FILE, value) }

    var lastOpenedDirPath: String?
        get() = prefs.getString(KEY_LAST_DIR, null)
        set(value) = prefs.edit { putString(KEY_LAST_DIR, value) }

    var autoLoadLastFile: Boolean
        get() = prefs.getBoolean(KEY_AUTO_LOAD_LAST_FILE, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_LOAD_LAST_FILE, value) }

    /**
     * Build compiler options list from current configuration.
     */
    fun buildOptions(): List<String> {
        val options = mutableListOf<String>()

        // debug
        options.add("-d${debugLevel.value}")

        // code style
        if (mandatorySemicolons) options.add("-;+")
        if (mandatoryParentheses) options.add("-(+")

        // samp compatibility
        if (sampCompatibility) options.add("-Z+")

        // include paths
        for (path in includePaths) {
            if (path.isNotBlank()) {
                options.add("-i$path")
            }
        }

        // custom flags
        val custom = customFlags.trim()
        if (custom.isNotEmpty()) {
            options.addAll(custom.split("\\s+".toRegex()).filter { it.isNotBlank() })
        }

        return options
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

    enum class CompilerVersion(val value: String, val libraryName: String, val label: String, val description: String) {
        V3107("3.10.7", "pawnc3107", "Pawn 3.10.7-pawnmc.2", "Recommended for most SA-MP gamemodes"),
        V31011("3.10.11", "pawnc31011", "Pawn 3.10.11-pawnmc.1", "Recommended for open.mp gamemodes");

        companion object {
            fun fromValue(value: String) = entries.find { it.value == value } ?: V3107
        }
    }

    companion object {
        private const val PREFS_NAME = "compiler_config"
        private const val KEY_DEBUG = "debug"
        private const val KEY_SEMICOLONS = "semicolons"
        private const val KEY_PARENTHESES = "parentheses"
        private const val KEY_SAMP_COMPAT = "samp_compat"
        private const val KEY_CUSTOM_FLAGS = "custom_flags"
        private const val KEY_INCLUDE_PATHS = "include_paths"
        private const val KEY_COMPILER_VERSION = "compiler_version"
        private const val KEY_LAST_FILE = "last_selected_file"
        private const val KEY_LAST_DIR = "last_opened_dir"
        private const val KEY_AUTO_LOAD_LAST_FILE = "auto_load_last_file"

        @Volatile
        private var instance: CompilerConfig? = null

        fun getInstance(context: Context): CompilerConfig {
            return instance ?: synchronized(this) {
                instance ?: CompilerConfig(context).also { instance = it }
            }
        }
    }
}
