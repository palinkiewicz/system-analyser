package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanCacheTest {

    private fun dir(path: String, own: Long, vararg children: DiskNode) = DiskNode(
        name = path.substringAfterLast('/'),
        path = path,
        sizeBytes = own + children.sumOf { it.sizeBytes },
        fileCount = 1 + children.sumOf { it.fileCount },
        ownFilesBytes = own,
        ownFileCount = 1,
        children = children.sortedByDescending { it.sizeBytes },
    )

    private fun scan(root: DiskNode, at: Long, vararg largest: DiskFile) =
        DiskScan(root, largest.toList(), scannedAt = at)

    private val music = dir("/v/Music", 100)
    private val docs = dir("/v/Documents", 50)
    private val volume = dir("/v", 10, dir("/v/Music", 80), dir("/v/Documents", 40))

    private fun add(scans: List<DiskScan>, scan: DiskScan, limit: Int = 8) =
        ScanCache.add(scans, scan, limit, largestLimit = 10)

    @Test
    fun `unrelated folders are both remembered`() {
        val scans = add(add(emptyList(), scan(music, 1)), scan(docs, 2))
        assertEquals(2, scans.size)
        assertEquals("/v/Music", ScanCache.newestCovering(scans, "/v/Music/song.mp3")?.root?.path)
        assertEquals("/v/Documents", ScanCache.newestCovering(scans, "/v/Documents")?.root?.path)
        assertNull(ScanCache.newestCovering(scans, "/v/Pictures"))
    }

    @Test
    fun `a scan that contains older ones replaces them`() {
        val scans = add(add(add(emptyList(), scan(music, 1)), scan(docs, 2)), scan(volume, 3))
        assertEquals(listOf("/v"), scans.map { it.root.path })
    }

    @Test
    fun `rescanning the same folder replaces it`() {
        val newer = dir("/v/Music", 300)
        val scans = add(add(emptyList(), scan(music, 1)), scan(newer, 2))
        assertSame(newer, scans.single().root)
    }

    @Test
    fun `a newer scan inside a bigger one is kept and grafted into it`() {
        val bigger = scan(volume, 1, DiskFile("old.mp3", "/v/Music/old.mp3", 70, 0))
        val fresh = scan(music, 2, DiskFile("new.mp3", "/v/Music/new.mp3", 90, 0))
        val scans = add(add(emptyList(), bigger), fresh)

        assertEquals(2, scans.size)
        // Lookups prefer the newest covering scan.
        assertSame(fresh, ScanCache.newestCovering(scans, "/v/Music"))
        val grafted = scans.first { it.root.path == "/v" }
        assertEquals(100L, grafted.root.find("/v/Music")!!.sizeBytes)
        // 10 own + 100 Music + 40 Documents.
        assertEquals(150L, grafted.root.sizeBytes)
        assertEquals(listOf("/v/Music/new.mp3"), grafted.largestFiles.map { it.path })
    }

    @Test
    fun `grafting a folder that didn't exist at scan time adds it under its parent`() {
        val pictures = dir("/v/Pictures", 25)
        val grafted = volume.withSubtree(pictures)
        assertEquals(volume.sizeBytes + 25, grafted.sizeBytes)
        assertSame(pictures, grafted.find("/v/Pictures"))
        // An unknown intermediate folder is left alone rather than invented.
        assertSame(volume, volume.withSubtree(dir("/v/New/Deep", 5)))
    }

    @Test
    fun `the oldest scan goes once the limit is reached`() {
        val scans = add(add(add(emptyList(), scan(music, 1)), scan(docs, 2)), scan(dir("/v/Pictures", 1), 3), limit = 2)
        assertEquals(listOf("/v/Pictures", "/v/Documents"), scans.map { it.root.path })
    }

    @Test
    fun `stale marking only touches scans covering the change`() {
        val scans = add(add(emptyList(), scan(music, 1)), scan(docs, 2))
        val marked = ScanCache.markStale(scans, listOf("/v/Music/new.txt"))
        assertTrue(marked.first { it.root.path == "/v/Music" }.stale)
        assertFalse(marked.first { it.root.path == "/v/Documents" }.stale)
        // Deleting or renaming an ancestor of a scanned folder affects it too.
        assertTrue(ScanCache.markStale(scans, listOf("/v")).all { it.stale })
    }

    @Test
    fun `deletes are patched into every scan containing them`() {
        val bigger = scan(volume, 1)
        val fresh = scan(music, 2)
        val scans = ScanCache.applyDeleted(
            listOf(fresh, bigger),
            listOf(RemovedEntry("/v/Music/song.mp3", isDirectory = false, sizeBytes = 20, fileCount = 1)),
        )
        assertEquals(80L, scans.first { it.root.path == "/v/Music" }.root.sizeBytes)
        assertEquals(110L, scans.first { it.root.path == "/v" }.root.sizeBytes)
    }
}
