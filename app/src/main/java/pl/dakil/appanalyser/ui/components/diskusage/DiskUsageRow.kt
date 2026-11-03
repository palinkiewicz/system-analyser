package pl.dakil.appanalyser.ui.components.diskusage

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.DiskUsageItem
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.ui.components.filesystem.EntryIcon
import pl.dakil.appanalyser.ui.components.filesystem.iconForFolder
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.tabular

/** Same fixed leading slot as the browser's FileRow, so selection mode never changes row height. */
private val LEADING_SIZE = 40.dp

/**
 * One folder or file under the chart, as a segmented tile: name, size and share of the focus, with
 * a proportion bar (and, for folders, a badge) in the same colour as its chart segment so list and
 * chart read as one thing. [index] and [count] place it in its list for the segmented corners.
 */
@Composable
fun DiskUsageRow(
    item: DiskUsageItem,
    /** Fraction of the focus folder this item takes, 0..1. */
    share: Float,
    color: Color,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown instead of the file count, e.g. the parent folder in the largest-files list. */
    detail: String? = null,
    index: Int = 0,
    count: Int = 1,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val swapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = {
            if (!selectionMode && item.selectable) haptics.longPress()
            onLongClick()
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = {
            Box(modifier = Modifier.size(LEADING_SIZE), contentAlignment = Alignment.Center) {
                // Rows that can't be selected keep their icon rather than show a dead checkbox.
                AnimatedContent(
                    targetState = selectionMode && item.selectable,
                    transitionSpec = { (scaleIn(swapSpec) + fadeIn()) togetherWith (scaleOut(swapSpec) + fadeOut()) },
                    label = "usageLeading",
                ) { selecting ->
                    when {
                        selecting -> CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                        ) {
                            Checkbox(checked = selected, onCheckedChange = { onClick() })
                        }
                        item.restricted || item.isRestrictedSpace -> ShapedIcon(
                            AppSymbols.Lock,
                            polygon = MaterialShapes.Square,
                            size = LEADING_SIZE,
                            iconSize = 22.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        item.isDirectory -> ShapedIcon(
                            iconForFolder(item.path),
                            size = LEADING_SIZE,
                            iconSize = 22.dp,
                            containerColor = color,
                            contentColor = MaterialTheme.colorScheme.surface,
                        )
                        else -> EntryIcon(item.asEntry())
                    }
                }
            }
        },
        trailingContent = {
            Text(
                text = if (item.sizeUnknown) "?" else formatShare(share),
                style = MaterialTheme.typography.labelLargeEmphasized.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Column {
                val size = if (item.sizeUnknown) {
                    stringResource(R.string.disk_usage_unknown_size)
                } else {
                    Formatter.formatShortFileSize(context, item.sizeBytes)
                }
                val secondary = when {
                    detail != null -> detail
                    item.isRestrictedSpace -> stringResource(R.string.disk_usage_restricted_space_detail)
                    item.restricted -> stringResource(R.string.disk_usage_restricted)
                    item.isDirectory -> pluralStringResource(
                        R.plurals.disk_usage_files_x,
                        item.fileCount,
                        NumberFormat.getIntegerInstance().format(item.fileCount),
                    )
                    else -> null
                }
                Text(
                    text = if (secondary == null) size else "$size · $secondary",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.s))
                ShareBar(share, color)
            }
        },
    ) {
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ShareBar(share: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = share.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "shareBar",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (share > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated.coerceIn(0.015f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** "37%", or "<1%" for anything that would otherwise round down to nothing. */
private fun formatShare(share: Float): String {
    val percent = NumberFormat.getPercentInstance()
    return if (share > 0f && share < 0.01f) "<${percent.format(0.01)}" else percent.format(share.toDouble())
}

/** Lets the row reuse the browser's per-extension icons. */
private fun DiskUsageItem.asEntry() = FileEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    isHidden = name.startsWith('.'),
)
