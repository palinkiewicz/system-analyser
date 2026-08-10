package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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
import pl.dakil.appanalyser.domain.ShareNetwork
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.filesystem.DeleteConfirmDialog
import pl.dakil.appanalyser.ui.components.filesystem.EDITABLE_EXTENSIONS
import pl.dakil.appanalyser.ui.components.filesystem.FileRow
import pl.dakil.appanalyser.ui.components.filesystem.NameInputDialog
import pl.dakil.appanalyser.ui.components.filesystem.NoNetworkDialog
import pl.dakil.appanalyser.ui.components.filesystem.PropertiesDialog
import pl.dakil.appanalyser.ui.components.filesystem.ShareMethodSheet
import pl.dakil.appanalyser.ui.components.filesystem.ShareRunningDialog
import pl.dakil.appanalyser.ui.components.filesystem.openHotspotSettings
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

    // Wi-Fi sharing state. The path is captured when the sheet opens, because starting the server
    // clears the selection.
    var shareTarget by remember { mutableStateOf<String?>(null) }
    var showNoNetwork by remember { mutableStateOf(false) }
    val shareState by ShareServerRepository.state.collectAsState()
    val networkInfo = remember(context) { NetworkInfoProvider(context) }
    var networkNameRevealed by remember { mutableStateOf(false) }
    // The running dialog is driven by service state, so dismissing it has to be remembered against
    // the session it belongs to — otherwise Close would immediately re-show it.
    var dismissedShareUrl by remember { mutableStateOf<String?>(null) }
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { networkNameRevealed = true }

    BackHandler(enabled = selection.isNotEmpty() || currentPath != null) {
        if (selection.isNotEmpty()) viewModel.clearSelection() else viewModel.navigateUp()
    }

    if (!hasAccess) {
        FileSystemPermissionGate(onGranted = { viewModel.refreshAccess() })
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
                    onProperties = {
                        currentPath?.let { path ->
                            propertiesTarget = File(path).toEntry()
                        }
                    },
                )
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
                                        else -> openWith(context, viewModel, entry.path) {
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

            AnimatedVisibility(visible = clipboard != null && currentPath != null) {
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
                shareFiles(context, viewModel, listOf(target))
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

    (shareState as? ShareState.Running)
        ?.takeIf { dismissedShareUrl != it.endpoint.url }
        ?.let { running ->
        // Recomputed when the reveal completes so a freshly granted permission fills the name in.
        val label = remember(running, networkNameRevealed) {
            running.network.name ?: networkInfo.ssid() ?: networkInfo.label(running.network)
        }
        val canReveal = running.network.kind == ShareNetwork.Kind.WIFI &&
            running.network.name == null &&
            !networkInfo.hasLocationPermission()
        ShareRunningDialog(
            running = running,
            networkLabel = label,
            onRevealNetworkName = if (canReveal) {
                { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            } else {
                null
            },
            onStop = {
                ShareServerManager.stop(context)
                shareTarget = null
            },
            onDismiss = {
                dismissedShareUrl = running.endpoint.url
                shareTarget = null
            },
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
// Permission gate
// ---------------------------------------------------------------------------

/**
 * Shown the first time the tab is opened. Nothing is requested until the button is tapped —
 * entering the tab alone must never pop a system dialog.
 */
@Composable
private fun FileSystemPermissionGate(onGranted: () -> Unit) {
    val context = LocalContext.current
    val legacyLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onGranted() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.files_permission_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.files_permission_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Some OEM ROMs reject the per-app action; fall back to the global list.
                    val opened = runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                "package:${context.packageName}".toUri(),
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    if (!opened) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                } else {
                    legacyLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }) {
                Text(stringResource(R.string.files_permission_grant))
            }
        }
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

/** Tappable path segments; the leading chip steps back out to the volume chooser. */
@Composable
private fun Breadcrumbs(
    path: String,
    roots: List<Pair<String, String>>,
    onSegmentClick: (String) -> Unit,
) {
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
        Text(
            text = root?.second ?: stringResource(R.string.files_root),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onSegmentClick(basePath) },
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
            Text(
                text = segment,
                style = MaterialTheme.typography.labelLarge,
                color = if (target == path) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.clickable { onSegmentClick(target) },
            )
        }
        Spacer(Modifier.width(8.dp))
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

// ---------------------------------------------------------------------------
// Intents
// ---------------------------------------------------------------------------

private fun openWith(
    context: android.content.Context,
    viewModel: FileSystemViewModel,
    path: String,
    onFailure: () -> Unit,
) {
    val uri = viewModel.contentUri(path) ?: return onFailure()
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, viewModel.mimeType(path))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure { onFailure() }
}

private fun shareFiles(
    context: android.content.Context,
    viewModel: FileSystemViewModel,
    paths: List<String>,
) {
    val uri = paths.firstOrNull()?.let { viewModel.contentUri(it) } ?: return
    val send = Intent(Intent.ACTION_SEND)
        .setType(viewModel.mimeType(paths.first()))
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.files_share))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun File.toEntry() = FileEntry(
    name = name,
    path = absolutePath,
    isDirectory = isDirectory,
    sizeBytes = if (isDirectory) 0L else length(),
    lastModified = lastModified(),
    isHidden = isHidden,
)
