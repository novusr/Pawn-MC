package com.pocketsamp.server.crash_handler

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.pocketsamp.server.MainActivity
import com.pocketsamp.server.user_interface.screens._CrashInfo
import com.pocketsamp.server.user_interface.theme.PocketSAMPTheme
import com.pocketsamp.server.user_interface.theme.SampThemeColors

class CrashDialog : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crashId = intent.getStringExtra(Handler.EXTRA_CRASH_ID)

        setContent {
            PocketSAMPTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = SampThemeColors.bg
                ) {
                    _CrashInfo(
                        initialCrashId = crashId,
                        isCrashDialog = true,
                        onRestartApp = { restartApp() }
                    )
                }
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        finish()
        Process.killProcess(Process.myPid())
    }
}
