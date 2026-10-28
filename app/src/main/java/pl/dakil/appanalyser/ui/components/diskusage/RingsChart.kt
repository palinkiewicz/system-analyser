package pl.dakil.appanalyser.ui.components.diskusage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.domain.DiskNode
import kotlin.math.max
import kotlin.math.min

/** Rings drawn around the centre disc. */
private const val RINGS = 4

/** Centre disc radius as a fraction of the chart radius. */
private const val CENTER_FRACTION = 0.32f

/** Arcs narrower than this on screen are skipped (and so are their descendants). */
private const val MIN_SWEEP_DEGREES = 0.6

private const val ZOOM_DURATION_MS = 500

/** Material 3 "emphasized" easing. */
internal val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Baobab-style rings chart of [focus]: each ring is one level deeper, each arc's sweep its share of
 * the focus. Tapping an arc opens that folder, tapping the centre steps up. Changing [focus]
 * zooms: the arcs are laid out once from the common ancestor and only the viewport is animated,
 * read inside the draw phase so a frame never recomposes.
 *
 * [root] is the top of what the caller lets the user browse; it is only used to find the common
 * ancestor of the old and new focus. [centerContent] sits inside the centre disc.
 */
@Composable
fun RingsChart(
    root: DiskNode,
    focus: DiskNode,
    palette: ChartPalette,
    onOpen: (DiskNode) -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit = {},
) {
    val centerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val progress = remember { Animatable(1f) }
    var scene by remember { mutableStateOf<RingScene?>(null) }
    var shownFocus by remember { mutableStateOf<DiskNode?>(null) }

    LaunchedEffect(focus, root, palette) {
        // Same path means the tree was patched (a delete) rather than navigated: no zoom.
        val previous = shownFocus?.takeIf { it.path != focus.path }
        shownFocus = focus
        val settled = { RingScene.build(focus, focus, focus, RingViewport.FULL, RingViewport.FULL, palette) }
        val common = previous?.let { commonAncestor(root, it.path, focus.path) }
        val from = if (previous != null && common != null) viewportOf(common, previous.path) else null
        val to = common?.let { viewportOf(it, focus.path) }
        if (previous == null || common == null || from == null || to == null) {
            scene = settled()
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        scene = RingScene.build(common, common.find(previous.path) ?: previous, focus, from, to, palette)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ZOOM_DURATION_MS, easing = EmphasizedEasing))
        // Re-laid out from the focus itself: far fewer arcs than from the ancestor.
        scene = settled()
    }

    val currentFocus by rememberUpdatedState(focus)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnUp by rememberUpdatedState(onUp)

    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val radius = min(size.width, size.height) / 2f
                        val centerRadius = radius * CENTER_FRACTION
                        val hit = hitTestRings(
                            focus = currentFocus,
                            dx = position.x - size.width / 2f,
                            dy = position.y - size.height / 2f,
                            centerRadius = centerRadius,
                            ringWidth = (radius - centerRadius) / RINGS,
                            rings = RINGS,
                        )
                        when (hit) {
                            RingHit.Center -> currentOnUp()
                            is RingHit.Directory -> currentOnOpen(hit.node)
                            else -> Unit
                        }
                    }
                }
        ) {
            val current = scene ?: return@Canvas
            val t = progress.value
            val viewport = current.from.lerp(current.to, t.toDouble())
            val radius = size.minDimension / 2f
            val centerRadius = radius * CENTER_FRACTION
            val ringWidth = (radius - centerRadius) / RINGS
            val gap = 1.5.dp.toPx()
            val width = viewport.width
            if (width > 0) {
                current.segments.forEachIndexed { i, segment ->
                    // Rings sliding in from the edge or into the centre are clipped, not overlapped.
                    val ring = (segment.depth - viewport.depth).toFloat()
                    val inner = max(centerRadius + (ring - 1) * ringWidth, centerRadius)
                    val outer = min(centerRadius + ring * ringWidth, radius)
                    val thickness = outer - inner - gap
                    if (thickness < 1f) return@forEachIndexed

                    val a0 = ((segment.start - viewport.start) / width).coerceIn(0.0, 1.0)
                    val a1 = ((segment.end - viewport.start) / width).coerceIn(0.0, 1.0)
                    val sweep = ((a1 - a0) * 360.0).toFloat()
                    if (sweep < MIN_DRAW_SWEEP) return@forEachIndexed

                    val mid = (inner + outer) / 2f
                    val gapDegrees = if (sweep >= 359.9f) {
                        0f
                    } else {
                        min(Math.toDegrees((gap / mid).toDouble()).toFloat(), sweep / 2f)
                    }
                    drawArc(
                        color = lerp(current.fromColors[i], current.toColors[i], t),
                        startAngle = -90f + (a0 * 360.0).toFloat() + gapDegrees / 2f,
                        sweepAngle = sweep - gapDegrees,
                        useCenter = false,
                        topLeft = Offset(center.x - mid, center.y - mid),
                        size = Size(mid * 2f, mid * 2f),
                        style = Stroke(thickness),
                    )
                }
            }
            drawCircle(centerColor, radius = centerRadius - gap, center = center)
        }
        Box(
            // Roughly the square inscribed in the centre disc (whose diameter is CENTER_FRACTION).
            modifier = Modifier.fillMaxSize(CENTER_FRACTION * 0.85f),
            contentAlignment = Alignment.Center,
        ) {
            centerContent()
        }
    }
}

/** Minimum sweep, in degrees, worth a draw call. */
private const val MIN_DRAW_SWEEP = 0.3f

/** The deepest node that contains both paths, or null if either isn't under [root]. */
private fun commonAncestor(root: DiskNode, a: String, b: String): DiskNode? {
    val chainA = root.chainTo(a)
    val chainB = root.chainTo(b)
    if (chainA.isEmpty() || chainB.isEmpty()) return null
    var common: DiskNode? = null
    for (i in 0 until min(chainA.size, chainB.size)) {
        if (chainA[i].path != chainB[i].path) break
        common = chainA[i]
    }
    return common
}

/** Arcs for one zoom (or a settled view), with their colours under both the old and new focus. */
private class RingScene(
    val from: RingViewport,
    val to: RingViewport,
    val segments: List<RingSegment>,
    val fromColors: List<Color>,
    val toColors: List<Color>,
) {
    companion object {
        fun build(
            layoutRoot: DiskNode,
            fromFocus: DiskNode,
            toFocus: DiskNode,
            from: RingViewport,
            to: RingViewport,
            palette: ChartPalette,
        ): RingScene {
            val segments = layoutRings(layoutRoot, from, to, RINGS, MIN_SWEEP_DEGREES / 360.0)
            return RingScene(
                from = from,
                to = to,
                segments = segments,
                fromColors = colorsFor(segments, fromFocus, from.depth.toInt(), palette),
                toColors = colorsFor(segments, toFocus, to.depth.toInt(), palette),
            )
        }

        /**
         * Colours as they look with [focus] at the centre: each child of the focus gets its own
         * hue, inherited (and faded) by everything underneath; loose files take their folder's
         * hue washed out. Arcs outside the focus — visible only mid-zoom — stay neutral.
         */
        private fun colorsFor(
            segments: List<RingSegment>,
            focus: DiskNode,
            focusDepth: Int,
            palette: ChartPalette,
        ): List<Color> {
            val indexByName = HashMap<String, Int>(focus.children.size * 2)
            focus.children.forEachIndexed { i, child -> indexByName[child.name] = i }
            val prefix = "${focus.path}/"
            return segments.map { segment ->
                val relativeDepth = (segment.depth - focusDepth).coerceAtLeast(1)
                val path = segment.node.path
                // The focus's own loose files (node == focus) have no folder hue to borrow.
                val index = if (path.startsWith(prefix)) {
                    indexByName[path.substring(prefix.length).substringBefore('/')] ?: -1
                } else {
                    -1
                }
                when {
                    segment.isFiles -> palette.filesColor(index, relativeDepth)
                    segment.node.isRestrictedSpace -> palette.restricted
                    else -> palette.color(index, relativeDepth)
                }
            }
        }
    }
}
