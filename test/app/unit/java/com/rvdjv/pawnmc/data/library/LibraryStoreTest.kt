package com.rvdjv.pawnmc.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryStoreTest {
    @Test
    fun `accepts only the expected ELF architecture and class`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-elf-${System.nanoTime()}")
        assertTrue(root.mkdirs())

        try {
            val x86_64 = writeElf(File(root, "x86_64.so"), elfHeader(elfClass = 2, machine = 62))
            val arm64 = writeElf(File(root, "arm64.so"), elfHeader(elfClass = 2, machine = 183))
            val arm32 = writeElf(File(root, "arm.so"), elfHeader(elfClass = 1, machine = 40))
            val invalidClass = writeElf(File(root, "wrong-class.so"), elfHeader(elfClass = 1, machine = 62))

            assertTrue(LibraryStore.hasExpectedElfAbi(x86_64, "x86_64"))
            assertFalse(LibraryStore.hasExpectedElfAbi(x86_64, "arm64-v8a"))
            assertTrue(LibraryStore.hasExpectedElfAbi(arm64, "arm64-v8a"))
            assertTrue(LibraryStore.hasExpectedElfAbi(arm32, "armeabi-v7a"))
            assertFalse(LibraryStore.hasExpectedElfAbi(invalidClass, "x86_64"))
        } finally {
            root.listFiles()?.forEach(File::delete)
            root.delete()
        }
    }

    @Test
    fun `rejects invalid ELF headers and reports actual machine`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-elf-invalid-${System.nanoTime()}")
        assertTrue(root.mkdirs())

        try {
            val invalid = writeElf(File(root, "invalid.so"), ByteArray(20))
            val x86_64 = writeElf(File(root, "x86_64.so"), elfHeader(elfClass = 2, machine = 62))

            assertFalse(LibraryStore.hasExpectedElfAbi(invalid, "x86_64"))
            assertEquals("invalid or unreadable ELF", LibraryStore.elfDescription(invalid))
            assertEquals("ELF64 EM_X86_64 (62)", LibraryStore.elfDescription(x86_64))
        } finally {
            root.listFiles()?.forEach(File::delete)
            root.delete()
        }
    }

    @Test
    fun `stages the Documents library into a content-addressed private cache`() {
        val root = File(System.getProperty("java.io.tmpdir"), "pawnmc-stage-${System.nanoTime()}")
        val sourceDir = File(root, "Documents/PawnMC/library/x86_64")
        val cacheDir = File(root, "code_cache/pawnmc-libraries")
        assertTrue(sourceDir.mkdirs())

        try {
            val source = writeElf(File(sourceDir, "libpawnc3107.so"), elfHeader(elfClass = 2, machine = 62))
            val staged = LibraryStore.stageLibraryForLoading(source, cacheDir, "x86_64")

            assertTrue(staged.isFile)
            assertTrue(staged.absolutePath.startsWith(cacheDir.absolutePath))
            assertEquals(source.readBytes().toList(), staged.readBytes().toList())
            assertEquals(staged, LibraryStore.stageLibraryForLoading(source, cacheDir, "x86_64"))

            source.writeBytes(elfHeader(elfClass = 2, machine = 62).also { it[8] = 1 })
            val updated = LibraryStore.stageLibraryForLoading(source, cacheDir, "x86_64")
            assertTrue(updated.isFile)
            assertFalse(staged.parentFile == updated.parentFile)
            assertEquals(source.readBytes().toList(), updated.readBytes().toList())
        } finally {
            root.walkBottomUp().forEach(File::delete)
        }
    }

    private fun writeElf(file: File, header: ByteArray): File = file.apply { writeBytes(header) }

    private fun elfHeader(elfClass: Int, machine: Int): ByteArray = ByteArray(20).apply {
        this[0] = 0x7f
        this[1] = 'E'.code.toByte()
        this[2] = 'L'.code.toByte()
        this[3] = 'F'.code.toByte()
        this[4] = elfClass.toByte()
        this[5] = 1
        this[18] = (machine and 0xff).toByte()
        this[19] = (machine ushr 8).toByte()
    }
}
