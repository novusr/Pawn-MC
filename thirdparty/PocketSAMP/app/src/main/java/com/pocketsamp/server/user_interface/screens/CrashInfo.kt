package com.pocketsamp.server.user_interface.screens

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
import com.pocketsamp.server.Locales
import com.pocketsamp.server.crash_handler.Manager
import com.pocketsamp.server.crash_handler.Report
import com.pocketsamp.server.user_interface.theme.*

@Composable
fun _CrashInfo(
    initialCrashId: String? = null,
    isCrashDialog: Boolean = false,
    onRestartApp: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)
    var crashes by remember { mutableStateOf<List<Report>>(emptyList()) }
    var selectedReport by remember { mutableStateOf<Report?>(null) }
    var activeSubTab by remember { mutableStateOf(if (initialCrashId != null) 0 else 1) }

    fun refresh() {
        val list = Manager.getAllCrashes(context)
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
        Toast.makeText(context, t("crash.copied"), Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isCrashDialog) SampThemeColors.danger.copy(alpha = 0.15f) else SampThemeColors.card),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isCrashDialog) SampThemeColors.danger else SampThemeColors.cardBorder
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCrashDialog) t("crash.title.dialog") else t("crash.title"),
                        color = SampThemeColors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Text(
                        text = if (isCrashDialog) t("crash.desc.dialog") else t("crash.desc"),
                        color = SampThemeColors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = SampThemeColors.surface,
            contentColor = SampThemeColors.accent,
            divider = {}
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { activeSubTab = 0 },
                text = { Text(t("crash.tab.detail"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = activeSubTab == 1,
                onClick = { activeSubTab = 1 },
                text = {
                    Text(
                        t("crash.tab.history").replace("{count}", crashes.size.toString()),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }

        Box(modifier = Modifier.weight(1f).padding(bottom = 52.dp)) {
            if (activeSubTab == 0) {
                if (selectedReport != null) {
                    CrashDetailView(
                        report = selectedReport!!,
                        isCrashDialog = isCrashDialog,
                        lang = lang,
                        onCopy = { copyToClipboard(Manager.formatForClipboard(selectedReport!!)) },
                        onRestart = onRestartApp,
                        onDelete = {
                            Manager.deleteCrash(context, selectedReport!!.id)
                            refresh()
                            activeSubTab = 1
                        }
                    )
                } else {
                    EmptyCrashState(message = t("crash.empty.detail"))
                }
            } else {
                if (crashes.isNotEmpty()) {
                    CrashHistoryList(
                        crashes = crashes,
                        lang = lang,
                        selectedId = selectedReport?.id,
                        onSelect = { report ->
                            selectedReport = report
                            activeSubTab = 0
                        },
                        onCopy = { report ->
                            copyToClipboard(Manager.formatForClipboard(report))
                        },
                        onDelete = { report ->
                            Manager.deleteCrash(context, report.id)
                            refresh()
                        },
                        onClearAll = {
                            Manager.clearAllCrashes(context)
                            refresh()
                        }
                    )
                } else {
                    EmptyCrashState(message = t("crash.empty.history"))
                }
            }
        }
    }
}

@Composable
private fun CrashDetailView(
    report: Report,
    isCrashDialog: Boolean,
    lang: Locales.Lang,
    onCopy: () -> Unit,
    onRestart: (() -> Unit)?,
    onDelete: () -> Unit
) {
    fun t(key: String): String = Locales.text(lang, key)
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                Text(t("crash.copy"), fontWeight = FontWeight.Bold)
            }

            if (isCrashDialog && onRestart != null) {
                Button(
                    onClick = onRestart,
                    colors = ButtonDefaults.buttonColors(containerColor = SampThemeColors.success, contentColor = SampThemeColors.bg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(t("crash.reopen"), fontWeight = FontWeight.Bold)
                }
            } else {
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SampThemeColors.danger),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SampThemeColors.danger.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(t("crash.delete"), color = SampThemeColors.danger)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = report.exceptionClass,
                    color = SampThemeColors.danger,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                report.message?.let { msg ->
                    Text(
                        text = msg,
                        color = SampThemeColors.textPrimary,
                        fontSize = 12.sp
                    )
                }
                HorizontalDivider(color = SampThemeColors.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        t("crash.time").replace("{value}", report.formattedDate),
                        color = SampThemeColors.textSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        t("crash.thread").replace("{value}", report.threadName),
                        color = SampThemeColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SampThemeColors.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(t("crash.device"), color = SampThemeColors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = report.deviceInfo,
                    color = SampThemeColors.textSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(t("crash.stack"), color = SampThemeColors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    crashes: List<Report>,
    selectedId: String?,
    lang: Locales.Lang,
    onSelect: (Report) -> Unit,
    onCopy: (Report) -> Unit,
    onDelete: (Report) -> Unit,
    onClearAll: () -> Unit
) {
    fun t(key: String): String = Locales.text(lang, key)
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
                text = t("crash.total").replace("{count}", crashes.size.toString()),
                color = SampThemeColors.textSecondary,
                fontSize = 14.sp
            )
            TextButton(
                onClick = onClearAll,
                colors = ButtonDefaults.textButtonColors(contentColor = SampThemeColors.danger)
            ) {
                Text(t("crash.delete.all"), fontSize = 14.sp)
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = report.exceptionClass.substringAfterLast('.'),
                            color = SampThemeColors.danger,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = report.message
                                ?: t("crash.thread").replace("{value}", report.threadName),
                            color = SampThemeColors.textSecondary,
                            fontSize = 14.sp,
                            maxLines = 2
                        )
                    }
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
