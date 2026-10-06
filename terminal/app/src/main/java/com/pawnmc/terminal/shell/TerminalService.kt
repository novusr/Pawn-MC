package com.pawnmc.terminal.shell

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.pawnmc.terminal.core.SandboxEnvironment
import com.pawnmc.terminal.core.ShellScripts
import com.pawnmc.terminal.core.TerminalPaths
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Owns every sandbox shell for the lifetime of the process.
 *
 * Sessions live in a service rather than in the composable so that closing the editor
 * (or rotating the device) does not kill a running `apt install` or a compile. The
 * service deliberately does not run in the foreground: it has no notification and no
 * reason to keep the CPU awake, and Android will simply stop it together with the app.
 */
class TerminalService : Service() {

    private val sessions = linkedMapOf<String, TerminalSession>()
    private val _sessionIds = MutableStateFlow<List<String>>(emptyList())
    val sessionIds = _sessionIds.asStateFlow()

    /** The session the UI should currently render, kept here so it survives recomposition. */
    val activeSessionId = MutableStateFlow(DEFAULT_SESSION_ID)

    /**
     * Where the shell should start, set by the editor before a session is created.
     *
     * A device path; converted to its container equivalent when the session starts.
     */
    var requestedWorkingDirectory: String? = null

    /**
     * Library file name of the compiler the editor currently has selected, e.g.
     * `libpawnc3107.so`.
     *
     * Null hides `pawncc` from the sandbox rather than exposing a stale version.
     */
    var requestedCompilerLibraryName: String? = null

    inner class TerminalBinder : Binder() {
        fun service(): TerminalService = this@TerminalService
    }

    private val binder = TerminalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * Returns the session [id], creating it when it does not exist yet.
     *
     * Creation is the expensive step — it forks a shell under proot and starts three
     * I/O threads — so callers are expected to reuse the returned session.
     */
    fun session(id: String = DEFAULT_SESSION_ID, client: TerminalSessionClient): TerminalSession {
        sessions[id]?.let { existing ->
            existing.updateTerminalSessionClient(client)
            return existing
        }

        val created = createSession(id, client)
        sessions[id] = created
        _sessionIds.update { it + id }
        return created
    }

    fun existingSession(id: String): TerminalSession? = sessions[id]

    fun terminate(id: String) {
        val session = sessions.remove(id) ?: return
        runCatching { session.finishIfRunning() }
        _sessionIds.update { it - id }

        if (id == activeSessionId.value) {
            activeSessionId.value = _sessionIds.value.firstOrNull() ?: DEFAULT_SESSION_ID
        }
        if (sessions.isEmpty()) stopSelf()
    }

    fun terminateAll() {
        sessions.values.forEach { runCatching { it.finishIfRunning() } }
        sessions.clear()
        _sessionIds.value = emptyList()
        stopSelf()
    }

    override fun onDestroy() {
        sessions.values.forEach { runCatching { it.finishIfRunning() } }
        sessions.clear()
        super.onDestroy()
    }

    private fun createSession(id: String, client: TerminalSessionClient): TerminalSession {
        ShellScripts.install(this)

        val sandboxScript = ShellScripts.sandboxScript(this)
        val pawncc = resolvePawncc()
        val containerWorkingDir = SandboxEnvironment.containerWorkingDirectory(requestedWorkingDirectory)

        val environment = SandboxEnvironment.build(
            context = this,
            workingDirectoryInsideContainer = containerWorkingDir,
            pawnccBinary = pawncc,
        )

        // The PTY launcher calls execvp(command, argv) without inserting argv[0]. The
        // first argument must therefore be the shell executable, followed by the script
        // path. Otherwise Android sh treats an option like `-c` as its process name and
        // runs the wrong command (the reported `exec "$1"` error). sandbox.sh stays the
        // PRoot entry point.
        return TerminalSession(
            "/system/bin/sh",
            TerminalPaths.localDir(this).absolutePath,
            sandboxShellArguments(sandboxScript.absolutePath),
            environment,
            SCROLLBACK_ROWS,
            client,
        )
    }

    /**
     * The `pawncc` the editor currently has selected, if it is installed.
     *
     * The compiler lives in the app's native library directory under a versioned name
     * (`libpawnc3107.so`), so it is handed to the container as-is and symlinked onto
     * `PATH` by `init.sh` rather than being duplicated into the rootfs.
     */
    private fun resolvePawncc(): File? =
        requestedCompilerLibraryName
            ?.let { File(TerminalPaths.nativeLibraryDir(this), it) }
            ?.takeIf { it.isFile }

    companion object {
        const val DEFAULT_SESSION_ID = "pawnmc"

        /**
         * Lines kept in the emulator's scrollback.
         *
         * `apt` and `make` produce far more output than a screenful, and losing the
         * beginning of an error is worse than a few hundred kilobytes of memory.
         */
        private const val SCROLLBACK_ROWS = 5_000
    }
}

/** argv passed to execvp must start with argv[0], the shell's own name. */
internal fun sandboxShellArguments(scriptPath: String): Array<String> =
    arrayOf("/system/bin/sh", "-c", "exec \"$1\"", "pawnmc-terminal", scriptPath)
