package pl.dakil.appanalyser.ui.components.homegrid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope
import pl.dakil.appanalyser.domain.HomeWidget

/** Cell-space placement of a widget produced by [packWidgets]. */
data class GridRect(val col: Int, val row: Int, val colSpan: Int, val rowSpan: Int)

/** Pixel-space result of the last layout pass, used for drag hit-testing and the resize overlay. */
data class GridGeometry(
    val columns: Int,
    val cellWidth: Float,
    val gap: Float,
    val rowHeights: List<Float>,
    val rowTops: List<Float>,
    val rects: Map<String, GridRect>,
    val positions: Map<String, Offset>,
    val sizes: Map<String, Size>,
    val totalHeight: Float,
) {
    /** The (col, row) cell containing a point in grid coordinates. */
    fun cellAt(position: Offset): Pair<Int, Int> {
        val col = (position.x / (cellWidth + gap)).toInt().coerceIn(0, columns - 1)
        val row = rowTops.indexOfLast { it <= position.y }.coerceAtLeast(0)
        return col to row
    }
}

/** Which edge of the selected card a resize gesture started on. */
enum class ResizeEdge { LEFT, RIGHT, TOP, BOTTOM }

sealed interface GridMode {
    data object Idle : GridMode
    /** A card is selected and shows its resize handles. */
    data class Editing(val id: String) : GridMode
    data class Dragging(val id: String) : GridMode
    data class Resizing(val id: String, val edge: ResizeEdge) : GridMode
}

/**
 * Order-driven first-fit packing: scans row-major for the first free block that fits each
 * widget's (clamped) spans. Deterministic, so the same list always yields the same layout.
 */
fun packWidgets(widgets: List<HomeWidget>, columns: Int): Map<String, GridRect> {
    val occupied = mutableListOf<BooleanArray>()

    fun ensureRows(lastRow: Int) {
        while (occupied.size <= lastRow) occupied.add(BooleanArray(columns))
    }

    fun fits(col: Int, row: Int, colSpan: Int, rowSpan: Int): Boolean {
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                if (occupied[r][c]) return false
            }
        }
        return true
    }

    fun mark(col: Int, row: Int, colSpan: Int, rowSpan: Int) {
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                occupied[r][c] = true
            }
        }
    }

    val result = mutableMapOf<String, GridRect>()
    widgets.forEach { widget ->
        val colSpan = widget.columnSpan.coerceIn(1, columns)
        val rowSpan = widget.rowSpan.coerceIn(1, HomeWidget.MAX_ROW_SPAN)
        var row = 0
        placement@ while (true) {
            ensureRows(row + rowSpan - 1)
            for (col in 0..columns - colSpan) {
                if (fits(col, row, colSpan, rowSpan)) {
                    mark(col, row, colSpan, rowSpan)
                    result[widget.id] = GridRect(col, row, colSpan, rowSpan)
                    break@placement
                }
            }
            row++
        }
    }
    return result
}

/**
 * Holds the grid's interaction state: the edit-mode state machine, the in-progress working copy
 * of the widget list (committed to the repository when a gesture ends), drag/placement animation
 * offsets and the geometry published by the layout pass.
 */
@Stable
class WidgetGridState(val scope: CoroutineScope) {

    var mode by mutableStateOf<GridMode>(GridMode.Idle)

    /** Uncommitted widget list while a drag/resize is in progress; null = use the repository list. */
    var workingWidgets by mutableStateOf<List<HomeWidget>?>(null)

    /** Latest repository list, refreshed by the grid on every composition so gestures never see a stale one. */
    var latestWidgets: List<HomeWidget> = emptyList()

    /**
     * Reorder gating: a move is applied at most once per finger cell, and never before the
     * layout pass has published the geometry of the previous move. Without this, a move whose
     * packing doesn't land the dragged card exactly under the finger re-triggers on the next
     * pointer event, making the list flip-flop every frame (and vibrate on each flip).
     */
    var reorderPending = false
    var lastMoveCell: Pair<Int, Int>? = null
    var lastTickUptimeMillis = 0L

    var geometry by mutableStateOf<GridGeometry?>(null)

    /** Root-window position of the grid, for hit-testing against the trash button. */
    var gridOriginInRoot by mutableStateOf(Offset.Zero)
    var trashBoundsInRoot by mutableStateOf<Rect?>(null)
    var overTrash by mutableStateOf(false)

    /** Finger translation of the dragged card, relative to its current cell position. */
    val dragOffset = Animatable(Offset.Zero, Offset.VectorConverter)

    /**
     * Per-widget translation compensating cell changes: when a card's cell moves, the offset
     * snaps to the old visual position and animates back to zero (or stays, while dragging).
     */
    private val placementOffsets = mutableMapOf<String, Animatable<Offset, AnimationVector2D>>()
    val lastPositions = mutableMapOf<String, Offset>()

    fun placementOffset(id: String): Animatable<Offset, AnimationVector2D> =
        placementOffsets.getOrPut(id) { Animatable(Offset.Zero, Offset.VectorConverter) }

    fun draggingId(): String? = (mode as? GridMode.Dragging)?.id

    fun selectedId(): String? = when (val m = mode) {
        is GridMode.Editing -> m.id
        is GridMode.Dragging -> m.id
        is GridMode.Resizing -> m.id
        GridMode.Idle -> null
    }

    /** Visual center of a card in grid coordinates, including drag and placement offsets. */
    fun visualCenter(id: String): Offset? {
        val geo = geometry ?: return null
        val pos = geo.positions[id] ?: return null
        val size = geo.sizes[id] ?: return null
        val dragged = draggingId() == id
        val offset = placementOffset(id).value + if (dragged) dragOffset.value else Offset.Zero
        return pos + offset + Offset(size.width / 2f, size.height / 2f)
    }

    fun exitEditMode() {
        mode = GridMode.Idle
        workingWidgets = null
        overTrash = false
    }
}

@Composable
fun rememberWidgetGridState(): WidgetGridState {
    val scope = rememberCoroutineScope()
    return remember { WidgetGridState(scope) }
}
