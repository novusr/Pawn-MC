package com.pocketsamp.server.user_interface.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
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
import com.pocketsamp.server.user_interface.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun _Console(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)
    val context = LocalContext.current

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

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ConsoleIconButton(Icons.Default.ContentCopy) {
                    val joined = logs.joinToString("\n")
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("PocketSAMP log", joined))
                    Toast.makeText(context, t("copy.done"), Toast.LENGTH_SHORT).show()
                }
                ConsoleIconButton(Icons.Default.Save) {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    val name = "pocketsamp-console-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt"
                    runCatching {
                        val out = File(dir, name)
                        out.parentFile?.mkdirs()
                        out.writeText(logs.joinToString("\n"))
                    }.onSuccess {
                        Toast.makeText(context, name, Toast.LENGTH_LONG).show()
                    }.onFailure {
                        Toast.makeText(context, t("files.operation.failed"), Toast.LENGTH_SHORT).show()
                    }
                }
                ConsoleIconButton(Icons.Default.Delete) { onClearLogs() }
            }
        }

        /*
         * Simple console card: one flat semi-dark surface, no decorative
         * chrome. A slim scrollbar rides the right edge so long logs can be
         * dragged through without flinging blind.
         */
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                        .width(14.dp)
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
                            .background(SampThemeColors.textMuted.copy(alpha = 0.55f))
                    ) {}
                    Box(
                        modifier = Modifier.weight((1f - progress - 0.08f).coerceAtLeast(0.001f))
                    ) {}
                }
            }
        }

        /*
         * Passive info panel: a second, "dead" console at the bottom that just
         * reports what the app is running on — device model, Android version,
         * PawnMC/PocketSAMP version and the current timestamp. Nothing here is
         * interactive; it exists so support requests come with context attached.
         */
        DeviceInfoConsole()
    }
}

@Composable
private fun ConsoleIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = SampThemeColors.textSecondary),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DeviceInfoConsole() {
    val lang = Locales.current
    fun t(key: String): String = Locales.text(lang, key)

    val details = listOf(
        "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "android" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})",
        "app" to "PocketSAMP module · PawnMC host",
        "abi" to Build.SUPPORTED_ABIS.joinToString("/"),
        "date" to SimpleDateFormat("EEEE, dd MMMM yyyy — HH:mm:ss z", Locale.getDefault()).format(Date())
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SampThemeColors.terminal.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        details.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SampThemeColors.textMuted
                )
                Text(
                    text = value,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SampThemeColors.textSecondary,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
