package com.rvdjv.pawnmc.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rvdjv.pawnmc.data.compiler.PawnCompiler
import com.rvdjv.pawnmc.data.update.AppUpdateManager
import com.rvdjv.pawnmc.ui.editor.XedEditorScreen
import com.rvdjv.pawnmc.ui.editor.XedEditorViewModel
import com.rvdjv.pawnmc.ui.editor.XedEditorViewModelFactory
import com.rvdjv.pawnmc.ui.settings.SettingsActivity
import com.rvdjv.pawnmc.ui.theme.PawnMCTheme
import com.rvdjv.pawnmc.ui.theme.resolveDarkTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PawnCompiler.resetSessionState()
        AppUpdateManager(applicationContext).ensureVersionFileWritten()
        enableEdgeToEdge()
        setContent {
            PawnMCTheme(darkTheme = resolveDarkTheme(viewModel.n_app_theme)) {
                var isEditorOpen by remember { mutableStateOf(false) }
                val currentFilePath = viewModel.selectedFilePath

                if (isEditorOpen && !currentFilePath.isNullOrBlank()) {
                    val editorViewModel: XedEditorViewModel = viewModel(
                        key = currentFilePath,
                        factory = XedEditorViewModelFactory(currentFilePath)
                    )
                    XedEditorScreen(
                        viewModel = editorViewModel,
                        onNavigateBack = {
                            isEditorOpen = false
                            viewModel.loadLastSelectedFile()
                        },
                        darkTheme = resolveDarkTheme(viewModel.n_app_theme)
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onEditorClick = {
                            isEditorOpen = true
                        },
                        onSettingsClick = {
                            startActivity(Intent(this, SettingsActivity::class.java))
                        },
                        initialUri = intent.data
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshTheme()
    }
}
