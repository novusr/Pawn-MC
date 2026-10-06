package com.pawnmc.terminal.core

import android.os.Build

/**
 * Where the Ubuntu rootfs comes from.
 *
 * `ubuntu-base` is the same minimal image the Xed editor uses: ~30 MB of userspace with
 * `apt`, `dpkg` and `dash`, which is everything a compiler sandbox needs and nothing it
 * does not. The build downloads it once and then works offline forever.
 */
internal object RootfsSources {

    private const val BASE = "https://github.com/Xed-Editor/Karbon-PackagesX/releases/download/ubuntu"

    private const val UBUNTU_VERSION = "24.04.3"

    private const val ARMHF = "$BASE/ubuntu-base-$UBUNTU_VERSION-base-armhf.tar.gz"
    private const val ARM64 = "$BASE/ubuntu-base-$UBUNTU_VERSION-base-arm64.tar.gz"
    private const val X86_64 = "$BASE/ubuntu-base-$UBUNTU_VERSION-base-amd64.tar.gz"

    /** Everything the app ships an ABI for, so no supported device is left without a rootfs. */
    val supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

    /**
     * The rootfs matching this device's primary ABI.
     *
     * proot cannot emulate a foreign instruction set, so the rootfs architecture has to
     * equal the device's, not merely be compatible with it. The ABI list is ordered by
     * preference, and the first entry the app actually packages wins.
     */
    fun forDevice(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in supportedAbis } ?: return null
        return when (abi) {
            "arm64-v8a" -> ARM64
            "armeabi-v7a" -> ARMHF
            "x86_64" -> X86_64
            else -> null
        }
    }
}
