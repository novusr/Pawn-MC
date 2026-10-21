package com.pocketsamp.server

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import com.pocketsamp.server.db.DatabaseManager
import com.pocketsamp.server.service.ServerForegroundService
import com.pocketsamp.server.service.ServerStatus
import com.pocketsamp.server.ui.screens.ConfigScreen
import com.pocketsamp.server.ui.screens.ConsoleScreen
import com.pocketsamp.server.ui.screens.CrashLogsScreen
import com.pocketsamp.server.ui.screens.DashboardScreen
import com.pocketsamp.server.ui.screens.DatabaseScreen
import com.pocketsamp.server.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    CONSOLE("Console", Icons.Default.Terminal),
    DATABASE("Database", Icons.Default.Storage),
    CRASHES("Crash Logs", Icons.Default.BugReport),
    CONFIG("Config", Icons.Default.Timer)
}

class PocketSampActivity : ComponentActivity() {

    /**
     * Called when the user leaves PocketSAMP through the back button while no server
     * is running. Kept as a host hook for future PawnMC integration; by default the
     * activity simply finishes and the host task (PawnMC main interface) shows again.
     *
     * When a server is still running the activity keeps itself alive (moveTaskToBack)
     * instead, so the background service stays visible in the notification and the
     * user can return to it without the UI being torn down.
     */
    var onNavigateBack: (() -> Unit)? = null

    private val dbManager by lazy { DatabaseManager() }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Prevent accidental server termination when pressing Back button/gesture
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val isRunning = ServerForegroundService.serverStatus.value == ServerStatus.RUNNING ||
                                ServerForegroundService.serverStatus.value == ServerStatus.STARTING
                if (isRunning) {
                    moveTaskToBack(true)
                } else {
                    // No server running: leave this activity and land back on the
                    // PawnMC main interface (the host task stays in the stack below).
                    val callback = onNavigateBack
                    if (callback != null) callback() else finish()
                }
            }
        })

        setContent {
            PocketSAMPTheme {
                val view = LocalView.current
                androidx.compose.runtime.SideEffect {
                    val isDark = PocketSampLocales.theme == PocketSampLocales.Theme.DARK ||
                        (PocketSampLocales.theme == PocketSampLocales.Theme.SYSTEM &&
                            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    val statusBarBackground = if (isDark) BgDark else BgLight
                    @Suppress("DEPRECATION")
                    window.statusBarColor = statusBarBackground.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                }
                MainAppLayout()
            }
        }
    }

    @Composable
    private fun MainAppLayout() {
        var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
        // The whole embedded UI follows the language configured in the PawnMC main
        // interface (Settings → Language); the host set it before launching.
        val lang = PocketSampLocales.current
        fun t(key: String): String = PocketSampLocales.text(lang, key)

        val serverStatus by ServerForegroundService.serverStatus.collectAsState()
        val isDbRunning by ServerForegroundService.isDbRunning.collectAsState()
        val logs by ServerForegroundService.logFeed.collectAsState()
        val lanIp by ServerForegroundService.lanIp.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(SampThemeColors.bg)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
 color = SampThemeColors.bg
                ) {
                    when (currentTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            status = serverStatus,
                            isDbRunning = isDbRunning,
                            lanIp = lanIp,
                            onStartServer = { startServerService() },
                            onStopServer = { stopServerService() },
                            lang = lang,
                            onNavigateBack = {
                                val callback = onNavigateBack
                                if (callback != null) callback() else finish()
                            }
                        )
                        AppTab.CONSOLE -> ConsoleScreen(
                            logs = logs,
                            onClearLogs = { ServerForegroundService.clearLogs() }
                        )
                        AppTab.DATABASE -> DatabaseScreen(
                            dbManager = dbManager,
                            isDbRunning = isDbRunning
                        )
                        AppTab.CRASHES -> CrashLogsScreen()
                        AppTab.CONFIG -> ConfigScreen()
                    }
                }

                // Floating tab rail, like the floating toolbar in the Xed editor:
                // a rounded card pinned to the trailing edge above the bottom
                // margin instead of a full-width bar eating the bottom of the
                // screen. Labels appear only on the active tab.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SampThemeColors.surface)
                        .border(1.dp, SampThemeColors.card, RoundedCornerShape(20.dp))
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppTab.values().forEach { tab ->
                        val selected = tab == currentTab
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) SampThemeColors.card else Color.Transparent)
                                .clickable { currentTab = tab }
                                .padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = t("tab.${tab.name.lowercase()}"),
                                tint = if (selected) SampThemeColors.accent else SampThemeColors.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            if (selected) {
                                Text(
                                    text = t("tab.${tab.name.lowercase()}"),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SampThemeColors.accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startServerService() {
        if (!com.pocketsamp.server.config.ServerDirectoryManager.hasStoragePermission(this)) {
            android.widget.Toast.makeText(
                this,
                PocketSampLocales.text(
                    PocketSampLocales.current,
                    "perm.file.grant"
                ),
                android.widget.Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(com.pocketsamp.server.config.ServerDirectoryManager.getManageStorageIntent(this))
            } catch (ignored: Exception) {
            }
            return
        }

        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopServerService() {
        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_STOP
        }
        startService(intent)
    }
}
