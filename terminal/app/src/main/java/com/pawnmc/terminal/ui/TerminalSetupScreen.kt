package com.pawnmc.terminal.ui
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pawnmc.terminal.R
import com.pawnmc.terminal.core.DownloadProgress
import com.pawnmc.terminal.core.SetupState

/**
 * Progress of the first-run sandbox install.
 *
 * The first launch downloads roughly 30 MB and then unpacks it; both stages are shown
 * because a silent screen for a minute looks like a hang. The extraction itself is not
 * cancellable — a half-unpacked rootfs is worse than a slow one — but the screen always
 * offers a way out, because a failure with only a Retry button left users stuck with no
 * obvious escape other than the system Back gesture.
 */
@Composable
internal fun TerminalSetupScreen(
    state: SetupState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingLogText by remember { mutableStateOf("") }
    val downloadLogLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(pendingLogText)
                } != null
            }.getOrDefault(false)
            Toast.makeText(
                context,
                if (saved) R.string.terminal_log_saved else R.string.terminal_log_save_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is SetupState.Failed -> {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.terminal_setup_failed),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                state.message?.takeIf { it.isNotBlank() }?.let { message ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                state.details?.takeIf { it.isNotBlank() }?.let { details ->
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.terminal_setup_failed), modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText("Sandbox error", details)
                            )
                            Toast.makeText(context, R.string.terminal_log_copied, Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.terminal_copy_output))
                        }
                        IconButton(onClick = {
                            pendingLogText = details
                            downloadLogLauncher.launch("pawnmc-sandbox-error.log")
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.terminal_download_output))
                        }
                    }
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(0.9f),
                    ) {
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .background(Color.Transparent),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.terminal_retry))
                    }
                    OutlinedButton(onClick = onClose) {
                        Text(stringResource(R.string.terminal_close))
                    }
                }
            }

            SetupState.Extracting -> {
                Progress(label = stringResource(R.string.terminal_extracting), fraction = null)
            }

            is SetupState.Downloading -> {
                Progress(
                    label = stringResource(R.string.terminal_downloading),
                    fraction = state.progress.fraction,
                    progress = state.progress,
                )
            }

            SetupState.Checking -> {
                Progress(label = stringResource(R.string.terminal_preparing), fraction = null)
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.terminal_setup_keep_open),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // Shown while work is in flight, so the screen is escapable even if a stage never
        // completes — a stalled download would otherwise leave no way out at all.
        if (state !is SetupState.Failed) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onClose) {
                Text(stringResource(R.string.terminal_close))
            }
        }
    }
}

@Composable
private fun Progress(
    label: String,
    fraction: Float?,
    progress: DownloadProgress? = null,
) {    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))

    if (fraction == null) {
        // The server did not send a Content-Length, or the step has no measurable
        // progress: an indeterminate bar is more honest than a fake percentage.
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(0.8f),
        )
        progress?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${megabytes(it.downloadedBytes)} / ${megabytes(it.totalBytes)} MB",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun megabytes(bytes: Long): String = "%.1f".format(bytes / (1024.0 * 1024.0))
