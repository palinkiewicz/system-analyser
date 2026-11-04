package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics

/** Thickness of the divider's touch strip between the panes. */
private val DIVIDER_THICKNESS = 16.dp

/** Within this distance of an even split the divider snaps to it. */
private const val SNAP_BAND = 0.03f

/**
 * Hosts the Files tab's one or two browsers. With [dual] off only [primary] is composed and fills
 * the space, exactly like the single pane browser; turning it on slides [secondary] in below
 * ([horizontal] false) or beside it, with a draggable divider between them.
 *
 * [primary] stays at the same spot in the composition either way, so its list and scroll position
 * survive the toggle. Tapping the divider opens [actions] on a small floating toolbar. [split] is the primary pane's share; dragging previews locally and only
 * [onSplitChange]s once the finger lifts, so preferences aren't written every frame.
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
    // The length the panes share, recorded by the measure pass for converting drag pixels into a
    // fraction. Not state: nothing should recompose or relayout when it is written.
    val length = remember { SplitLength() }

    fun moveSplitTo(target: Float): Boolean {
        rawSplit = target.coerceIn(SettingsRepository.MIN_PANE_SPLIT, SettingsRepository.MAX_PANE_SPLIT)
        val snapped = if (abs(rawSplit - 0.5f) < SNAP_BAND) 0.5f else rawSplit
        val snappedNow = snapped == 0.5f && shownSplit != 0.5f
        shownSplit = snapped
        return snappedNow
    }

    Layout(
        modifier = modifier,
        content = {
            Box { primary() }
            if (progress > 0f) {
                PaneDivider(
                    horizontal = horizontal,
                    split = shownSplit,
                    onDrag = { delta -> moveSplitTo(rawSplit + delta / length.pixels.coerceAtLeast(1f)) },
                    onSet = { target ->
                        moveSplitTo(target)
                        onSplitChange(shownSplit)
                    },
                    onSettle = { onSplitChange(shownSplit) },
                    onReset = {
                        rawSplit = 0.5f
                        shownSplit = 0.5f
                        onSplitChange(0.5f)
                    },
                    actions = actions,
                )
                Box { secondary() }
            }
        },
    ) { measurables, constraints ->
        val total = if (horizontal) constraints.maxWidth else constraints.maxHeight
        val cross = if (horizontal) constraints.maxHeight else constraints.maxWidth
        val divider = (DIVIDER_THICKNESS.toPx() * progress).roundToInt()
        val available = (total - divider).coerceAtLeast(0)
        length.pixels = (total - DIVIDER_THICKNESS.toPx()).coerceAtLeast(0f)
        val primarySize = (available * (1f - progress * (1f - shownSplit))).roundToInt()
        val secondarySize = (available - primarySize).coerceAtLeast(0)

        fun fixed(main: Int) = if (horizontal) {
            Constraints.fixed(main.coerceAtLeast(0), cross)
        } else {
            Constraints.fixed(cross, main.coerceAtLeast(0))
        }

        val primaryPlaceable = measurables[0].measure(fixed(primarySize))
        // The divider is measured at full thickness and slid in; only its slot grows with progress.
        val dividerPlaceable = measurables.getOrNull(1)?.measure(fixed(DIVIDER_THICKNESS.roundToPx()))
        val secondaryPlaceable = measurables.getOrNull(2)?.measure(fixed(secondarySize))

        layout(constraints.maxWidth, constraints.maxHeight) {
            primaryPlaceable.place(0, 0)
            val dividerOffset = primarySize - (DIVIDER_THICKNESS.roundToPx() - divider) / 2
            dividerPlaceable?.let {
                if (horizontal) it.place(dividerOffset, 0) else it.place(0, dividerOffset)
            }
            secondaryPlaceable?.let {
                val offset = primarySize + divider
                if (horizontal) it.place(offset, 0) else it.place(0, offset)
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
        Box(
            Modifier
                .size(
                    width = if (horizontal) 4.dp else pillLength,
                    height = if (horizontal) pillLength else 4.dp,
                )
                .background(pillColor, CircleShape)
        )

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
        PaneToolbarButton(
            symbol = if (horizontal) AppSymbols.VerticalSplit else AppSymbols.Splitscreen,
            label = stringResource(R.string.files_even_split),
            horizontal = horizontal,
        ) {
            haptics.confirm()
            onReset()
            onDone()
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
