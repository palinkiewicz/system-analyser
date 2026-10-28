package pl.dakil.appanalyser.ui.components.diskusage

import pl.dakil.appanalyser.domain.DiskNode
import kotlin.math.max
import kotlin.math.min

/*
 * Pure layout for the treemap, kept free of Compose so it can be unit-tested.
 */

/** An axis-aligned rectangle in pixels. */
data class Tile(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    fun inset(by: Float) = Tile(left + by, top + by, right - by, bottom - by)

    fun lerp(to: Tile, t: Float) = Tile(
        left = left + (to.left - left) * t,
        top = top + (to.top - top) * t,
        right = right + (to.right - right) * t,
        bottom = bottom + (to.bottom - bottom) * t,
    )

    /** This tile expressed in the space where [from] is stretched to fill [to]. */
    fun mapped(from: Tile, to: Tile): Tile {
        val sx = if (from.width > 0) to.width / from.width else 0f
        val sy = if (from.height > 0) to.height / from.height else 0f
        return Tile(
            left = to.left + (left - from.left) * sx,
            top = to.top + (top - from.top) * sy,
            right = to.left + (right - from.left) * sx,
            bottom = to.top + (bottom - from.top) * sy,
        )
    }
}

/**
 * Squarified treemap (Bruls, Huizing & van Wijk): tiles [weights] into [bounds] with areas
 * proportional to each weight, keeping aspect ratios close to square. Results are in input order;
 * weights should be sorted largest first for the best layout. Non-positive weights get an empty
 * tile at the bounds' corner.
 */
fun squarify(weights: List<Double>, bounds: Tile): List<Tile> {
    val empty = Tile(bounds.left, bounds.top, bounds.left, bounds.top)
    val result = MutableList(weights.size) { empty }
    val indices = weights.indices.filter { weights[it] > 0 }
    val total = indices.sumOf { weights[it] }
    if (indices.isEmpty() || total <= 0 || bounds.width <= 0 || bounds.height <= 0) return result

    val scale = bounds.width.toDouble() * bounds.height / total
    val areas = indices.map { weights[it] * scale }

    var left = bounds.left.toDouble()
    var top = bounds.top.toDouble()
    var right = bounds.right.toDouble()
    var bottom = bounds.bottom.toDouble()

    fun worst(from: Int, to: Int, sum: Double, side: Double): Double {
        var smallest = Double.MAX_VALUE
        var largest = 0.0
        for (k in from until to) {
            smallest = min(smallest, areas[k])
            largest = max(largest, areas[k])
        }
        val side2 = side * side
        val sum2 = sum * sum
        return max(side2 * largest / sum2, sum2 / (side2 * smallest))
    }

    var i = 0
    while (i < areas.size) {
        val width = right - left
        val height = bottom - top
        val side = min(width, height)
        var rowSum = areas[i]
        var j = i + 1
        var current = worst(i, j, rowSum, side)
        while (j < areas.size) {
            val next = worst(i, j + 1, rowSum + areas[j], side)
            if (next > current) break
            rowSum += areas[j]
            current = next
            j++
        }
        // The last row takes whatever is left, so rounding never leaves a sliver uncovered.
        val last = j == areas.size
        if (width >= height) {
            // A column along the left edge.
            val thickness = if (last) width else rowSum / height
            var y = top
            for (k in i until j) {
                val h = if (k == j - 1) bottom - y else areas[k] / thickness
                result[indices[k]] = Tile(left.toFloat(), y.toFloat(), (left + thickness).toFloat(), (y + h).toFloat())
                y += h
            }
            left += thickness
        } else {
            // A row along the top edge.
            val thickness = if (last) height else rowSum / width
            var x = left
            for (k in i until j) {
                val w = if (k == j - 1) right - x else areas[k] / thickness
                result[indices[k]] = Tile(x.toFloat(), top.toFloat(), (x + w).toFloat(), (top + thickness).toFloat())
                x += w
            }
            top += thickness
        }
        i = j
    }
    return result
}

/** One drawn treemap tile: a subdirectory, or (when [isFiles]) the loose files inside [node]. */
class TreemapTile(
    val node: DiskNode,
    val isFiles: Boolean,
    /** 1 for children of the focus, 2 for their children. */
    val depth: Int,
    val rect: Tile,
    /** Index of the depth-1 ancestor among the focus's children; -1 for files tiles. */
    val colorIndex: Int,
)

/**
 * Two-level treemap of [focus]: its children fill [bounds], and any child tile large enough gets a
 * [header] strip for its label with its own children nested underneath.
 */
fun layoutTreemap(
    focus: DiskNode,
    bounds: Tile,
    gap: Float,
    header: Float,
    minNested: Float,
    minTile: Float,
): List<TreemapTile> {
    val out = ArrayList<TreemapTile>()

    fun slices(node: DiskNode): List<Pair<DiskNode?, Double>> {
        val list = node.children.filter { it.sizeBytes > 0 }.map { it as DiskNode? to it.sizeBytes.toDouble() }
        val files = node.ownFilesBytes.toDouble()
        return if (files > 0) {
            (list + (null to files)).sortedByDescending { it.second }
        } else {
            list
        }
    }

    val top = slices(focus)
    val topRects = squarify(top.map { it.second }, bounds)
    // Children arrive largest first, and the files slice is the only thing inserted between them,
    // so a running count of directories is each one's index in focus.children.
    var dirIndex = 0
    top.forEachIndexed { index, (child, _) ->
        val rect = topRects[index].inset(gap / 2)
        val colorIndex = if (child == null) -1 else dirIndex++
        if (rect.width < minTile || rect.height < minTile) return@forEachIndexed
        out += TreemapTile(child ?: focus, child == null, 1, rect, colorIndex)
        if (child == null || rect.width < minNested || rect.height < header + minNested) return@forEachIndexed

        val inner = Tile(rect.left + gap, rect.top + header, rect.right - gap, rect.bottom - gap)
        val nested = slices(child)
        val nestedRects = squarify(nested.map { it.second }, inner)
        nested.forEachIndexed { k, (grandChild, _) ->
            val r = nestedRects[k].inset(gap / 2)
            if (r.width < minTile || r.height < minTile) return@forEachIndexed
            out += TreemapTile(grandChild ?: child, grandChild == null, 2, r, colorIndex)
        }
    }
    return out
}
