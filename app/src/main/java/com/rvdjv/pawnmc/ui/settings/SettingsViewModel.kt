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

    var compilerVersion by mutableStateOf(config.compilerVersion)
        private set

    var debugLevel by mutableStateOf(config.debugLevel)
        private set

    var mandatorySemicolons by mutableStateOf(config.mandatorySemicolons)
        private set

    var mandatoryParentheses by mutableStateOf(config.mandatoryParentheses)
        private set

    var customFlags by mutableStateOf(config.customFlags)
        private set

    val includePaths = mutableStateListOf<String>().apply { addAll(config.includePaths) }

    fun updateCompilerVersion(version: CompilerConfig.CompilerVersion) {
        compilerVersion = version
        config.compilerVersion = version
    }

    fun updateDebugLevel(level: CompilerConfig.DebugLevel) {
        debugLevel = level
        config.debugLevel = level
    }

    fun updateMandatorySemicolons(enabled: Boolean) {
        mandatorySemicolons = enabled
        config.mandatorySemicolons = enabled
    }

    fun updateMandatoryParentheses(enabled: Boolean) {
        mandatoryParentheses = enabled
        config.mandatoryParentheses = enabled
    }

    fun updateCustomFlags(flags: String) {
        customFlags = flags
        config.customFlags = flags
    }

    fun addIncludePath(path: String) {
        if (path !in includePaths) {
            includePaths.add(path)
            config.includePaths = includePaths.toList()
        }
    }

    fun removeIncludePathAt(index: Int) {
        if (index in includePaths.indices) {
            includePaths.removeAt(index)
            config.includePaths = includePaths.toList()
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
