package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.io.File
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.ScanFreshness
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.canPostNotifications
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
import pl.dakil.appanalyser.ui.designsystem.AppMenu
import pl.dakil.appanalyser.ui.designsystem.AppMenuCheckItem
import pl.dakil.appanalyser.ui.designsystem.AppMenuChoiceItem
import pl.dakil.appanalyser.ui.designsystem.AppMenuGroup
import pl.dakil.appanalyser.ui.designsystem.AppMenuItem
import pl.dakil.appanalyser.ui.designsystem.AppMenuLabel
import pl.dakil.appanalyser.ui.designsystem.BackButton
import pl.dakil.appanalyser.ui.designsystem.EmptyState
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.UsageBar
import pl.dakil.appanalyser.ui.designsystem.appTopBarColors
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.FileSystemViewModel

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

    var fabMenuExpanded by remember { mutableStateOf(false) }
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

    val haptics = rememberHaptics()
    val inFolder = currentPath != null
    val selecting = selection.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AnimatedContent(targetState = selecting, label = "filesTopBar") { isSelecting ->
                if (isSelecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                    )
                } else {
                    BrowserTopBar(
                        title = currentPath?.let { File(it).name }
                            ?: stringResource(R.string.nav_files),
                        canNavigateUp = inFolder,
                        inFolder = inFolder,
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
            }
        },
        // The create menu only exists inside a folder with nothing selected and nothing waiting to
        // be pasted, so it never competes with the floating toolbars for the bottom edge.
        floatingActionButton = {
            AnimatedVisibility(
                visible = inFolder && !selecting && clipboard == null,
                enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                CreateMenu(
                    expanded = fabMenuExpanded,
                    onExpandedChange = {
                        haptics.tick()
                        fabMenuExpanded = it
                    },
                    onNewFolder = {
                        fabMenuExpanded = false
                        createFolder = true
                    },
                    onNewFile = {
                        fabMenuExpanded = false
                        createFile = true
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxSize(),
            ) {
                currentPath?.let { path ->
                    Breadcrumbs(
                        path = path,
                        roots = roots.map { it.path to it.label },
                        onSegmentClick = { viewModel.open(it) },
                    )
                }

                Box(Modifier.weight(1f)) {
                    when {
                        isLoading || isBusy -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            LoadingIndicator(Modifier.size(64.dp))
                        }
                        currentPath == null -> StorageRootList(
                            roots = roots,
                            onOpen = { viewModel.open(it) },
                        )
                        entries.isEmpty() -> Box(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            Alignment.Center,
                        ) {
                            EmptyState(
                                symbol = AppSymbols.FolderOpen,
                                title = stringResource(R.string.files_empty_folder),
                                polygon = MaterialShapes.Cookie9Sided,
                            )
                        }
                        else -> LazyColumn(
                            contentPadding = PaddingValues(
                                start = Spacing.gutter,
                                end = Spacing.gutter,
                                top = Spacing.xs,
                                // Room for the FAB or a floating toolbar over the last rows.
                                bottom = 112.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        ) {
                            itemsIndexed(entries, key = { _, entry -> entry.path }) { index, entry ->
                                FileRow(
                                    entry = entry,
                                    selected = entry.path in selection,
                                    selectionMode = selecting,
                                    index = index,
                                    count = entries.size,
                                    modifier = Modifier.animateItem(),
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

            // Selection actions float over the list's bottom edge (five of them didn't fit the
            // top bar); the paste bar takes the same spot once something is on the clipboard.
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.l),
            ) {
                SelectionToolbar(
                    single = selection.size == 1,
                    onCopy = { viewModel.copySelection() },
                    onCut = { viewModel.cutSelection() },
                    onRename = { renameTarget = selection.first() },
                    onShare = { shareTarget = selection.firstOrNull() },
                    onDelete = {
                        if (confirmDeletion) {
                            showDeleteConfirm = true
                        } else {
                            haptics.confirm()
                            viewModel.deleteSelection()
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = clipboard != null && inFolder && !selecting,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.l, start = Spacing.gutter, end = Spacing.gutter),
            ) {
                val clip = clipboard
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
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
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(horizontal = Spacing.m)
                            .align(Alignment.CenterVertically)
                            .weight(1f, fill = false),
                    )
                    TextButton(onClick = { viewModel.clearClipboard() }) {
                        Text(stringResource(R.string.files_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.confirm()
                            viewModel.paste()
                        },
                    ) {
                        AppIcon(AppSymbols.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.files_paste), modifier = Modifier.padding(start = Spacing.s))
                    }
                }
            }
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
    val haptics = rememberHaptics()

    TopAppBar(
        title = {
            AnimatedContent(targetState = title, label = "folderTitle") {
                Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        navigationIcon = {
            if (canNavigateUp) BackButton(onNavigateUp)
        },
        actions = {
            IconButton(onClick = onOpenDiskUsage, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.DonutLarge, contentDescription = stringResource(R.string.disk_usage_title))
            }
            if (inFolder) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.Sort, contentDescription = stringResource(R.string.files_more_options))
                    }
                    AppMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        AppMenuGroup(index = 0, count = 3) {
                            AppMenuLabel(stringResource(R.string.files_sort_by))
                            FileSortOrder.entries.forEach { order ->
                                AppMenuChoiceItem(
                                    text = order.label(),
                                    selected = order == sortOrder,
                                    onClick = {
                                        if (order != sortOrder) haptics.tick()
                                        viewModel.setSortOrder(order)
                                    },
                                )
                            }
                        }
                        AppMenuGroup(index = 1, count = 3) {
                            AppMenuCheckItem(
                                text = stringResource(R.string.files_sort_descending),
                                checked = descending,
                                onCheckedChange = { viewModel.setSortDescending(it) },
                                symbol = AppSymbols.ArrowDownward,
                            )
                            AppMenuCheckItem(
                                text = stringResource(R.string.files_show_hidden),
                                checked = showHidden,
                                onCheckedChange = { viewModel.setShowHidden(it) },
                                symbol = AppSymbols.Visibility,
                            )
                        }
                        AppMenuGroup(index = 2, count = 3) {
                            AppMenuItem(
                                text = stringResource(R.string.files_select_all),
                                symbol = AppSymbols.SelectAll,
                                onClick = {
                                    viewModel.selectAll()
                                    menuExpanded = false
                                },
                            )
                            AppMenuItem(
                                text = stringResource(R.string.files_properties),
                                symbol = AppSymbols.Info,
                                onClick = {
                                    onProperties()
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
        colors = appTopBarColors(),
    )
}

@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
) {
    TopAppBar(
        title = {
            AnimatedContent(targetState = count, label = "selectionCount") {
                Text(
                    text = pluralStringResource(R.plurals.files_selected_x, it, it),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.files_cancel))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.SelectAll, contentDescription = stringResource(R.string.files_select_all))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

/** The actions for the current selection, on a vibrant floating toolbar. */
@Composable
private fun SelectionToolbar(
    single: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        ToolbarAction(AppSymbols.ContentCopy, stringResource(R.string.files_copy), onCopy)
        ToolbarAction(AppSymbols.ContentCut, stringResource(R.string.files_cut), onCut)
        AnimatedVisibility(visible = single) {
            Row {
                ToolbarAction(AppSymbols.DriveFileRenameOutline, stringResource(R.string.files_rename), onRename)
                ToolbarAction(AppSymbols.Share, stringResource(R.string.files_share), onShare)
            }
        }
        ToolbarAction(AppSymbols.Delete, stringResource(R.string.files_delete), onDelete)
    }
}

@Composable
private fun ToolbarAction(symbol: AppSymbol, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        AppIcon(symbol, contentDescription = label)
    }
}

/** New folder / new file, from an expressive FAB menu. */
@Composable
private fun CreateMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNewFolder: () -> Unit,
    onNewFile: () -> Unit,
) {
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = onExpandedChange,
            ) {
                val progress = { checkedProgress }
                AppIcon(
                    if (checkedProgress > 0.5f) AppSymbols.Close else AppSymbols.Add,
                    contentDescription = stringResource(R.string.files_create),
                    modifier = with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon(progress) },
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = onNewFolder,
            text = { Text(stringResource(R.string.files_new_folder)) },
            icon = { AppIcon(AppSymbols.CreateNewFolder, contentDescription = null) },
        )
        FloatingActionButtonMenuItem(
            onClick = onNewFile,
            text = { Text(stringResource(R.string.files_new_file)) },
            icon = { AppIcon(AppSymbols.NoteAdd, contentDescription = null) },
        )
    }
}

// ---------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------

/** The volumes to start from, as tiles showing how full each one is. */
@Composable
private fun StorageRootList(
    roots: List<StorageRoot>,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        itemsIndexed(roots, key = { _, root -> root.path }) { index, root ->
            SegmentedListItem(
                onClick = { onOpen(root.path) },
                shapes = ListItemDefaults.segmentedShapes(index, roots.size),
                colors = segmentedItemColors(),
                leadingContent = {
                    ShapedIcon(
                        if (root.isRemovable) AppSymbols.SdCard else AppSymbols.Smartphone,
                        polygon = if (root.isRemovable) MaterialShapes.Cookie6Sided else MaterialShapes.Cookie9Sided,
                        size = 48.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text(root.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        if (root.totalBytes > 0) {
                            UsageBar(root.usedBytes.toFloat() / root.totalBytes)
                            Text(
                                stringResource(
                                    R.string.disk_usage_used_of,
                                    Formatter.formatShortFileSize(context, root.usedBytes),
                                    Formatter.formatShortFileSize(context, root.totalBytes),
                                ),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                trailingContent = {
                    AppIcon(AppSymbols.ChevronRight, contentDescription = null)
                },
            ) {
                Text(root.label, style = MaterialTheme.typography.titleMedium)
            }
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
