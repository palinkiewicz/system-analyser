package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.PinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.domain.dropDestination
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing

/** What a drag carries: the source pane's selection, led by the row the finger picked up. */
data class FileDragPayload(
    val source: PaneId,
    val paths: List<String>,
    val lead: FileEntry,
)

/**
 * Where a drop would land. [folderRow] is set when the finger is over a folder row (the items go
 * into that folder) rather than over the other pane at large (they go into its current folder).
 */
data class DropTarget(
    val pane: PaneId,
    val destination: String,
    val folderRow: Boolean,
)

/**
 * The screen's side of a drag: it owns selection, haptics and the actual transfer, while
 * [FileDragState] only tracks geometry. Set fresh on every composition, so rows never act on a
 * stale lambda captured when their pointer handler started.
 */
interface FileDragHandler {
    /** The long press landed on [entry]; returns whether it was already selected beforehand. */
    fun onLongPress(pane: PaneId, entry: FileEntry): Boolean

    /** The finger left the row: what to carry, or null to refuse the drag. */
    fun payloadFor(pane: PaneId, entry: FileEntry): FileDragPayload?

    /** A long press let go without moving — the plain selection gesture of single pane mode. */
    fun onReleasedInPlace(pane: PaneId, entry: FileEntry, wasSelected: Boolean)

    fun onHoverChanged(target: DropTarget?)

    /** The finger lifted; [target] is null when it lifted over nowhere a drop is allowed. */
    fun onDrop(payload: FileDragPayload, target: DropTarget?, pointer: Offset)
}

/**
 * Geometry and progress of a file drag, within one pane or across two. Panes and rows publish their root bounds here as they
 * are laid out; the dragged row's pointer handler feeds the finger position in, and every move is
 * hit-tested against those bounds. All coordinates are in the root's space.
 */
@Stable
class FileDragState {
    var handler: FileDragHandler? = null

    var payload by mutableStateOf<FileDragPayload?>(null)
        private set

    /** The finger, in root coordinates. Read only in layout/draw lambdas to avoid recomposing. */
    var pointer by mutableStateOf(Offset.Zero)
        private set

    var hover by mutableStateOf<DropTarget?>(null)
        private set

    /** Root offset of the overlay the ghost is drawn in, so it can map [pointer] into its space. */
    var overlayOrigin = Offset.Zero

    private class PaneSlot(var bounds: Rect = Rect.Zero, var folder: String? = null)
    private class RowSlot(val pane: PaneId, val path: String, val isDirectory: Boolean, var bounds: Rect)

    private val panes = mutableMapOf<PaneId, PaneSlot>()
    private val rows = mutableMapOf<Pair<PaneId, String>, RowSlot>()

    fun placePane(id: PaneId, bounds: Rect) {
        panes.getOrPut(id) { PaneSlot() }.bounds = bounds
    }

    fun setPaneFolder(id: PaneId, folder: String?) {
        panes.getOrPut(id) { PaneSlot() }.folder = folder
    }

    fun removePane(id: PaneId) {
        panes.remove(id)
    }

    fun paneBounds(id: PaneId): Rect? = panes[id]?.bounds

    internal fun placeRow(pane: PaneId, entry: FileEntry, bounds: Rect) {
        rows[pane to entry.path] = RowSlot(pane, entry.path, entry.isDirectory, bounds)
    }

    internal fun removeRow(pane: PaneId, path: String) {
        rows.remove(pane to path)
    }

    internal fun lift(payload: FileDragPayload, at: Offset) {
        this.payload = payload
        moveTo(at)
    }

    internal fun moveTo(at: Offset) {
        pointer = at
        val target = resolve()
        if (target != hover) {
            hover = target
            handler?.onHoverChanged(target)
        }
    }

    internal fun release() {
        val carried = payload ?: return
        val target = hover
        reset()
        handler?.onDrop(carried, target, pointer)
    }

    internal fun cancel() = reset()

    private fun reset() {
        payload = null
        hover = null
    }

    /**
     * A folder row under the finger wins (in either pane), otherwise the other pane's current
     * folder. The source pane at large is never a target — that is where the items already are —
     * and neither is a pane still on its volume chooser. With a single pane, folder rows are
     * therefore the only targets.
     */
    private fun resolve(): DropTarget? {
        val carried = payload ?: return null
        val pane = panes.entries.firstOrNull { it.value.bounds.contains(pointer) } ?: return null
        // Bounds are clipped by the list, so a row half-hidden under the pane header can't be hit
        // through it; the pane check also rules out rows of a pane that is sliding away.
        val row = rows.values.firstOrNull {
            it.pane == pane.key && it.isDirectory && it.bounds.contains(pointer)
        }
        if (row != null && row.path !in carried.paths) {
            dropDestination(carried.paths, row.path)?.let { return DropTarget(row.pane, it, folderRow = true) }
        }
        if (pane.key == carried.source) return null
        val folder = pane.value.folder ?: return null
        return dropDestination(carried.paths, folder)?.let { DropTarget(pane.key, it, folderRow = false) }
    }
}

/**
 * Makes a file row draggable and registers its bounds as a potential drop target.
 *
 * The long press belongs to this handler, not to the row's own `onLongClick` (pass it null there):
 * both would fire on the same press and race over the selection. Letting go without moving keeps
 * the single pane meaning of a long press, toggling the row's selection; moving past the touch slop
 * picks up the pane's whole selection.
 */
fun Modifier.fileDragSource(state: FileDragState, pane: PaneId, entry: FileEntry): Modifier =
    this then FileDragElement(state, pane, entry)

private data class FileDragElement(
    val state: FileDragState,
    val pane: PaneId,
    val entry: FileEntry,
) : ModifierNodeElement<FileDragNode>() {
    override fun create() = FileDragNode(state, pane, entry)

    override fun update(node: FileDragNode) = node.update(state, pane, entry)

    override fun InspectorInfo.inspectableProperties() {
        name = "fileDragSource"
    }
}

private class FileDragNode(
    var state: FileDragState,
    var pane: PaneId,
    var entry: FileEntry,
) : DelegatingNode(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode {

    private var coordinates: LayoutCoordinates? = null

    /**
     * Holds the dragged row in its lazy list while the items are carried. Auto-scroll would
     * otherwise scroll it out of composition, detaching this node and cancelling the drag under
     * the finger.
     */
    private var pin: PinnableContainer.PinnedHandle? = null

    private val pointerInput = delegate(
        SuspendingPointerInputModifierNode {
            // Watched on the Initial pass, ahead of the row's own clickable and the list's scroll:
            // once the long press lands every change is consumed here, which cancels the pending
            // tap (or a hold released in place would toggle the selection straight back) and keeps
            // the list from scrolling under the finger.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed || event.changes.size > 1) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }
                if (endedEarly != null) return@awaitEachGesture

                val wasSelected = state.handler?.onLongPress(pane, entry) ?: false
                var lifted = false
                var finished = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) {
                            finished = true
                            if (lifted) {
                                state.release()
                            } else {
                                state.handler?.onReleasedInPlace(pane, entry, wasSelected)
                            }
                            break
                        }
                        val at = coordinates?.takeIf { it.isAttached }?.localToRoot(change.position) ?: continue
                        if (lifted) {
                            state.moveTo(at)
                        } else if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            // A finger never holds perfectly still through a long press; only a
                            // real move picks the items up.
                            val payload = state.handler?.payloadFor(pane, entry) ?: break
                            state.lift(payload, at)
                            lifted = true
                            pin = currentValueOf(LocalPinnableContainer)?.pin()
                        }
                    }
                } finally {
                    // The gesture was cancelled mid-drag (another pointer, the row removed).
                    if (lifted && !finished) state.cancel()
                    unpin()
                }
            }
        }
    )

    fun update(state: FileDragState, pane: PaneId, entry: FileEntry) {
        val moved = state != this.state || pane != this.pane || entry.path != this.entry.path
        if (moved) this.state.removeRow(this.pane, this.entry.path)
        this.state = state
        this.pane = pane
        this.entry = entry
        coordinates?.takeIf { it.isAttached }?.let { state.placeRow(pane, entry, it.boundsInRoot()) }
        if (moved) pointerInput.resetPointerInputHandler()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        state.placeRow(pane, entry, coordinates.boundsInRoot())
    }

    private fun unpin() {
        pin?.release()
        pin = null
    }

    override fun onDetach() {
        state.removeRow(pane, entry.path)
        coordinates = null
        unpin()
    }
}

/**
 * The card following the finger while items are carried: the lead item's badge and name (or a
 * count), and where they would land. Sits just above and left of the finger so it stays visible.
 * Its position is read in the layout phase, so moving the finger re-places it without recomposing.
 */
@Composable
fun FileDragGhost(
    state: FileDragState,
    folderLabel: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val payload = state.payload
    // Kept through the exit animation, after the state has already let the payload go. A plain
    // holder, not state: it only ever changes in step with `payload`, which already recomposes.
    val shown = remember { LastPayload() }
    if (payload != null) shown.value = payload
    val hover = state.hover
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val container by animateColorAsState(
        if (hover != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "ghostContainer",
    )
    val lift = with(LocalDensity.current) { GHOST_LIFT.roundToPx() }

    AnimatedVisibility(
        visible = payload != null,
        enter = scaleIn(spatial, initialScale = 0.6f) + fadeIn(),
        exit = scaleOut(spatial, targetScale = 0.6f) + fadeOut(),
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(placeable.width, placeable.height) {
                val at = state.pointer - state.overlayOrigin
                // Flips below the finger near the top, where it would slide under the app bar,
                // and never leaves the sides of the screen.
                val above = at.y.toInt() - placeable.height - lift
                val y = if (above >= 0) above else at.y.toInt() + lift
                val x = (at.x - placeable.width / 2).toInt()
                    .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
                placeable.place(IntOffset(x, y))
            }
        },
    ) {
        val carried = shown.value ?: return@AnimatedVisibility
        Surface(
            shape = MaterialTheme.shapes.large,
            color = container,
            shadowElevation = 6.dp,
            modifier = Modifier.widthIn(max = 260.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                EntryIcon(carried.lead, size = 36.dp)
                Column {
                    Text(
                        text = if (carried.paths.size == 1) {
                            carried.lead.name
                        } else {
                            pluralStringResource(R.plurals.files_items_x, carried.paths.size, carried.paths.size)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AnimatedContent(targetState = hover?.destination, label = "ghostDestination") { destination ->
                        if (destination != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(
                                    AppSymbols.DriveFileMove,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = folderLabel(destination),
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = Spacing.xs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class LastPayload(var value: FileDragPayload? = null)

/** How far above the finger the ghost floats, so the finger doesn't cover it. */
private val GHOST_LIFT = 24.dp
