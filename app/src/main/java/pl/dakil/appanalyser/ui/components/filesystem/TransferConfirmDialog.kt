package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.ui.designsystem.ConnectedToggleRow
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.ToggleOption
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.theme.Spacing
import java.io.File

/**
 * The optional second step before a copy or move lands: what goes where, spelled out as a
 * from → to route, with the operation still switchable — so the dialog is the confirmation and a
 * last chance to pick the other verb, in the spirit of Total Commander's transfer dialog.
 */
@Composable
fun TransferConfirmDialog(
    paths: List<String>,
    destination: String,
    operation: ClipboardOperation,
    /** Volumes read better by their label than by their mount path's last segment. */
    folderLabel: (String) -> String,
    onConfirm: (ClipboardOperation) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var chosen by remember { mutableStateOf(operation) }
    val parents = paths.mapNotNull { File(it).parent }.distinct()
    val move = chosen == ClipboardOperation.MOVE

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AnimatedContent(targetState = move, label = "transferIcon") {
                DialogIcon(if (it) AppSymbols.DriveFileMove else AppSymbols.FileCopy)
            }
        },
        title = {
            AnimatedContent(targetState = move, label = "transferTitle") { isMove ->
                Text(
                    text = if (paths.size == 1) {
                        stringResource(
                            if (isMove) R.string.files_transfer_move_one else R.string.files_transfer_copy_one,
                            File(paths.first()).name,
                        )
                    } else {
                        pluralStringResource(
                            if (isMove) R.plurals.files_transfer_move_x else R.plurals.files_transfer_copy_x,
                            paths.size,
                            paths.size,
                        )
                    },
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                ConnectedToggleRow(
                    options = listOf(
                        ToggleOption(ClipboardOperation.COPY, stringResource(R.string.files_copy), AppSymbols.FileCopy),
                        ToggleOption(ClipboardOperation.MOVE, stringResource(R.string.files_move), AppSymbols.DriveFileMove),
                    ),
                    selected = chosen,
                    onSelect = { chosen = it },
                )
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(Modifier.padding(Spacing.m)) {
                        RouteStop(
                            caption = stringResource(R.string.files_transfer_from),
                            name = parents.singleOrNull()?.let(folderLabel)
                                ?: stringResource(R.string.files_transfer_several_folders),
                            path = parents.singleOrNull(),
                            highlighted = false,
                        )
                        // The arrow sits under the badges, so the route reads top to bottom.
                        Box(Modifier.size(width = 40.dp, height = 24.dp), contentAlignment = Alignment.Center) {
                            AppIcon(
                                AppSymbols.ArrowDownward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        RouteStop(
                            caption = stringResource(R.string.files_transfer_to),
                            name = folderLabel(destination),
                            path = destination,
                            highlighted = true,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.confirm()
                    onConfirm(chosen)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                AnimatedContent(targetState = move, label = "transferVerb") {
                    Text(stringResource(if (it) R.string.files_drop_move_here else R.string.files_drop_copy_here))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.files_cancel))
            }
        },
    )
}

@Composable
private fun RouteStop(caption: String, name: String, path: String?, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        ShapedIcon(
            symbol = path?.let { iconForFolder(it) } ?: AppSymbols.Folder,
            polygon = MaterialShapes.Cookie9Sided,
            size = 40.dp,
            containerColor = if (highlighted) scheme.primaryContainer else scheme.surfaceContainerLow,
            contentColor = if (highlighted) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        )
        Column(Modifier.weight(1f)) {
            Text(
                caption,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlighted) scheme.primary else scheme.onSurfaceVariant,
            )
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (path != null) {
                Text(
                    path,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

