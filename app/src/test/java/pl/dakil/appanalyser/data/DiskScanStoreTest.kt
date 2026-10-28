package pl.dakil.appanalyser.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.dakil.appanalyser.domain.DiskFile
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import pl.dakil.appanalyser.domain.ScanCache
import java.io.File
import java.nio.file.Files

class DiskScanStoreTest {

    private lateinit var directory: File

    @Before
    fun setUp() {
        directory = Files.createTempDirectory("disk-scans").toFile()
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    private fun store(label: String = "Restricted") = DiskScanStore(directory) { label }

    private fun dir(path: String, own: Long, vararg children: DiskNode, restricted: Boolean = false) = DiskNode(
        name = path.substringAfterLast('/'),
        path = path,
        sizeBytes = own + children.sumOf { it.sizeBytes },
        fileCount = 1 + children.sumOf { it.fileCount },
        ownFilesBytes = own,
        ownFileCount = 1,
        restricted = restricted,
        children = children.sortedByDescending { it.sizeBytes },
    )

    private val volume = dir(
        "/storage/emulated/0", 10,
        dir("/storage/emulated/0/Music", 80, dir("/storage/emulated/0/Music/Album", 30)),
        dir("/storage/emulated/0/Android", 0, dir("/storage/emulated/0/Android/obb", 0, restricted = true)),
    ).withRestrictedSpace(500, "Ograniczone")

    private val volumeScan = DiskScan(
        root = volume,
        largestFiles = listOf(DiskFile("song.mp3", "/storage/emulated/0/Music/song.mp3", 80, 1234)),
        scannedAt = 1_000,
        stale = true,
        volumeTotalBytes = 6_000,
        volumeFreeBytes = 4_000,
    )

    private fun files() = directory.listFiles().orEmpty().filter { it.name.endsWith(".scan") }

    @Test
    fun `a scan survives a round trip`() {
        store().sync(listOf(volumeScan))
        val loaded = store(label = "Restricted by Android").load(limit = 8, largestLimit = 10).single()

        assertEquals(volumeScan.scannedAt, loaded.scannedAt)
        assertTrue(loaded.stale)
        assertEquals(6_000L, loaded.volumeTotalBytes)
        assertEquals(4_000L, loaded.volumeFreeBytes)
        assertEquals(volumeScan.largestFiles, loaded.largestFiles)

        assertEquals(volume.sizeBytes, loaded.root.sizeBytes)
        assertEquals(volume.fileCount, loaded.root.fileCount)
        assertEquals("0", loaded.root.name)
        val album = loaded.root.find("/storage/emulated/0/Music/Album")!!
        assertEquals(30L, album.sizeBytes)
        assertEquals("Album", album.name)
        assertTrue(loaded.root.find("/storage/emulated/0/Android/obb")!!.restricted)
        assertTrue(loaded.root.find("/storage/emulated/0/Android")!!.sizeUnknown)

        // The synthetic entry keeps its path and flag, and is named in the current language.
        val restricted = loaded.root.children.single { it.isRestrictedSpace }
        assertEquals(volume.children.single { it.isRestrictedSpace }.path, restricted.path)
        assertEquals("Restricted by Android", restricted.name)
        assertEquals(500L, restricted.sizeBytes)
    }

    @Test
    fun `files mirror the remembered scans and superseded ones are deleted`() {
        val music = DiskScan(dir("/storage/emulated/0/Music", 5), emptyList(), scannedAt = 1)
        val docs = DiskScan(dir("/storage/emulated/0/Documents", 5), emptyList(), scannedAt = 2)
        val store = store()
        var scans = ScanCache.add(ScanCache.add(emptyList(), music, 8, 10), docs, 8, 10)
        store.sync(scans)
        assertEquals(2, files().size)

        // Scanning the whole volume replaces both: only its file is left.
        scans = ScanCache.add(scans, volumeScan.copy(stale = false), 8, 10)
        store.sync(scans)
        assertEquals(1, files().size)
        assertEquals(listOf("/storage/emulated/0"), store().load(8, 10).map { it.root.path })
    }

    @Test
    fun `a rescan of the same folder leaves one file with the newest result`() {
        val store = store()
        val first = DiskScan(dir("/storage/emulated/0/Music", 5), emptyList(), scannedAt = 1)
        val second = DiskScan(dir("/storage/emulated/0/Music", 99), emptyList(), scannedAt = 2)
        store.sync(ScanCache.add(emptyList(), first, 8, 10))
        store.sync(ScanCache.add(listOf(first), second, 8, 10))
        assertEquals(1, files().size)
        assertEquals(99L, store().load(8, 10).single().root.sizeBytes)
    }

    @Test
    fun `unreadable files and leftovers are deleted on load`() {
        store().sync(listOf(volumeScan))
        File(directory, "garbage.scan").writeText("not a scan")
        File(directory, "half-written.scan.tmp").writeText("partial")

        val loaded = store().load(8, 10)
        assertEquals(1, loaded.size)
        assertEquals(1, files().size)
        assertFalse(File(directory, "half-written.scan.tmp").exists())
    }

    @Test
    fun `an empty or missing directory loads nothing`() {
        directory.deleteRecursively()
        assertTrue(store().load(8, 10).isEmpty())
    }
}
