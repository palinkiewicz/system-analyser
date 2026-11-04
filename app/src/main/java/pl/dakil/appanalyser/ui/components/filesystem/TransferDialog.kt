package pl.dakil.appanalyser.ui.components.filesystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.domain.transferAllowed
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import java.io.File

/**
 * Total Commander mobile's transfer dialog, behind the optional single transfer button: where the
 * selection would go — tap it to pick another folder — and Copy, Move or Share. Choosing an action
 * is the confirmation, so nothing asks again after it.
 */
@Composable
fun TransferDialog(
    paths: List<String>,
    initialDestination: String,
    roots: List<StorageRoot>,
    /** Volumes read better by their label than by their mount path's last segment. */
    folderLabel: (String) -> String,
    /** Share hands over one item, so it is only offered for a single selection. */
    canShare: Boolean,
    listFolders: suspend (String) -> List<FileEntry>,
    onTransfer: (destination: String, operation: ClipboardOperation) -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var picking by rememberSaveable { mutableStateOf(false) }

    // One dialog at a time: the picker stands in for this one and hands the folder back.
    if (picking) {
        FolderPickerDialog(
            initial = destination,
            roots = roots,
            folderLabel = folderLabel,
            listFolders = listFolders,
            onPick = {
                destination = it
                picking = false
            },
            onDismiss = { picking = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(AppSymbols.DriveFileMove) },
        title = {
            Text(
                text = if (paths.size == 1) {
                    stringResource(R.string.files_transfer_to_one, File(paths.first()).name)
                } else {
                    pluralStringResource(R.plurals.files_transfer_to_x, paths.size, paths.size)
                },
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
                Surface(
                    onClick = {
                        haptics.tick()
                        picking = true
                    },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RouteStop(
                            caption = null,
                            name = folderLabel(destination),
                            path = destination,
                            highlighted = true,
                            modifier = Modifier.weight(1f),
                        )
                        AppIcon(
                            AppSymbols.ChevronRight,
                            contentDescription = stringResource(R.string.files_transfer_change_destination),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = Spacing.s)
                                .size(20.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    TransferAction(
                        symbol = AppSymbols.FileCopy,
                        label = stringResource(R.string.files_copy),
                        polygon = MaterialShapes.Cookie9Sided,
                        enabled = transferAllowed(paths, destination, ClipboardOperation.COPY),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.confirm()
                            onTransfer(destination, ClipboardOperation.COPY)
                        },
                    )
                    TransferAction(
                        symbol = AppSymbols.DriveFileMove,
                        label = stringResource(R.string.files_move),
                        polygon = MaterialShapes.Cookie6Sided,
                        enabled = transferAllowed(paths, destination, ClipboardOperation.MOVE),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            haptics.confirm()
                            onTransfer(destination, ClipboardOperation.MOVE)
                        },
                    )
                    TransferAction(
                        symbol = AppSymbols.Share,
                        label = stringResource(R.string.files_share),
                        polygon = MaterialShapes.Sunny,
                        enabled = canShare,
                        modifier = Modifier.weight(1f),
                        onClick = onShare,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.files_cancel))
            }
        },
    )
}

/** One of the dialog's three verbs: a shaped glyph over its label, on a tonal tile. */
@Composable
private fun TransferAction(
    symbol: AppSymbol,
    label: String,
    polygon: RoundedPolygon,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = scheme.secondaryContainer,
        contentColor = scheme.onSecondaryContainer,
        modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.m, horizontal = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ShapedIcon(
                symbol,
                polygon = polygon,
                size = 40.dp,
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A folders-only browser for the transfer destination. It starts at [initial]; going up past a
 * volume's root lands on the volume list, where there is nothing to select yet.
 */
@Composable
fun FolderPickerDialog(
    initial: String,
    roots: List<StorageRoot>,
    folderLabel: (String) -> String,
    listFolders: suspend (String) -> List<FileEntry>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    // Null is the volume list.
    var path by rememberSaveable { mutableStateOf<String?>(initial) }
    var folders by remember { mutableStateOf<List<FileEntry>?>(null) }
    LaunchedEffect(path) {
        folders = null
        folders = path?.let { listFolders(it) }
    }

    fun up() {
        val current = path ?: return
        path = if (roots.any { it.path == current }) null else File(current).parent
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { up() }, enabled = path != null) {
                    AppIcon(AppSymbols.ArrowUpward, contentDescription = stringResource(R.string.files_pick_folder_up))
                }
                Column(Modifier.padding(start = Spacing.s)) {
                    Text(
                        text = path?.let(folderLabel) ?: stringResource(R.string.files_pick_folder_volumes),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = path ?: stringResource(R.string.files_pick_folder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PICKER_LIST_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                val current = path
                val shown = folders
                when {
                    current == null -> PickerList(
                        items = roots.map { PickerItem(it.path, it.label, if (it.isRemovable) AppSymbols.SdCard else AppSymbols.Smartphone) },
                        onOpen = {
                            haptics.tick()
                            path = it
                        },
                    )
                    shown == null -> LoadingIndicator(Modifier.size(48.dp))
                    shown.isEmpty() -> Text(
                        stringResource(R.string.files_pick_folder_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> PickerList(
                        items = shown.map { PickerItem(it.path, it.name, iconForFolder(it.path)) },
                        onOpen = {
                            haptics.tick()
                            path = it
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    path?.let {
                        haptics.confirm()
                        onPick(it)
                    }
                },
                enabled = path != null,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.files_pick_folder_select))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.files_cancel))
            }
        },
    )
}

private data class PickerItem(val path: String, val name: String, val symbol: AppSymbol)

@Composable
private fun PickerList(items: List<PickerItem>, onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(PICKER_LIST_HEIGHT),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
            SegmentedListItem(
                onClick = { onOpen(item.path) },
                shapes = ListItemDefaults.segmentedShapes(index, items.size),
                colors = segmentedItemColors(),
                leadingContent = {
                    ShapedIcon(
                        item.symbol,
                        polygon = MaterialShapes.Cookie9Sided,
                        size = 36.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
            ) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private val PICKER_LIST_HEIGHT = 320.dp
private const val DISABLED_ALPHA = 0.38f
