package com.rvdjv.pawnmc.`interface`.main

import android.content.Intent
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.rvdjv.pawnmc.data.compiler.Explanations
import com.rvdjv.pawnmc.data.compiler.Compiler
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.pawn.InternDat
import com.rvdjv.pawnmc.data.update.UpdateManager
import com.rvdjv.pawnmc.`interface`.editor.XedEditorScreen
import com.rvdjv.pawnmc.`interface`.editor.XedEditorViewModel
import com.rvdjv.pawnmc.`interface`.editor.XedEditorViewModelFactoryForActivity
import com.rvdjv.pawnmc.`interface`.settings.SettingsActivity
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme
import com.rvdjv.pawnmc.`interface`.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

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
    private val editorViewModel: XedEditorViewModel by viewModels {
        XedEditorViewModelFactoryForActivity(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = CompilerConfig.getInstance(applicationContext)
        // Include paths can point at folders the user deleted or an unmounted SD card.
        // Drop them once on startup so the list never shows dead entries and the compiler
        // is not handed stale -i values.
        config.pruneMissingIncludePaths()
        Compiler.resetSessionState()
        // The local compiler message explanations live in `_dat/_dat_explain.dat`; load the
        // table once so `explainCompilerOutput` never has to touch the disk per line.
        Explanations.load(applicationContext)
        InternDat.load(applicationContext)
        UpdateManager(applicationContext).ensureVersionFileWritten()
        enableEdgeToEdge()
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
                        initialUri = intent.data
                    )
                    if (isEditorOpen && !currentFilePath.isNullOrBlank()) {
                        XedEditorScreen(
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

    override fun onResume() {
        super.onResume()
        viewModel.refreshTheme()
        viewModel.refreshLanguage()
        viewModel.refreshEditorBackgroundColor()
    }
}
