package pl.dakil.appanalyser.data

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class DiskScannerTest {

    private lateinit var root: File
    private lateinit var outside: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("disk-scan").toFile()
        outside = Files.createTempDirectory("disk-scan-outside").toFile()
        File(root, "a.bin").writeBytes(ByteArray(100))
        File(root, "photos").mkdirs()
        File(root, "photos/one.jpg").writeBytes(ByteArray(1_000))
        File(root, "photos/two.jpg").writeBytes(ByteArray(2_000))
        File(root, "photos/trip").mkdirs()
        File(root, "photos/trip/three.jpg").writeBytes(ByteArray(4_000))
        File(root, "empty").mkdirs()
        File(root, ".hidden").mkdirs()
        File(root, ".hidden/secret").writeBytes(ByteArray(10))
    }

    @After
    fun tearDown() {
        root.walkBottomUp().forEach { it.setReadable(true); it.setExecutable(true) }
        root.deleteRecursively()
        outside.deleteRecursively()
    }

    private fun scan(
        scanner: DiskScanner = DiskScanner(Dispatchers.IO),
    ) = runBlocking { scanner.scan(root) }

    @Test
    fun `totals are recursive and children are sorted largest first`() {
        val tree = scan()!!.root
        assertEquals(7_110L, tree.sizeBytes)
        assertEquals(5, tree.fileCount)
        assertEquals(100L, tree.ownFilesBytes)
        assertEquals(1, tree.ownFileCount)
        assertEquals(listOf("photos", ".hidden", "empty"), tree.children.map { it.name })

        val photos = tree.find("${root.path}/photos")!!
        assertEquals(7_000L, photos.sizeBytes)
        assertEquals(3_000L, photos.ownFilesBytes)
        assertEquals(3, photos.fileCount)
        assertEquals(4_000L, photos.children.single().sizeBytes)
    }

    @Test
    fun `symbolic links are neither followed nor counted`() {
        File(outside, "huge.bin").writeBytes(ByteArray(50_000))
        Files.createSymbolicLink(File(root, "link-dir").toPath(), outside.toPath())
        Files.createSymbolicLink(File(root, "link-file").toPath(), File(outside, "huge.bin").toPath())
        // A link back to the root would loop forever if links were followed.
        Files.createSymbolicLink(File(root, "photos/loop").toPath(), root.toPath())

        val tree = scan()!!.root
        assertEquals(7_110L, tree.sizeBytes)
        assertTrue(tree.children.none { it.name.startsWith("link") })
    }

    @Test
    fun `an unreadable directory is marked restricted without aborting the scan`() {
        val locked = File(root, "locked").apply { mkdirs() }
        File(locked, "inside").writeBytes(ByteArray(10))
        locked.setReadable(false)
        locked.setExecutable(false)
        // Root ignores permission bits, in which case there is nothing to test.
        assumeFalse(locked.canRead())

        val tree = scan()!!.root
        val node = tree.find(locked.path)!!
        assertTrue(node.restricted)
        assertEquals(0L, node.sizeBytes)
        assertEquals(7_110L, tree.sizeBytes)
    }

    @Test
    fun `the restricted predicate flags matching directories`() {
        val photos = "${root.path}/photos"
        val tree = scan(DiskScanner(Dispatchers.IO, isRestricted = { it == photos }))!!.root
        assertTrue(tree.find(photos)!!.restricted)
        assertTrue(!tree.restricted)
    }

    @Test
    fun `largest files are ordered and capped`() {
        val result = scan(DiskScanner(Dispatchers.IO, largestFilesLimit = 3))!!
        assertEquals(listOf("three.jpg", "two.jpg", "one.jpg"), result.largestFiles.map { it.name })
        assertEquals(4_000L, result.largestFiles.first().sizeBytes)
    }

    @Test
    fun `a missing root returns null`() {
        val result = runBlocking { DiskScanner(Dispatchers.IO).scan(File(root, "nope")) }
        assertNull(result)
    }

    @Test
    fun `cancellation stops the walk`() = runBlocking {
        repeat(200) { File(root, "bulk/$it/deeper").mkdirs() }
        // The predicate runs once per directory, which makes it a hook to cancel mid-walk.
        val visited = AtomicInteger()
        lateinit var job: Job
        val scanner = DiskScanner(Dispatchers.IO, isRestricted = {
            if (visited.incrementAndGet() == 5) job.cancel()
            false
        })
        job = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { scanner.scan(root) }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
        // 406 directories exist; only the few already in flight may finish after the cancel.
        assertTrue("visited ${visited.get()}", visited.get() < 50)
    }
}
