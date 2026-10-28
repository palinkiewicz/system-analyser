package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R

/**
 * Tappable path segments. [roots] are (path, label) pairs; the one containing [path] becomes the
 * leading chip. Segments above [minPath] are shown for context but dimmed and not tappable — the
 * analyser uses it when it only scanned a subfolder.
 */
@Composable
fun Breadcrumbs(
    path: String,
    roots: List<Pair<String, String>>,
    onSegmentClick: (String) -> Unit,
    minPath: String? = null,
) {
    fun reachable(target: String) =
        minPath == null || target == minPath || target.startsWith("$minPath/")

    val root = roots.firstOrNull { path == it.first || path.startsWith("${it.first}/") }
    val relative = root?.let { path.removePrefix(it.first).trim('/') } ?: path.trim('/')
    val segments = if (relative.isEmpty()) emptyList() else relative.split('/')
    val basePath = root?.first ?: "/"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Segment(
            text = root?.second ?: stringResource(R.string.files_root),
            current = basePath == path,
            reachable = reachable(basePath),
            onClick = { onSegmentClick(basePath) },
        )
        var accumulated = basePath
        segments.forEach { segment ->
            accumulated = "$accumulated/$segment"
            val target = accumulated
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Segment(
                text = segment,
                current = target == path,
                reachable = reachable(target),
                onClick = { onSegmentClick(target) },
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Segment(text: String, current: Boolean, reachable: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            current -> MaterialTheme.colorScheme.onSurface
            reachable -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = if (reachable && !current) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
