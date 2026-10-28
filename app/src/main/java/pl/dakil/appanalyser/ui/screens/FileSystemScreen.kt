package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.ScanFreshness
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.filesystem.AllFilesAccessGate
import pl.dakil.appanalyser.ui.components.filesystem.Breadcrumbs
import pl.dakil.appanalyser.ui.components.filesystem.DeleteConfirmDialog
import pl.dakil.appanalyser.ui.components.filesystem.EDITABLE_EXTENSIONS
import pl.dakil.appanalyser.ui.components.filesystem.FileRow
import pl.dakil.appanalyser.ui.components.filesystem.NameInputDialog
import pl.dakil.appanalyser.ui.components.filesystem.NoNetworkDialog
import pl.dakil.appanalyser.ui.components.filesystem.PropertiesDialog
import pl.dakil.appanalyser.ui.components.filesystem.ScanFolderDialog
import pl.dakil.appanalyser.ui.components.filesystem.ShareMethodSheet
import pl.dakil.appanalyser.ui.components.filesystem.openFile
import pl.dakil.appanalyser.ui.components.filesystem.openHotspotSettings
import pl.dakil.appanalyser.ui.components.filesystem.shareFile
import pl.dakil.appanalyser.ui.components.canPostNotifications
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.FileSystemViewModel
import java.io.File

/**
 * The experimental File system tab: a folder tree over the real volumes.
 *
 * All files access is never requested before the user gets here — the gate below is the only place
 * that asks, and only when the user taps its button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSystemScreen(
    onOpenEditor: (String) -> Unit,
    /**
     * Opens the disk usage analyser: its volume list for a null path, otherwise that folder —
     * scanning it afresh when the second argument is set.
     */
    onOpenDiskUsage: (String?, Boolean) -> Unit,
    /** A folder to open on arrival ("Show in browser"); null keeps wherever the tab was. */
    initialPath: String? = null,
    viewModel: FileSystemViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasAccess by viewModel.hasAccess.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val roots by viewModel.roots.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val confirmDeletion by viewModel.confirmDeletion.collectAsState()

    // Re-checked on entry and on every resume, because the All files access grant happens in a
    // system settings page that reports no result back to us.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Entering the tab is what triggers the check; the gate below still waits for a tap before
    // anything is actually requested from the system.
    LaunchedEffect(Unit) { viewModel.refreshAccess() }

    // Handled once per navigation, like the Device tab's deep link: a later restore of this entry
    // keeps the folder the user browsed to since.
    var handledInitialPath by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialPath, hasAccess) {
        if (!handledInitialPath && hasAccess && initialPath != null) {
            viewModel.open(initialPath)
            handledInitialPath = true
        }
    }

    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }
    val openFailedMessage = stringResource(R.string.files_open_failed)

    var showCreateSheet by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var createFile by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var scanPrompt by remember { mutableStateOf<Pair<String, ScanFreshness>?>(null) }

    // Inside a folder the analyser opens on that folder. A current scan shows straight away;
    // otherwise the user is asked first, and only that folder is scanned — never the whole volume.
    fun openDiskUsage() {
        val path = currentPath ?: return onOpenDiskUsage(null, false)
        when (val freshness = viewModel.diskScanFreshness(path)) {
            ScanFreshness.Fresh -> onOpenDiskUsage(path, false)
            else -> scanPrompt = path to freshness
        }
    }

    // Wi-Fi sharing state. The path is captured when the sheet opens, because starting the server
    // clears the selection.
    var shareTarget by remember { mutableStateOf<String?>(null) }
    var showNoNetwork by remember { mutableStateOf(false) }
    val shareState by ShareServerRepository.state.collectAsState()
    val networkInfo = remember(context) { NetworkInfoProvider(context) }
    // The running dialog itself lives in AppNavigation; once a session is up the target is spent,
    // and keeping it would bring the method sheet back as soon as the session stops.
    LaunchedEffect(shareState) {
        if (shareState is ShareState.Running) shareTarget = null
    }
    val notificationsBlockedMessage = stringResource(R.string.share_notification_permission_required)

    fun startWifiShare(path: String) {
        if (networkInfo.resolve() == null) {
            showNoNetwork = true
        } else {
            ShareServerManager.start(context, path)
        }
    }

    // The notification permission is only relevant to the share server, so it is requested here —
    // the moment before starting it — rather than anywhere earlier in the app.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = shareTarget
        // Starting without it would leave a server running that the user can neither see nor stop.
        if (granted && target != null) {
            startWifiShare(target)
        } else {
            shareTarget = null
            scope.launch { snackbarHostState.showSnackbar(notificationsBlockedMessage) }
        }
    }

    BackHandler(enabled = selection.isNotEmpty() || currentPath != null) {
        if (selection.isNotEmpty()) viewModel.clearSelection() else viewModel.navigateUp()
    }

    if (!hasAccess) {
        AllFilesAccessGate(onGranted = { viewModel.refreshAccess() })
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selection.isNotEmpty()) {
                SelectionTopBar(
                    count = selection.size,
                    onClose = { viewModel.clearSelection() },
                    onCopy = { viewModel.copySelection() },
                    onCut = { viewModel.cutSelection() },
                    onRename = { renameTarget = selection.first() },
                    onDelete = {
                        if (confirmDeletion) showDeleteConfirm = true else viewModel.deleteSelection()
                    },
                    onShare = { shareTarget = selection.firstOrNull() },
                )
            } else {
                BrowserTopBar(
                    title = currentPath?.let { File(it).name }
                        ?: stringResource(R.string.nav_files),
                    canNavigateUp = currentPath != null,
                    inFolder = currentPath != null,
                    viewModel = viewModel,
                    onNavigateUp = { viewModel.navigateUp() },
                    onOpenDiskUsage = { openDiskUsage() },
                    onProperties = {
                        currentPath?.let { path ->
                            propertiesTarget = File(path).toEntry()
                        }
                    },
                )
            }
        },
        // The paste bar lives in the bottomBar slot rather than in the content, so the Scaffold
        // lifts the FAB above it instead of letting the FAB cover Paste and Cancel.
        bottomBar = {
            AnimatedVisibility(
                visible = clipboard != null && currentPath != null,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
            ) {
                val clip = clipboard
                Surface(tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pluralStringResource(
                                if (clip?.operation == ClipboardOperation.MOVE) {
                                    R.plurals.files_clipboard_move_x
                                } else {
                                    R.plurals.files_clipboard_copy_x
                                },
                                clip?.paths?.size ?: 0,
                                clip?.paths?.size ?: 0,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.clearClipboard() }) {
                            Text(stringResource(R.string.files_cancel))
                        }
                        TextButton(onClick = { viewModel.paste() }) {
                            Text(stringResource(R.string.files_paste))
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentPath != null && selection.isEmpty()) {
                FloatingActionButton(onClick = { showCreateSheet = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.files_create))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            currentPath?.let { path ->
                Breadcrumbs(
                    path = path,
                    roots = roots.map { it.path to it.label },
                    onSegmentClick = { viewModel.open(it) },
                )
                HorizontalDivider()
            }

            Box(Modifier.weight(1f)) {
                when {
                    isLoading || isBusy -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    currentPath == null -> StorageRootList(
                        roots = roots,
                        onOpen = { viewModel.open(it) },
                    )
                    entries.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            text = stringResource(R.string.files_empty_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(entries, key = { it.path }) { entry ->
                            FileRow(
                                entry = entry,
                                selected = entry.path in selection,
                                selectionMode = selection.isNotEmpty(),
                                onClick = {
                                    when {
                                        selection.isNotEmpty() -> viewModel.toggleSelection(entry.path)
                                        entry.isDirectory -> viewModel.open(entry.path)
                                        entry.extension in EDITABLE_EXTENSIONS ->
                                            onOpenEditor(entry.path)
                                        else -> openFile(
                                            context = context,
                                            uri = viewModel.contentUri(entry.path),
                                            mimeType = viewModel.mimeType(entry.path),
                                        ) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(openFailedMessage)
                                            }
                                        }
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(entry.path) },
                            )
                        }
                    }
                }
            }

        }
    }

    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            ListItem(
                modifier = Modifier.clickable {
                    showCreateSheet = false
                    createFolder = true
                },
                headlineContent = { Text(stringResource(R.string.files_new_folder)) },
                leadingContent = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
            )
            ListItem(
                modifier = Modifier.clickable {
                    showCreateSheet = false
                    createFile = true
                },
                headlineContent = { Text(stringResource(R.string.files_new_file)) },
                supportingContent = { Text(stringResource(R.string.files_new_file_summary)) },
                leadingContent = {
                    Icon(Icons.AutoMirrored.Rounded.NoteAdd, contentDescription = null)
                },
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (createFolder) {
        NameInputDialog(
            title = stringResource(R.string.files_new_folder),
            confirmLabel = stringResource(R.string.files_create),
            onConfirm = {
                viewModel.createFolder(it)
                createFolder = false
            },
            onDismiss = { createFolder = false },
        )
    }

    if (createFile) {
        NameInputDialog(
            title = stringResource(R.string.files_new_file),
            confirmLabel = stringResource(R.string.files_create),
            onConfirm = {
                viewModel.createFile(it)
                createFile = false
            },
            onDismiss = { createFile = false },
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = stringResource(R.string.files_rename),
            confirmLabel = stringResource(R.string.files_rename),
            initialName = File(target).name,
            onConfirm = {
                viewModel.rename(target, it)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            count = selection.size,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelection()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    // Only shown while nothing is being hosted yet; once the server is up the running dialog wins.
    if (shareTarget != null && shareState !is ShareState.Running && !showNoNetwork) {
        val target = shareTarget!!
        ShareMethodSheet(
            onSendToApp = {
                shareFile(context, viewModel.contentUri(target), viewModel.mimeType(target))
                shareTarget = null
                viewModel.clearSelection()
            },
            onShareOverWifi = {
                viewModel.clearSelection()
                if (context.canPostNotifications()) {
                    startWifiShare(target)
                } else {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onDismiss = { shareTarget = null },
        )
    }

    if (showNoNetwork) {
        NoNetworkDialog(
            onOpenHotspotSettings = {
                showNoNetwork = false
                openHotspotSettings(context)
            },
            onDismiss = {
                showNoNetwork = false
                shareTarget = null
            },
        )
    }

    scanPrompt?.let { (path, freshness) ->
        val volume = roots.firstOrNull { it.path == path }
        ScanFolderDialog(
            folderName = volume?.label ?: File(path).name,
            isVolume = volume != null,
            lastScannedAt = (freshness as? ScanFreshness.Stale)?.scannedAt,
            onScan = {
                scanPrompt = null
                onOpenDiskUsage(path, true)
            },
            onShowLastScan = {
                scanPrompt = null
                onOpenDiskUsage(path, false)
            },
            onDismiss = { scanPrompt = null },
        )
    }

    propertiesTarget?.let { entry ->
        var folderSize by remember(entry.path) { mutableStateOf<Long?>(null) }
        var children by remember(entry.path) { mutableStateOf<Int?>(null) }
        LaunchedEffect(entry.path) {
            if (entry.isDirectory) {
                children = viewModel.childCount(entry.path)
                folderSize = viewModel.directorySize(entry.path)
            }
        }
        PropertiesDialog(
            entry = entry,
            folderSizeBytes = folderSize,
            childCount = children,
            onDismiss = { propertiesTarget = null },
        )
    }
}

// ---------------------------------------------------------------------------
// Bars
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    title: String,
    canNavigateUp: Boolean,
    inFolder: Boolean,
    viewModel: FileSystemViewModel,
    onNavigateUp: () -> Unit,
    onOpenDiskUsage: () -> Unit,
    onProperties: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sortOrder by viewModel.sortOrder.collectAsState()
    val descending by viewModel.sortDescending.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()

    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (canNavigateUp) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onOpenDiskUsage) {
                Icon(
                    Icons.Rounded.DonutLarge,
                    contentDescription = stringResource(R.string.disk_usage_title),
                )
            }
            if (inFolder) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.files_more_options),
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    Text(
                        text = stringResource(R.string.files_sort_by),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    FileSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label()) },
                            onClick = { viewModel.setSortOrder(order) },
                            trailingIcon = {
                                if (order == sortOrder) {
                                    Icon(Icons.Rounded.Check, contentDescription = null)
                                }
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_sort_descending)) },
                        onClick = { viewModel.setSortDescending(!descending) },
                        trailingIcon = {
                            if (descending) Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_show_hidden)) },
                        onClick = { viewModel.setShowHidden(!showHidden) },
                        trailingIcon = {
                            if (showHidden) Icon(Icons.Rounded.Check, contentDescription = null)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_select_all)) },
                        onClick = {
                            viewModel.selectAll()
                            menuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_properties)) },
                        leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                        onClick = {
                            onProperties()
                            menuExpanded = false
                        },
                    )
                }
            }
        },
        colors = flatTopAppBarColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Everything lives in the overflow: five icon buttons overflowed the bar, which both hid the
    // trailing actions and squeezed the count label onto three lines.
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(R.plurals.files_selected_x, count, count),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.files_cancel))
            }
        },
        actions = {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.files_more_options),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_copy)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onCopy()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_cut)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCut, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onCut()
                    },
                )
                if (count == 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_rename)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.files_share)) },
                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        },
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_delete)) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        },
        colors = flatTopAppBarColors(),
    )
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

@Composable
private fun StorageRootList(
    roots: List<pl.dakil.appanalyser.domain.StorageRoot>,
    onOpen: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(roots, key = { it.path }) { root ->
            ListItem(
                modifier = Modifier.clickable { onOpen(root.path) },
                headlineContent = { Text(root.label) },
                supportingContent = { Text(root.path) },
                leadingContent = {
                    Icon(
                        imageVector = if (root.isRemovable) {
                            Icons.Rounded.SdStorage
                        } else {
                            Icons.Rounded.Smartphone
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun FileSortOrder.label(): String = stringResource(
    when (this) {
        FileSortOrder.NAME -> R.string.files_sort_name
        FileSortOrder.SIZE -> R.string.files_sort_size
        FileSortOrder.DATE -> R.string.files_sort_date
        FileSortOrder.TYPE -> R.string.files_sort_type
    }
)

private fun File.toEntry() = FileEntry(
    name = name,
    path = absolutePath,
    isDirectory = isDirectory,
    sizeBytes = if (isDirectory) 0L else length(),
    lastModified = lastModified(),
    isHidden = isHidden,
)
