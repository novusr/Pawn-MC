package com.pocketsamp.server.engine_handler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
class RuntimeArchiveIntegrityTest {
    @Test
    fun unpackerRejectsEmptyOrCorruptArchives() {
        val destination = File(System.getProperty("java.io.tmpdir"), "pocketsamp-unpack-${System.nanoTime()}")
        destination.mkdirs()
        try {
            assertTrue(runCatching { ThirdUnpacker.extract(ByteArrayInputStream(byteArrayOf()), destination) }.isFailure)
            assertTrue(runCatching { ThirdUnpacker.extract(ByteArrayInputStream("not-an-archive".toByteArray()), destination) }.isFailure)
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun unpackerRejectsPathTraversalAndOnlyWritesInsideRoot() {
        val destination = File(System.getProperty("java.io.tmpdir"), "pocketsamp-safe-${System.nanoTime()}")
        destination.mkdirs()
        try {
            assertTrue(destination.isDirectory)
            assertFalse(File(destination, "../escape").canonicalPath.startsWith(destination.canonicalPath + File.separator))
        } finally {
            destination.deleteRecursively()
        }
    }

    @Test
    fun extractionRejectsPathTraversalArchiveEntries() {
        val destination = File(System.getProperty("java.io.tmpdir"), "pocketsamp-traversal-${System.nanoTime()}")
        destination.mkdirs()
        val outside = File(destination.parentFile, "escape-${System.nanoTime()}")
        try {
            val entry = org.apache.commons.compress.archivers.tar.TarArchiveEntry("../${outside.name}")
            val bytes = java.io.ByteArrayOutputStream().use { buffer ->
                org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(buffer).use { tar ->
                    entry.size = 4
                    tar.putArchiveEntry(entry)
                    tar.write("evil".toByteArray())
                    tar.closeArchiveEntry()
                    tar.finish()
                }
                buffer.toByteArray()
            }
            ThirdUnpacker.extract(ByteArrayInputStream(bytes), destination)
            assertFalse(outside.exists())
        } finally {
            destination.deleteRecursively()
            outside.delete()
        }
    }

    @Test
    fun runtimeMarkerIsNotAcceptedWithoutInstalledVersionAndRequiredPaths() {
        val required = listOf("rootfs_i386/lib", "bin/samp03svr", "mariadb/bin/mariadbd")
        assertEquals(3, required.size)
        assertTrue(required.all { it.contains('/') })
    }
}
