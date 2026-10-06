package com.pawnmc.terminal.core

import android.content.Context
import com.pawnmc.terminal.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * What the install screen is currently doing.
 *
 * Internal, like the rest of the sandbox layer: only [com.pawnmc.terminal.TerminalOverlay]
 * observes it, and nothing outside this module needs to see the bootstrap internals.
 */
internal sealed interface SetupState {
    data object Checking : SetupState

    data class Downloading(val progress: DownloadProgress) : SetupState

    data object Extracting : SetupState

    data class Failed(
        val message: String?,
        val details: String? = null,
    ) : SetupState
}

/**
 * Prepares the sandbox: stages the rootfs archive and extracts it once.
 *
 * Extraction is done by running `setup.sh` inside proot, because the Ubuntu image
 * contains absolute symlinks and device nodes that a plain `tar` on Android cannot
 * create. proot fakes the root so the archive unpacks correctly — this is the same
 * reason the upstream editor does it this way.
 */
internal object SandboxSetup {

    /**
     * Runs the whole first-launch sequence, reporting progress through [onState].
     *
     * Safe to call repeatedly: both stages short-circuit when their output already
     * exists, so a completed install is a no-op.
     */
    suspend fun ensureReady(
        context: Context,
        onState: (SetupState) -> Unit,
    ) = withContext(Dispatchers.IO) {
        onState(SetupState.Checking)

        if (TerminalPaths.isInstalled(context)) return@withContext

        try {
            val archive = TerminalPaths.rootfsArchive(context)
            if (!archive.exists() || archive.length() == 0L) {
                onState(SetupState.Downloading(DownloadProgress(0, 0, archive.name)))
                RootfsDownloader.ensureStaged(context) { progress ->
                    onState(SetupState.Downloading(progress))
                }
            }

            onState(SetupState.Extracting)
            extract(context)
        } catch (e: Exception) {
            val rawOutput = (e as? SandboxPreparationException)?.rawOutput
            onState(
                SetupState.Failed(
                    message = failureMessage(context, e),
                    details = failureDetails(e, rawOutput),
                ),
            )
            throw e
        }

        onState(SetupState.Checking)
    }

    /**
     * Unpacks the staged archive into `files/local/sandbox`.
     *
     * `setup.sh` removes the archive and writes the setup marker when it succeeds, so
     * this is the single place that decides a sandbox is usable.
     */
    private suspend fun extract(context: Context) {
        val archive = TerminalPaths.rootfsArchive(context)
        if (!archive.exists()) throw IOException("The rootfs archive is missing")

        ShellScripts.install(context)
        // `sandbox.sh` sets this directory to 1777; creating it early means apt has a
        // writable /dev/shm even on the very first extraction run.
        TerminalPaths.sharedMemoryDir(context).mkdirs()

        val setupScript = ShellScripts.install(context, "setup")
        val environment = SandboxEnvironment.build(
            context = context,
            workingDirectoryInsideContainer = "/",
            pawnccBinary = null,
        )

        // `setup.sh` is a host-side script: Android's shell extracts the archive with
        // the bundled link2symlink preload shim. Extraction does not require PRoot, so a
        // PRoot SIGILL cannot prevent the first sandbox install.
        val started = ProcessBuilder("/system/bin/sh", setupScript.absolutePath)
            .apply {
                environment().putAll(environment.toMap())
                // setup.sh normally launches the interactive sandbox after extraction;
                // setup must instead return so this bootstrap can validate the marker.
                environment()["PAWNMC_SETUP_ONLY"] = "1"
                directory(TerminalPaths.localDir(context))
            }
            .redirectErrorStream(true)
            .start()

        val output = started.inputStream.bufferedReader().use { it.readText() }
        val exitCode = started.waitFor()

        if (exitCode != 0 || !TerminalPaths.isInstalled(context)) {
            val diagnostics = buildString {
                appendLine("setup.sh exit code: $exitCode")
                appendLine("Device ABI list: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
                appendLine("Android API level: ${android.os.Build.VERSION.SDK_INT}")
                appendLine("Package: ${context.packageName}")
                appendLine("Rootfs archive: ${archive.absolutePath} (${archive.length()} bytes, exists=${archive.isFile})")
                appendLine("Staged archive retained: ${archive.exists()}")
                appendLine("Native library directory: ${TerminalPaths.nativeLibraryDir(context)}")
                appendLine("link2symlink present: ${File(TerminalPaths.nativeLibraryDir(context), "liblink2symlink.so").isFile}")
                appendLine("Android tar present: ${File("/system/bin/tar").canExecute()}")
                appendLine("App-private free bytes: ${TerminalPaths.localDir(context).usableSpace}")
                appendLine("Rootfs directory: ${TerminalPaths.rootfsDir(context).absolutePath}")
                appendLine("Rootfs installed check: ${TerminalPaths.isInstalled(context)}")
                appendLine("Rootfs top-level entries: ${TerminalPaths.rootfsDir(context).list()?.sorted()?.joinToString().orEmpty()}")
                appendLine("Rootfs markers: bin/sh=${File(TerminalPaths.rootfsDir(context), "bin/sh").exists()}, bin/dash=${File(TerminalPaths.rootfsDir(context), "bin/dash").exists()}, usr/bin/dpkg=${File(TerminalPaths.rootfsDir(context), "usr/bin/dpkg").exists()}")
                if (output.isNotBlank()) appendLine(output.trim())
            }.trim()
            throw SandboxPreparationException(
                summary = describeFailure(output),
                rawOutput = diagnostics,
            )
        }
    }

    /**
     * Turns raw script output into one readable line.
     *
     * The scripts print coloured banners and end with `clear` plus a nested shell, so the
     * raw tail is escape sequences rather than an explanation. Taking the *last* line was
     * wrong for exactly that reason: it reported `[2J [H` instead of the real error.
     * Stripping the control codes first and then preferring the line that follows an
     * `ERROR` banner gives something a user can act on.
     */
    internal fun describeFailure(rawOutput: String): String {
        val lines = rawOutput.lineSequence()
            .map { line -> ANSI_ESCAPE.replace(line, "").trim() }
            .filter { it.isNotEmpty() }
            .toList()

        // The scripts prefix failures with a coloured "ERROR" banner, so the line right
        // after the last banner is the explanation. Nothing else is filtered out here:
        // real failures often *are* paths (".../libproot.so: inaccessible or not found"),
        // so skipping lines that start with `/` would discard the useful one.
        lines.firstOrNull { it.contains("signal hint:", ignoreCase = true) }?.let { return it }
        val actionable = lines.firstOrNull { line ->
            line.contains("failed", ignoreCase = true) ||
                line.contains("missing", ignoreCase = true) ||
                line.contains("not readable", ignoreCase = true) ||
                line.contains("not writable", ignoreCase = true) ||
                line.contains("could not", ignoreCase = true)
        }
        if (actionable != null) return actionable
        lines.firstOrNull { it.contains("exit status:", ignoreCase = true) }?.let { return it }
        val setupFailureIndex = lines.indexOfFirst { it.contains("Sandbox setup failed during:", ignoreCase = true) }
        if (setupFailureIndex >= 0) {
            return lines.drop(setupFailureIndex).take(3).joinToString(" — ")
        }
        val bannerIndex = lines.indexOfLast { it.contains(ERROR_BANNER) }
        lines.getOrNull(bannerIndex + 1)?.let { return it }

        return lines.lastOrNull() ?: "The sandbox could not be extracted"
    }

    /**
     * The word the scripts' `error()` helper prints.
     *
     * Matched without the surrounding colour codes, which are stripped before this is
     * used, and without case so a shell that uppercases its output still matches.
     */
    private const val ERROR_BANNER = "ERROR"

    /** Matches CSI/OSC sequences, including the `[2J` and `[H` a `clear` emits. */
    private val ANSI_ESCAPE = Regex("\\u001B\\[[0-9;?]*[ -/]*[@-~]|\\u001B\\][^\\u0007]*\\u0007")

    /** Human readable reason for a failure, preferring the thrown message. */
    fun failureMessage(context: Context, error: Throwable?): String =
        when {
            error is SandboxPreparationException && error.summary.isNotBlank() -> error.summary
            // `message` is nullable on Throwable, so it is stored in a local first to
            // smart-cast it: the previous `-> error.message` claimed to return `String`
            // while the compiler still saw `String?`.
            error?.message?.takeIf { it.isNotBlank() } != null -> error.message!!
            else -> context.getString(R.string.terminal_setup_failed)
        }

    /** Debug details for the user-facing error card, including stack trace and script logs. */
    internal fun failureDetails(
        error: Throwable?,
        rawOutput: String? = null,
    ): String {
        val builder = StringBuilder()
        val summary = error?.message?.takeIf { it.isNotBlank() }
            ?: error?.javaClass?.simpleName
            ?: "Unknown sandbox setup failure"

        builder.appendLine("Sandbox setup error: $summary")

        if (error != null) {
            val trace = error.stackTraceToString().trim()
            if (trace.isNotBlank()) {
                builder.appendLine()
                builder.appendLine("Stack trace:")
                builder.appendLine(trace.take(4000))
            }
        }

        if (!rawOutput.isNullOrBlank()) {
            builder.appendLine()
            builder.appendLine("Script output and diagnostics:")
            builder.appendLine(rawOutput.trim())
        }

        return builder.toString().trim()
    }

    /**
     * Failure from [extract] that keeps the script's raw output alongside the summary.
     *
     * Deliberately public: `TerminalOverlay` unwraps it to show the script log on the
     * error card, so it cannot be `internal` unless that composable is too.
     */
    class SandboxPreparationException(
        val summary: String,
        val rawOutput: String? = null,
    ) : IOException(summary)

    private fun Array<String>.toMap(): Map<String, String> =
        mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) null else entry.substring(0, separator) to entry.substring(separator + 1)
        }.toMap()
}
