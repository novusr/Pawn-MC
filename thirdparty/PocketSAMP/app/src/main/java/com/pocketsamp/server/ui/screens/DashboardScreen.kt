package com.pocketsamp.server.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketsamp.server.PocketSampLocales
import com.pocketsamp.server.config.ServerDirectoryManager
import com.pocketsamp.server.service.ServerStatus
import com.pocketsamp.server.ui.theme.*
import java.io.File

@Composable
fun DashboardScreen(
    status: ServerStatus,
    isDbRunning: Boolean,
    lanIp: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    lang: PocketSampLocales.Lang = PocketSampLocales.Lang.EN,
    onNavigateBack: () -> Unit = {}
) {
    fun t(key: String): String = PocketSampLocales.text(lang, key)
    val context = LocalContext.current
    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    var currentServerDir by remember { mutableStateOf(ServerDirectoryManager.getServerDirectory(context)) }
    var hasStoragePermission by remember { mutableStateOf(ServerDirectoryManager.hasStoragePermission(context)) }

    val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
    var isBatteryOptIgnored by remember {
        mutableStateOf(
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
        )
    }

    if (lifecycleOwner != null) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasStoragePermission = ServerDirectoryManager.hasStoragePermission(context)
                    currentServerDir = ServerDirectoryManager.getServerDirectory(context)
                    isBatteryOptIgnored = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    } else {
                        true
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
            if (uri != null) {
        val dir = ServerDirectoryManager.parseTreeUriToFile(uri)
        if (dir != null && dir.exists() && dir.isDirectory) {
            ServerDirectoryManager.setServerDirectory(context, dir)
            currentServerDir = dir
            Toast.makeText(context, t("folder.changed") + ": ${dir.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, t("folder.pick.failed"), Toast.LENGTH_LONG).show()
        }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header. The back arrow mirrors the Xed editor's navigation icon: one
        // tap lands back on the PawnMC main interface, because expecting the system
        // back gesture to close a foreground server service is not discoverable.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = t("back.to.pawnmc"),
                        tint = SampThemeColors.textPrimary
                    )
                }
                Column {
                    Text(
                        text = "PocketSAMP",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = SampThemeColors.textPrimary
                    )
                    Text(
                        text = t("tagline"),
                        fontSize = 14.sp,
                        color = SampThemeColors.textSecondary
                    )
                }
            }

            // Status Indicator Pill
            val (statusText, statusColor) = when (status) {
                ServerStatus.RUNNING -> t("status.online") to SampThemeColors.success
                ServerStatus.STARTING, ServerStatus.INITIALIZING -> t("status.starting") to SampThemeColors.warn
                ServerStatus.STOPPING -> t("status.stopping") to SampThemeColors.warn
                ServerStatus.STOPPED -> t("status.offline") to SampThemeColors.danger
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        // Server Address Card (Dual Localhost & LAN Wi-Fi)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Section 1: Localhost (Main di HP Ini - Disarankan)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SampThemeColors.accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = t("dash.local.title"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = SampThemeColors.accent
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SA-MP Server IP", "127.0.0.1:7777")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "${t("copy.done")} 127.0.0.1:7777", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Localhost IP",
                                tint = SampThemeColors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = "127.0.0.1:7777",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SampThemeColors.textPrimary
                    )

                    Text(
                        text = t("dash.local.note"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textSecondary
                    )
                }

                // Section 2: LAN Wi-Fi (Multiplayer Bareng Teman)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = t("dash.lan.title"),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SampThemeColors.textSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SA-MP Server IP", "$lanIp:7777")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "${t("copy.done")} $lanIp:7777", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy LAN IP",
                                tint = SampThemeColors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "$lanIp:7777",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = SampThemeColors.textPrimary
                    )

                    Text(
                        text = t("dash.lan.note"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textMuted
                    )
                }
            }
        }

        // Database Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "MariaDB (MySQL 5.7/8.0 Wire)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textPrimary
                        )
                        Text(
                            text = if (isDbRunning) "127.0.0.1:3306 (Active)" else "Stopped",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDbRunning) SampThemeColors.success else SampThemeColors.textMuted
                        )
                    }
                }

                val dbStatusText = if (isDbRunning) t("db.running") else t("status.offline")
                val dbColor = if (isDbRunning) SampThemeColors.success else SampThemeColors.danger

                Text(
                    text = dbStatusText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = dbColor
                )
            }
        }

        // Specification Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpecCard(
                title = "ARCH TRANSLATION",
                value = "QEMU-i386",
                subtext = "ARM64 -> x86",
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                title = "RAM PROFILE",
                value = "~150 MB",
                subtext = "Low Memory Tuned",
                modifier = Modifier.weight(1f)
            )
        }

        // Storage Permission Alert (if needed on Android 11+)
        if (!hasStoragePermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SampThemeColors.warn.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SampThemeColors.warn)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = t("perm.file.title"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.warn
                        )
                    }

                    Text(
                        text = t("perm.file.desc"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textPrimary
                    )

                    Button(
                        onClick = {
                            try {
                                context.startActivity(ServerDirectoryManager.getManageStorageIntent(context))
                            } catch (e: Exception) {
                                Toast.makeText(context, t("perm.file.settings"), Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.warn, contentColor = SampThemeColors.accentOn),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = t("perm.file.button"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Battery Optimization Alert (keeps server responsive in background when user opens SA-MP client)
        if (!isBatteryOptIgnored) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SampThemeColors.accent.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SampThemeColors.accent)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = t("perm.battery.title"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.accent
                        )
                    }

                    Text(
                        text = t("perm.battery.desc"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textPrimary
                    )

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.accentOn),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = t("perm.battery.button"), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Background Stability & Anti-Disconnect Guide Card
        var showAntiDcGuide by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (showAntiDcGuide) SampThemeColors.accent else SampThemeColors.cardBorder)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = t("guide.antidc.title"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.textPrimary
                        )
                    }

                    TextButton(onClick = { showAntiDcGuide = !showAntiDcGuide }) {
                        Text(
                            text = if (showAntiDcGuide) t("perm.close") else t("perm.guide"),
                            color = SampThemeColors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showAntiDcGuide) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SampThemeColors.bg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = t("guide.antidc.step1"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.accent
                        )
                        Text(
                            text = t("guide.antidc.step1.desc"),
                            fontSize = 13.sp,
                            color = SampThemeColors.textSecondary
                        )

                        Text(
                            text = t("guide.antidc.step2"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.accent
                        )
                        Text(
                            text = t("guide.antidc.step2.desc"),
                            fontSize = 13.sp,
                            color = SampThemeColors.textSecondary
                        )

                        Text(
                            text = t("guide.antidc.step3"),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.accent
                        )
                        Text(
                            text = t("guide.antidc.step3.desc"),
                            fontSize = 13.sp,
                            color = SampThemeColors.textSecondary
                        )
                    }
                } else {
                    Text(
                        text = t("guide.antidc.short"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textMuted
                    )
                }
            }
        }

        // Gamemode Directory Card (Zero-Copy)
        val hasCfg = remember(currentServerDir) { File(currentServerDir, "server.cfg").exists() }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = t("folder.gamemode") + " SA-MP",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textSecondary
                        )
                    }

                    val badgeColor = if (hasCfg) SampThemeColors.success else SampThemeColors.warn
                    val badgeText = if (hasCfg) "server.cfg OK" else "server.cfg"
                    Text(
                        text = badgeText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentServerDir.absolutePath,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SampThemeColors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Server Path", currentServerDir.absolutePath)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, t("copy.path.done"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Path",
                            tint = SampThemeColors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.accentOn),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Text(text = t("folder.pick"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            currentServerDir = ServerDirectoryManager.resetToDefaultDirectory(context)
                            Toast.makeText(context, t("folder.reset.done"), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.textSecondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(text = t("folder.reset"), fontSize = 14.sp)
                    }
                }

                Text(
                    text = if (hasCfg) t("folder.has.cfg") else t("folder.no.cfg"),
                    fontSize = 13.sp,
                    color = if (hasCfg) SampThemeColors.textMuted else SampThemeColors.warn
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main Action Button
        val isBusy = status == ServerStatus.STARTING || status == ServerStatus.INITIALIZING || status == ServerStatus.STOPPING
        val isRunning = status == ServerStatus.RUNNING

        Button(
            onClick = {
                if (isRunning) onStopServer() else onStartServer()
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) SampThemeColors.danger else SampThemeColors.accent,
                contentColor = if (isRunning) Color.White else SampThemeColors.bg
            )
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = SampThemeColors.textPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isRunning) t("action.stop") else t("action.start"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SpecCard(
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SampThemeColors.textSecondary
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SampThemeColors.accent
            )
            Text(
                text = subtext,
                fontSize = 13.sp,
                color = SampThemeColors.textMuted
            )
        }
    }
}
