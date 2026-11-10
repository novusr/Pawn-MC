package com.pocketsamp.server.engine_handler

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Programmatically blunts the Android 12+ Phantom Process Killer before any
 * child process (qemu-i386, samp03svr, mariadbd) is spawned.
 *
 * The stock advice ("disable child process restrictions" in Developer options)
 * needs a manual trip through Settings. Most of that toggle is backed by two
 * device settings that can be written without it, from the app itself:
 *
 *   - `activity_manager/max_phantom_processes` (the 32-child trigger cap)
 *   - `settings_enable_monitor_phantom_procs` (the monitor master switch)
 *
 * `cmd device_config` and `settings put global` come from the `shell` uid, so
 * a plain app cannot run them — but an app in the shared `com.android.shell`
 * group on many AOSP-based firmwares can, and a rooted device always can. The
 * calls below are tried in order and every failure is silent: a device where
 * none of them sticks behaves exactly like before, just with the fallback
 * state left untouched.
 *
 * Everything here is best-effort hardening layered under the existing recovery
 * logic; no user-facing setting changes and nothing requires root to still be
 * a safe no-op.
 */
object PhantomGuard {

    private const val TAG = "PhantomGuard"

    /** [context] currently unused, kept for symmetry with future checks. */
    fun harden(context: Context, report: (String) -> Unit = {}) {
        runCatching { deviceConfig("activity_manager", "max_phantom_processes", "2147483647") }
            .onSuccess { if (it) report("phantom: device_config max_phantom_processes raised") }
        runCatching { globalSetting("settings_enable_monitor_phantom_procs", "0") }
            .onSuccess { if (it) report("phantom: monitor switch disabled") }
        runCatching { globalSetting("settings_ephemeral_phr", "2147483647") }
            .onSuccess { if (it) report("phantom: ephemeral cap raised") }
    }

    /** True when the shell accepted the write (exit 0). */
    private fun deviceConfig(namespace: String, key: String, value: String): Boolean =
        exec("/system/bin/cmd", "device_config", "put", namespace, key, value)

    /** True when the settings provider accepted the write. */
    private fun globalSetting(key: String, value: String): Boolean =
        exec("/system/bin/settings", "put", "global", key, value)

    /** Short-lived exec with a hard timeout; never throws, never blocks long. */
    private fun exec(vararg command: String): Boolean = try {
        val proc = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        proc.inputStream.use { it.readBytes() } // drain so the writer cannot stall
        val done = proc.waitFor() == 0
        proc.destroy()
        if (!done) Log.v(TAG, "${command.joinToString(" ")} -> denied")
        done
    } catch (denied: Exception) {
        Log.v(TAG, "hardening unavailable: ${denied.message}")
        false
    }

    /**
     * Clears the staging mirror when the source tree changed underneath it.
     *
     * The private runtime mirror previously copied every `lib/` and `usr/lib/`
     * file on every server start; on a slow flash that meant tens of thousands
     * of byte-copies in a tight loop, exactly the kind of disk/IO churn that
     * pushes a low-memory device towards the OOM killer mid-session. Re-copying
     * only when the source differs keeps the mirror fresh without the churn.
     */
    fun needsRestage(sourceRoot: File, privateRoot: File): Boolean {
        val stamp = File(privateRoot, ".stage_stamp")
        val signature = signatureOf(File(sourceRoot, "lib")) + "|" + signatureOf(File(sourceRoot, "usr/lib"))
        return runCatching {
            stamp.takeIf { it.isFile }?.readText() != signature
        }.getOrDefault(true).also { changed ->
            if (changed) runCatching { stamp.writeText(signature) }
        }
    }

    /** Cheap fingerprint: total size + entry count, not a per-file walk of hashes. */
    private fun signatureOf(dir: File): String {
        if (!dir.isDirectory) return "absent"
        var count = 0L
        var bytes = 0L
        dir.walkTopDown().forEach {
            if (it.isFile) {
                count++
                bytes += it.length()
            }
        }
        return "$count:$bytes"
    }
}
