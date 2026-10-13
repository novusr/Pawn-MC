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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
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
    onStopServer: () -> Unit
) {
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
                Toast.makeText(context, "Folder gamemode diubah ke: ${dir.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Gagal mendapatkan path folder yang dipilih.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PocketSAMP",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Local SA-MP & MySQL Portable Runtime",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            // Status Indicator Pill
            val (statusText, statusColor) = when (status) {
                ServerStatus.RUNNING -> "ONLINE" to StatusGreen
                ServerStatus.STARTING, ServerStatus.INITIALIZING -> "STARTING" to AccentOrange
                ServerStatus.STOPPING -> "STOPPING" to AccentOrange
                ServerStatus.STOPPED -> "OFFLINE" to StatusRed
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        // Server Address Card (Dual Localhost & LAN Wi-Fi)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Localhost (Main di HP Ini - Disarankan)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AccentCyan.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
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
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Main di HP Ini (Satu HP)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SA-MP Server IP", "127.0.0.1:7777")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Disalin: 127.0.0.1:7777", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Localhost IP",
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "127.0.0.1:7777",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    Text(
                        text = "⭐ Sangat Disarankan: Koneksi internal langsung (0ms ping), tidak bergantung sinyal Wi-Fi, dan anti-putus saat beralih/membuka game.",
                        fontSize = 11.sp,
                        color = TextSecondary
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
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Main Bareng Teman (Satu Wi-Fi)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("SA-MP Server IP", "$lanIp:7777")
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Disalin: $lanIp:7777", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy LAN IP",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "$lanIp:7777",
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )

                    Text(
                        text = "Bagikan alamat ini jika teman Anda ingin bergabung dari HP lain via Wi-Fi/Hotspot yang sama.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Database Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
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
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "MySQL",
                        tint = AccentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "MariaDB (MySQL 5.7/8.0 Wire)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isDbRunning) "127.0.0.1:3306 (Active)" else "Stopped",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDbRunning) StatusGreen else TextMuted
                        )
                    }
                }

                val dbStatusText = if (isDbRunning) "RUNNING" else "OFFLINE"
                val dbColor = if (isDbRunning) StatusGreen else StatusRed

                Text(
                    text = dbStatusText,
                    fontSize = 11.sp,
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
                colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentOrange)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Izin Akses File Diperlukan",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                    }

                    Text(
                        text = "Android memerlukan izin 'Akses Semua File' agar server dapat membaca folder gamemode Anda secara langsung.",
                        fontSize = 11.sp,
                        color = TextPrimary
                    )

                    Button(
                        onClick = {
                            try {
                                context.startActivity(ServerDirectoryManager.getManageStorageIntent(context))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Buka Pengaturan -> Aplikasi -> PocketSAMP -> Izin", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = "Beri Izin Akses File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Battery Optimization Alert (keeps server responsive in background when user opens SA-MP client)
        if (!isBatteryOptIgnored) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AccentCyan.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AccentCyan)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Stabilkan Proses Latar Belakang",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }

                    Text(
                        text = "Nonaktifkan optimasi baterai agar Android tidak membatasi/menjeda server saat Anda membuka game/launcher SA-MP.",
                        fontSize = 11.sp,
                        color = TextPrimary
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Text(text = "Nonaktifkan Optimasi Baterai", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Background Stability & Anti-Disconnect Guide Card
        var showAntiDcGuide by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (showAntiDcGuide) AccentCyan else SurfaceCardBorder)
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
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Panduan Anti-Disconnect (HyperOS / Android 14)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    TextButton(onClick = { showAntiDcGuide = !showAntiDcGuide }) {
                        Text(
                            text = if (showAntiDcGuide) "Tutup" else "Solusi HP",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showAntiDcGuide) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "1. Gembok Aplikasi di Recent Apps (Penting di Xiaomi/HyperOS):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text(
                            text = "Buka menu Recent Apps (Multitask HP) -> Tekan & tahan jendela PocketSAMP -> Ketuk ikon Gembok (Lock). Ini mencegah OS membekukan server saat Anda membuka game full screen.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Text(
                            text = "2. Gunakan IP 127.0.0.1:7777:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text(
                            text = "Jika bermain di HP ini, masukkan 127.0.0.1:7777 di launcher SA-MP Anda. Jalur loopback internal ini tidak pernah diputus oleh Wi-Fi sleep Android.",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )

                        Text(
                            text = "3. Matikan Pembatasan Proses Turunan (Android 12+):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                        Text(
                            text = "Buka Pengaturan HP > Opsi Pengembang (Developer Options) > Aktifkan 'Nonaktifkan pembatasan proses turunan' (Disable child process restrictions) agar proses server QEMU tidak dibunuh sistem (Exit 137).",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    Text(
                        text = "Jika sering muncul 'Lost Connection' saat beralih ke game full screen, ketuk 'Solusi HP' untuk langkah penstabilan.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }
        }

        // Gamemode Directory Card (Zero-Copy)
        val hasCfg = remember(currentServerDir) { File(currentServerDir, "server.cfg").exists() }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Folder Gamemode",
                            tint = AccentCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Folder Gamemode SA-MP",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }

                    val badgeColor = if (hasCfg) StatusGreen else AccentOrange
                    val badgeText = if (hasCfg) "server.cfg OK" else "server.cfg ?"
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
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
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Server Path", currentServerDir.absolutePath)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Path disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Path",
                            tint = AccentCyan,
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
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan, contentColor = BgDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Pilih Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            currentServerDir = ServerDirectoryManager.resetToDefaultDirectory(context)
                            Toast.makeText(context, "Direset ke default PocketSAMP", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text(text = "Reset Default", fontSize = 12.sp)
                    }
                }

                Text(
                    text = if (hasCfg) {
                        "Server akan membaca server.cfg & gamemode Anda langsung dari folder ini."
                    } else {
                        "server.cfg belum ditemukan di folder ini. Letakkan gamemode Anda di sini atau simpan setting di tab Config."
                    },
                    fontSize = 11.sp,
                    color = if (hasCfg) TextMuted else AccentOrange
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
                containerColor = if (isRunning) StatusRed else AccentCyan,
                contentColor = if (isRunning) Color.White else BgDark
            )
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = TextPrimary,
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
                    text = if (isRunning) "STOP SERVER" else "START SERVER",
                    fontSize = 16.sp,
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
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = AccentCyan
            )
            Text(
                text = subtext,
                fontSize = 11.sp,
                color = TextMuted
            )
        }
    }
}
