package pl.dakil.appanalyser.ui.components.filesystem

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.domain.FileEntry

private const val HIDDEN_ALPHA = 0.6f

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

fun iconForEntry(entry: FileEntry): ImageVector = when {
    entry.isDirectory -> Icons.Rounded.Folder
    entry.extension in IMAGE_EXTENSIONS -> Icons.Rounded.Image
    entry.extension in VIDEO_EXTENSIONS -> Icons.Rounded.VideoFile
    entry.extension in AUDIO_EXTENSIONS -> Icons.Rounded.AudioFile
    entry.extension in ARCHIVE_EXTENSIONS -> Icons.Rounded.Archive
    entry.extension == "apk" -> Icons.Rounded.Android
    entry.extension == "pdf" -> Icons.Rounded.PictureAsPdf
    entry.extension in CODE_EXTENSIONS -> Icons.Rounded.Code
    entry.extension in TEXT_EXTENSIONS -> Icons.Rounded.Description
    else -> Icons.Rounded.InsertDriveFile
}

/**
 * One directory child. Mirrors the app-list row proportions so both lists feel the same; in
 * selection mode the leading icon is swapped for a checkbox.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    entry: FileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            Icon(
                imageVector = iconForEntry(entry),
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(40.dp)
                    .padding(8.dp)
                    .alpha(if (entry.isHidden) HIDDEN_ALPHA else 1f),
            )
        }
        Spacer(Modifier.width(16.dp))
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (entry.isHidden) HIDDEN_ALPHA else 1f),
            )
            Text(
                text = entrySubtitle(entry, context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
