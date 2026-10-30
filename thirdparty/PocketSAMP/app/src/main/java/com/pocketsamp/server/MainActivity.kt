package com.pocketsamp.server

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.pocketsamp.server.configuration.DirectoryManager
import com.pocketsamp.server.database_handler.DatabaseManager
import com.pocketsamp.server.engine_handler.ThirdRuntime
import com.pocketsamp.server.service_handler.RuntimeService
import com.pocketsamp.server.service_handler.Status
import com.pocketsamp.server.user_interface.screens.ConfigScreen
import com.pocketsamp.server.user_interface.screens.ConsoleScreen
import com.pocketsamp.server.user_interface.screens.CrashLogsScreen
import com.pocketsamp.server.user_interface.screens.DashboardScreen
import com.pocketsamp.server.user_interface.screens.DatabaseScreen
import com.pocketsamp.server.user_interface.screens.PhpMyAdminScreen
import com.pocketsamp.server.user_interface.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    CONSOLE("Console", Icons.Default.Terminal),
    DATABASE("Database", Icons.Default.Storage),
    CRASHES("Crash Logs", Icons.Default.BugReport),
    CONFIG("Config", Icons.Default.Timer)
}

class MainActivity : ComponentActivity() {

    /** Host hook invoked when the user leaves while no server is running. */
    var onNavigateBack: (() -> Unit)? = null

    private val dbManager by lazy { DatabaseManager() }

    /** Storage access state, pushed into Compose; the gate reads it on every resume. */
    private val hasAllFilesAccess = mutableStateOf(false)

    /** Keeps [hasAllFilesAccess] in sync while the user is in system settings. */
    private val permissionObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
            val granted = DirectoryManager.hasStoragePermission(this)
            hasAllFilesAccess.value = granted
            if (granted) startRuntimeDownload()
        }
    }

    /** Only one background sync per process lifetime. */
    private var downloadStarted = false
    /** Fetches the runtime archives into `Documents/PawnMC/storage` without any UI. */
    private fun startRuntimeDownload() {
        if (downloadStarted) return
        downloadStarted = true
        lifecycleScope.launch {
            val ThirdRuntime = ThirdRuntime(applicationContext)
            if (ThirdRuntime.isComplete(applicationContext)) return@launch
            ThirdRuntime.sync { line -> android.util.Log.i("RuntimeDownload", line) }
        }
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            /*
             * Handled
             */
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(permissionObserver)
        hasAllFilesAccess.value = DirectoryManager.hasStoragePermission(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val isRunning = RuntimeService.serverStatus.value == Status.RUNNING ||
                                RuntimeService.serverStatus.value == Status.STARTING
                if (isRunning) {
                    moveTaskToBack(true)
                } else {
                    val callback = onNavigateBack
                    if (callback != null) callback() else finish()
                }
            }
        })

        setContent {
            PocketSAMPTheme {
                val view = LocalView.current
                androidx.compose.runtime.SideEffect {
                    val isDark = Locales.theme == Locales.Theme.DARK ||
                        (Locales.theme == Locales.Theme.SYSTEM &&
                            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    val statusBarBackground = if (isDark) BgDark else BgLight
                    @Suppress("DEPRECATION")
                    window.statusBarColor = statusBarBackground.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                }
                if (hasAllFilesAccess.value) {
                    MainAppLayout()
                } else {
                    StorageGate(
                        onRequestAccess = {
                            runCatching { startActivity(DirectoryManager.getManageStorageIntent(this)) }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun MainAppLayout() {
        var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
        var isPhpMyAdminVisible by remember { mutableStateOf(false) }
        val lang = Locales.current
        fun t(key: String): String = Locales.text(lang, key)

        val serverStatus by RuntimeService.serverStatus.collectAsState()
        val isDbRunning by RuntimeService.isDbRunning.collectAsState()
        val logs by RuntimeService.logFeed.collectAsState()
        val lanIp by RuntimeService.lanIp.collectAsState()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
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
                            onClearLogs = { RuntimeService.clearLogs() }
                        )
                        AppTab.DATABASE -> DatabaseScreen(
                            dbManager = dbManager,
                            isDbRunning = isDbRunning,
                            onOpenPhpMyAdmin = { isPhpMyAdminVisible = true },
                            lang = lang
                        )
                        AppTab.CRASHES -> CrashLogsScreen()
                        AppTab.CONFIG -> ConfigScreen()
                    }
                }

                if (isPhpMyAdminVisible) {
                    PhpMyAdminScreen(
                        isDbRunning = isDbRunning,
                        onClose = { isPhpMyAdminVisible = false },
                        lang = lang
                    )
                }

                // Floating tab rail: fixed 36 dp squares, icons only.
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SampThemeColors.surface)
                        .border(1.dp, SampThemeColors.card, RoundedCornerShape(14.dp))
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isPhpMyAdminVisible) AppTab.values().forEach { tab ->
                        val selected = tab == currentTab
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) SampThemeColors.card else Color.Transparent)
                                .clickable { currentTab = tab },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = t("tab.${tab.name.lowercase()}"),
                                tint = if (selected) SampThemeColors.accent else SampThemeColors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startServerService() {
        if (!DirectoryManager.hasStoragePermission(this)) {
            android.widget.Toast.makeText(
                this,
                Locales.text(
                    Locales.current,
                    "perm.file.grant"
                ),
                android.widget.Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(DirectoryManager.getManageStorageIntent(this))
            } catch (ignored: Exception) {
            }
            return
        }

        val intent = Intent(this, RuntimeService::class.java).apply {
            action = RuntimeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopServerService() {
        val intent = Intent(this, RuntimeService::class.java).apply {
            action = RuntimeService.ACTION_STOP
        }
        startService(intent)
    }
}

/**
 * The one screen shown while "All files access" is missing.
 */
@Composable
private fun StorageGate(onRequestAccess: () -> Unit) {
    BackHandler(enabled = true) { /* deliberately ignored */ }

    Surface(modifier = Modifier.fillMaxSize(), color = SampThemeColors.bg) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = Locales.text("storage.gate.title"),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SampThemeColors.textPrimary
            )
            Spacer12()
            Text(
                text = Locales.text("storage.gate.desc"),
                fontSize = 13.sp,
                color = SampThemeColors.textSecondary
            )
            Spacer12()
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SampThemeColors.accent,
                    contentColor = SampThemeColors.accentOn
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = Locales.text("storage.gate.button"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun Spacer12() {
    Spacer(modifier = Modifier.height(12.dp))
}
