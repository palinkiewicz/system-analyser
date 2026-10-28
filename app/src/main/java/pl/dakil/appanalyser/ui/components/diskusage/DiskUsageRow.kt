package pl.dakil.appanalyser.ui.components.diskusage

import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.DiskUsageItem
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.ui.components.filesystem.iconForEntry
import java.text.NumberFormat

/** Same fixed leading slot as the browser's FileRow, so selection mode never changes row height. */
private val LEADING_SIZE = 40.dp

/**
 * One folder or file under the chart: name, size and share of the focus, with a proportion bar in
 * the same colour as its chart segment so list and chart read as one thing.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    /** Shown instead of the file count, e.g. the parent folder in the largest-files list. */
    detail: String? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LEADING_SIZE), contentAlignment = Alignment.Center) {
            // Rows that can't be selected keep their icon rather than show a dead checkbox.
            if (selectionMode && item.selectable) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Checkbox(checked = selected, onCheckedChange = { onClick() })
                }
            } else {
                Icon(
                    imageVector = if (item.restricted || item.isRestrictedSpace) {
                        Icons.Rounded.Lock
                    } else {
                        iconForEntry(item.asEntry())
                    },
                    contentDescription = null,
                    tint = if (item.isDirectory) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (item.sizeUnknown) "?" else formatShare(share),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            ShareBar(share, color)
        }
    }
}

@Composable
private fun ShareBar(share: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (share > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceIn(0.01f, 1f))
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
