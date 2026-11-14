package com.rvdjv.pawnmc.user_interface.settings

import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig
import com.rvdjv.pawnmc.apps_data.data_compiler.Explanations
import com.rvdjv.pawnmc.apps_data.data_config.AppLocalization
import com.rvdjv.pawnmc.apps_data.data_update.UpdateManager
import com.rvdjv.pawnmc.user_interface.main.SettingsTarget
import com.rvdjv.pawnmc.user_interface.theme.PawnMCTheme
import com.rvdjv.pawnmc.user_interface.theme.resolveDarkTheme

class SettingsActivity : ComponentActivity() {

    companion object {
        /**
         * Value of [SettingsTarget] the screen should scroll to on open.
         *
         * The enum name travels as a string rather than as a serialised enum so the
         * activity stays constructible from any caller - the settings guide card, an
         * intent filter, a shell test - without that caller having to import the enum,
         * and an unknown value is simply ignored instead of crashing the screen.
         */
        const val EXTRA_FOCUS_TARGET = "com.rvdjv.pawnmc.extra.SETTINGS_FOCUS"

        /**
         * Pending compiler-switch request written before the restart.
         *
         * A restart kills the process mid-write risk-free only when the request is
         * on disk: this JSON holds the version and forced/auto mode the user asked
         * for, and the next launch replays it against the live config before the
         * first frame.
         */
        const val PENDING_SWITCH_FILE = "pending_compiler_switch.json"
    }

    override fun attachBaseContext(newBase: Context) {
        val language = ContentConfig.getInstance(newBase).n_app_language
        super.attachBaseContext(AppLocalization.localizedContext(newBase, language))
    }

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Settings has its own entry point; without this line a crash on this screen and
        // on the main screen would be indistinguishable in the journal.
        com.rvdjv.pawnmc.apps_data.data_crash.ActivityJournal.record(
            applicationContext,
            "lifecycle",
            "SettingsActivity.onCreate focus=${intent.getStringExtra(EXTRA_FOCUS_TARGET)}"
        )
        ContentConfig.getInstance(applicationContext)
        // A compiler switch queued by the previous process is applied (and verified)
        // here, before anything reads the config - see [applyPendingCompilerSwitch].
        applyPendingCompilerSwitch()
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
                    onRestartRequested = { queueCompilerSwitchAndRestart() },
                    // Only the name travels in the intent; an unknown one resolves to
                    // `null` and the screen simply opens at the top.
                    focusTarget = intent.getStringExtra(EXTRA_FOCUS_TARGET)
                        ?.let { name -> SettingsTarget.entries.firstOrNull { it.name == name } }
                )
            }
        }
    }

    /** File holding the pending switch request inside the private files dir. */
    private fun pendingSwitchFile() = java.io.File(filesDir, PENDING_SWITCH_FILE)

    /**
     * Writes the user's requested compiler state to the temporary file, then
     * restarts. The next launch replays it in [applyPendingCompilerSwitch].
     */
    private fun queueCompilerSwitchAndRestart() {
        runCatching {
            pendingSwitchFile().writeText(
                org.json.JSONObject()
                    .put("version", viewModel.n_compiler_version.value)
                    .put("forced", viewModel.n_forced_compiler_mode)
                    .put("requested", System.currentTimeMillis())
                    .toString()
            )
        }
        restartApp()
    }

    /**
     * Replays a pending switch request against the live config.
     *
     * When the stored config already matches the request the change went through
     * cleanly - the temporary file is deleted and nothing else happens. When it
     * does not (a write lost to the process kill, or a system-side switch like the
     * `__addressof` recovery overwriting it), the request is applied again so the
     * user's choice always wins the race.
     */
    private fun applyPendingCompilerSwitch() {
        val file = pendingSwitchFile()
        if (!file.isFile) return
        val request = runCatching {
            val root = org.json.JSONObject(file.readText())
            val version = ContentConfig.CompilerVersion.fromValue(root.optString("version"))
            val forced = root.optBoolean("forced", false)
            Triple(version, forced, root.optLong("requested", 0L))
        }.getOrNull()
        file.delete()
        val (requestedVersion, requestedForced, _) = request ?: return

        val config = ContentConfig.getInstance(applicationContext)
        val alreadyApplied = config.n_compiler_version == requestedVersion &&
            config.n_forced_compiler_mode == requestedForced
        if (alreadyApplied) return

        config.n_compiler_version = requestedVersion
        config.n_forced_compiler_mode = requestedForced
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
