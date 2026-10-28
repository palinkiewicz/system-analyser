package pl.dakil.appanalyser.ui.components.diskusage

import pl.dakil.appanalyser.domain.DiskNode
import kotlin.math.atan2
import kotlin.math.sqrt

/*
 * Pure geometry for the rings (sunburst) chart, kept free of Compose so it can be unit-tested.
 *
 * Angles are expressed as fractions of the layout root's full turn ([0, 1), clockwise from twelve
 * o'clock); rings as depths below that root (1 = innermost ring). A [RingViewport] picks which
 * part of that space fills the chart, which is all a zoom animation has to interpolate.
 */

/** One arc: a subdirectory, or (when [isFiles]) the loose files directly inside [node]. */
class RingSegment(
    val node: DiskNode,
    val isFiles: Boolean,
    val depth: Int,
    val start: Double,
    val end: Double,
)

/** The slice of the layout root shown as a full turn, with [depth] rings hidden inside the centre. */
data class RingViewport(val start: Double, val end: Double, val depth: Double) {
    val width: Double get() = end - start

    fun lerp(to: RingViewport, t: Double) = RingViewport(
        start = start + (to.start - start) * t,
        end = end + (to.end - end) * t,
        depth = depth + (to.depth - depth) * t,
    )

    companion object {
        val FULL = RingViewport(0.0, 1.0, 0.0)
    }
}

/**
 * Calls [action] for each slice of [node] within [start, end): subdirectories largest first, then
 * the node's own files. A null child means the files slice.
 */
inline fun forEachSlice(
    node: DiskNode,
    start: Double,
    end: Double,
    action: (child: DiskNode?, sliceStart: Double, sliceEnd: Double) -> Unit,
) {
    if (node.sizeBytes <= 0) return
    val scale = (end - start) / node.sizeBytes
    var cursor = start
    for (child in node.children) {
        if (child.sizeBytes <= 0) continue
        val next = cursor + child.sizeBytes * scale
        action(child, cursor, next)
        cursor = next
    }
    if (node.ownFilesBytes > 0) action(null, cursor, cursor + node.ownFilesBytes * scale)
}

/** Where [target] sits inside [root]'s layout; null when it isn't part of [root]. */
fun viewportOf(root: DiskNode, target: String): RingViewport? {
    val chain = root.chainTo(target)
    if (chain.isEmpty()) return null
    var start = 0.0
    var end = 1.0
    for (i in 1 until chain.size) {
        var found = false
        forEachSlice(chain[i - 1], start, end) { child, s, e ->
            if (!found && child === chain[i]) {
                start = s
                end = e
                found = true
            }
        }
        // An empty directory has no slice; collapse onto its parent's start.
        if (!found) end = start
    }
    return RingViewport(start, end, (chain.size - 1).toDouble())
}

/**
 * Every segment below [root] that is visible in [from] or [to], or on the way between them. Arcs
 * narrower than [minSweep] (a fraction of a full turn on screen) are dropped together with their
 * descendants, which bounds the work on a volume with tens of thousands of directories.
 */
fun layoutRings(
    root: DiskNode,
    from: RingViewport,
    to: RingViewport,
    rings: Int,
    minSweep: Double,
): List<RingSegment> {
    val out = ArrayList<RingSegment>()

    fun relevantIn(v: RingViewport, depth: Int, start: Double, end: Double): Boolean =
        depth <= v.depth + rings && end > v.start && start < v.end &&
            (end - start) >= minSweep * v.width

    fun visit(node: DiskNode, depth: Int, start: Double, end: Double) {
        forEachSlice(node, start, end) { child, s, e ->
            val inFrom = relevantIn(from, depth, s, e)
            val inTo = relevantIn(to, depth, s, e)
            if (!inFrom && !inTo) return@forEachSlice
            if (child == null) {
                out += RingSegment(node, isFiles = true, depth = depth, start = s, end = e)
            } else {
                out += RingSegment(child, isFiles = false, depth = depth, start = s, end = e)
                visit(child, depth + 1, s, e)
            }
        }
    }

    visit(root, 1, 0.0, 1.0)
    return out
}

/** What a tap on the chart landed on. */
sealed interface RingHit {
    data object Center : RingHit
    data class Directory(val node: DiskNode) : RingHit
    data class Files(val parent: DiskNode) : RingHit
    data object None : RingHit
}

/**
 * Converts a tap at ([dx], [dy]) from the chart centre into a ring index and a turn fraction.
 * Returns [RingHit.Center] inside the centre disc and [RingHit.None] outside the last ring;
 * otherwise resolves the segment under [focus].
 */
fun hitTestRings(
    focus: DiskNode,
    dx: Float,
    dy: Float,
    centerRadius: Float,
    ringWidth: Float,
    rings: Int,
): RingHit {
    val distance = sqrt(dx * dx + dy * dy)
    if (distance < centerRadius) return RingHit.Center
    val ring = ((distance - centerRadius) / ringWidth).toInt() + 1
    if (ring > rings) return RingHit.None
    // atan2(dx, -dy): 0 at twelve o'clock, growing clockwise in screen coordinates (y down).
    var degrees = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
    if (degrees < 0) degrees += 360.0
    return segmentAt(focus, ring, degrees / 360.0)
}

/** The segment at [ring] (1-based) and [fraction] of a full turn, laid out from [focus]. */
fun segmentAt(focus: DiskNode, ring: Int, fraction: Double): RingHit {
    var node = focus
    var start = 0.0
    var end = 1.0
    for (level in 1..ring) {
        var hit: DiskNode? = null
        var hitFiles = false
        forEachSlice(node, start, end) { child, s, e ->
            if (hit == null && !hitFiles && fraction >= s && fraction < e) {
                if (child == null) hitFiles = true else hit = child
                start = s
                end = e
            }
        }
        if (hitFiles) return if (level == ring) RingHit.Files(node) else RingHit.None
        node = hit ?: return RingHit.None
        if (level == ring) return RingHit.Directory(node)
    }
    return RingHit.None
}
