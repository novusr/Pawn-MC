package com.pocketsamp.server.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.PocketSampLocales
import com.pocketsamp.server.engine.MariaDbDownloader
import com.pocketsamp.server.ui.theme.SampThemeColors
import kotlinx.coroutines.launch

/**
 * Installs a MariaDB bundle the user points the app at.
 *
 * This exists because the bundled asset may be incomplete and MariaDB cannot be
 * produced on the device. The dialog is deliberately explicit about the two things
 * that make a download work: an **arm64-v8a** Linux build, and the archive layout
 * `bin/mariadbd` + `lib/` + `share/mariadb/`. A distro package from a phone browser
 * satisfies neither, so the hint says so before the user wastes a transfer.
 */
@Composable
fun MariaDbDownloadDialog(
    onDismiss: () -> Unit,
    onInstalled: () -> Unit,
    lang: PocketSampLocales.Lang = PocketSampLocales.current
) {
    fun t(key: String): String = PocketSampLocales.text(lang, key)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloader = remember { MariaDbDownloader(context) }

    var url by remember { mutableStateOf("") }
    var sha256 by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var progressLine by remember { mutableStateOf("") }
    var progressFraction by remember { mutableStateOf<Float?>(null) }
    var result by remember { mutableStateOf<MariaDbDownloader.Result?>(null) }

    fun start() {
        val target = url.trim()
        if (target.isEmpty() || isDownloading) return
        isDownloading = true
        result = null
        progressLine = t("db.download.connecting")
        progressFraction = 0f
        scope.launch {
            val outcome = downloader.downloadAndInstall(
                url = target,
                sha256 = sha256.trim().takeIf { it.isNotEmpty() }
            ) { line ->
                progressLine = line
                // Only the transfer line carries a percentage. Every other line
                // ("Extracting bundle...", "Verifying SHA-256...") is indeterminate, so
                // the bar switches back to a spinner instead of freezing at the last
                // download percentage.
                val match = Regex("(\\d{1,3})%").find(line)
                progressFraction = match?.groupValues?.get(1)?.toIntOrNull()?.div(100f)
            }
            isDownloading = false
            result = outcome
            progressFraction = if (outcome.success) 1f else null
            if (outcome.success) onInstalled()
        }
    }

    val closeLabel = if (result?.success == true) t("perm.close") else t("db.download.cancel")

    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = SampThemeColors.card,
        titleContentColor = SampThemeColors.textPrimary,
        textContentColor = SampThemeColors.textSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = SampThemeColors.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = t("db.download.title"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("mariadb_download_dialog"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = t("db.download.desc"),
                    fontSize = 12.sp,
                    color = SampThemeColors.textSecondary
                )

                // What the archive must contain — the vocabulary the validator uses.
                Text(
                    text = t("db.download.layout"),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SampThemeColors.accent
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mariadb_download_url"),
                    enabled = !isDownloading,
                    singleLine = true,
                    label = { Text(t("db.download.url"), fontSize = 13.sp) },
                    placeholder = { Text("https://…/mariadb_arm64.tar.gz", fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = SampThemeColors.textPrimary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = url.isNotBlank() && !url.trim().startsWith("https://"),
                    supportingText = if (url.isNotBlank() && !url.trim().startsWith("https://")) {
                        { Text(t("db.download.https"), fontSize = 11.sp, color = SampThemeColors.danger) }
                    } else null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SampThemeColors.accent,
                        unfocusedBorderColor = SampThemeColors.cardBorder,
                        focusedContainerColor = SampThemeColors.bg,
                        unfocusedContainerColor = SampThemeColors.bg
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = sha256,
                    onValueChange = { sha256 = it.trim() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mariadb_download_sha256"),
                    enabled = !isDownloading,
                    singleLine = true,
                    label = { Text(t("db.download.sha"), fontSize = 13.sp) },
                    placeholder = { Text(t("db.download.sha.optional"), fontSize = 12.sp) },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = SampThemeColors.textPrimary
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SampThemeColors.accent,
                        unfocusedBorderColor = SampThemeColors.cardBorder,
                        focusedContainerColor = SampThemeColors.bg,
                        unfocusedContainerColor = SampThemeColors.bg
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isDownloading) {
                    progressFraction?.let { fraction ->
                        LinearProgressIndicator(
                            progress = { fraction.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            color = SampThemeColors.accent
                        )
                    } ?: CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SampThemeColors.accent
                    )
                    Text(
                        text = progressLine,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = SampThemeColors.textSecondary
                    )
                }

                result?.let { outcome ->
                    Text(
                        text = outcome.message,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (outcome.success) SampThemeColors.success else SampThemeColors.danger
                    )
                }

                Text(
                    text = t("db.download.device"),
                    fontSize = 10.sp,
                    color = SampThemeColors.textMuted
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { start() },
                enabled = !isDownloading && url.trim().startsWith("https://")
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = t("db.download.start"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SampThemeColors.accent
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text(closeLabel, fontSize = 13.sp, color = SampThemeColors.textSecondary)
            }
        }
    )
}
