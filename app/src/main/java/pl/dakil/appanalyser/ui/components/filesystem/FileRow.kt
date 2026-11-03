package pl.dakil.appanalyser.ui.components.filesystem

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors

private const val HIDDEN_ALPHA = 0.6f

/** Fixed leading slot so the row height never depends on selection mode. */
private val LEADING_SIZE = 40.dp

/** Extension groups worth a distinct icon; everything else falls back to a generic file. */
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "svg", "ico")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "ogg", "flac", "m4a", "aac", "opus")
private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
private val CODE_EXTENSIONS = setOf(
    "kt", "kts", "java", "js", "ts", "tsx", "jsx", "c", "h", "cpp", "hpp", "py", "rb", "go", "rs",
    "swift", "sh", "bash", "gradle", "json", "xml", "html", "css", "yml", "yaml", "toml",
)
private val TEXT_EXTENSIONS = setOf("txt", "md", "log", "csv", "ini", "conf", "properties", "env", "rtf")

/** Extensions the built-in editor opens directly rather than handing to another app. */
val EDITABLE_EXTENSIONS: Set<String> = CODE_EXTENSIONS + TEXT_EXTENSIONS

fun iconForEntry(entry: FileEntry): AppSymbol = when {
    entry.isDirectory -> AppSymbols.Folder
    entry.extension in IMAGE_EXTENSIONS -> AppSymbols.Image
    entry.extension in VIDEO_EXTENSIONS -> AppSymbols.VideoFile
    entry.extension in AUDIO_EXTENSIONS -> AppSymbols.AudioFile
    entry.extension in ARCHIVE_EXTENSIONS -> AppSymbols.FolderOpen
    entry.extension == "apk" -> AppSymbols.Android
    entry.extension == "pdf" -> AppSymbols.PictureAsPdf
    entry.extension in CODE_EXTENSIONS -> AppSymbols.Code
    entry.extension in TEXT_EXTENSIONS -> AppSymbols.Description
    else -> AppSymbols.Draft
}

/**
 * An entry's type badge: folders sit in a primary cookie, media in tertiary, packages in secondary,
 * and documents in a neutral rounded square, so a listing reads by kind at a glance.
 */
@Composable
fun EntryIcon(entry: FileEntry, modifier: Modifier = Modifier, size: Dp = LEADING_SIZE) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when {
        entry.isDirectory -> scheme.primaryContainer to scheme.onPrimaryContainer
        entry.extension in IMAGE_EXTENSIONS || entry.extension in VIDEO_EXTENSIONS ->
            scheme.tertiaryContainer to scheme.onTertiaryContainer
        entry.extension in AUDIO_EXTENSIONS || entry.extension in ARCHIVE_EXTENSIONS || entry.extension == "apk" ->
            scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    ShapedIcon(
        symbol = iconForEntry(entry),
        polygon = if (entry.isDirectory) MaterialShapes.Cookie9Sided else MaterialShapes.Square,
        size = size,
        iconSize = 22.dp,
        containerColor = container,
        contentColor = content,
        filled = entry.isDirectory,
        modifier = modifier,
    )
}

/**
 * One directory child as a segmented tile: [index] and [count] place it in its list so the first
 * and last tiles carry the group's big corners. A selected tile fills and rounds; in selection mode
 * the type badge flips to a checkbox.
 *
 * With [longPressExternal] the long press is left to a gesture handler on [modifier] (the file
 * drag), which would otherwise race this tile's own long-click; [onLongClick] then only serves
 * accessibility. [dropHighlighted] marks a folder that carried items would be dropped into.
 */
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
    longPressExternal: Boolean = false,
    dropHighlighted: Boolean = false,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val entryAlpha = if (entry.isHidden) HIDDEN_ALPHA else 1f
    val swapSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    SegmentedListItem(
        selected = selected || dropHighlighted,
        onClick = onClick,
        onLongClick = if (longPressExternal) {
            null
        } else {
            {
                if (!selectionMode) haptics.longPress()
                onLongClick()
            }
        },
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = if (longPressExternal) {
            modifier.semantics {
                onLongClick {
                    onLongClick()
                    true
                }
            }
        } else {
            modifier
        },
        leadingContent = {
            // Both leading states share one fixed slot: the checkbox's 48.dp minimum touch target
            // would otherwise make every row taller the moment selection mode starts.
            Box(modifier = Modifier.size(LEADING_SIZE), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = selectionMode,
                    transitionSpec = {
                        (scaleIn(swapSpec) + fadeIn()) togetherWith (scaleOut(swapSpec) + fadeOut())
                    },
                    label = "rowLeading",
                ) { selecting ->
                    if (selecting) {
                        CompositionLocalProvider(
                            LocalMinimumInteractiveComponentSize provides Dp.Unspecified
                        ) {
                            Checkbox(checked = selected, onCheckedChange = { onClick() })
                        }
                    } else {
                        EntryIcon(entry, Modifier.alpha(entryAlpha))
                    }
                }
            }
        },
        supportingContent = {
            Text(
                text = entrySubtitle(entry, context),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            text = entry.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(entryAlpha),
        )
    }
}

/** "12 KB · 3 days ago" for files, just the date for folders (whose size isn't computed on list). */
private fun entrySubtitle(entry: FileEntry, context: android.content.Context): String {
    val modified = DateUtils.getRelativeTimeSpanString(
        entry.lastModified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return if (entry.isDirectory) {
        modified
    } else {
        "${Formatter.formatFileSize(context, entry.sizeBytes)} · $modified"
    }
}
