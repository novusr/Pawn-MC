package com.pocketsamp.server.user_interface.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.Locales
import com.pocketsamp.server.user_interface.theme.*

@Composable
fun ConsoleScreen(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.totalItemsCount }
            .distinctUntilChanged()
            .collectLatest { count ->
                if (count > 0) listState.scrollToItem(count - 1)
            }
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SampThemeColors.bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("console.title"),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SampThemeColors.textPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = t("console.buffered").replace("{count}", logs.size.toString()),
                        fontSize = 12.sp,
                        color = SampThemeColors.textSecondary,
                        maxLines = 1
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = onClearLogs,
                        colors = ButtonDefaults.textButtonColors(contentColor = SampThemeColors.textSecondary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = t("console.clear"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textSecondary
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SampThemeColors.terminal)
                    .padding(12.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = t("console.idle"),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SampThemeColors.textMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs, key = { it.hashCode().toString() + it.length }) { line ->
                        val lineColor = when {
                            line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> SampThemeColors.danger
                            line.contains("loaded", ignoreCase = true) || line.contains("success", ignoreCase = true) -> SampThemeColors.success
                            line.contains("warning", ignoreCase = true) -> SampThemeColors.warn
                            line.startsWith("[SA-MP]") -> SampThemeColors.accent
                            line.startsWith("[MariaDB]") -> MaterialTheme.colorScheme.secondary
                            else -> SampThemeColors.textSecondary
                        }

                        Text(
                            text = line,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = lineColor,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }
    }
}
