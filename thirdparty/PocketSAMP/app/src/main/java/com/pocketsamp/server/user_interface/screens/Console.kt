package com.pocketsamp.server.user_interface.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
fun _Console(
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

        /*
         * Simple console card: one flat semi-dark surface, no decorative
         * chrome. A slim scrollbar rides the right edge so long logs can be
         * dragged through without flinging blind.
         */
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 52.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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
                        itemsIndexed(logs) { _, line ->
                            val lineColor = when {
                                line.contains("error", ignoreCase = true) || line.contains("failed", ignoreCase = true) -> SampThemeColors.danger
                                line.contains("loaded", ignoreCase = true) || line.contains("success", ignoreCase = true) -> SampThemeColors.success
                                line.contains("warning", ignoreCase = true) -> SampThemeColors.warn
                                line.contains("\$System") -> SampThemeColors.accent
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

            // Slim scrollbar riding the right edge of the log surface.
            val layoutInfo = listState.layoutInfo
            val totalCount = layoutInfo.totalItemsCount
            val visibleCount = layoutInfo.visibleItemsInfo.size
            if (totalCount > visibleCount && visibleCount > 0) {
                val first = layoutInfo.visibleItemsInfo.first().index
                val scrollRange = (totalCount - visibleCount).coerceAtLeast(1)
                val progress = (first.toFloat() / scrollRange).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .padding(start = 4.dp, top = 12.dp, bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.weight(progress.coerceAtLeast(0.001f))
                    ) {}
                    Box(
                        modifier = Modifier
                            .weight(0.08f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SampThemeColors.textMuted.copy(alpha = 0.45f))
                    ) {}
                    Box(
                        modifier = Modifier.weight((1f - progress - 0.08f).coerceAtLeast(0.001f))
                    ) {}
                }
            }
        }
    }
}
