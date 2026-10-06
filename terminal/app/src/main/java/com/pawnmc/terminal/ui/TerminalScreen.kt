package com.pawnmc.terminal.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pawnmc.terminal.R
import com.pawnmc.terminal.shell.TerminalBackend
import com.pawnmc.terminal.shell.TerminalService
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import java.util.Properties

/**
 * The interactive terminal.
 *
 * Renders the live [TerminalSession] through the vendored Termux view and adds the two
 * controls a phone needs: a key row for the characters a soft keyboard hides, and the
 * colour palette of the editor, so the terminal does not look like a separate app.
 */
@Composable
internal fun TerminalScreen(
    service: TerminalService,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val background = MaterialTheme.colorScheme.surface.toArgb()
    val foreground = MaterialTheme.colorScheme.onSurface.toArgb()
    val palette = remember(isDark) { terminalColors(isDark) }

    val backend = remember { TerminalBackend() }
    var activeSessionId by remember { mutableStateOf(service.activeSessionId.value) }
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
    val transcript: () -> String = {
        service.existingSession(activeSessionId)?.emulator?.screen?.transcriptText.orEmpty()
    }

    // The backend reaches the widget and the clipboard through TerminalSurface because
    // the Termux callbacks cannot receive them as parameters.
    DisposableEffect(context) {
        TerminalSurface.clipboard = context.getSystemService(ClipboardManager::class.java)
        onDispose { TerminalSurface.clipboard = null }
    }

    // A finished session is restarted on Enter, which is what the "[Process completed]"
    // banner instructs the user to do. Dropping the dead session makes Enter create a
    // fresh one on the next composition.
    LaunchedEffect(service, activeSessionId) {
        TerminalSurface.onSessionExited = { finished ->
            if (finished === service.existingSession(activeSessionId)) {
                service.terminate(activeSessionId)
                activeSessionId = TerminalService.DEFAULT_SESSION_ID
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().imePadding()) {
        TerminalHeader(
            title = stringResource(R.string.terminal_title),
            subtitle = stringResource(R.string.terminal_subtitle),
            onNewSession = {
                activeSessionId = TerminalSurface.requestNewSession(service)
            },
            onClearScreen = {
                // 0x0C is form feed: everything on screen is dropped while the running
                // command keeps going.
                service.existingSession(activeSessionId)?.write("\u000c")
            },
            onCopyOutput = {
                val output = transcript()
                if (output.isBlank()) {
                    Toast.makeText(context, R.string.terminal_log_empty, Toast.LENGTH_SHORT).show()
                } else {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                        ClipData.newPlainText("Terminal output", output)
                    )
                    Toast.makeText(context, R.string.terminal_log_copied, Toast.LENGTH_SHORT).show()
                }
            },
            onDownloadOutput = {
                val output = transcript()
                if (output.isBlank()) {
                    Toast.makeText(context, R.string.terminal_log_empty, Toast.LENGTH_SHORT).show()
                } else {
                    pendingLogText = output
                    downloadLogLauncher.launch("pawnmc-terminal.log")
                }
            },
            onClose = onClose,
        )

        TerminalHost(
            background = background,
            foreground = foreground,
            palette = palette,
            service = service,
            sessionId = activeSessionId,
            backend = backend,
            modifier = Modifier.weight(1f),
        )

        TerminalKeyRow(session = service.existingSession(activeSessionId))
    }
}

/**
 * Hosts the Termux widget.
 *
 * The view is created once and kept: recreating it on every recomposition would detach
 * and re-attach the session, which resets the screen contents.
 */
@Composable
private fun TerminalHost(
    background: Int,
    foreground: Int,
    palette: Properties,
    service: TerminalService,
    sessionId: String,
    backend: TerminalBackend,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
        factory = { context ->
            TerminalView(context, null).apply {
                TerminalSurface.view = this
                setTextSize(DEFAULT_TEXT_SIZE)
                // A monospace face keeps ANSI box drawing aligned; without it apt's
                // progress tables and `ls` columns drift apart.
                setTypeface(Typeface.MONOSPACE)
                attachSession(service.session(sessionId, backend))
                setTerminalViewClient(backend)
                applyColors(background, foreground, palette)
                keepScreenOn = true
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                setOnClickListener { TerminalSurface.showSoftKeyboard() }
                requestFocus()
            }
        },
        update = { view ->
            // Re-attach only when the user switched sessions; the view is otherwise
            // already showing the right one, and re-attaching would clear the screen.
            val desired = service.session(sessionId, backend)
            if (view.mTermSession?.mHandle != desired.mHandle) {
                desired.updateTerminalSessionClient(backend)
                view.attachSession(desired)
                view.setTerminalViewClient(backend)
            }
            view.applyColors(background, foreground, palette)
            if (view.mEmulator != null && !view.hasFocus()) view.requestFocus()
        },
        onRelease = { view -> TerminalSurface.detach(view) },
    )
}

@Composable
private fun TerminalHeader(
    title: String,
    subtitle: String,
    onNewSession: () -> Unit,
    onClearScreen: () -> Unit,
    onCopyOutput: () -> Unit,
    onDownloadOutput: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNewSession) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.terminal_session_new),
                )
            }
            IconButton(onClick = onClearScreen) {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = stringResource(R.string.terminal_clear),
                )
            }
            IconButton(onClick = onCopyOutput) {
                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.terminal_copy_output))
            }
            IconButton(onClick = onDownloadOutput) {
                Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.terminal_download_output))
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.terminal_close),
                )
            }
        }
    }
}

/**
 * Characters and control sequences the soft keyboard cannot produce.
 *
 * Each button writes straight to the session's stdin, so it works for whatever is
 * currently reading input — including an `apt` prompt waiting for a `y`.
 */
@Composable
private fun TerminalKeyRow(session: TerminalSession?) {
    val scroll = rememberScrollState()

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // These are terminal-specific helpers and intentionally not tied to the editor's
            // quick-symbol toggle. The editor may hide its own bar, but the sandbox keeps its
            // own control strip so the shell remains usable when the user leaves the editor.
            KeyButton(label = "CTRL") { session?.write("\u0001") }
            KeyButton(label = "ALT") { session?.write(ESCAPE) }
            KeyButton(label = "SHIFT") { session?.write("\u001b[1;2") }

            KeyButton(label = "CTRL+C") { session?.write(CONTROL_C) }
            KeyButton(label = "CTRL+D") { session?.write("\u0004") }
            KeyButton(label = "CTRL+Z") { session?.write("\u001a") }
            KeyButton(label = "CTRL+L") { session?.write("\u000c") }
            KeyButton(label = "ESC") { session?.write(ESCAPE) }
            KeyButton(label = "TAB") { session?.write("\t") }
            KeyButton(label = "ENTER") { session?.write("\r") }
            KeyButton(label = "DEL") { session?.write("\u007f") }

            KeyButton(label = "\u2191") { session?.write("$ESCAPE[A") }
            KeyButton(label = "\u2193") { session?.write("$ESCAPE[B") }
            KeyButton(label = "\u2190") { session?.write("$ESCAPE[D") }
            KeyButton(label = "\u2192") { session?.write("$ESCAPE[C") }

            SHELL_SYMBOLS.forEach { symbol ->
                KeyButton(label = symbol) { session?.write(symbol) }
            }
        }
    }
}

@Composable
private fun KeyButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * The ANSI palette, matched to the editor's light or dark colour scheme.
 *
 * Only the 16 base colours are set here. The foreground/background/cursor defaults are
 * left to `updateWith`, which derives a cursor colour with enough contrast against the
 * background — overriding them here would break that contrast calculation.
 */
private fun terminalColors(isDark: Boolean): Properties {
    val scheme =
        if (isDark) {
            mapOf(
                "color0" to "#1E1F22",
                "color1" to "#F07178",
                "color2" to "#7ECE5A",
                "color3" to "#FFCB6B",
                "color4" to "#82AAFF",
                "color5" to "#C792EA",
                "color6" to "#89DDFF",
                "color7" to "#D0D0D0",
                "color8" to "#5C6370",
                "color9" to "#FF8B92",
                "color10" to "#A5E075",
                "color11" to "#FFD580",
                "color12" to "#9CC4FF",
                "color13" to "#DDB0F3",
                "color14" to "#A7F2FF",
                "color15" to "#FFFFFF",
            )
        } else {
            mapOf(
                "color0" to "#1E1F22",
                "color1" to "#C62828",
                "color2" to "#2E7D32",
                "color3" to "#B26A00",
                "color4" to "#1565C0",
                "color5" to "#6A1B9A",
                "color6" to "#00838F",
                "color7" to "#4A4A4A",
                "color8" to "#6E6E6E",
                "color9" to "#D32F2F",
                "color10" to "#388E3C",
                "color11" to "#EF6C00",
                "color12" to "#1976D2",
                "color13" to "#7B1FA2",
                "color14" to "#0097A7",
                "color15" to "#000000",
            )
        }

    return Properties().apply {
        putAll(scheme)
    }
}

/**
 * Pushes the palette into the emulator.
 *
 * `TerminalColors.COLOR_SCHEME` is process-wide static state, so this must be re-applied
 * whenever the widget is created or the theme changes, and the emulator's own current
 * colours have to be reset afterwards so a shell that already changed them with an OSC 4
 * sequence does not keep the old values.
 */
private fun TerminalView.applyColors(background: Int, foreground: Int, palette: Properties) {
    TerminalColors.COLOR_SCHEME.updateWith(palette)
    mEmulator?.mColors?.reset()
    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, foreground)
        set(TextStyle.COLOR_INDEX_BACKGROUND, background)
        set(TextStyle.COLOR_INDEX_CURSOR, foreground)
    }
    onScreenUpdated()
    invalidate()
}

/** Termux font sizes are in pixels, not sp. */
private const val DEFAULT_TEXT_SIZE = 30

private const val ESCAPE = "\u001b"
private const val CONTROL_C = "\u0003"

/** Punctuation a Pawn session needs constantly but the soft keyboard buries. */
private val SHELL_SYMBOLS = listOf("/", "-", "_", "|", "&", "*", "~", "$", "\"", "'", ":")
