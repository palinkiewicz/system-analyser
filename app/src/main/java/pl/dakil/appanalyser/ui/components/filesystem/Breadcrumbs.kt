package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing

/**
 * Tappable path segments as pills; the current folder is the filled one, and the row glides to it
 * whenever the path changes. [roots] are (path, label) pairs; the one containing [path] becomes the
 * leading pill. Segments above [minPath] are shown for context but dimmed and not tappable — the
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

    val scrollState = rememberScrollState()
    // Keep the current folder (always last) in view as the user goes deeper or back up.
    LaunchedEffect(path, scrollState.maxValue) { scrollState.animateScrollTo(scrollState.maxValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = Spacing.gutter - Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
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
            AppIcon(
                AppSymbols.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
            Segment(
                text = segment,
                current = target == path,
                reachable = reachable(target),
                onClick = { onSegmentClick(target) },
            )
        }
    }
}

@Composable
private fun Segment(text: String, current: Boolean, reachable: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "segmentContainer",
    )
    val content = when {
        current -> MaterialTheme.colorScheme.onSecondaryContainer
        reachable -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    }
    val label: @Composable () -> Unit = {
        Text(
            text = text,
            style = if (current) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
        )
    }
    if (reachable && !current) {
        Surface(onClick = onClick, shape = CircleShape, color = container, contentColor = content, content = label)
    } else {
        Surface(shape = CircleShape, color = container, contentColor = content, content = label)
    }
}
