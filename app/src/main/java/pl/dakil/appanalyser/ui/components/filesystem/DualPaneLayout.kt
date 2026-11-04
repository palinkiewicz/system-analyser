package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalFloatingToolbar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics

/**
 * Thickness of the divider's touch strip between the panes. Stacked panes get a taller one: there
 * the divider is also the way to the actions the folded-away top bar used to offer.
 */
private val DIVIDER_THICKNESS = 16.dp
private val STACKED_DIVIDER_THICKNESS = 24.dp

/** Within this distance of an even split the divider snaps to it. */
private const val SNAP_BAND = 0.03f

/** Past the normal split limits the divider follows the finger at this fraction of its travel. */
private const val RUBBER_BAND = 0.5f

/** A finger within this share of either end arms closing the pane on that side. */
private const val COLLAPSE_BAND = 0.1f

/**
 * Where the divider is drawn for a finger at [raw] (0 = top/left edge, 1 = bottom/right edge), and
 * which pane would be kept if it were let go there: past the usual limits it resists, and near an
 * edge it arms closing the pane being squeezed out. Inside the limits it snaps to an even split.
 */
internal data class DividerPosition(val shown: Float, val keep: PaneId?)

internal fun dividerPosition(raw: Float): DividerPosition {
    val min = SettingsRepository.MIN_PANE_SPLIT
    val max = SettingsRepository.MAX_PANE_SPLIT
    return when {
        raw < min -> DividerPosition(
            shown = min - (min - raw) * RUBBER_BAND,
            keep = if (raw < COLLAPSE_BAND) PaneId.SECONDARY else null,
        )
        raw > max -> DividerPosition(
            shown = max + (raw - max) * RUBBER_BAND,
            keep = if (raw > 1f - COLLAPSE_BAND) PaneId.PRIMARY else null,
        )
        abs(raw - 0.5f) < SNAP_BAND -> DividerPosition(0.5f, keep = null)
        else -> DividerPosition(raw, keep = null)
    }
}

/**
 * Hosts the Files tab's one or two browsers. With [dual] off only [primary] is composed and fills
 * the space, exactly like the single pane browser; turning it on slides [secondary] in below
 * ([horizontal] false) or beside it, with a draggable divider between them.
 *
 * [primary] stays at the same spot in the composition either way, so its list and scroll position
 * survive the toggle. [split] is the primary pane's share; dragging previews locally and only
 * [onSplitChange]s once the finger lifts, so preferences aren't written every frame.
 *
 * Tapping the divider opens [actions] on a small floating toolbar. Dragging it far towards an edge
 * squeezes that pane out: the pane fades, a "release" chip appears, and letting go there closes the
 * split, keeping the other pane's folder ([PaneActions.onCollapse]).
 */
@Composable
fun DualPaneLayout(
    dual: Boolean,
    horizontal: Boolean,
    split: Float,
    onSplitChange: (Float) -> Unit,
    actions: PaneActions,
    modifier: Modifier = Modifier,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = if (dual) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "dualPaneProgress",
    )
    var rawSplit by remember { mutableFloatStateOf(split) }
    var shownSplit by remember { mutableFloatStateOf(split) }
    LaunchedEffect(split) {
        rawSplit = split
        shownSplit = split
    }
    // A collapse leaves the shown split at "primary fills everything"; a new split starts over
    // from the stored one, which a collapse never overwrites.
    LaunchedEffect(dual) {
        if (dual) {
            rawSplit = split
            shownSplit = split
        }
    }
    // The pane that would survive a release right now, or null.
    var keep by remember { mutableStateOf<PaneId?>(null) }
    // The length the panes share, recorded by the measure pass for converting drag pixels into a
    // fraction. Not state: nothing should recompose or relayout when it is written.
    val length = remember { SplitLength() }
    val scope = rememberCoroutineScope()
    val settleSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

    /** Returns true when the drag just crossed something worth a haptic: the snap or the edge. */
    fun moveSplitTo(target: Float): Boolean {
        rawSplit = target.coerceIn(0f, 1f)
        val position = dividerPosition(rawSplit)
        val crossed = (position.shown == 0.5f && shownSplit != 0.5f) || position.keep != keep
        shownSplit = position.shown
        keep = position.keep
        return crossed
    }

    fun settle() {
        val kept = keep
        val from = shownSplit
        scope.launch {
            if (kept != null) {
                // Finish squeezing the pane out, then hand over; the primary slot fills the space
                // once the panes have been swapped (if the secondary is the one kept).
                animate(from, if (kept == PaneId.PRIMARY) 1f else 0f, animationSpec = settleSpec) { value, _ ->
                    shownSplit = value
                }
                keep = null
                shownSplit = 1f
                actions.onCollapse(kept)
            } else {
                val clamped = from.coerceIn(SettingsRepository.MIN_PANE_SPLIT, SettingsRepository.MAX_PANE_SPLIT)
                if (clamped != from) {
                    animate(from, clamped, animationSpec = settleSpec) { value, _ -> shownSplit = value }
                }
                rawSplit = clamped
                onSplitChange(clamped)
            }
        }
    }

    // The pane being squeezed out fades, so it is clear which one a release would close.
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val primaryAlpha by animateFloatAsState(if (keep == PaneId.SECONDARY) LEAVING_ALPHA else 1f, effects, label = "primaryAlpha")
    val secondaryAlpha by animateFloatAsState(if (keep == PaneId.PRIMARY) LEAVING_ALPHA else 1f, effects, label = "secondaryAlpha")

    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.graphicsLayer { alpha = primaryAlpha }) { primary() }
            if (progress > 0f) {
                PaneDivider(
                    horizontal = horizontal,
                    split = shownSplit,
                    keep = keep,
                    onDrag = { delta -> moveSplitTo(rawSplit + delta / length.pixels.coerceAtLeast(1f)) },
                    onSet = { target ->
                        moveSplitTo(target)
                        onSplitChange(shownSplit)
                    },
                    onSettle = { settle() },
                    onReset = {
                        rawSplit = 0.5f
                        shownSplit = 0.5f
                        onSplitChange(0.5f)
                    },
                    actions = actions,
                )
                Box(Modifier.graphicsLayer { alpha = secondaryAlpha }) { secondary() }
            }
        },
    ) { measurables, constraints ->
        val thickness = if (horizontal) DIVIDER_THICKNESS else STACKED_DIVIDER_THICKNESS
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val divider = (thickness.toPx() * progress).roundToInt()
        val available = (total - divider).coerceAtLeast(0)
        length.pixels = (total - thickness.toPx()).coerceAtLeast(0f)
        val primarySize = (available * (1f - progress * (1f - shownSplit))).roundToInt()
        val secondarySize = (available - primarySize).coerceAtLeast(0)

        fun fixed(main: Int) = if (horizontal) {
            Constraints.fixed(main.coerceAtLeast(0), cross)
        } else {
            Constraints.fixed(cross, main.coerceAtLeast(0))
        }

        val primaryPlaceable = measurables[0].measure(fixed(primarySize))
        // The divider is measured at full thickness and slid in; only its slot grows with progress.
        val dividerPlaceable = measurables.getOrNull(1)?.measure(fixed(thickness.roundToPx()))
        val secondaryPlaceable = measurables.getOrNull(2)?.measure(fixed(secondarySize))

        layout(constraints.maxWidth, constraints.maxHeight) {
            primaryPlaceable.place(0, 0)
            secondaryPlaceable?.let {
                val offset = primarySize + divider
                if (horizontal) it.place(offset, 0) else it.place(0, offset)
            }
            // Placed last so it draws above both panes: the release chip overhangs its strip.
            val dividerOffset = primarySize - (thickness.roundToPx() - divider) / 2
            dividerPlaceable?.let {
                if (horizontal) it.place(dividerOffset, 0) else it.place(0, dividerOffset)
            }
        }
    }
}

/** What the divider's toolbar can do; the layout itself only knows about the split. */
class PaneActions(
    val onSwap: () -> Unit,
    /** Opens [from]'s folder in the other pane too. */
    val onMirror: (from: PaneId) -> Unit,
    val canMirror: (from: PaneId) -> Boolean,
    val onClose: () -> Unit,
    /** The divider was dragged away and let go: leave dual pane mode showing [keep]'s folder. */
    val onCollapse: (keep: PaneId) -> Unit,
    /** Set while the top bar is folded away: its actions, carried by the toolbar instead. */
    val extras: PaneToolbarExtras? = null,
)

/**
 * The top bar's actions for the divider toolbar to carry while that bar is hidden. [menu] draws the
 * browser menu (anchored by the toolbar); its last group gets an "Even split" that calls back.
 */
class PaneToolbarExtras(
    val onOpenDiskUsage: () -> Unit,
    val menu: @Composable (expanded: Boolean, onDismiss: () -> Unit, onEvenSplit: () -> Unit) -> Unit,
)

/**
 * The pill between the panes. Drag to resize (it snaps to an even split with a haptic); tap for a
 * small toolbar of pane actions. TalkBack users get it as an adjustable control with the toolbar
 * as a custom action.
 */
@Composable
private fun PaneDivider(
    horizontal: Boolean,
    split: Float,
    /** Set while a release would close the split, keeping this pane. */
    keep: PaneId?,
    /** Applies a drag delta in pixels; returns true when it snapped to an even split. */
    onDrag: (delta: Float) -> Boolean,
    /** Jumps straight to a split, for accessibility services. */
    onSet: (Float) -> Unit,
    onSettle: () -> Unit,
    onReset: () -> Unit,
    actions: PaneActions,
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val currentOnDrag by rememberUpdatedState(onDrag)
    val description = stringResource(R.string.files_resize_panes)
    val actionsLabel = stringResource(R.string.files_pane_actions)
    // Kept composed through the exit animation, then the popup goes away.
    val toolbar = remember { MutableTransitionState(false) }
    val showToolbar = { open: Boolean ->
        if (open && !toolbar.targetState) haptics.tick()
        toolbar.targetState = open
    }

    val highlighted = dragged || toolbar.targetState
    val pillColor by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dividerPill",
    )
    val pillLength by animateDpAsState(
        if (highlighted) 48.dp else 32.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dividerPillLength",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .draggable(
                state = rememberDraggableState { delta ->
                    if (currentOnDrag(delta)) haptics.threshold()
                },
                orientation = if (horizontal) Orientation.Horizontal else Orientation.Vertical,
                interactionSource = interactionSource,
                onDragStarted = { showToolbar(false) },
                onDragStopped = { onSettle() },
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { showToolbar(!toolbar.targetState) })
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(
                    split,
                    SettingsRepository.MIN_PANE_SPLIT..SettingsRepository.MAX_PANE_SPLIT,
                )
                setProgress { target ->
                    onSet(target)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(actionsLabel) {
                        showToolbar(true)
                        true
                    }
                )
            },
    ) {
        val pillAlpha by animateFloatAsState(
            if (keep != null) 0f else 1f,
            MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "dividerPillAlpha",
        )
        Box(
            Modifier
                .size(
                    width = if (horizontal) 4.dp else pillLength,
                    height = if (horizontal) pillLength else 4.dp,
                )
                .graphicsLayer { alpha = pillAlpha }
                .background(pillColor, CircleShape)
        )

        // Overhangs the thin strip, so the promise of what letting go does is readable.
        val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
        AnimatedVisibility(
            visible = keep != null,
            enter = scaleIn(spatial, initialScale = 0.5f) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = scaleOut(spatial, targetScale = 0.5f) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier.wrapContentSize(unbounded = true),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 3.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    AppIcon(AppSymbols.CloseFullscreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.files_release_to_close),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (toolbar.currentState || toolbar.targetState) {
            Popup(
                alignment = Alignment.Center,
                onDismissRequest = { showToolbar(false) },
                properties = PopupProperties(focusable = true),
            ) {
                val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                AnimatedVisibility(
                    visibleState = toolbar,
                    enter = scaleIn(spatial, initialScale = 0.6f) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                    exit = scaleOut(spatial, targetScale = 0.6f) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    PaneToolbar(
                        horizontal = horizontal,
                        actions = actions,
                        onReset = onReset,
                        onDone = { showToolbar(false) },
                    )
                }
            }
        }
    }
}

/**
 * The divider's actions on a vibrant floating toolbar, laid along the divider: swap, show one
 * pane's folder in the other (an arrow per direction, pointing the way the folder goes), even
 * split and close. Stacked panes get a horizontal bar, side-by-side panes a vertical one.
 */
@Composable
private fun PaneToolbar(
    horizontal: Boolean,
    actions: PaneActions,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val haptics = rememberHaptics()
    val content: @Composable () -> Unit = {
        PaneToolbarButton(
            symbol = if (horizontal) AppSymbols.SwapHoriz else AppSymbols.SwapVert,
            label = stringResource(R.string.files_swap_panes),
            horizontal = horizontal,
        ) {
            haptics.confirm()
            actions.onSwap()
            onDone()
        }
        // Rotated a quarter turn when side by side, so the arrow points from pane to pane.
        PaneToolbarButton(
            symbol = AppSymbols.ArrowDownward,
            label = stringResource(if (horizontal) R.string.files_mirror_right else R.string.files_mirror_down),
            horizontal = horizontal,
            rotation = if (horizontal) -90f else 0f,
            enabled = actions.canMirror(PaneId.PRIMARY),
        ) {
            actions.onMirror(PaneId.PRIMARY)
            onDone()
        }
        PaneToolbarButton(
            symbol = AppSymbols.ArrowUpward,
            label = stringResource(if (horizontal) R.string.files_mirror_left else R.string.files_mirror_up),
            horizontal = horizontal,
            rotation = if (horizontal) -90f else 0f,
            enabled = actions.canMirror(PaneId.SECONDARY),
        ) {
            actions.onMirror(PaneId.SECONDARY)
            onDone()
        }
        val extras = actions.extras
        if (extras == null) {
            PaneToolbarButton(
                symbol = if (horizontal) AppSymbols.VerticalSplit else AppSymbols.Splitscreen,
                label = stringResource(R.string.files_even_split),
                horizontal = horizontal,
            ) {
                haptics.confirm()
                onReset()
                onDone()
            }
        } else {
            // Standing in for the hidden top bar; the even split moves into the menu so the bar
            // still fits a narrow phone.
            PaneToolbarButton(
                symbol = AppSymbols.DonutLarge,
                label = stringResource(R.string.disk_usage_title),
                horizontal = horizontal,
            ) {
                onDone()
                extras.onOpenDiskUsage()
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                PaneToolbarButton(
                    symbol = AppSymbols.Sort,
                    label = stringResource(R.string.files_more_options),
                    horizontal = horizontal,
                ) { menuOpen = true }
                extras.menu(
                    menuOpen,
                    {
                        menuOpen = false
                        onDone()
                    },
                    onReset,
                )
            }
        }
        PaneToolbarButton(
            symbol = AppSymbols.CloseFullscreen,
            label = stringResource(R.string.files_close_split),
            horizontal = horizontal,
        ) {
            haptics.toggle(false)
            onDone()
            actions.onClose()
        }
    }
    val colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
    if (horizontal) {
        VerticalFloatingToolbar(expanded = true, colors = colors) { content() }
    } else {
        HorizontalFloatingToolbar(expanded = true, colors = colors) { content() }
    }
}

/** One toolbar action, its name shown as a tooltip on long press (and read out by TalkBack). */
@Composable
private fun PaneToolbarButton(
    symbol: AppSymbol,
    label: String,
    horizontal: Boolean,
    rotation: Float = 0f,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (horizontal) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
        ),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            AppIcon(symbol, contentDescription = label, modifier = Modifier.rotate(rotation))
        }
    }
}

private class SplitLength(var pixels: Float = 0f)

/** How faint a pane gets while a release would close it. */
private const val LEAVING_ALPHA = 0.35f
