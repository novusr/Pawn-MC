package com.rvdjv.pawnmc.`interface`.main

import android.Manifest
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rvdjv.pawnmc.data.compiler.Explanations
import com.rvdjv.pawnmc.data.compiler.Compiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.syntax.InternDat
import com.rvdjv.pawnmc.data.update.UpdateManager
import com.rvdjv.pawnmc.`interface`.editor.Workspace.buildFolderPickerIntent
import com.rvdjv.pawnmc.`interface`.editor.Workspace.buildFilePickerIntent
import com.rvdjv.pawnmc.`interface`.editor.Workspace.resolveDocumentToFile
import com.rvdjv.pawnmc.`interface`.editor.Workspace.resolveTreeToFile
import com.rvdjv.pawnmc.`interface`.editor.Workspace.WorkspaceSession
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorScreen
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorViewModel
import com.rvdjv.pawnmc.`interface`.editor.xedapi.EditorViewModelFactoryForActivity
import com.rvdjv.pawnmc.`interface`.settings.SettingsActivity
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme
import com.rvdjv.pawnmc.`interface`.theme.resolveDarkTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var initialUri by mutableStateOf<Uri?>(null)

    override fun attachBaseContext(newBase: Context) {
        val language = CompilerConfig.getInstance(newBase).n_app_language
        super.attachBaseContext(AppLocalization.localizedContext(newBase, language))
    }

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    /**
     * Editor view model scoped to the activity, not to the editor screen.
     *
     * Keeping one instance alive means the opened workspaces and their recent
     * files survive leaving the editor, for example after compiling from it.
     */
    private val editorViewModel: EditorViewModel by viewModels {
        EditorViewModelFactoryForActivity(applicationContext)
    }

    /**
     * Scope for the work needed to bring the app's data tables up.
     *
     * It outlives [onCreate] on purpose: the tables are no longer read on the launch
     * path (that is what made the first frame wait seconds on a low-RAM device), so the
     * load has to keep running after the method that started it returns.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialUri = intent.data

        // The first frame only needs the theme, which comes from SharedPreferences. Every
        // other startup task reads and parses a multi-hundred-kilobyte asset or walks the
        // file system, so it is handed to the IO dispatcher and the UI is drawn immediately
        // instead of after all of it. The UI is expected to appear first; nothing below is
        // required for it to render, and the loaders fall back to loading on demand if a
        // screen asks for a table before this finishes.
        startupScope.launch {
            val config = CompilerConfig.getInstance(applicationContext)
            // Include paths can point at folders the user deleted or an unmounted SD card.
            // Drop them once on startup so the list never shows dead entries and the
            // compiler is not handed stale -i values.
            config.pruneMissingIncludePaths()
            Compiler.resetSessionState()
            // The local compiler message explanations live in `data/_data_2026_exp.toml`; load
            // the table once so `explainCompilerOutput` never has to touch the disk per line.
            Explanations.load(applicationContext)
            // The localisation table and the Pawn symbol table are the two largest assets;
            // warming them here is what keeps the first screen from paying for them.
            AppLocalization.load(applicationContext)
            InternDat.load(applicationContext)
            UpdateManager(applicationContext).ensureVersionFileWritten()
        }

        val config = CompilerConfig.getInstance(applicationContext)
        enableEdgeToEdge()
        // The ongoing session notification is a nicety, not a requirement, so the prompt
        // is fired once and a refusal only means the shade stays empty.
        requestNotificationPermissionIfNeeded()
        setContent {
            PawnMCTheme(darkTheme = resolveDarkTheme(viewModel.n_app_theme)) {
                // Covers SettingsActivity, which is a separate entry point.
                SideEffect {
                    config.pruneMissingIncludePaths()
                }

                var isEditorOpen by remember { mutableStateOf(false) }
                val currentFilePath = viewModel.selectedFilePath

                // The editor follows the file chosen on the main screen, without
                // recreating its view model so the workspaces stay intact.
                LaunchedEffect(currentFilePath) {
                    if (!currentFilePath.isNullOrBlank()) {
                        editorViewModel.attachFile(currentFilePath)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel,
                        onEditorClick = {
                            isEditorOpen = true
                        },
                        onSettingsClick = {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        },
                        onOpenSettingsTarget = { target ->
                            startActivity(
                                Intent(this@MainActivity, SettingsActivity::class.java).apply {
                                    putExtra(SettingsActivity.EXTRA_FOCUS_TARGET, target.name)
                                }
                            )
                        },
                        initialUri = initialUri,
                        workspaceAvailable = editorViewModel.workspace.isWorkspaceOpen,
                    )
                    if (isEditorOpen && (editorViewModel.workspace.isWorkspaceOpen || !currentFilePath.isNullOrBlank())) {
                        EditorScreen(
                            viewModel = editorViewModel,
                            onNavigateBack = {
                                isEditorOpen = false
                                viewModel.loadLastSelectedFile()
                            },
                            editorBackgroundColor = viewModel.n_editor_background_color,
                            compileOutput = when {
                                viewModel.isCompiling && viewModel.outputText.isBlank() -> "Compiling...\n"
                                viewModel.hasCompilerOutput -> viewModel.outputText
                                else -> ""
                            },
                            onCompileRequest = { path ->
                                viewModel.requestCompileFromEditor(path)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialUri = intent.data
        if (intent.data != null) {
            viewModel.handleInitialUri(intent.data)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTheme()
        viewModel.refreshLanguage()
        viewModel.refreshEditorBackgroundColor()
    }

    /**
     * Asks for `POST_NOTIFICATIONS` once on API 33+.
     *
     * The result is deliberately not observed: [MainNotification] re-checks the grant on
     * every update, so a later grant (from system settings) starts working without this
     * activity having to remember anything, and a refusal costs nothing but the shade.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
