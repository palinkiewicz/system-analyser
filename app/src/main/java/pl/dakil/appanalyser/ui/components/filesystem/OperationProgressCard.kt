package pl.dakil.appanalyser.ui.components.filesystem

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.FileOperationKind
import pl.dakil.appanalyser.domain.FileOperationProgress
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.tabular

/**
 * A long copy, move or delete, floating where the selection toolbar sits: what is happening, the
 * file being worked on, a wavy determinate bar and how much is done, with a way out.
 */
@Composable
fun OperationProgressCard(
    progress: FileOperationProgress,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    // Eased between reports, which arrive in steps, so the bar glides instead of jumping.
    val fraction by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "operationFraction",
    )
    val measuring = progress.totalFiles == 0 && progress.currentName == null

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = scheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.widthIn(max = 480.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.l, bottom = Spacing.s)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ShapedIcon(
                    symbol = when (progress.kind) {
                        FileOperationKind.COPY -> AppSymbols.FileCopy
                        FileOperationKind.MOVE -> AppSymbols.DriveFileMove
                        FileOperationKind.DELETE -> AppSymbols.Delete
                    },
                    polygon = MaterialShapes.Cookie9Sided,
                    size = 40.dp,
                    containerColor = if (progress.kind == FileOperationKind.DELETE) scheme.errorContainer else scheme.primaryContainer,
                    contentColor = if (progress.kind == FileOperationKind.DELETE) scheme.onErrorContainer else scheme.onPrimaryContainer,
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(end = Spacing.s)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        text = pluralStringResource(
                            when (progress.kind) {
                                FileOperationKind.COPY -> R.plurals.files_operation_copying_x
                                FileOperationKind.MOVE -> R.plurals.files_operation_moving_x
                                FileOperationKind.DELETE -> R.plurals.files_operation_deleting_x
                            },
                            progress.items,
                            progress.items,
                        ),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = progress.currentName ?: stringResource(R.string.files_operation_preparing),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
            if (measuring) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.m, end = Spacing.s))
            } else {
                LinearWavyProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.m, end = Spacing.s),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        measuring -> ""
                        progress.totalBytes > 0 -> stringResource(
                            R.string.files_operation_bytes,
                            Formatter.formatShortFileSize(context, progress.doneBytes),
                            Formatter.formatShortFileSize(context, progress.totalBytes),
                        )
                        else -> pluralStringResource(
                            R.plurals.files_operation_files_x,
                            progress.totalFiles,
                            progress.doneFiles,
                            progress.totalFiles,
                        )
                    },
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text(stringResource(R.string.files_cancel)) }
            }
        }
    }
}
