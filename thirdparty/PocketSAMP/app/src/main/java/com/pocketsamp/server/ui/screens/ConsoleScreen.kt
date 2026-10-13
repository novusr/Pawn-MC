package com.pocketsamp.server.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.ui.theme.*

@Composable
fun ConsoleScreen(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val context = LocalContext.current
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
            .background(BgDark)
            .padding(16.dp),
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
                    color = TextPrimary
                )
                Text(
                    text = "${logs.size} log lines buffered",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear logs",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, logs.joinToString("\n"))
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Server Log"))
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share logs",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Terminal Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF07080B))
                .padding(12.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Server console is idle. Press 'Start Server' to view live execution logs.",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextMuted,
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
                            line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> StatusRed
                            line.contains("loaded", ignoreCase = true) || line.contains("success", ignoreCase = true) -> StatusGreen
                            line.contains("warning", ignoreCase = true) -> AccentOrange
                            line.startsWith("[SA-MP]") -> AccentCyan
                            line.startsWith("[MariaDB]") -> Color(0xFFE040FB)
                            else -> TextSecondary
                        }

                        Text(
                            text = line,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = lineColor,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
