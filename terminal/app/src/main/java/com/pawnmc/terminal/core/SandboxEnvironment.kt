package com.pawnmc.terminal.core

import android.content.Context
import java.io.File

/**
 * Builds the environment the sandboxed shell runs in.
 *
 * Everything the container needs is passed as environment variables rather than
 * arguments, because `sandbox.sh` reads them to assemble its proot bind list. The set is
 * deliberately smaller than the Xed original: no Termux:X11, no feature flags, no
 * debug-mode switch — only what a Pawn workspace requires.
 */
internal object SandboxEnvironment {

    /** `TERM`/colour setup that makes apt and the shell render correctly in our emulator. */
    private const val TERM_VALUE = "xterm-256color"

    fun build(
        context: Context,
        workingDirectoryInsideContainer: String,
        pawnccBinary: File?,
    ): Array<String> {
        val localDir = TerminalPaths.localDir(context).absolutePath
        val privateDir = TerminalPaths.privateDir(context).absolutePath
        val nativeLibDir = TerminalPaths.nativeLibraryDir(context)
        val externalFilesDir = context.getExternalFilesDir(null)?.absolutePath.orEmpty()

        val environment = linkedMapOf(
            // --- proot itself ---------------------------------------------------------
            // PROOT/PROOT_LOADER point at the packaged loaders. They are `lib*.so` so
            // Android installs them into nativeLibraryDir, the only execable location.
            "PROOT" to "$nativeLibDir/libproot.so",
            "PROOT_LOADER" to "$nativeLibDir/libloader.so",
            "PROOT_TMP_DIR" to TerminalPaths.temporaryDir(context).absolutePath,
            // proot needs a writable linker scratch dir and a real TMPDIR for apt.
            "LINKER" to linkerPath(),

            // --- paths the sandbox scripts read --------------------------------------
            // `$LOCAL` is the container-facing name for the app's private storage; the
            // scripts use it to find bin/utils and to locate the extracted rootfs.
            "LOCAL" to localDir,
            "PRIVATE_DIR" to privateDir,
            "PRIMARY_ABI" to android.os.Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            "ANDROID_API_LEVEL" to android.os.Build.VERSION.SDK_INT.toString(),
            "NATIVE_LIB_DIR" to nativeLibDir,
            "TMP_DIR" to TerminalPaths.temporaryDir(context).absolutePath,
            "TMPDIR" to TerminalPaths.temporaryDir(context).absolutePath,
            "EXT_HOME" to TerminalPaths.homeDir(context).absolutePath,
            "PUBLIC_HOME" to externalFilesDir,
            "WKDIR" to workingDirectoryInsideContainer,

            // --- container identity ---------------------------------------------------
            "HOME" to "/home",
            "TERM" to TERM_VALUE,
            "COLORTERM" to "truecolor",
            "LANG" to "C.UTF-8",
            "TZ" to "UTC",
            "PROMPT_DIRTRIM" to "2",

            // --- the compiler the editor selected ------------------------------------
            // Consumed by `init.sh`, which links it to `bin/pawncc` on PATH.
            "PAWNCC" to pawnccBinary?.absolutePath.orEmpty(),

            // --- Android runtime, forwarded so proot'ed binaries can find their libs ---
            "ANDROID_DATA" to System.getenv("ANDROID_DATA").orEmpty(),
            "ANDROID_ROOT" to System.getenv("ANDROID_ROOT").orEmpty(),
            "ANDROID_RUNTIME_ROOT" to System.getenv("ANDROID_RUNTIME_ROOT").orEmpty(),
            "ANDROID_TZDATA_ROOT" to System.getenv("ANDROID_TZDATA_ROOT").orEmpty(),
            "BOOTCLASSPATH" to System.getenv("BOOTCLASSPATH").orEmpty(),
            "DEX2OATBOOTCLASSPATH" to System.getenv("DEX2OATBOOTCLASSPATH").orEmpty(),
        )

        return environment.map { (key, value) -> "$key=$value" }.toTypedArray()
    }

    /**
     * The dynamic linker proot is started through.
     *
     * 64-bit devices ship `/system/bin/linker64`, 32-bit ones only `/system/bin/linker`.
     * The sandbox shell picks the same file, so the two must not disagree.
     */
    fun linkerPath(): String =
        if (File("/system/bin/linker64").exists()) "/system/bin/linker64" else "/system/bin/linker"

    /**
     * Translates a device path into its path inside the container.
     *
     * `/sdcard` is bind-mounted at the same location, so a project the user opened from
     * shared storage keeps a usable working directory. Anything else (the app's private
     * storage) is only reachable through the container's `/home`, so it falls back there.
     */
    fun containerWorkingDirectory(devicePath: String?): String {
        if (devicePath.isNullOrBlank()) return "/home"
        return when {
            devicePath == "/sdcard" || devicePath.startsWith("/sdcard/") -> devicePath
            devicePath.startsWith("/storage/") -> devicePath
            // Private app data is already exposed through the /home bind; /home is safer
            // than passing an inaccessible Android-private path as a PRoot working dir.
            else -> "/home"
        }
    }
}
