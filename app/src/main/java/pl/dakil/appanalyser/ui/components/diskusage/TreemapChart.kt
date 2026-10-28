package pl.dakil.appanalyser.ui.components.diskusage

import android.text.format.Formatter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.domain.DiskNode

private const val ZOOM_DURATION_MS = 450

/**
 * Two-level squarified treemap of [focus]. Tapping a tile opens that folder (a nested tile opens
 * its parent's child, i.e. the tile it sits in). Changing [focus] to a descendant or ancestor
 * zooms: a camera rectangle moves between the full chart and the tapped tile while the incoming
 * level fades in over (or the outgoing one out of) that tile.
 */
@Composable
fun TreemapChart(
    focus: DiskNode,
    palette: ChartPalette,
    filesLabel: String,
    onOpen: (DiskNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(1f) }
    var scene by remember { mutableStateOf<TreemapScene?>(null) }
    var shown by remember { mutableStateOf<TreemapLevel?>(null) }

    LaunchedEffect(focus, canvasSize, palette) {
        if (canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect
        val level = with(density) {
            TreemapLevel.build(
                focus = focus,
                size = canvasSize,
                gap = 2.dp.toPx(),
                header = 22.dp.toPx(),
                minNested = 48.dp.toPx(),
                minTile = 2.dp.toPx(),
                padding = 6.dp.toPx(),
                labels = LabelSpec(textMeasurer, labelStyle, filesLabel) {
                    Formatter.formatShortFileSize(context, it)
                },
            )
        }
        val previous = shown
        shown = level
        val full = level.bounds
        val transition = previous?.takeIf { it.focus.path != focus.path }?.let { old ->
            when {
                // Zooming in: the camera closes in on the tapped tile of the old level.
                old.focus.contains(focus.path) -> old.anchorFor(focus.path)?.let { anchor ->
                    TreemapScene(bottom = old, top = level, anchor = anchor, cameraFrom = full, cameraTo = anchor, topFadesIn = true)
                }
                // Zooming out: the camera pulls back from the old focus's tile in the new level.
                focus.contains(old.focus.path) -> level.anchorFor(old.focus.path)?.let { anchor ->
                    TreemapScene(bottom = level, top = old, anchor = anchor, cameraFrom = anchor, cameraTo = full, topFadesIn = false)
                }
                else -> null
            }
        }
        if (transition == null) {
            scene = TreemapScene.settled(level)
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        scene = transition
        progress.snapTo(0f)
        progress.animateTo(1f, tween(ZOOM_DURATION_MS, easing = EmphasizedEasing))
        scene = TreemapScene.settled(level)
    }

    val currentOnOpen by rememberUpdatedState(onOpen)

    Canvas(
        modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val level = shown ?: return@detectTapGestures
                    // Nested tiles are listed after their parent, so the last hit is the deepest.
                    val tile = level.tiles.lastOrNull { it.rect.contains(position.x, position.y) }
                        ?: return@detectTapGestures
                    // The focus's own files lead nowhere. A nested files tile's node is the folder
                    // it sits in, so tapping it opens that folder.
                    if (!(tile.depth == 1 && tile.isFiles)) currentOnOpen(tile.node)
                }
            }
    ) {
        val current = scene ?: return@Canvas
        val t = progress.value
        val camera = current.cameraFrom.lerp(current.cameraTo, t)
        val full = current.bottom.bounds
        drawLevel(current.bottom, palette, alpha = 1f) { it.mapped(camera, full) }
        val top = current.top ?: return@Canvas
        val anchor = current.anchor ?: return@Canvas
        // Fade quickly so the incoming level reads as "opening" rather than ghosting.
        val alpha = (if (current.topFadesIn) t else 1f - t).let { (it * 1.6f).coerceIn(0f, 1f) }
        drawLevel(top, palette, alpha) { it.mapped(full, anchor).mapped(camera, full) }
    }
}

/** Everything needed to draw one focus: tiles plus their pre-measured labels. */
private class TreemapLevel(
    val focus: DiskNode,
    val bounds: Tile,
    val tiles: List<TreemapTile>,
    val labels: List<TextLayoutResult?>,
    val corner: Float,
    val padding: Float,
) {
    /** The deepest folder tile containing [path], to zoom into or out of. */
    fun anchorFor(path: String): Tile? =
        tiles.lastOrNull { !it.isFiles && it.node.contains(path) }?.rect

    companion object {
        fun build(
            focus: DiskNode,
            size: IntSize,
            gap: Float,
            header: Float,
            minNested: Float,
            minTile: Float,
            padding: Float,
            labels: LabelSpec,
        ): TreemapLevel {
            val bounds = Tile(0f, 0f, size.width.toFloat(), size.height.toFloat())
            val tiles = layoutTreemap(focus, bounds, gap, header, minNested, minTile)
            return TreemapLevel(
                focus = focus,
                bounds = bounds,
                tiles = tiles,
                labels = tiles.map { labels.measure(it, padding) },
                corner = gap * 3,
                padding = padding,
            )
        }
    }
}

/** Measures labels once per layout; tiles too small to hold a name get none. */
private class LabelSpec(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val filesLabel: String,
    private val formatSize: (Long) -> String,
) {
    /**
     * One line, so it always fits a nested tile's header strip: "name · size" on top-level tiles,
     * just the name on nested ones.
     */
    fun measure(tile: TreemapTile, padding: Float): TextLayoutResult? {
        val maxWidth = (tile.rect.width - padding * 2).toInt()
        if (maxWidth <= 0) return null
        val name = if (tile.isFiles) filesLabel else tile.node.name
        val text = if (tile.depth == 1) {
            val bytes = if (tile.isFiles) tile.node.ownFilesBytes else tile.node.sizeBytes
            "$name · ${formatSize(bytes)}"
        } else {
            name
        }
        val layout = measurer.measure(
            text = text,
            style = style,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidth),
        )
        // A label that can show only an ellipsis is noise.
        val tooSmall = layout.size.height + padding > tile.rect.height || maxWidth < layout.size.height * 2
        return layout.takeUnless { tooSmall }
    }
}

/** A zoom in progress, or a settled level when [top] is null. */
private class TreemapScene(
    val bottom: TreemapLevel,
    val top: TreemapLevel?,
    val anchor: Tile?,
    val cameraFrom: Tile,
    val cameraTo: Tile,
    val topFadesIn: Boolean,
) {
    companion object {
        fun settled(level: TreemapLevel) = TreemapScene(
            bottom = level,
            top = null,
            anchor = null,
            cameraFrom = level.bounds,
            cameraTo = level.bounds,
            topFadesIn = true,
        )
    }
}

private fun DrawScope.drawLevel(
    level: TreemapLevel,
    palette: ChartPalette,
    alpha: Float,
    transform: (Tile) -> Tile,
) {
    if (alpha <= 0f) return
    level.tiles.forEachIndexed { i, tile ->
        val rect = transform(tile.rect)
        if (rect.right < 0 || rect.bottom < 0 || rect.left > size.width || rect.top > size.height) {
            return@forEachIndexed
        }
        val color = when {
            tile.isFiles -> palette.filesColor(if (tile.depth == 1) -1 else tile.colorIndex, tile.depth)
            tile.node.isRestrictedSpace -> palette.restricted
            else -> palette.color(tile.colorIndex, tile.depth)
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            cornerRadius = CornerRadius(level.corner / tile.depth),
            alpha = alpha,
        )
        val label = level.labels[i] ?: return@forEachIndexed
        // Labels keep their measured size; skip them while a zooming tile is still too small.
        if (rect.width < label.size.width + level.padding || rect.height < label.size.height + level.padding) {
            return@forEachIndexed
        }
        drawText(
            textLayoutResult = label,
            color = palette.contentColorFor(color),
            topLeft = Offset(rect.left + level.padding, rect.top + level.padding / 2),
            alpha = alpha,
        )
    }
}
