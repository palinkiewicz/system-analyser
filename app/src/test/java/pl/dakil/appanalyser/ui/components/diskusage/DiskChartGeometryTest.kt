package pl.dakil.appanalyser.ui.components.diskusage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.appanalyser.domain.DiskNode
import kotlin.math.abs

class DiskChartGeometryTest {

    //  /r: a (750, of which deep 500) · b (200) · own files 50
    private val deep = DiskNode("deep", "/r/a/deep", 500, 1, 500, 1)
    private val a = DiskNode("a", "/r/a", 750, 2, 250, 1, children = listOf(deep))
    private val b = DiskNode("b", "/r/b", 200, 1, 200, 1)
    private val root = DiskNode("r", "/r", 1_000, 4, 50, 1, children = listOf(a, b))

    // -- Squarify -----------------------------------------------------------------------------

    @Test
    fun `squarified tiles are proportional, in bounds and non-overlapping`() {
        val bounds = Tile(0f, 0f, 400f, 300f)
        val weights = listOf(50.0, 25.0, 12.0, 8.0, 3.0, 2.0)
        val tiles = squarify(weights, bounds)
        val total = weights.sum()
        val area = bounds.width * bounds.height

        tiles.forEachIndexed { i, tile ->
            assertEquals(weights[i] / total * area, (tile.width * tile.height).toDouble(), area * 0.001)
            assertTrue(tile.left >= -0.01f && tile.top >= -0.01f)
            assertTrue(tile.right <= bounds.right + 0.01f && tile.bottom <= bounds.bottom + 0.01f)
        }
        for (i in tiles.indices) for (j in i + 1 until tiles.size) {
            val p = tiles[i]
            val q = tiles[j]
            val overlapW = minOf(p.right, q.right) - maxOf(p.left, q.left)
            val overlapH = minOf(p.bottom, q.bottom) - maxOf(p.top, q.top)
            assertTrue("tiles $i and $j overlap", overlapW <= 0.01f || overlapH <= 0.01f)
        }
        assertEquals(area.toDouble(), tiles.sumOf { (it.width * it.height).toDouble() }, area * 0.001)
    }

    @Test
    fun `zero weights get empty tiles`() {
        val tiles = squarify(listOf(10.0, 0.0, 5.0), Tile(0f, 0f, 100f, 100f))
        assertEquals(0f, tiles[1].width * tiles[1].height)
        assertTrue(tiles[0].width * tiles[0].height > 0)
    }

    @Test
    fun `tile mapping stretches one rect onto another`() {
        val mapped = Tile(10f, 10f, 20f, 20f).mapped(from = Tile(0f, 0f, 20f, 20f), to = Tile(0f, 0f, 100f, 200f))
        assertEquals(Tile(50f, 100f, 100f, 200f), mapped)
    }

    // -- Rings --------------------------------------------------------------------------------

    @Test
    fun `segments resolve by ring and angle`() {
        // Ring 1: a [0, .75) · b [.75, .95) · files [.95, 1)
        assertEquals(RingHit.Directory(a), segmentAt(root, 1, 0.1))
        assertEquals(RingHit.Directory(b), segmentAt(root, 1, 0.8))
        assertEquals(RingHit.Files(root), segmentAt(root, 1, 0.97))
        // Ring 2 under a: deep [0, .5) · a's files [.5, .75)
        assertEquals(RingHit.Directory(deep), segmentAt(root, 2, 0.2))
        assertEquals(RingHit.Files(a), segmentAt(root, 2, 0.6))
        // b has no subfolders, so its own files fill ring 2 beneath it.
        assertEquals(RingHit.Files(b), segmentAt(root, 2, 0.8))
        // Nothing sits beyond a files slice.
        assertEquals(RingHit.None, segmentAt(root, 2, 0.97))
        assertEquals(RingHit.None, segmentAt(root, 3, 0.8))
    }

    @Test
    fun `polar hit testing maps the centre, rings and outside`() {
        val center = 50f
        val ring = 20f
        assertEquals(RingHit.Center, hitTestRings(root, 10f, 10f, center, ring, rings = 4))
        // Straight up (twelve o'clock) in ring 1 is the start of a.
        assertEquals(RingHit.Directory(a), hitTestRings(root, 0f, -60f, center, ring, rings = 4))
        // Nine o'clock is 75% around: the start of b.
        assertEquals(RingHit.Directory(b), hitTestRings(root, -60f, -1f, center, ring, rings = 4))
        assertEquals(RingHit.None, hitTestRings(root, 0f, -200f, center, ring, rings = 4))
    }

    @Test
    fun `viewport of a descendant is its angular range and depth`() {
        val v = viewportOf(root, "/r/a/deep")!!
        assertEquals(0.0, v.start, 1e-9)
        assertEquals(0.5, v.end, 1e-9)
        assertEquals(2.0, v.depth, 1e-9)
        val vb = viewportOf(root, "/r/b")!!
        assertEquals(0.75, vb.start, 1e-9)
        assertEquals(0.95, vb.end, 1e-9)
        assertEquals(1.0, vb.depth, 1e-9)
    }

    @Test
    fun `layout drops arcs too narrow to see`() {
        val segments = layoutRings(root, RingViewport.FULL, RingViewport.FULL, rings = 4, minSweep = 0.1)
        // The 5% files slice of the root is below the 10% threshold.
        assertTrue(segments.none { it.isFiles && it.node === root })
        assertTrue(segments.any { it.node === deep })
        segments.forEach { assertTrue(abs(it.end - it.start) >= 0.1 - 1e-9) }
    }

    @Test
    fun `zoomed-in layout keeps the arcs visible at the target`() {
        val target = viewportOf(root, "/r/b")!!
        // b is 20% of the turn: its own files slice fills the zoomed view, so it must be kept.
        val segments = layoutRings(root, RingViewport.FULL, target, rings = 4, minSweep = 0.5)
        assertTrue(segments.any { it.isFiles && it.node === b })
    }
}
