package com.rvdjv.pawnmc.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.rvdjv.pawnmc.data.compiler.PawnCompiler
import com.rvdjv.pawnmc.ui.settings.SettingsActivity
import com.rvdjv.pawnmc.ui.theme.PawnMCTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PawnCompiler.resetSessionState()
        enableEdgeToEdge()
        setContent {
            PawnMCTheme {
                MainScreen(
                    viewModel = viewModel,
                    onSettingsClick = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    initialUri = intent.data
                )
            }
        }
    }
}
