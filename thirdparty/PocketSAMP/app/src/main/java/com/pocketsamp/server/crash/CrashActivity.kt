package com.pocketsamp.server.crash

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketsamp.server.PocketSampActivity
import com.pocketsamp.server.ui.screens.CrashLogsScreen
import com.pocketsamp.server.ui.theme.PocketSAMPTheme
import com.pocketsamp.server.ui.theme.SampThemeColors

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashId = intent.getStringExtra(CrashHandler.EXTRA_CRASH_ID)

        setContent {
            PocketSAMPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SampThemeColors.bg
                ) {
                    CrashLogsScreen(
                        initialCrashId = crashId,
                        isCrashActivity = true,
                        onRestartApp = { restartApp() }
                    )
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, PocketSampActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
        Process.killProcess(Process.myPid())
    }
}
