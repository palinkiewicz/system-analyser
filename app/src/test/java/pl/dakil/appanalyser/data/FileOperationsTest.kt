package pl.dakil.appanalyser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.dakil.appanalyser.domain.FileOperationKind
import pl.dakil.appanalyser.domain.FileOperationProgress
import java.io.File
import java.nio.file.Files

class FileOperationsTest {

    private lateinit var source: File
    private lateinit var destination: File

    @Before
    fun setUp() {
        source = Files.createTempDirectory("ops-source").toFile()
        destination = Files.createTempDirectory("ops-destination").toFile()
        File(source, "note.txt").writeText("hello")
        File(source, "tree/inner").mkdirs()
        File(source, "tree/a.bin").writeBytes(ByteArray(1_000) { it.toByte() })
        File(source, "tree/inner/b.bin").writeBytes(ByteArray(3_000) { (it * 7).toByte() })
    }

    @After
    fun tearDown() {
        source.deleteRecursively()
        destination.deleteRecursively()
    }

    private fun path(name: String) = File(source, name).path

    @Test
    fun `copy reports byte and file totals and ends complete`() {
        val reports = mutableListOf<FileOperationProgress>()
        val ok = runBlocking {
            FileOperations.copy(listOf(path("note.txt"), path("tree")), destination.path) { reports += it }
        }
        assertTrue(ok)
        val last = reports.last()
        assertEquals(FileOperationKind.COPY, last.kind)
        assertEquals(2, last.items)
        assertEquals(3, last.totalFiles)
        assertEquals(4_005L, last.totalBytes)
        assertEquals(last.totalFiles, last.doneFiles)
        assertEquals(last.totalBytes, last.doneBytes)
        assertEquals(1f, last.fraction)
        // Never goes backwards.
        reports.zipWithNext().forEach { (a, b) -> assertTrue(b.doneBytes >= a.doneBytes) }
        assertArrayEquals(File(source, "tree/inner/b.bin").readBytes(), File(destination, "tree/inner/b.bin").readBytes())
        assertEquals("hello", File(destination, "note.txt").readText())
        assertTrue(File(source, "tree").exists())
    }

    @Test
    fun `copy keeps the modification time`() {
        val original = File(source, "note.txt").apply { setLastModified(1_600_000_000_000L) }
        runBlocking { FileOperations.copy(listOf(original.path), destination.path) }
        assertEquals(original.lastModified(), File(destination, "note.txt").lastModified())
    }

    @Test
    fun `a taken name gets a numbered copy`() {
        File(destination, "note.txt").writeText("existing")
        runBlocking { FileOperations.copy(listOf(path("note.txt")), destination.path) }
        assertEquals("existing", File(destination, "note.txt").readText())
        assertEquals("hello", File(destination, "note (1).txt").readText())
    }

    @Test
    fun `copying a folder into itself is refused`() {
        val ok = runBlocking { FileOperations.copy(listOf(path("tree")), path("tree/inner")) }
        assertFalse(ok)
        assertFalse(File(source, "tree/inner/tree").exists())
    }

    @Test
    fun `moving into the same folder changes nothing`() {
        val ok = runBlocking { FileOperations.move(listOf(path("note.txt")), source.path) }
        assertTrue(ok)
        assertTrue(File(source, "note.txt").exists())
        assertFalse(File(source, "note (1).txt").exists())
    }

    @Test
    fun `move takes the items away from the source`() {
        val ok = runBlocking { FileOperations.move(listOf(path("tree")), destination.path) }
        assertTrue(ok)
        assertFalse(File(source, "tree").exists())
        assertEquals(3_000L, File(destination, "tree/inner/b.bin").length())
    }

    @Test
    fun `a missing source fails without stopping the others`() {
        val ok = runBlocking { FileOperations.copy(listOf(path("gone.txt"), path("note.txt")), destination.path) }
        assertFalse(ok)
        assertTrue(File(destination, "note.txt").exists())
    }

    @Test
    fun `cancelling keeps finished items and removes the partial one`() {
        File(source, "big.bin").writeBytes(ByteArray(2 * 1024 * 1024))
        runBlocking {
            lateinit var job: Job
            job = launch(Dispatchers.IO) {
                FileOperations.copy(listOf(path("note.txt"), path("big.bin")), destination.path) {
                    if (it.currentName == "big.bin") job.cancel()
                }
            }
            job.join()
        }
        assertTrue(File(destination, "note.txt").exists())
        assertFalse(File(destination, "big.bin").exists())
    }

    @Test
    fun `cancelling inside a folder removes that folder's partial copy`() {
        runBlocking {
            lateinit var job: Job
            job = launch(Dispatchers.IO) {
                FileOperations.copy(listOf(path("tree")), destination.path) {
                    if (it.currentName == "b.bin") job.cancel()
                }
            }
            job.join()
        }
        assertFalse(File(destination, "tree").exists())
        assertTrue(File(source, "tree/inner/b.bin").exists())
    }

    @Test
    fun `delete counts every file and removes whole trees`() {
        val reports = mutableListOf<FileOperationProgress>()
        val ok = runBlocking { FileOperations.delete(listOf(path("tree"), path("note.txt"))) { reports += it } }
        assertTrue(ok)
        assertFalse(File(source, "tree").exists())
        assertFalse(File(source, "note.txt").exists())
        val last = reports.last()
        assertEquals(FileOperationKind.DELETE, last.kind)
        assertEquals(3, last.totalFiles)
        assertEquals(3, last.doneFiles)
        assertEquals(1f, last.fraction)
    }

    @Test
    fun `delete never follows a symlink out of the tree`() {
        val outside = Files.createTempDirectory("ops-outside").toFile()
        try {
            File(outside, "keep.txt").writeText("keep")
            Files.createSymbolicLink(File(source, "tree/link").toPath(), outside.toPath())
            runBlocking { FileOperations.delete(listOf(path("tree"))) }
            assertTrue(File(outside, "keep.txt").exists())
        } finally {
            outside.deleteRecursively()
        }
    }
}
