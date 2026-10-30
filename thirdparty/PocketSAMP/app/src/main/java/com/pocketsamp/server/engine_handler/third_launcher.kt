package com.pocketsamp.server.engine_handler

import android.content.Context
import java.io.File
import java.io.RandomAccessFile

/**
 * Decides how a Linux binary is executed on this device, from its ELF header.
 */
internal object ThirdLauncher {

    /** ELF `e_machine` values this launcher distinguishes. */
    private const val EM_386 = 3
    private const val EM_X86_64 = 62
    private const val EM_AARCH64 = 183
    private const val EM_ARM = 40

    enum class Kind { I386, X86_64, AARCH64, ARM, UNKNOWN }

    /**
     * Reads the ELF header of [file].
     *
     * Returns [Kind.UNKNOWN] for anything that is not a well-formed ELF, including the
     * Android "lib*.so" wrapper some projects ship (which is a valid ELF but has no
     * interpreter, so it is handled by [isPlainExecutable] instead).
     */
    fun kindOf(file: File): Kind {
        if (!file.isFile || file.length() < 20) return Kind.UNKNOWN
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] != 0x7f.toByte() || magic[1] != 'E'.code.toByte() ||
                    magic[2] != 'L'.code.toByte() || magic[3] != 'F'.code.toByte()
                ) {
                    return Kind.UNKNOWN
                }
                /*
                 * EI_CLASS (1 = 32-bit, 2 = 64-bit) is read past; e_machine alone
                 * decides the kind, and the byte order comes from EI_DATA below.
                 */
                raf.readUnsignedByte()
                val eiData = raf.readUnsignedByte() /* 1 = little-endian, 2 = big-endian */
                raf.seek(0x12)
                /*
                 * e_machine is a 16-bit field at offset 0x12. Android targets are all
                 * little-endian, but the header is read correctly either way.
                 */
                val lo = raf.readUnsignedByte()
                val hi = raf.readUnsignedByte()
                val machine = if (eiData == 2) (lo shl 8) or hi else lo or (hi shl 8)
                when {
                    machine == EM_386 -> Kind.I386
                    machine == EM_X86_64 -> Kind.X86_64
                    machine == EM_AARCH64 -> Kind.AARCH64
                    machine == EM_ARM -> Kind.ARM
                    else -> Kind.UNKNOWN
                }
            }
        }.getOrDefault(Kind.UNKNOWN)
    }

    /** True when [file] is a 32-bit x86 binary that the bundled QEMU can translate. */
    fun needsQemu(file: File): Boolean {
        val kind = kindOf(file)
        return kind == Kind.I386 || kind == Kind.X86_64
    }

    /**
     * Locates the QEMU user-mode translator.
     *
     * Shared with [Server] so both engines look in the same places: the copies
     * Android extracts from `jniLibs` first (the only ones guaranteed executable),
     * then the loose binaries a rootfs may carry.
     */
    fun findQemu(context: Context): File? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val filesDir = context.filesDir
        val candidates = mutableListOf(
            File(nativeLibDir, "libqemu-i386.so"),
            File(nativeLibDir, "qemu-i386"),
            File(filesDir, "bin/qemu-i386"),
            File(filesDir, "bin/libqemu-i386.so"),
            File(filesDir, "rootfs_i386/bin/qemu-i386"),
            File(filesDir, "rootfs_i386/usr/bin/qemu-i386"),
            File("/system/bin/qemu-i386"),
            File("/vendor/bin/qemu-i386")
        )
        runCatching {
            File(nativeLibDir).parentFile?.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    candidates.add(File(dir, "libqemu-i386.so"))
                    candidates.add(File(dir, "qemu-i386"))
                    candidates.add(File(dir, "libproot.so"))
                }
            }
        }
        /*
         * proot is not a translator; return it only through [findProot].
         */
        return candidates.firstOrNull { isExecutableFile(it) && !it.name.startsWith("libproot") }
    }

    /**
     * Locates the PRoot launcher, if one is packaged.
     *
     * The SA-MP guest resolves `/dev`, `/proc` and the server folder through PRoot when
     * it is available; QEMU alone works for a self-contained rootfs, so this is
     * optional and returning null is not an error.
     */
    fun findProot(context: Context): File? {
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val candidates = mutableListOf(
            File(nativeLibDir, "libproot.so"),
            File(nativeLibDir, "proot"),
            File(context.filesDir, "bin/proot")
        )
        runCatching {
            File(nativeLibDir).parentFile?.listFiles()?.forEach { dir ->
                if (dir.isDirectory) candidates.add(File(dir, "libproot.so"))
            }
        }
        return candidates.firstOrNull { isExecutableFile(it) }
    }

    /**
     * Command that runs [binary] with [arguments].
     *
     * @param qemu      translator returned by [findQemu]; required for i386 binaries.
     * @param rootfsDir i386 sysroot passed to QEMU with `-L`; the guest's own
     *                  `/lib` and `/usr/lib` resolve inside it.
     * @param guestLibraryPath extra `LD_LIBRARY_PATH` for the guest (bundle `lib/` dirs).
     * @return the full argv, or `null` when the binary needs QEMU and none is available.
     */
    fun command(
        binary: File,
        arguments: List<String>,
        qemu: File?,
        rootfsDir: File,
        guestLibraryPath: String? = null
    ): List<String>? {
        if (!needsQemu(binary)) {
            /*
             * Native ARM64/ARM build: exec directly.
             */
            return listOf(binary.absolutePath) + arguments
        }
        val translator = qemu ?: return null
        val guestLdPath = listOfNotNull(
            guestLibraryPath?.takeIf { it.isNotBlank() },
            "/usr/lib/i386-linux-gnu",
            "/lib/i386-linux-gnu",
            "/usr/lib",
            "/lib"
        ).joinToString(":")
        return listOf(
            translator.absolutePath,
            "-L", rootfsDir.absolutePath,
            "-E", "LD_LIBRARY_PATH=$guestLdPath",
            binary.absolutePath
        ) + arguments
    }

    /** Regular, non-empty, executable — a directory named `x` passes `canExecute()`. */
    fun isExecutableFile(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        if (file.canExecute()) return true
        return runCatching { file.setExecutable(true, false) }.getOrDefault(false)
    }
}
