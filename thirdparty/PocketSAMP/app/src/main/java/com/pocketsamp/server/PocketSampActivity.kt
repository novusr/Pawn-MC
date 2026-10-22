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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import com.pocketsamp.server.ui.screens.PhpMyAdminScreen
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
        // phpMyAdmin is a full-screen surface above the tab content rather than a
        // sixth tab: it is a transient view of the database server, and closing it
        // must land back on whichever tab opened it.
        var isPhpMyAdminVisible by remember { mutableStateOf(false) }
        // The whole embedded UI follows the language configured in the PawnMC main
        // interface (Settings → Language); the host set it before launching.
        val lang = PocketSampLocales.current
        fun t(key: String): String = PocketSampLocales.text(lang, key)

        val serverStatus by ServerForegroundService.serverStatus.collectAsState()
        val isDbRunning by ServerForegroundService.isDbRunning.collectAsState()
        val logs by ServerForegroundService.logFeed.collectAsState()
        val lanIp by ServerForegroundService.lanIp.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            // The floating rail is an overlay, not part of the scaffold: no bottom
            // inset is reserved for it. Screens add their own bottom room instead,
            // which keeps the pages from getting a double bottom gap. Only the
            // system bars stay inset-aware so nothing slides under the status bar.
            contentWindowInsets = WindowInsets.systemBars
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
                            onOpenDatabase = { currentTab = AppTab.DATABASE },
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
                            isDbRunning = isDbRunning,
                            onOpenPhpMyAdmin = { isPhpMyAdminVisible = true },
                            lang = lang,
                            onBundleInstalled = { reinitializeDatabase() }
                        )
                        AppTab.CRASHES -> CrashLogsScreen()
                        AppTab.CONFIG -> ConfigScreen()
                    }
                }

                // Full-screen phpMyAdmin over the tab content.
                if (isPhpMyAdminVisible) {
                    PhpMyAdminScreen(
                        isDbRunning = isDbRunning,
                        onClose = { isPhpMyAdminVisible = false },
                        lang = lang
                    )
                }

                // Floating tab rail, like the floating toolbar in the Xed editor:
                // a rounded card pinned to the trailing edge above the bottom
                // margin instead of a full-width bar eating the bottom of the
                // screen. Labels appear only on the active tab.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SampThemeColors.surface)
                        .border(1.dp, SampThemeColors.card, RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Hidden while phpMyAdmin is open so the browser is truly
                    // full-screen and the rail cannot overlap its pages.
                    if (!isPhpMyAdminVisible) AppTab.values().forEach { tab ->
                        val selected = tab == currentTab
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) SampThemeColors.card else Color.Transparent)
                                .clickable { currentTab = tab }
                                .padding(horizontal = if (selected) 10.dp else 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = t("tab.${tab.name.lowercase()}"),
                                tint = if (selected) SampThemeColors.accent else SampThemeColors.textMuted,
                                modifier = Modifier.size(18.dp)
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

    /**
     * Asks the running service to pick up a freshly installed MariaDB bundle.
     *
     * Starting the service with [ServerForegroundService.ACTION_REINIT_DB] is safe in
     * both states: if the service is already alive it handles the action in place, and
     * if it is not, Android starts it and the action is handled on creation.
     */
    private fun reinitializeDatabase() {
        val intent = Intent(this, ServerForegroundService::class.java).apply {
            action = ServerForegroundService.ACTION_REINIT_DB
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    private fun startServerService() {        if (!com.pocketsamp.server.config.ServerDirectoryManager.hasStoragePermission(this)) {
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
