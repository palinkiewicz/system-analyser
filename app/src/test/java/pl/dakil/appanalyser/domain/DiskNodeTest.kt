package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DiskNodeTest {

    //  /r (files 10)
    //  ├── a (files 100)
    //  │   └── deep (files 500)
    //  └── b (files 300)
    private val deep = DiskNode("deep", "/r/a/deep", 500, 5, 500, 5)
    private val a = DiskNode("a", "/r/a", 600, 7, 100, 2, children = listOf(deep))
    private val b = DiskNode("b", "/r/b", 300, 3, 300, 3)
    private val root = DiskNode("r", "/r", 910, 11, 10, 1, children = listOf(a, b))

    @Test
    fun `find walks down to the node and rejects lookalike prefixes`() {
        assertSame(deep, root.find("/r/a/deep"))
        assertSame(root, root.find("/r"))
        assertNull(root.find("/r/ab"))
        assertNull(root.find("/elsewhere"))
    }

    @Test
    fun `contains requires a path boundary`() {
        assertTrue(root.contains("/r/a/deep/file.txt"))
        assertFalse(a.contains("/r/ab"))
    }

    @Test
    fun `chainTo lists every node from the root to the target`() {
        assertEquals(listOf("/r", "/r/a", "/r/a/deep"), root.chainTo("/r/a/deep").map { it.path })
        assertTrue(root.chainTo("/nope").isEmpty())
    }

    @Test
    fun `removing a directory subtracts along the ancestor chain only`() {
        val patched = root.without(RemovedEntry("/r/a/deep", isDirectory = true, sizeBytes = 500, fileCount = 5))
        assertEquals(410L, patched.sizeBytes)
        assertEquals(6, patched.fileCount)
        assertEquals(10L, patched.ownFilesBytes)
        val newA = patched.find("/r/a")!!
        assertEquals(100L, newA.sizeBytes)
        assertTrue(newA.children.isEmpty())
        // Untouched subtrees are shared, not copied.
        assertSame(b, patched.find("/r/b"))
        // Shrinking a below b re-sorts the children.
        assertEquals(listOf("b", "a"), patched.children.map { it.name })
    }

    @Test
    fun `removing a file subtracts from its directory's own files`() {
        val patched = root.without(RemovedEntry("/r/b/video.mp4", isDirectory = false, sizeBytes = 200, fileCount = 1))
        val newB = patched.find("/r/b")!!
        assertEquals(100L, newB.sizeBytes)
        assertEquals(100L, newB.ownFilesBytes)
        assertEquals(2, newB.ownFileCount)
        assertEquals(710L, patched.sizeBytes)
        assertEquals(10L, patched.ownFilesBytes)
    }

    @Test
    fun `removing something outside the tree is a no-op`() {
        assertSame(root, root.without(RemovedEntry("/other/x", isDirectory = false, sizeBytes = 1, fileCount = 1)))
    }

    @Test
    fun `restricted space joins the root as a sorted, synthetic child`() {
        val patched = root.withRestrictedSpace(2_000, "Restricted")
        assertEquals(2_910L, patched.sizeBytes)
        assertEquals(11, patched.fileCount)
        val restricted = patched.children.first()
        assertTrue(restricted.isRestrictedSpace)
        assertEquals("Restricted", restricted.name)
        assertSame(restricted, patched.find(restricted.path))
        // The synthetic node survives later deletes untouched.
        val afterDelete = patched.without(RemovedEntry("/r/b", isDirectory = true, sizeBytes = 300, fileCount = 3))
        assertEquals(2_000L, afterDelete.children.first { it.isRestrictedSpace }.sizeBytes)
    }

    @Test
    fun `no restricted space for a non-positive gap`() {
        assertSame(root, root.withRestrictedSpace(0, "Restricted"))
        assertSame(root, root.withRestrictedSpace(-5, "Restricted"))
    }

    @Test
    fun `a restricted folder with nothing visible has an unknown size`() {
        assertTrue(DiskNode("obb", "/r/obb", 0, 0, 0, 0, restricted = true).sizeUnknown)
        assertFalse(DiskNode("data", "/r/data", 10, 1, 10, 1, restricted = true).sizeUnknown)
        assertFalse(b.sizeUnknown)
        val obb = DiskNode("obb", "/r/android/obb", 0, 0, 0, 0, restricted = true)
        assertTrue(DiskNode("android", "/r/android", 0, 0, 0, 0, children = listOf(obb)).sizeUnknown)
    }

    @Test
    fun `freshness depends on coverage and the age limit`() {
        val hour = 3_600_000L
        val scan = DiskScan(root, emptyList(), scannedAt = 10 * hour)
        assertEquals(ScanFreshness.None, ScanFreshness.of(null, "/r/a", 6, 11 * hour))
        assertEquals(ScanFreshness.None, ScanFreshness.of(scan, "/elsewhere", 6, 11 * hour))
        assertEquals(ScanFreshness.Fresh, ScanFreshness.of(scan, "/r/a", 6, 16 * hour))
        assertEquals(ScanFreshness.Stale(10 * hour), ScanFreshness.of(scan, "/r/a", 6, 16 * hour + 1))
        // 0 means a scan never goes stale.
        assertEquals(ScanFreshness.Fresh, ScanFreshness.of(scan, "/r/a", 0, 1_000 * hour))
    }
}
