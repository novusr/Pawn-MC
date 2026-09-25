package com.rvdjv.pawnmc.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.rvdjv.pawnmc.data.compiler.PawnCompiler
import com.rvdjv.pawnmc.data.config.CompilerConfig

class SettingsViewModel(private val config: CompilerConfig) : ViewModel() {

    var n_compiler_version by mutableStateOf(config.n_compiler_version)
        private set

    var n_debug_level by mutableStateOf(config.n_debug_level)
        private set

    var n_mandatory_semicolons by mutableStateOf(config.n_mandatory_semicolons)
        private set

    var n_mandatory_parentheses by mutableStateOf(config.n_mandatory_parentheses)
        private set

    var n_custom_flags by mutableStateOf(config.n_custom_flags)
        private set

    val n_include_paths = mutableStateListOf<String>().apply { addAll(config.n_include_paths) }

    fun updateCompilerVersion(version: CompilerConfig.CompilerVersion) {
        n_compiler_version = version
        config.n_compiler_version = version
    }

    fun updateDebugLevel(level: CompilerConfig.DebugLevel) {
        n_debug_level = level
        config.n_debug_level = level
    }

    fun updateMandatorySemicolons(enabled: Boolean) {
        n_mandatory_semicolons = enabled
        config.n_mandatory_semicolons = enabled
    }

    fun updateMandatoryParentheses(enabled: Boolean) {
        n_mandatory_parentheses = enabled
        config.n_mandatory_parentheses = enabled
    }

    fun updateCustomFlags(flags: String) {
        n_custom_flags = flags
        config.n_custom_flags = flags
    }

    fun addIncludePath(path: String) {
        if (path !in n_include_paths) {
            n_include_paths.add(path)
            config.n_include_paths = n_include_paths.toList()
        }
    }

    fun removeIncludePathAt(index: Int) {
        if (index in n_include_paths.indices) {
            n_include_paths.removeAt(index)
            config.n_include_paths = n_include_paths.toList()
        }
    }

    fun isRestartRequired(requestedVersion: CompilerConfig.CompilerVersion): Boolean {
        return PawnCompiler.isRestartRequired(requestedVersion)
    }

    fun getLoadedVersion(): CompilerConfig.CompilerVersion? {
        return PawnCompiler.getLoadedVersion()
    }
}

class SettingsViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(CompilerConfig.getInstance(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
