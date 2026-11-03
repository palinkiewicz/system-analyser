package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
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
 * survive the toggle. [split] is the primary pane's share; dragging previews locally and only
 * [onSplitChange]s once the finger lifts, so preferences aren't written every frame.
 */
@Composable
fun DualPaneLayout(
    dual: Boolean,
    horizontal: Boolean,
    split: Float,
    onSplitChange: (Float) -> Unit,
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

/**
 * The pill between the panes. Drag to resize (it snaps to an even split with a haptic), double-tap
 * to reset; TalkBack users get it as an adjustable control.
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
) {
    val haptics = rememberHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val dragged by interactionSource.collectIsDraggedAsState()
    val currentOnDrag by rememberUpdatedState(onDrag)
    val description = stringResource(R.string.files_resize_panes)

    val pillColor by animateColorAsState(
        if (dragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "dividerPill",
    )
    val pillLength by animateDpAsState(
        if (dragged) 48.dp else 32.dp,
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
                onDragStopped = { onSettle() },
            )
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    haptics.confirm()
                    onReset()
                })
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
    }
}

private class SplitLength(var pixels: Float = 0f)
