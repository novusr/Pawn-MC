package com.pocketsamp.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.pocketsamp.server.ui.theme.*

@Composable
fun ConsoleScreen(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Console Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Server Console",
                    fontSize = 20.sp,
                    color = SampThemeColors.textPrimary
                )
                Text(
                    text = "${logs.size} log lines buffered",
                    fontSize = 14.sp,
                    color = SampThemeColors.textSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onClearLogs,
                    colors = ButtonDefaults.textButtonColors(contentColor = SampThemeColors.textSecondary)
                ) {
                    Text(
                        text = "Clear",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SampThemeColors.textSecondary
                    )
                }
            }
        }

        // Terminal Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(SampThemeColors.terminal)
                .padding(16.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Server console is idle. Press 'Start Server' to view live execution logs.",
                    fontSize = 14.sp,
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
                    items(logs) { line ->
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
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = lineColor,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        }
    }
}
