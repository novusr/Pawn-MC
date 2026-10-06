package com.pawnmc.terminal

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.pawnmc.terminal.core.SandboxSetup.SandboxPreparationException
import com.pawnmc.terminal.core.SandboxSetup
import com.pawnmc.terminal.core.SetupState
import com.pawnmc.terminal.core.TerminalPaths
import com.pawnmc.terminal.shell.TerminalService
import com.pawnmc.terminal.ui.TerminalScreen
import com.pawnmc.terminal.ui.TerminalSetupScreen
import com.pawnmc.terminal.ui.TerminalSurface

/**
 * Session config the editor hands to the terminal when it opens.
 *
 * Kept as a data class so the caller can describe "open a shell in this folder with this
 * compiler selected" without knowing anything about proot.
 */
data class TerminalRequest(
    /** Device path of the folder the shell should start in, normally the open project. */
    val workingDirectory: String? = null,
    /** Library file name of the compiler to expose as `pawncc`, e.g. `libpawnc3107.so`. */
    val compilerLibraryName: String? = null,
)

/**
 * The terminal as the editor sees it: a full-screen overlay.
 *
 * It is deliberately an overlay rather than an activity. The editor owns the whole
 * window, and giving the terminal its own task would break the "back closes the
 * terminal, not the editor" expectation the floating panels already set.
 */
@Composable
fun TerminalOverlay(
    request: TerminalRequest,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var setupState by remember { mutableStateOf<SetupState>(SetupState.Checking) }
    var service by remember { mutableStateOf<TerminalService?>(null) }
    var attempt by remember { mutableStateOf(0) }

    // Bind once. The service outlives this composable so a compile started here keeps
    // running when the user returns to the editor.
    DisposableEffect(context) {
        val connection =
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val bound = (binder as? TerminalService.TerminalBinder)?.service()
                    if (bound != null) {
                        bound.requestedWorkingDirectory = request.workingDirectory
                        bound.requestedCompilerLibraryName = request.compilerLibraryName
                        service = bound
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }

        val bound = context.bindService(
            Intent(context, TerminalService::class.java),
            connection,
            Context.BIND_AUTO_CREATE,
        )

        onDispose { if (bound) runCatching { context.unbindService(connection) } }
    }

    // Prepare the sandbox before the first shell is started. `ensureReady` is a no-op
    // once the rootfs is in place, so this is cheap on every later open.
    LaunchedEffect(attempt) {
        if (TerminalPaths.isInstalled(context)) {
            setupState = SetupState.Checking
            return@LaunchedEffect
        }
        runCatching {
            SandboxSetup.ensureReady(context) { setupState = it }
        }.onFailure { error ->
            setupState = SetupState.Failed(
                message = SandboxSetup.failureMessage(context, error),
                details = SandboxSetup.failureDetails(
                    error = error,
                    rawOutput = (error as? SandboxPreparationException)?.rawOutput,
                ),
            )
        }
    }

    BackHandler(enabled = true) {
        // If Android/IME didn't consume Back first, the IME visibility is observable from
        // this composable and we ask it to dismiss rather than navigate away.
        if (imeVisible) {
            TerminalSurface.hideSoftKeyboard()
        } else {
            onClose()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize().zIndex(40f),
    ) {
        val ready = service

        when {
            setupState is SetupState.Failed -> {
                TerminalSetupScreen(
                    state = setupState,
                    onRetry = { attempt++ },
                    onClose = onClose,
                )
            }

            ready == null || !TerminalPaths.isInstalled(context) -> {
                TerminalSetupScreen(
                    state = setupState,
                    onRetry = { attempt++ },
                    onClose = onClose,
                )
            }

            else -> {
                // A shell started from a stale request would land in the wrong folder,
                // so re-apply it whenever the request changes.
                LaunchedEffect(ready, request) {
                    ready.requestedWorkingDirectory = request.workingDirectory
                    ready.requestedCompilerLibraryName = request.compilerLibraryName
                }
                TerminalScreen(service = ready, onClose = onClose)
            }
        }
    }
}
