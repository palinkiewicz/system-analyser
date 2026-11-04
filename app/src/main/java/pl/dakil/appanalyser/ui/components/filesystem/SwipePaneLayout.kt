package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.pressScale
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics

/** How close to the screen edge carried items have to be to reach for the other pane. */
private val EDGE_ZONE = 40.dp

/** How long carried items rest at the edge before the other pane slides in. */
private const val EDGE_DWELL_MS = 550L

/** How much of the other pane a carried item peeks at while resting at the edge. */
private const val PEEK_FRACTION = 0.14f

/**
 * Dual pane mode for a portrait phone, the Total Commander Mobile way: one pane fills the screen
 * and the other waits beside it. Swipe, or tap the tab on the edge, to switch; the page shown is
 * the active pane, both ways.
 *
 * While files are carried, holding them against the edge towards the other pane makes it peek in
 * (the sliver is already a drop target), then slide over, so a drop can land anywhere in it.
 */
@Composable
fun SwipePaneLayout(
    activePane: PaneId,
    onActivePaneChange: (PaneId) -> Unit,
    dragState: FileDragState,
    modifier: Modifier = Modifier,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = activePane.ordinal) { 2 }
    val carrying = dragState.payload != null
    val currentOnActivePaneChange by rememberUpdatedState(onActivePaneChange)

    // Page ⇄ active pane, in both directions.
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .collect { currentOnActivePaneChange(PaneId.entries[it]) }
    }
    LaunchedEffect(activePane) {
        if (pagerState.settledPage != activePane.ordinal && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(activePane.ordinal)
        }
    }

    var bounds by remember { mutableStateOf<Rect?>(null) }
    var edgeHot by remember { mutableStateOf(false) }
    val edge = with(LocalDensity.current) { EDGE_ZONE.toPx() }
    LaunchedEffect(carrying) {
        if (!carrying) return@LaunchedEffect
        var hotSince = -1L
        try {
            while (true) {
                val now = withFrameMillis { it }
                val area = bounds ?: continue
                if (pagerState.isScrollInProgress && hotSince < 0) continue
                val page = pagerState.settledPage
                val pointer = dragState.pointer
                val atEdge = pointer.y in area.top..area.bottom &&
                    if (page == 0) pointer.x > area.right - edge else pointer.x < area.left + edge
                when {
                    atEdge && hotSince < 0 -> {
                        hotSince = now
                        edgeHot = true
                        scope.launch {
                            pagerState.animateScrollToPage(page, if (page == 0) PEEK_FRACTION else -PEEK_FRACTION)
                        }
                    }
                    atEdge && now - hotSince >= EDGE_DWELL_MS -> {
                        haptics.tick()
                        hotSince = -1L
                        edgeHot = false
                        pagerState.animateScrollToPage(1 - page)
                    }
                    !atEdge && hotSince >= 0 -> {
                        hotSince = -1L
                        edgeHot = false
                        scope.launch { pagerState.animateScrollToPage(page) }
                    }
                }
            }
        } finally {
            // The drop (or a cancel) landed mid-peek: settle back onto a whole page.
            if (edgeHot) {
                edgeHot = false
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage) }
            }
        }
    }

    Column(modifier) {
        PageIndicator(pagerState)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { bounds = it.boundsInRoot() },
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                // A carried file must not swipe the pages; the edge does that instead.
                userScrollEnabled = !carrying,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(Modifier.fillMaxSize()) {
                    if (page == 0) primary() else secondary()
                    EdgeTab(
                        towardsEnd = page == 0,
                        visible = !carrying,
                        modifier = Modifier.align(if (page == 0) Alignment.CenterEnd else Alignment.CenterStart),
                    ) {
                        haptics.tick()
                        scope.launch { pagerState.animateScrollToPage(1 - page) }
                    }
                }
            }
            // A glow on the edge being held, promising the other pane.
            val towardsEnd = pagerState.settledPage == 0
            EdgeGlow(
                visible = edgeHot,
                towardsEnd = towardsEnd,
                modifier = Modifier
                    .align(if (towardsEnd) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(EDGE_ZONE),
            )
        }
    }
}

@Composable
private fun EdgeGlow(visible: Boolean, towardsEnd: Boolean, modifier: Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        if (towardsEnd) listOf(Color.Transparent, glow) else listOf(glow, Color.Transparent)
                    )
                )
        )
    }
}

/** Two dots, the current one stretched into a pill, so it's always clear which pane is shown. */
@Composable
private fun PageIndicator(pagerState: PagerState) {
    val description = stringResource(R.string.files_pane_x_of_2, pagerState.currentPage + 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .semantics { contentDescription = description },
    ) {
        repeat(2) { index ->
            val selected = pagerState.currentPage == index
            val width by animateDpAsState(
                if (selected) 20.dp else 6.dp,
                MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "pageDot",
            )
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
            )
        }
    }
}

/** A tab sticking out of the screen edge that brings in the other pane. */
@Composable
private fun EdgeTab(towardsEnd: Boolean, visible: Boolean, modifier: Modifier, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val shape = if (towardsEnd) {
            RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
        } else {
            RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
        }
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 2.dp,
            interactionSource = interactionSource,
            modifier = Modifier
                .pressScale(interactionSource, pressedScale = 0.9f)
                .size(width = 28.dp, height = 64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    if (towardsEnd) AppSymbols.ChevronRight else AppSymbols.ChevronLeft,
                    contentDescription = stringResource(R.string.files_other_pane),
                    modifier = Modifier
                        .padding(start = if (towardsEnd) 4.dp else 0.dp, end = if (towardsEnd) 0.dp else 4.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
