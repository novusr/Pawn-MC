package com.pocketsamp.server.user_interface.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.OpenInBrowser
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pocketsamp.server.Locales
import com.pocketsamp.server.configuration.DirectoryManager
import com.pocketsamp.server.service_handler.Status
import com.pocketsamp.server.user_interface.theme.*
import java.io.File

@Composable
fun DashboardScreen(
    status: Status,
    isDbRunning: Boolean,
    lanIp: String,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    lang: Locales.Lang = Locales.Lang.EN,
    onOpenDatabase: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    fun t(key: String): String = Locales.text(lang, key)
    val context = LocalContext.current
    val lifecycleOwner = context as? androidx.lifecycle.LifecycleOwner
    var currentServerDir by remember { mutableStateOf(DirectoryManager.getServerDirectory(context)) }
    var hasStoragePermission by remember { mutableStateOf(DirectoryManager.hasStoragePermission(context)) }
    /*
     * phpMyAdmin is offered from the Dashboard too: the MariaDB card is the first
     * place a user looks after starting the server, and making them open the
     * Database tab first only hid an action they were about to take anyway.
     */
    var showPhpMyAdmin by remember { mutableStateOf(false) }

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
                    hasStoragePermission = DirectoryManager.hasStoragePermission(context)
                    currentServerDir = DirectoryManager.getServerDirectory(context)
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
        val dir = DirectoryManager.parseTreeUriToFile(uri)
        if (dir != null && dir.exists() && dir.isDirectory) {
            DirectoryManager.setServerDirectory(context, dir)
            currentServerDir = dir
            Toast.makeText(context, t("folder.changed") + ": ${dir.name}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, t("folder.pick.failed"), Toast.LENGTH_LONG).show()
        }
        }
    }

    val scrollState = rememberScrollState()

    /*
     * Dashboard-level phpMyAdmin: a full-screen browser over the dashboard so the
     * MariaDB card can open it without routing through the Database tab.
     */
    if (showPhpMyAdmin) {
        PhpMyAdminScreen(
            isDbRunning = isDbRunning,
            onClose = { showPhpMyAdmin = false },
            lang = lang
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        /*
         * App Header. The back arrow mirrors the Xed editor's navigation icon: one
         * tap lands back on the PawnMC main interface, because expecting the system
         * back gesture to close a foreground server service is not discoverable.
         */
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SampThemeColors.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = t("tagline"),
                        fontSize = 12.sp,
                        color = SampThemeColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            /*
             * Status Indicator Pill
             */
            val (statusText, statusColor) = when (status) {
                Status.RUNNING -> t("status.online") to SampThemeColors.success
                Status.STARTING, Status.INITIALIZING -> t("status.starting") to SampThemeColors.warn
                Status.STOPPING -> t("status.stopping") to SampThemeColors.warn
                Status.STOPPED -> t("status.offline") to SampThemeColors.danger
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    maxLines = 1
                )
            }
        }

        /*
         * Server Address Card (Dual Localhost & LAN Wi-Fi)
         */
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                /*
                 * Section 1: Localhost (Main di HP Ini - Disarankan)
                 */
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SampThemeColors.accent.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
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
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = SampThemeColors.textPrimary,
                        maxLines = 1
                    )

                    Text(
                        text = t("dash.local.note"),
                        fontSize = 11.sp,
                        color = SampThemeColors.textSecondary
                    )
                }

                /*
                 * Section 2: LAN Wi-Fi (Multiplayer Bareng Teman)
                 */
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
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = SampThemeColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = t("dash.lan.note"),
                        fontSize = 11.sp,
                        color = SampThemeColors.textMuted
                    )
                }
            }
        }

        /*
         * Database Card
         */
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenDatabase() },
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column {
                        Text(
                            text = "MariaDB (MySQL 5.7/8.0 Wire)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isDbRunning) "127.0.0.1:3306 (Active)" else "Stopped",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDbRunning) SampThemeColors.success else SampThemeColors.textMuted,
                            maxLines = 1
                        )
                    }
                }

                val dbStatusText = if (isDbRunning) t("db.running") else t("status.offline")
                val dbColor = if (isDbRunning) SampThemeColors.success else SampThemeColors.danger

                Text(
                    text = dbStatusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = dbColor,
                    maxLines = 1
                )
            }

            /*
             * Direct phpMyAdmin launch, enabled only while the server is up: the
             * page is served by MariaDB's local web stack.
             */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenDatabase() },
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.accent)
                ) {
                    Text(text = t("db.manage"), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showPhpMyAdmin = true },
                    enabled = isDbRunning,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = t("db.phpmyadmin"), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        /*
         * Specification Grid
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
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

        /*
         * Storage Permission Alert (if needed on Android 11+)
         */
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
                                context.startActivity(DirectoryManager.getManageStorageIntent(context))
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

        /*
         * Battery Optimization Alert (keeps server responsive in background when user opens SA-MP client)
         */
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

        /*
         * Background Stability & Anti-Disconnect Guide Card
         */
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

        /*
         * Gamemode Directory Card (Zero-Copy)
         */
        val hasCfg = remember(currentServerDir) { File(currentServerDir, "server.cfg").exists() }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
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
                            text = t("folder.gamemode") + " SA-MP",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textSecondary
                        )
                    }

                    val badgeColor = if (hasCfg) SampThemeColors.success else SampThemeColors.warn
                    val badgeText = if (hasCfg) "server.cfg OK" else "server.cfg"
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
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
                        fontSize = 12.sp,
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
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Path",
                            tint = SampThemeColors.accent,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { folderPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.accentOn),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = t("folder.pick"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            currentServerDir = DirectoryManager.resetToDefaultDirectory(context)
                            Toast.makeText(context, t("folder.reset.done"), Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.textSecondary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = t("folder.reset"), fontSize = 13.sp)
                    }
                }

                Text(
                    text = if (hasCfg) t("folder.has.cfg") else t("folder.no.cfg"),
                    fontSize = 12.sp,
                    color = if (hasCfg) SampThemeColors.textMuted else SampThemeColors.warn
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        /*
         * Main Action Button
         */
        val isBusy = status == Status.STARTING || status == Status.INITIALIZING || status == Status.STOPPING
        val isRunning = status == Status.RUNNING

        Button(
            onClick = {
                if (isRunning) onStopServer() else onStartServer()
            },
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRunning) SampThemeColors.danger else SampThemeColors.accent,
                contentColor = if (isRunning) Color.White else SampThemeColors.bg
            )
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = SampThemeColors.textPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) t("action.stop") else t("action.start"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = SampThemeColors.textSecondary,
                maxLines = 1
            )
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = SampThemeColors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = SampThemeColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
