package com.pocketsamp.server.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.pocketsamp.server.crash.CrashManager
import com.pocketsamp.server.crash.CrashReport
import com.pocketsamp.server.ui.theme.*

@Composable
fun CrashLogsScreen(
    initialCrashId: String? = null,
    isCrashActivity: Boolean = false,
    onRestartApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var crashes by remember { mutableStateOf<List<CrashReport>>(emptyList()) }
    var selectedReport by remember { mutableStateOf<CrashReport?>(null) }
    var activeSubTab by remember { mutableStateOf(if (initialCrashId != null) 0 else 1) }

    fun refresh() {
        val list = CrashManager.getAllCrashes(context)
        crashes = list
        if (selectedReport == null || list.none { it.id == selectedReport?.id }) {
            selectedReport = if (initialCrashId != null) {
                list.find { it.id == initialCrashId } ?: list.firstOrNull()
            } else {
                list.firstOrNull()
            }
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    fun copyToClipboard(text: String, label: String = "Crash Log") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Log crash berhasil disalin ke clipboard!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Warning Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isCrashActivity) SampThemeColors.danger.copy(alpha = 0.15f) else SampThemeColors.card),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isCrashActivity) SampThemeColors.danger else SampThemeColors.cardBorder
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCrashActivity) "Aplikasi Mengalami Crash (Ditahan)" else "Sistem Pemantau Crash & Error",
                        color = SampThemeColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCrashActivity)
                            "Proses ditahan agar Anda dapat menyalin log error sebelum restart."
                        else
                            "Semua riwayat crash sistem tersimpan di sini untuk analisis.",
                        color = SampThemeColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Sub-tabs: Detail / Riwayat
        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = SampThemeColors.surface,
            contentColor = SampThemeColors.accent,
            divider = {}
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text("Detail Crash", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = { Text("Riwayat (${crashes.size})", fontWeight = FontWeight.SemiBold) }
            )
        }

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (activeSubTab == 0) {
                // Detail Tab
                if (selectedReport != null) {
                    CrashDetailView(
                        report = selectedReport!!,
                        isCrashActivity = isCrashActivity,
                        onCopy = { copyToClipboard(CrashManager.formatForClipboard(selectedReport!!)) },
                        onRestart = onRestartApp,
                        onDelete = {
                            CrashManager.deleteCrash(context, selectedReport!!.id)
                            refresh()
                            activeSubTab = 1
                        }
                    )
                } else {
                    EmptyCrashState(message = "Pilih crash dari tab Riwayat untuk melihat detail.")
                }
            } else {
                // History List Tab
                if (crashes.isNotEmpty()) {
                    CrashHistoryList(
                        crashes = crashes,
                        selectedId = selectedReport?.id,
                        onSelect = { report ->
                            selectedReport = report
                            activeSubTab = 0
                        },
                        onCopy = { report ->
                            copyToClipboard(CrashManager.formatForClipboard(report))
                        },
                        onDelete = { report ->
                            CrashManager.deleteCrash(context, report.id)
                            refresh()
                        },
                        onClearAll = {
                            CrashManager.clearAllCrashes(context)
                            refresh()
                        }
                    )
                } else {
                    EmptyCrashState(message = "Belum ada riwayat crash yang tercatat. Sistem berjalan stabil!")
                }
            }
        }
    }
}

@Composable
private fun CrashDetailView(
    report: CrashReport,
    isCrashActivity: Boolean,
    onCopy: () -> Unit,
    onRestart: (() -> Unit)?,
    onDelete: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onCopy,
                colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.accent, contentColor = SampThemeColors.bg),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("Salin Log Crash", fontWeight = FontWeight.Bold)
            }

            if (isCrashActivity && onRestart != null) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.success, contentColor = SampThemeColors.bg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Buka Ulang App", fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.danger),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SampThemeColors.danger.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hapus", color = SampThemeColors.danger)
                }
            }
        }

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = report.exceptionClass,
                    color = SampThemeColors.danger,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                report.message?.let { msg ->
                    Text(
                        text = msg,
                        color = SampThemeColors.textPrimary,
                        fontSize = 13.sp
                    )
                }
                Divider(color = SampThemeColors.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Waktu: ${report.formattedDate}", color = SampThemeColors.textSecondary, fontSize = 12.sp)
                    Text("Thread: ${report.threadName}", color = SampThemeColors.textSecondary, fontSize = 12.sp)
                }
            }
        }

        // Device Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Informasi Perangkat", color = SampThemeColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = report.deviceInfo,
                    color = SampThemeColors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Stack Trace Box
        Text("Stack Trace Lengkap:", color = SampThemeColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SampThemeColors.terminal)
                .border(1.dp, SampThemeColors.cardBorder, RoundedCornerShape(8.dp))
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            Text(
                text = report.stackTrace,
                color = SampThemeColors.danger.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun CrashHistoryList(
    crashes: List<CrashReport>,
    selectedId: String?,
    onSelect: (CrashReport) -> Unit,
    onCopy: (CrashReport) -> Unit,
    onDelete: (CrashReport) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total Log Tersimpan: ${crashes.size}",
                color = SampThemeColors.textSecondary,
                fontSize = 13.sp
            )
            TextButton(
                onClick = onClearAll,
                colors = ButtonDefaults.textButtonColors(contentColor = SampThemeColors.danger)
            ) {
                Text("Hapus Semua", fontSize = 13.sp)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(crashes) { report ->
                val isSelected = report.id == selectedId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(report) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) SampThemeColors.card.copy(alpha = 0.9f) else SampThemeColors.surface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SampThemeColors.accent else SampThemeColors.cardBorder
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = report.formattedDate,
                            color = SampThemeColors.warn,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = report.exceptionClass.substringAfterLast('.'),
                            color = SampThemeColors.danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = report.message ?: "Thread: ${report.threadName}",
                            color = SampThemeColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
            }
        }
    }
}

@Composable
private fun EmptyCrashState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = message,
                color = SampThemeColors.textSecondary,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
