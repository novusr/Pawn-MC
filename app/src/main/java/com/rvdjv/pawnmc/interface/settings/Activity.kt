package com.rvdjv.pawnmc.`interface`.settings

import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.compiler.Explanations
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.update.UpdateManager
import com.rvdjv.pawnmc.`interface`.main.SettingsTarget
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme
import com.rvdjv.pawnmc.`interface`.theme.resolveDarkTheme

class SettingsActivity : ComponentActivity() {

    companion object {
        /**
         * Value of [SettingsTarget] the screen should scroll to on open.
         *
         * The enum name travels as a string rather than as a serialised enum so the
         * activity stays constructible from any caller — the settings guide card, an
         * intent filter, a shell test — without that caller having to import the enum,
         * and an unknown value is simply ignored instead of crashing the screen.
         */
        const val EXTRA_FOCUS_TARGET = "com.rvdjv.pawnmc.extra.SETTINGS_FOCUS"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = CompilerConfig.getInstance(newBase).n_app_language
        super.attachBaseContext(AppLocalization.localizedContext(newBase, language))
    }

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CompilerConfig.getInstance(applicationContext)
        // Settings can be the launcher entry point, so make sure the explanation
        // table is loaded here too and not only from MainActivity.
        Explanations.load(applicationContext)
        UpdateManager(applicationContext).ensureVersionFileWritten()
        enableEdgeToEdge()
        setContent {
            PawnMCTheme(darkTheme = resolveDarkTheme(viewModel.n_app_theme)) {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onRestartRequested = { restartApp() },
                    // Only the name travels in the intent; an unknown one resolves to
                    // `null` and the screen simply opens at the top.
                    focusTarget = intent.getStringExtra(EXTRA_FOCUS_TARGET)
                        ?.let { name -> SettingsTarget.entries.firstOrNull { it.name == name } }
                )
            }
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
