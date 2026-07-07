package pl.dakil.appanalyser.ui.components.homegrid

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.domain.HomeWidget
import kotlin.math.roundToInt

private val GRID_GAP = 12.dp
private val MIN_ROW_HEIGHT = 64.dp
private val TRASH_HIT_SLOP = 16.dp

/**
 * Launcher-style widget grid: a custom Layout (LazyVerticalGrid has no row spans) that packs
 * cards by first-fit, auto-sizes rows from their contents, and supports long-press drag
 * reordering, drop-on-trash removal and edge-handle span resizing.
 */
@Composable
fun WidgetGrid(
    widgets: List<HomeWidget>,
    columns: Int,
    state: WidgetGridState,
    onCommit: (List<HomeWidget>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (HomeWidget) -> Unit,
) {
    state.latestWidgets = widgets
    val displayList = state.workingWidgets ?: widgets
    val view = LocalView.current

    Box(modifier = modifier) {
        Layout(
            content = {
                displayList.forEach { widget ->
                    key(widget.id) {
                        GridItem(
                            widget = widget,
                            state = state,
                            view = view,
                            onCommit = onCommit,
                            onRemove = onRemove,
                        ) {
                            content(widget)
                        }
                    }
                }
            },
            modifier = Modifier.onGloballyPositioned {
                state.gridOriginInRoot = it.positionInRoot()
            }
        ) { measurables, constraints ->
            val gapPx = GRID_GAP.toPx()
            val minRowPx = MIN_ROW_HEIGHT.toPx()
            val cellWidth = (constraints.maxWidth - gapPx * (columns - 1)) / columns
            val rects = packWidgets(displayList, columns)

            fun spanWidthPx(colSpan: Int): Int =
                (cellWidth * colSpan + gapPx * (colSpan - 1)).roundToInt()

            // Natural content heights via intrinsics — a child may only be measured once.
            val naturalHeights = displayList.mapIndexed { index, widget ->
                val rect = rects.getValue(widget.id)
                measurables[index].minIntrinsicHeight(spanWidthPx(rect.colSpan)).toFloat()
            }

            val rowCount = rects.values.maxOfOrNull { it.row + it.rowSpan } ?: 0
            val rowHeights = FloatArray(rowCount) { minRowPx }

            // Single-row cards set their row's height directly.
            displayList.forEachIndexed { index, widget ->
                val rect = rects.getValue(widget.id)
                if (rect.rowSpan == 1) {
                    rowHeights[rect.row] = maxOf(rowHeights[rect.row], naturalHeights[index])
                }
            }
            // Multi-row cards distribute any missing height equally over their spanned rows.
            displayList.withIndex()
                .filter { rects.getValue(it.value.id).rowSpan > 1 }
                .sortedBy { rects.getValue(it.value.id).rowSpan }
                .forEach { (index, widget) ->
                    val rect = rects.getValue(widget.id)
                    val available = (rect.row until rect.row + rect.rowSpan).sumOf {
                        rowHeights[it].toDouble()
                    }.toFloat() + gapPx * (rect.rowSpan - 1)
                    val deficit = naturalHeights[index] - available
                    if (deficit > 0f) {
                        val extra = deficit / rect.rowSpan
                        for (r in rect.row until rect.row + rect.rowSpan) {
                            rowHeights[r] += extra
                        }
                    }
                }

            val rowTops = FloatArray(rowCount)
            for (r in 1 until rowCount) {
                rowTops[r] = rowTops[r - 1] + rowHeights[r - 1] + gapPx
            }
            val totalHeight =
                if (rowCount == 0) 0f
                else rowTops[rowCount - 1] + rowHeights[rowCount - 1]

            val positions = mutableMapOf<String, Offset>()
            val sizes = mutableMapOf<String, Size>()
            val placeables = displayList.mapIndexed { index, widget ->
                val rect = rects.getValue(widget.id)
                val width = spanWidthPx(rect.colSpan)
                val height = ((rect.row until rect.row + rect.rowSpan).sumOf {
                    rowHeights[it].toDouble()
                } + gapPx * (rect.rowSpan - 1)).roundToInt()
                positions[widget.id] = Offset(rect.col * (cellWidth + gapPx), rowTops[rect.row])
                sizes[widget.id] = Size(width.toFloat(), height.toFloat())
                measurables[index].measure(Constraints.fixed(width, height))
            }

            val newGeometry = GridGeometry(
                columns = columns,
                cellWidth = cellWidth,
                gap = gapPx,
                rowHeights = rowHeights.toList(),
                rowTops = rowTops.toList(),
                rects = rects,
                positions = positions,
                sizes = sizes,
                totalHeight = totalHeight,
            )
            if (state.geometry != newGeometry) {
                state.geometry = newGeometry
            }

            layout(constraints.maxWidth, totalHeight.roundToInt()) {
                val draggingId = state.draggingId()
                placeables.forEachIndexed { index, placeable ->
                    val widget = displayList[index]
                    val pos = positions.getValue(widget.id)
                    placeable.place(
                        x = pos.x.roundToInt(),
                        y = pos.y.roundToInt(),
                        zIndex = if (widget.id == draggingId) 1f else 0f,
                    )
                }
            }
        }

        ResizeOverlay(state = state, view = view, columns = columns, onCommit = onCommit)
    }
}

// ---------------------------------------------------------------------
// Grid item: placement animation + long-press drag gesture
// ---------------------------------------------------------------------

@Composable
private fun GridItem(
    widget: HomeWidget,
    state: WidgetGridState,
    view: View,
    onCommit: (List<HomeWidget>) -> Unit,
    onRemove: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val id = widget.id
    val isDragged = state.draggingId() == id
    val placementOffset = state.placementOffset(id)
    val targetPosition = state.geometry?.positions?.get(id)

    // When this card's cell changes, keep it visually in place and spring to the new cell —
    // unless it's the card under the finger, which stays compensated until the drag ends.
    LaunchedEffect(targetPosition) {
        val target = targetPosition ?: return@LaunchedEffect
        val previous = state.lastPositions[id]
        state.lastPositions[id] = target
        if (previous != null && previous != target) {
            placementOffset.snapTo(placementOffset.value + (previous - target))
            if (state.draggingId() != id) {
                placementOffset.animateTo(
                    Offset.Zero,
                    spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                )
            }
        }
    }

    val liftScale by animateFloatAsState(
        targetValue = if (isDragged) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "liftScale"
    )
    val shadow by animateFloatAsState(
        targetValue = if (isDragged) 8f else 0f,
        label = "dragShadow"
    )

    Box(
        modifier = Modifier
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        state.workingWidgets = state.workingWidgets ?: state.latestWidgets
                        state.overTrash = false
                        state.mode = GridMode.Dragging(id)
                        state.scope.launch { state.dragOffset.snapTo(Offset.Zero) }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        state.scope.launch {
                            state.dragOffset.snapTo(state.dragOffset.value + dragAmount)
                        }
                        handleDragPositionChange(state, id, view)
                    },
                    onDragEnd = { finishDrag(state, id, view, onCommit, onRemove) },
                    onDragCancel = { finishDrag(state, id, view, onCommit, onRemove, cancelled = true) },
                )
            }
            .graphicsLayer {
                val total = placementOffset.value +
                    if (isDragged) state.dragOffset.value else Offset.Zero
                translationX = total.x
                translationY = total.y
                scaleX = liftScale
                scaleY = liftScale
                shadowElevation = shadow.dp.toPx()
            },
        // The grid measures this wrapper at the exact cell size; pass those constraints
        // down so the card surface visually fills every row it spans.
        propagateMinConstraints = true
    ) {
        content()
    }
}

/** Re-targets the working list order and the trash highlight from the card's visual center. */
private fun handleDragPositionChange(state: WidgetGridState, id: String, view: View) {
    val center = state.visualCenter(id) ?: return

    // Trash hit-testing happens in root coordinates.
    val trash = state.trashBoundsInRoot
    if (trash != null) {
        val centerInRoot = state.gridOriginInRoot + center
        val slop = TRASH_HIT_SLOP.value * view.resources.displayMetrics.density
        val hovering = trash.inflate(slop).contains(centerInRoot)
        if (hovering != state.overTrash) {
            state.overTrash = hovering
            if (hovering) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        HapticFeedbackConstants.GESTURE_START
                    } else {
                        HapticFeedbackConstants.CLOCK_TICK
                    }
                )
            }
        }
        if (hovering) return
    }

    val working = state.workingWidgets ?: return
    val geo = state.geometry ?: return
    val fromIndex = working.indexOfFirst { it.id == id }
    if (fromIndex < 0) return

    // Which widget is under the dragged card's center?
    val hit = geo.positions.entries.firstOrNull { (otherId, pos) ->
        otherId != id && androidx.compose.ui.geometry.Rect(
            pos,
            geo.sizes.getValue(otherId)
        ).contains(center)
    }
    val toIndex = when {
        hit != null -> working.indexOfFirst { it.id == hit.key }
        center.y > geo.totalHeight -> working.lastIndex
        else -> return
    }
    if (toIndex < 0 || toIndex == fromIndex) return

    state.workingWidgets = working.toMutableList().apply {
        val moved = removeAt(fromIndex)
        add(toIndex, moved)
    }
    view.performTickHaptic()
}

private fun finishDrag(
    state: WidgetGridState,
    id: String,
    view: View,
    onCommit: (List<HomeWidget>) -> Unit,
    onRemove: (String) -> Unit,
    cancelled: Boolean = false,
) {
    val working = state.workingWidgets
    if (!cancelled && state.overTrash) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        )
        if (working != null) onCommit(working.filterNot { it.id == id }) else onRemove(id)
        state.exitEditMode()
        state.scope.launch {
            state.dragOffset.snapTo(Offset.Zero)
            state.placementOffset(id).snapTo(Offset.Zero)
        }
        return
    }

    if (working != null) onCommit(working)
    state.workingWidgets = null
    state.overTrash = false
    state.mode = GridMode.Editing(id)
    state.scope.launch {
        launch {
            state.dragOffset.animateTo(
                Offset.Zero,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
            )
        }
        launch {
            state.placementOffset(id).animateTo(
                Offset.Zero,
                spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
            )
        }
    }
}

private fun View.performTickHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    )
}

// ---------------------------------------------------------------------
// Resize overlay — Android 16 launcher-style outline with edge handles
// ---------------------------------------------------------------------

@Composable
private fun ResizeOverlay(
    state: WidgetGridState,
    view: View,
    columns: Int,
    onCommit: (List<HomeWidget>) -> Unit,
) {
    val selectedId = when (val mode = state.mode) {
        is GridMode.Editing -> mode.id
        is GridMode.Resizing -> mode.id
        else -> return
    }
    val geometry = state.geometry ?: return
    val position = geometry.positions[selectedId] ?: return
    val size = geometry.sizes[selectedId] ?: return
    val density = LocalDensity.current

    val outlinePadding = 4.dp
    val outlinePaddingPx = with(density) { outlinePadding.toPx() }
    val overlayOffset = IntOffset(
        (position.x - outlinePaddingPx).roundToInt(),
        (position.y - outlinePaddingPx).roundToInt(),
    )
    val overlaySize = with(density) {
        DpSize(
            (size.width + 2 * outlinePaddingPx).toDp(),
            (size.height + 2 * outlinePaddingPx).toDp(),
        )
    }

    Box(
        modifier = Modifier
            .offset { overlayOffset }
            .size(overlaySize)
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        ResizeHandle(state, view, columns, selectedId, ResizeEdge.LEFT, onCommit, Alignment.CenterStart)
        ResizeHandle(state, view, columns, selectedId, ResizeEdge.RIGHT, onCommit, Alignment.CenterEnd)
        ResizeHandle(state, view, columns, selectedId, ResizeEdge.TOP, onCommit, Alignment.TopCenter)
        ResizeHandle(state, view, columns, selectedId, ResizeEdge.BOTTOM, onCommit, Alignment.BottomCenter)
    }
}

@Composable
private fun BoxScope.ResizeHandle(
    state: WidgetGridState,
    view: View,
    columns: Int,
    widgetId: String,
    edge: ResizeEdge,
    onCommit: (List<HomeWidget>) -> Unit,
    alignment: Alignment,
) {
    val horizontal = edge == ResizeEdge.LEFT || edge == ResizeEdge.RIGHT
    val handleSize = if (horizontal) DpSize(4.dp, 28.dp) else DpSize(28.dp, 4.dp)
    val haloSize = if (horizontal) DpSize(8.dp, 32.dp) else DpSize(32.dp, 8.dp)

    Box(
        modifier = Modifier
            .align(alignment)
            .size(48.dp)
            .pointerInput(widgetId, edge, columns) {
                var accumulated = 0f
                var startSpan = 0
                detectDragGestures(
                    onDragStart = {
                        accumulated = 0f
                        state.workingWidgets = state.workingWidgets ?: state.latestWidgets
                        val widget = state.workingWidgets?.firstOrNull { it.id == widgetId }
                        startSpan = when {
                            widget == null -> 0
                            horizontal -> widget.columnSpan
                            else -> widget.rowSpan
                        }
                        state.mode = GridMode.Resizing(widgetId, edge)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulated += if (horizontal) dragAmount.x else dragAmount.y
                        val geo = state.geometry ?: return@detectDragGestures
                        val working = state.workingWidgets ?: return@detectDragGestures
                        val widget = working.firstOrNull { it.id == widgetId } ?: return@detectDragGestures
                        if (startSpan == 0) {
                            startSpan = if (horizontal) widget.columnSpan else widget.rowSpan
                        }
                        val step = if (horizontal) {
                            geo.cellWidth + geo.gap
                        } else {
                            (geo.rowHeights.averageOrZero() + geo.gap)
                        }
                        if (step <= 0f) return@detectDragGestures
                        // Dragging outward from the card grows the span on any edge.
                        val direction = if (edge == ResizeEdge.LEFT || edge == ResizeEdge.TOP) -1 else 1
                        val delta = (direction * accumulated / step).roundToInt()
                        val newSpan = (startSpan + delta).coerceIn(
                            1,
                            if (horizontal) columns else HomeWidget.MAX_ROW_SPAN
                        )
                        val currentSpan = if (horizontal) widget.columnSpan else widget.rowSpan
                        if (newSpan != currentSpan) {
                            val updated = if (horizontal) {
                                widget.copy(columnSpan = newSpan)
                            } else {
                                widget.copy(rowSpan = newSpan)
                            }
                            state.workingWidgets = working.map { if (it.id == widgetId) updated else it }
                            view.performTickHaptic()
                        }
                    },
                    onDragEnd = {
                        state.workingWidgets?.let(onCommit)
                        state.workingWidgets = null
                        state.mode = GridMode.Editing(widgetId)
                    },
                    onDragCancel = {
                        state.workingWidgets = null
                        state.mode = GridMode.Editing(widgetId)
                    },
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Halo in the background color so the pill reads over any card content.
        Box(
            modifier = Modifier
                .size(haloSize)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.background)
        )
        Box(
            modifier = Modifier
                .size(handleSize)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun List<Float>.averageOrZero(): Float = if (isEmpty()) 0f else (sum() / size)
