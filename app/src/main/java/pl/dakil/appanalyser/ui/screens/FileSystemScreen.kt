package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.DualPaneStyle
import pl.dakil.appanalyser.domain.FileCompactView
import pl.dakil.appanalyser.domain.FileDropAction
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileFavorite
import pl.dakil.appanalyser.domain.FileOperationProgress
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.domain.ScanFreshness
import pl.dakil.appanalyser.domain.ScrollPosition
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.canPostNotifications
import pl.dakil.appanalyser.ui.components.filesystem.AllFilesAccessGate
import pl.dakil.appanalyser.ui.components.filesystem.Breadcrumbs
import pl.dakil.appanalyser.ui.components.filesystem.DeleteConfirmDialog
import pl.dakil.appanalyser.ui.components.filesystem.DropTarget
import pl.dakil.appanalyser.ui.components.filesystem.DualPaneLayout
import pl.dakil.appanalyser.ui.components.filesystem.EDITABLE_EXTENSIONS
import pl.dakil.appanalyser.ui.components.filesystem.FavoriteActions
import pl.dakil.appanalyser.ui.components.filesystem.FavoriteColorSheet
import pl.dakil.appanalyser.ui.components.filesystem.FavoritePalette
import pl.dakil.appanalyser.ui.components.filesystem.FileDragGhost
import pl.dakil.appanalyser.ui.components.filesystem.FileDragHandler
import pl.dakil.appanalyser.ui.components.filesystem.FileDragPayload
import pl.dakil.appanalyser.ui.components.filesystem.FileDragState
import pl.dakil.appanalyser.ui.components.filesystem.FileRow
import pl.dakil.appanalyser.ui.components.filesystem.FilesHome
import pl.dakil.appanalyser.ui.components.filesystem.NameInputDialog
import pl.dakil.appanalyser.ui.components.filesystem.NoNetworkDialog
import pl.dakil.appanalyser.ui.components.filesystem.OperationProgressCard
import pl.dakil.appanalyser.ui.components.filesystem.PaneActions
import pl.dakil.appanalyser.ui.components.filesystem.PaneToolbarExtras
import pl.dakil.appanalyser.ui.components.filesystem.PropertiesDialog
import pl.dakil.appanalyser.ui.components.filesystem.ScanFolderDialog
import pl.dakil.appanalyser.ui.components.filesystem.ShareMethodSheet
import pl.dakil.appanalyser.ui.components.filesystem.SwipePaneLayout
import pl.dakil.appanalyser.ui.components.filesystem.TransferConfirmDialog
import pl.dakil.appanalyser.ui.components.filesystem.TransferDialog
import pl.dakil.appanalyser.ui.components.filesystem.fileDragSource
import pl.dakil.appanalyser.ui.components.filesystem.openFile
import pl.dakil.appanalyser.ui.components.filesystem.openHotspotSettings
import pl.dakil.appanalyser.ui.components.filesystem.rememberFavoritePalette
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
import pl.dakil.appanalyser.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.FilePane
import pl.dakil.appanalyser.viewmodel.FileSystemViewModel

/** Room under a list for the FAB or a floating toolbar over its last rows. */
private val FLOATING_BAR_CLEARANCE = 112.dp

/** How close to a pane's top or bottom edge carried items start scrolling it. */
private val AUTO_SCROLL_EDGE = 56.dp

/** A drop waiting for the user to pick Move or Copy, with where the finger lifted. */
private data class PendingDrop(val payload: FileDragPayload, val target: DropTarget, val at: Offset)

/** A copy or move waiting on the optional confirmation; [source] is null for a paste. */
private data class PendingTransfer(
    val paths: List<String>,
    val destination: String,
    val operation: ClipboardOperation,
    val source: PaneId?,
)

/** The single transfer button's dialog: the selection, captured when it opens, and where it starts. */
private data class TransferRequest(val paths: List<String>, val destination: String)

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
    val roots by viewModel.roots.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val operation by viewModel.operation.collectAsState()
    val busy = operation != null
    val confirmDeletion by viewModel.confirmDeletion.collectAsState()
    val dual by viewModel.dualPane.collectAsState()
    val paneSplit by viewModel.paneSplit.collectAsState()
    val dropAction by viewModel.dropAction.collectAsState()
    val confirmTransfer by viewModel.confirmTransfer.collectAsState()
    val transferDialog by viewModel.transferDialog.collectAsState()
    val compactSetting by viewModel.compactView.collectAsState()
    val dualStyle by viewModel.dualPaneStyle.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val missingFavorites by viewModel.missingFavorites.collectAsState()
    val favoriteColors = remember(favorites) { favorites.associate { it.path to it.colorIndex } }

    // Every folder-level action — the bars, the FAB, paste, back — follows the active pane. In
    // single pane mode that is always the primary one.
    val activeId by viewModel.activePaneId.collectAsState()
    val active = viewModel.pane(activeId)
    val currentPath by active.currentPath.collectAsState()
    val selection by active.selection.collectAsState()
    val otherPath by viewModel.pane(activeId.other).currentPath.collectAsState()

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
            viewModel.setActivePane(PaneId.PRIMARY)
            viewModel.primary.open(initialPath)
            handledInitialPath = true
        }
    }

    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }
    val openFailedMessage = stringResource(R.string.files_open_failed)
    val haptics = rememberHaptics()

    var fabMenuExpanded by remember { mutableStateOf(false) }
    var createFolder by remember { mutableStateOf(false) }
    var createFile by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var propertiesTarget by remember { mutableStateOf<FileEntry?>(null) }
    var scanPrompt by remember { mutableStateOf<Pair<String, ScanFreshness>?>(null) }
    var pendingDrop by remember { mutableStateOf<PendingDrop?>(null) }
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var transferRequest by remember { mutableStateOf<TransferRequest?>(null) }
    var colorTarget by remember { mutableStateOf<FileFavorite?>(null) }

    // Inside a folder the analyser opens on that folder. A current scan shows straight away;
    // otherwise the user is asked first, and only that folder is scanned — never the whole volume.
    fun openDiskUsage() {
        val path = currentPath ?: return onOpenDiskUsage(null, false)
        when (val freshness = viewModel.diskScanFreshness(path)) {
            ScanFreshness.Fresh -> onOpenDiskUsage(path, false)
            else -> scanPrompt = path to freshness
        }
    }

    fun openEntry(entry: FileEntry) {
        if (entry.extension in EDITABLE_EXTENSIONS) {
            onOpenEditor(entry.path)
        } else {
            openFile(
                context = context,
                uri = viewModel.contentUri(entry.path),
                mimeType = viewModel.mimeType(entry.path),
            ) {
                scope.launch { snackbarHostState.showSnackbar(openFailedMessage) }
            }
        }
    }

    fun runTransfer(transfer: PendingTransfer) {
        if (transfer.source == null) {
            viewModel.paste(transfer.operation)
        } else {
            viewModel.transfer(transfer.paths, transfer.destination, transfer.operation, transfer.source)
        }
    }

    // Every copy and move — drops, the to-other-pane actions and paste — comes through here, so the
    // optional confirmation covers all of them alike.
    fun requestTransfer(transfer: PendingTransfer) {
        if (confirmTransfer) {
            pendingTransfer = transfer
        } else {
            haptics.confirm()
            runTransfer(transfer)
        }
    }

    // Volumes read better by their label than by the last segment of their mount path ("0").
    fun folderLabel(path: String) = roots.firstOrNull { it.path == path }?.label ?: File(path).name

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
        if (selection.isNotEmpty()) active.clearSelection() else active.navigateUp()
    }

    if (!hasAccess) {
        AllFilesAccessGate(onGranted = { viewModel.refreshAccess() })
        return
    }

    val inFolder = currentPath != null
    val selecting = selection.isNotEmpty()

    // Side by side in landscape and on anything tablet-wide, stacked on a portrait phone.
    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val sideBySide = windowSize.width > windowSize.height ||
        with(density) { windowSize.width.toDp() } >= 600.dp
    // Swipe style only applies where the panes would otherwise be stacked; wide layouts have room.
    val paged = dual && dualStyle == DualPaneStyle.SWIPE && !sideBySide
    // Stacked split screen gives the top bar's height to the panes: everything it offered is on the
    // divider's toolbar, and each pane carries its own location.
    val immersive = dual && !sideBySide && !paged
    // "In split view" means both panes on screen at once; a swiped pane gets the whole screen.
    val compact = when (compactSetting) {
        FileCompactView.ALWAYS -> true
        FileCompactView.DUAL_PANE -> dual && !paged
        FileCompactView.NEVER -> false
    }

    // The drag itself is tracked by the state; what a drag *means* is decided here, where the
    // selection, the haptics and the drop setting live.
    val dragState = remember { FileDragState() }
    SideEffect {
        dragState.handler = object : FileDragHandler {
            override fun onLongPress(pane: PaneId, entry: FileEntry): Boolean {
                haptics.longPress()
                viewModel.setActivePane(pane)
                val browser = viewModel.pane(pane)
                val wasSelected = entry.path in browser.selection.value
                if (!wasSelected) browser.select(entry.path)
                return wasSelected
            }

            override fun payloadFor(pane: PaneId, entry: FileEntry): FileDragPayload {
                val selected = viewModel.pane(pane).selection.value
                // The picked-up row leads, so the ghost shows what the finger is actually on.
                val paths = listOf(entry.path) + (selected - entry.path)
                return FileDragPayload(source = pane, paths = paths, lead = entry)
            }

            override fun onReleasedInPlace(pane: PaneId, entry: FileEntry, wasSelected: Boolean) {
                if (wasSelected) viewModel.pane(pane).toggleSelection(entry.path)
            }

            override fun onHoverChanged(target: DropTarget?) {
                if (target != null) haptics.tick()
            }

            override fun onDrop(payload: FileDragPayload, target: DropTarget?, pointer: Offset) {
                // One operation at a time; the progress card is already showing the running one.
                if (target == null || viewModel.isBusy) {
                    haptics.reject()
                    return
                }
                val operation = when (dropAction) {
                    FileDropAction.MOVE -> ClipboardOperation.MOVE
                    FileDropAction.COPY -> ClipboardOperation.COPY
                    FileDropAction.ASK -> {
                        pendingDrop = PendingDrop(payload, target, pointer - dragState.overlayOrigin)
                        return
                    }
                }
                requestTransfer(PendingTransfer(payload.paths, target.destination, operation, payload.source))
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // The status bar strip stays, tinted like whichever bar is under it; the bar itself
            // folds away in stacked split screen, its height going to the panes.
            val stripColor by animateColorAsState(
                if (selecting && !immersive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "statusStrip",
            )
            Column(Modifier.background(stripColor)) {
                Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars))
                AnimatedVisibility(
                    visible = !immersive,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
            AnimatedContent(targetState = selecting, label = "filesTopBar") { isSelecting ->
                if (isSelecting) {
                    SelectionTopBar(
                        count = selection.size,
                        onClose = { active.clearSelection() },
                        onSelectAll = { active.selectAll() },
                    )
                } else {
                    BrowserTopBar(
                        // Each pane carries its own location in dual mode, so the bar stays put.
                        title = currentPath?.takeUnless { dual }?.let { File(it).name }
                            ?: stringResource(R.string.nav_files),
                        canNavigateUp = inFolder && !dual,
                        inFolder = inFolder,
                        dual = dual,
                        sideBySide = sideBySide,
                        otherPaneReady = otherPath != null,
                        viewModel = viewModel,
                        onNavigateUp = { active.navigateUp() },
                        onToggleDual = {
                            haptics.toggle(!dual)
                            viewModel.setDualPane(!dual)
                        },
                        onOpenDiskUsage = { openDiskUsage() },
                        onProperties = {
                            currentPath?.let { path ->
                                propertiesTarget = File(path).toEntry()
                            }
                        },
                        currentIsFavorite = currentPath?.let { path -> favorites.any { it.path == path } } == true,
                    )
                }
            }
                }
            }
        },
        // The create menu only exists inside a folder with nothing selected and nothing waiting to
        // be pasted, so it never competes with the floating toolbars for the bottom edge.
        floatingActionButton = {
            AnimatedVisibility(
                visible = inFolder && !selecting && clipboard == null && !busy,
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
                .padding(padding)
                .onGloballyPositioned { dragState.overlayOrigin = it.positionInRoot() },
            contentAlignment = Alignment.TopCenter,
        ) {
            val favoritePalette = rememberFavoritePalette()

            @Composable
            fun Pane(pane: FilePane, bottomPadding: Dp, outerPadding: PaddingValues) {
                FileBrowserPane(
                    pane = pane,
                    roots = roots,
                    favoriteColors = favoriteColors,
                    favoritePalette = favoritePalette,
                    home = { horizontalPadding ->
                        FilesHome(
                            roots = roots,
                            favorites = favorites,
                            missing = missingFavorites,
                            palette = favoritePalette,
                            horizontalPadding = horizontalPadding,
                            bottomPadding = bottomPadding,
                            onOpenRoot = { pane.open(it) },
                            actions = FavoriteActions(
                                onOpen = { favorite ->
                                    if (favorite.isDirectory) pane.open(favorite.path) else openEntry(File(favorite.path).toEntry())
                                },
                                onShowInFolder = { favorite -> File(favorite.path).parent?.let { pane.open(it) } },
                                onColor = { colorTarget = it },
                                onRemove = { viewModel.removeFavorite(it.path) },
                            ),
                            onShown = { viewModel.checkFavorites() },
                        )
                    },
                    dual = dual,
                    compact = compact,
                    selectionInHeader = immersive,
                    active = activeId == pane.id,
                    dragState = dragState,
                    bottomPadding = bottomPadding,
                    outerPadding = outerPadding,
                    onActivate = { viewModel.setActivePane(pane.id) },
                    onOpenEntry = { openEntry(it) },
                )
            }

            val primaryPath by viewModel.primary.currentPath.collectAsState()
            val secondaryPath by viewModel.secondary.currentPath.collectAsState()
            val paneActions = PaneActions(
                onSwap = { viewModel.swapPanes() },
                onMirror = { viewModel.mirror(it) },
                canMirror = { (if (it == PaneId.PRIMARY) primaryPath else secondaryPath) != null },
                onClose = { viewModel.setDualPane(false) },
                onCollapse = {
                    haptics.toggle(false)
                    viewModel.collapseTo(it)
                },
                // With the top bar folded away, its disk usage button and menu move onto the toolbar.
                extras = if (immersive) {
                    PaneToolbarExtras(
                        onOpenDiskUsage = { openDiskUsage() },
                        menu = { expanded, onDismiss, onEvenSplit ->
                            BrowserMenu(
                                expanded = expanded,
                                onDismiss = onDismiss,
                                viewModel = viewModel,
                                inFolder = inFolder,
                                currentIsFavorite = currentPath?.let { path -> favorites.any { it.path == path } } == true,
                                onProperties = { currentPath?.let { propertiesTarget = File(it).toEntry() } },
                                extraGroup = {
                                    AppMenuItem(
                                        text = stringResource(R.string.files_even_split),
                                        symbol = AppSymbols.Splitscreen,
                                        onClick = {
                                            haptics.confirm()
                                            onEvenSplit()
                                            onDismiss()
                                        },
                                    )
                                },
                            )
                        },
                    )
                } else {
                    null
                },
            )
            if (paged) {
                SwipePaneLayout(
                    activePane = activeId,
                    onActivePaneChange = { viewModel.setActivePane(it) },
                    dragState = dragState,
                    modifier = Modifier.fillMaxSize(),
                    primary = {
                        Pane(
                            pane = viewModel.primary,
                            bottomPadding = FLOATING_BAR_CLEARANCE,
                            outerPadding = PaddingValues(start = Spacing.s, end = Spacing.s, bottom = Spacing.s),
                        )
                    },
                    secondary = {
                        Pane(
                            pane = viewModel.secondary,
                            bottomPadding = FLOATING_BAR_CLEARANCE,
                            outerPadding = PaddingValues(start = Spacing.s, end = Spacing.s, bottom = Spacing.s),
                        )
                    },
                )
            } else DualPaneLayout(
                dual = dual,
                horizontal = sideBySide,
                split = paneSplit,
                onSplitChange = { viewModel.setPaneSplit(it) },
                actions = paneActions,
                modifier = Modifier
                    // Two panes use the whole width; a single list keeps its readable maximum.
                    .then(if (dual) Modifier else Modifier.widthIn(max = Spacing.maxContentWidth))
                    .fillMaxSize(),
                primary = {
                    Pane(
                        pane = viewModel.primary,
                        // Stacked, only the bottom pane runs under the floating bars.
                        bottomPadding = if (dual && !sideBySide) Spacing.s else FLOATING_BAR_CLEARANCE,
                        outerPadding = when {
                            !dual -> PaddingValues()
                            sideBySide -> PaddingValues(start = Spacing.s, bottom = Spacing.s)
                            else -> PaddingValues(horizontal = Spacing.s)
                        },
                    )
                },
                secondary = {
                    Pane(
                        pane = viewModel.secondary,
                        bottomPadding = FLOATING_BAR_CLEARANCE,
                        outerPadding = if (sideBySide) {
                            PaddingValues(end = Spacing.s, bottom = Spacing.s)
                        } else {
                            PaddingValues(start = Spacing.s, end = Spacing.s, bottom = Spacing.s)
                        },
                    )
                },
            )

            // Selection actions float over the list's bottom edge (five of them didn't fit the
            // top bar); the paste bar takes the same spot once something is on the clipboard.
            AnimatedVisibility(
                visible = selecting && !busy,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.l),
            ) {
                SelectionToolbar(
                    single = selection.size == 1,
                    dual = dual,
                    combined = transferDialog,
                    onTransfer = {
                        // The other pane in dual mode (unless it is on its home page), else this folder.
                        val destination = (if (dual) otherPath else null) ?: currentPath
                        if (destination != null) transferRequest = TransferRequest(selection.toList(), destination)
                    },
                    otherPaneReady = otherPath != null,
                    onCopy = { viewModel.copySelection() },
                    onCut = { viewModel.cutSelection() },
                    onCopyToOther = {
                        otherPath?.let {
                            requestTransfer(PendingTransfer(selection.toList(), it, ClipboardOperation.COPY, activeId))
                        }
                    },
                    onMoveToOther = {
                        otherPath?.let {
                            requestTransfer(PendingTransfer(selection.toList(), it, ClipboardOperation.MOVE, activeId))
                        }
                    },
                    onRename = { renameTarget = selection.first() },
                    onShare = { shareTarget = selection.firstOrNull() },
                    allFavorites = selection.all { path -> favorites.any { it.path == path } },
                    onToggleFavorite = {
                        haptics.confirm()
                        viewModel.toggleSelectionFavorite()
                    },
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
                visible = clipboard != null && inFolder && !selecting && !busy,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.l, start = Spacing.gutter, end = Spacing.gutter),
            ) {
                val clip = clipboard
                // The toolbar balances its padding off the first touch target's offset, so it must
                // lead with a button — a leading label pushed that offset out and the bar grew tall.
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                ) {
                    IconButton(onClick = { viewModel.clearClipboard() }) {
                        AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.files_cancel))
                    }
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
                            .padding(start = Spacing.xs, end = Spacing.m)
                            .align(Alignment.CenterVertically)
                            .weight(1f, fill = false),
                    )
                    FilledTonalButton(
                        onClick = {
                            val path = currentPath
                            if (clip != null && path != null) {
                                requestTransfer(PendingTransfer(clip.paths, path, clip.operation, source = null))
                            }
                        },
                    ) {
                        AppIcon(AppSymbols.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.files_paste), modifier = Modifier.padding(start = Spacing.s))
                    }
                }
            }

            // A long operation takes the toolbars' spot. It only appears once the operation has run
            // for a moment, so a quick rename-move or a small copy never flashes it.
            var showProgress by remember { mutableStateOf(false) }
            LaunchedEffect(busy) {
                if (busy) delay(PROGRESS_CARD_DELAY_MS)
                showProgress = busy
            }
            // Kept through the exit animation, after the operation itself has finished.
            val shownOperation = remember { LastOperation() }
            operation?.let { shownOperation.value = it }
            AnimatedVisibility(
                visible = showProgress && busy,
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.l, start = Spacing.gutter, end = Spacing.gutter),
            ) {
                shownOperation.value?.let { progress ->
                    OperationProgressCard(
                        progress = progress,
                        onCancel = {
                            haptics.reject()
                            viewModel.cancelOperation()
                        },
                    )
                }
            }

            // Above both panes and the bars, so carried items are never drawn underneath anything.
            FileDragGhost(
                state = dragState,
                folderLabel = { folderLabel(it) },
                modifier = Modifier.align(Alignment.TopStart),
            )

            pendingDrop?.let { drop ->
                // A zero-size anchor where the finger lifted, so the menu opens right there.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(drop.at.x.roundToInt(), drop.at.y.roundToInt()) }
                ) {
                    AppMenu(expanded = true, onDismissRequest = { pendingDrop = null }) {
                        AppMenuGroup {
                            AppMenuLabel(stringResource(R.string.files_drop_into, folderLabel(drop.target.destination)))
                            AppMenuItem(
                                text = stringResource(R.string.files_drop_move_here),
                                symbol = AppSymbols.DriveFileMove,
                                onClick = {
                                    pendingDrop = null
                                    requestTransfer(
                                        PendingTransfer(
                                            drop.payload.paths, drop.target.destination,
                                            ClipboardOperation.MOVE, drop.payload.source,
                                        )
                                    )
                                },
                            )
                            AppMenuItem(
                                text = stringResource(R.string.files_drop_copy_here),
                                symbol = AppSymbols.FileCopy,
                                onClick = {
                                    pendingDrop = null
                                    requestTransfer(
                                        PendingTransfer(
                                            drop.payload.paths, drop.target.destination,
                                            ClipboardOperation.COPY, drop.payload.source,
                                        )
                                    )
                                },
                            )
                        }
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

    colorTarget?.let { target ->
        // Read live, so the sheet shows the pick straight away.
        val favorite = favorites.firstOrNull { it.path == target.path } ?: target
        FavoriteColorSheet(
            favorite = favorite,
            palette = rememberFavoritePalette(),
            onPick = { viewModel.setFavoriteColor(favorite.path, it) },
            onDismiss = { colorTarget = null },
        )
    }

    transferRequest?.let { request ->
        TransferDialog(
            paths = request.paths,
            initialDestination = request.destination,
            roots = roots,
            folderLabel = { folderLabel(it) },
            canShare = request.paths.size == 1,
            listFolders = { viewModel.listFolders(it) },
            // The dialog is the confirmation, so this skips requestTransfer's optional one.
            onTransfer = { target, operation ->
                transferRequest = null
                viewModel.transfer(request.paths, target, operation, activeId)
            },
            onShare = {
                transferRequest = null
                shareTarget = request.paths.single()
            },
            onDismiss = { transferRequest = null },
        )
    }

    pendingTransfer?.let { transfer ->
        TransferConfirmDialog(
            paths = transfer.paths,
            destination = transfer.destination,
            operation = transfer.operation,
            folderLabel = { folderLabel(it) },
            onConfirm = { operation ->
                pendingTransfer = null
                runTransfer(transfer.copy(operation = operation))
            },
            onDismiss = { pendingTransfer = null },
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
                active.clearSelection()
            },
            onShareOverWifi = {
                active.clearSelection()
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
// Panes
// ---------------------------------------------------------------------------

/**
 * One browser: its location header, then the volume list, a folder's rows, or an empty/loading
 * state. In single pane mode it is drawn exactly as the plain browser always was; in dual mode it
 * sits in its own rounded frame, outlined while it is the active pane or a drop target, and gets an
 * up button of its own. Either way its rows can be dragged onto a folder (or the other pane).
 */
@Composable
private fun FileBrowserPane(
    pane: FilePane,
    roots: List<StorageRoot>,
    favoriteColors: Map<String, Int>,
    favoritePalette: FavoritePalette,
    /** The Files home, shown while the pane sits above every volume. */
    home: @Composable (horizontalPadding: Dp) -> Unit,
    dual: Boolean,
    compact: Boolean,
    /** No top bar to carry the selection: the pane's own header turns into it. */
    selectionInHeader: Boolean,
    active: Boolean,
    dragState: FileDragState,
    bottomPadding: Dp,
    outerPadding: PaddingValues,
    onActivate: () -> Unit,
    onOpenEntry: (FileEntry) -> Unit,
) {
    val currentPath by pane.currentPath.collectAsState()
    val entries by pane.entries.collectAsState()
    val selection by pane.selection.collectAsState()
    val isLoading by pane.isLoading.collectAsState()
    val selecting = selection.isNotEmpty()

    // One list state per folder, opened where the folder was left if the user is coming back up to
    // it (and at the top when going down), then reported back continuously. The pane keeps the
    // positions, so a swap or a recomposition of the pane loses nothing.
    val listState = remember(pane, currentPath) {
        val saved = currentPath?.let { pane.scrollPositionFor(it) } ?: ScrollPosition.Top
        LazyListState(saved.index, saved.offset)
    }
    LaunchedEffect(listState) {
        val path = currentPath ?: return@LaunchedEffect
        snapshotFlow { ScrollPosition(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
            .collect { pane.recordScroll(path, it) }
    }

    // Published for hit-testing: the other pane at large drops into this folder.
    SideEffect { dragState.setPaneFolder(pane.id, currentPath) }
    DisposableEffect(dragState, pane.id) { onDispose { dragState.removePane(pane.id) } }

    val hover = dragState.hover
    val paneTargeted = hover != null && hover.pane == pane.id && !hover.folderRow
    val scheme = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    // The active pane is only a quiet hint, a tone off the background; the vivid primary is kept
    // for a pane that is about to receive a drop.
    val outline by animateColorAsState(
        when {
            !dual -> Color.Transparent
            paneTargeted -> scheme.primary
            active -> scheme.outlineVariant
            else -> Color.Transparent
        },
        effects,
        label = "paneOutline",
    )
    val container by animateColorAsState(
        when {
            !dual -> scheme.surface
            paneTargeted -> scheme.primaryContainer
            else -> scheme.surfaceContainerLow
        },
        effects,
        label = "paneContainer",
    )
    val outlineWidth by animateDpAsState(
        if (paneTargeted) 3.dp else 2.dp,
        MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "paneOutlineWidth",
    )

    // Carried items scroll whichever pane they hover near its edges, their own included — the
    // dragged row pins itself in the list, so scrolling it out of view doesn't end the drag.
    val edge = with(LocalDensity.current) { AUTO_SCROLL_EDGE.toPx() }
    val carrying = dragState.payload != null
    LaunchedEffect(carrying, listState) {
        if (!carrying) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val bounds = dragState.paneBounds(pane.id) ?: continue
            val pointer = dragState.pointer
            if (pointer.x !in bounds.left..bounds.right) continue
            val speed = when {
                pointer.y in bounds.top..(bounds.top + edge) -> -(1f - (pointer.y - bounds.top) / edge)
                pointer.y in (bounds.bottom - edge)..bounds.bottom -> 1f - (bounds.bottom - pointer.y) / edge
                else -> 0f
            }
            if (speed != 0f) listState.scrollBy(speed * MAX_AUTO_SCROLL_PER_FRAME)
        }
    }

    val shape = MaterialTheme.shapes.extraLarge
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(outerPadding)
            .onGloballyPositioned { dragState.placePane(pane.id, it.boundsInRoot()) }
            .then(
                if (dual) {
                    Modifier
                        .clip(shape)
                        .background(container)
                        .border(outlineWidth, outline, shape)
                        // Any touch makes this the active pane; nothing is consumed, so the row,
                        // breadcrumb or button underneath still gets the tap.
                        .pointerInput(pane.id) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                onActivate()
                            }
                        }
                } else {
                    Modifier
                }
            ),
    ) {
        PaneHeader(
            path = currentPath,
            roots = roots,
            dual = dual,
            selectedCount = if (selectionInHeader) selection.size else 0,
            onClearSelection = { pane.clearSelection() },
            onSelectAll = { pane.selectAll() },
            onHome = { pane.goHome() },
            onNavigateUp = { pane.navigateUp() },
            onOpen = { pane.open(it) },
        )

        // Compact lists give the margins to the rows as well.
        val listGutter = when {
            compact -> Spacing.xs
            dual -> Spacing.s
            else -> Spacing.gutter
        }
        Box(Modifier.weight(1f)) {
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator(Modifier.size(64.dp))
                }
                currentPath == null -> home(if (dual) Spacing.s else Spacing.gutter)
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
                    state = listState,
                    contentPadding = PaddingValues(
                        start = listGutter,
                        end = listGutter,
                        top = Spacing.xs,
                        bottom = bottomPadding,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.path }) { index, entry ->
                        val carried = dragState.payload?.let { it.source == pane.id && entry.path in it.paths } == true
                        val dropHere = hover != null && hover.folderRow &&
                            hover.pane == pane.id && hover.destination == entry.path
                        FileRow(
                            entry = entry,
                            selected = entry.path in selection,
                            selectionMode = selecting,
                            index = index,
                            count = entries.size,
                            modifier = Modifier
                                .animateItem()
                                .alpha(if (carried) CARRIED_ALPHA else 1f)
                                .fileDragSource(dragState, pane.id, entry),
                            longPressExternal = true,
                            dropHighlighted = dropHere,
                            onClick = {
                                when {
                                    selecting -> pane.toggleSelection(entry.path)
                                    entry.isDirectory -> pane.open(entry.path)
                                    else -> onOpenEntry(entry)
                                }
                            },
                            onLongClick = { pane.toggleSelection(entry.path) },
                            compact = compact,
                            favorite = favoriteColors[entry.path]?.let { favoritePalette.tint(it) },
                            onIconClick = {
                                onActivate()
                                pane.toggleSelection(entry.path)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** How long an operation runs before its progress card appears. */
private const val PROGRESS_CARD_DELAY_MS = 400L

private class LastOperation(var value: FileOperationProgress? = null)

/** Pixels per frame at the very edge of a pane while carried items auto-scroll it. */
private const val MAX_AUTO_SCROLL_PER_FRAME = 24f

/** Rows being carried stay in place, faded, so the user can see what they picked up. */
private const val CARRIED_ALPHA = 0.4f

/**
 * The pane's location: a Home button back to the volumes and favourites, then the breadcrumbs. In
 * dual mode the top bar's back arrow only belongs to the active pane, so each pane also leads with
 * an up button of its own, the Home button right beside it.
 */
@Composable
private fun PaneHeader(
    path: String?,
    roots: List<StorageRoot>,
    dual: Boolean,
    /** Above zero, the header shows this pane's selection instead of its location. */
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onHome: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpen: (String) -> Unit,
) {
    if (path == null) return
    // Same height either way, so swapping never moves the rows (or the one under the finger).
    val effectsIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val spatialIn = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val effectsOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    AnimatedContent(
        targetState = selectedCount > 0,
        transitionSpec = {
            (fadeIn(effectsIn) + scaleIn(spatialIn, initialScale = 0.92f)) togetherWith fadeOut(effectsOut)
        },
        label = "paneHeader",
    ) { selecting ->
        if (selecting) {
            PaneSelectionHeader(selectedCount, onClearSelection, onSelectAll)
        } else {
            PaneLocationHeader(path, roots, dual, onHome, onNavigateUp, onOpen)
        }
    }
}

/**
 * A pane's selection where its location usually is, for stacked split screen, which has no top
 * bar: the way out, the count and select all, on a pill tinted like the selection top bar.
 */
@Composable
private fun PaneSelectionHeader(count: Int, onClear: () -> Unit, onSelectAll: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
        val content = MaterialTheme.colorScheme.onSecondaryContainer
        IconButton(onClick = onClear, shapes = IconButtonDefaults.shapes()) {
            AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.files_cancel), tint = content)
        }
        AnimatedContent(targetState = count, label = "paneSelectionCount", modifier = Modifier.weight(1f)) {
            Text(
                text = pluralStringResource(R.plurals.files_selected_x, it, it),
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) {
            AppIcon(AppSymbols.SelectAll, contentDescription = stringResource(R.string.files_select_all), tint = content)
        }
    }
}

@Composable
private fun PaneLocationHeader(
    path: String,
    roots: List<StorageRoot>,
    dual: Boolean,
    onHome: () -> Unit,
    onNavigateUp: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Spacing.xs)) {
        if (dual) {
            IconButton(onClick = onNavigateUp, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.ArrowUpward, contentDescription = stringResource(R.string.files_parent_folder))
            }
        }
        IconButton(onClick = onHome, shapes = IconButtonDefaults.shapes()) {
            AppIcon(AppSymbols.Home, contentDescription = stringResource(R.string.files_home))
        }
        Box(Modifier.weight(1f)) {
            Breadcrumbs(
                path = path,
                roots = roots.map { it.path to it.label },
                onSegmentClick = onOpen,
            )
        }
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
    dual: Boolean,
    sideBySide: Boolean,
    otherPaneReady: Boolean,
    viewModel: FileSystemViewModel,
    onNavigateUp: () -> Unit,
    onToggleDual: () -> Unit,
    onOpenDiskUsage: () -> Unit,
    onProperties: () -> Unit,
    currentIsFavorite: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
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
            // The one-tap switch between single and dual pane; its glyph shows the split it makes.
            IconToggleButton(
                checked = dual,
                onCheckedChange = { onToggleDual() },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                AnimatedAppIcon(
                    if (sideBySide) AppSymbols.VerticalSplit else AppSymbols.Splitscreen,
                    contentDescription = stringResource(
                        if (dual) R.string.files_single_view else R.string.files_split_view
                    ),
                    filled = dual,
                )
            }
            IconButton(onClick = onOpenDiskUsage, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.DonutLarge, contentDescription = stringResource(R.string.disk_usage_title))
            }
            // In dual mode the menu also carries the pane actions, so it stays even on the volume
            // chooser; the folder-only entries are what drop out there.
            if (inFolder || dual) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.Sort, contentDescription = stringResource(R.string.files_more_options))
                    }
                    BrowserMenu(
                        expanded = menuExpanded,
                        onDismiss = { menuExpanded = false },
                        viewModel = viewModel,
                        inFolder = inFolder,
                        currentIsFavorite = currentIsFavorite,
                        onProperties = onProperties,
                        extraGroup = if (dual) {
                            {
                                AppMenuItem(
                                    text = stringResource(R.string.files_swap_panes),
                                    symbol = if (sideBySide) AppSymbols.SwapHoriz else AppSymbols.SwapVert,
                                    onClick = {
                                        haptics.confirm()
                                        viewModel.swapPanes()
                                        menuExpanded = false
                                    },
                                )
                                AppMenuItem(
                                    text = stringResource(R.string.files_sync_other_pane),
                                    symbol = AppSymbols.SyncAlt,
                                    enabled = inFolder,
                                    onClick = {
                                        viewModel.syncOtherPane()
                                        menuExpanded = false
                                    },
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        },
        colors = appTopBarColors(),
        windowInsets = TopBarHorizontalInsets,
    )
}

/**
 * The browser's menu: sort order, hidden files, then the current folder's actions. Shared by the
 * top bar and, while split screen hides that bar, the divider toolbar; each adds its own last
 * group ([extraGroup]).
 */
@Composable
private fun BrowserMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: FileSystemViewModel,
    inFolder: Boolean,
    currentIsFavorite: Boolean,
    onProperties: () -> Unit,
    extraGroup: (@Composable ColumnScope.() -> Unit)?,
) {
    val sortOrder by viewModel.sortOrder.collectAsState()
    val descending by viewModel.sortDescending.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val sortFolders by viewModel.sortFolders.collectAsState()
    val haptics = rememberHaptics()
    val groups = if (extraGroup != null) 4 else 3
    AppMenu(expanded = expanded, onDismissRequest = onDismiss) {
        AppMenuGroup(index = 0, count = groups) {
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
        AppMenuGroup(index = 1, count = groups) {
            AppMenuCheckItem(
                text = stringResource(R.string.files_sort_descending),
                checked = descending,
                onCheckedChange = { viewModel.setSortDescending(it) },
                symbol = AppSymbols.ArrowDownward,
            )
            // Off, folders stay A→Z whatever the files are sorted by.
            AppMenuCheckItem(
                text = stringResource(R.string.files_sort_folders),
                checked = sortFolders,
                onCheckedChange = { viewModel.setSortFolders(it) },
                symbol = AppSymbols.Folder,
            )
            AppMenuCheckItem(
                text = stringResource(R.string.files_show_hidden),
                checked = showHidden,
                onCheckedChange = { viewModel.setShowHidden(it) },
                symbol = AppSymbols.Visibility,
            )
        }
        AppMenuGroup(index = 2, count = groups) {
            AppMenuItem(
                text = stringResource(R.string.files_select_all),
                symbol = AppSymbols.SelectAll,
                enabled = inFolder,
                onClick = {
                    viewModel.activePane.selectAll()
                    onDismiss()
                },
            )
            AppMenuItem(
                text = stringResource(R.string.files_properties),
                symbol = AppSymbols.Info,
                enabled = inFolder,
                onClick = {
                    onProperties()
                    onDismiss()
                },
            )
            if (inFolder) {
                AppMenuCheckItem(
                    text = stringResource(R.string.files_favorite),
                    checked = currentIsFavorite,
                    onCheckedChange = {
                        viewModel.toggleCurrentFolderFavorite()
                        onDismiss()
                    },
                    symbol = AppSymbols.Star,
                )
            }
        }
        if (extraGroup != null) AppMenuGroup(index = 3, count = groups, content = extraGroup)
    }
}

/**
 * The bars draw the status bar strip themselves (see the Scaffold's top bar slot), so they only
 * keep the horizontal insets — a landscape display cutout.
 */
private val TopBarHorizontalInsets: WindowInsets
    @Composable get() = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)

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
        windowInsets = TopBarHorizontalInsets,
    )
}

/**
 * The actions for the current selection, on a vibrant floating toolbar. In dual mode it leads with
 * Total Commander's copy/move to the other pane, and the clipboard's copy and cut move into an
 * overflow menu to keep the bar within a phone's width. With [combined] (the optional single
 * transfer button), one button opening the transfer dialog stands in for copy, move and share,
 * and the clipboard's copy and cut sit in the overflow menu in either mode.
 */
@Composable
private fun SelectionToolbar(
    single: Boolean,
    dual: Boolean,
    combined: Boolean,
    onTransfer: () -> Unit,
    otherPaneReady: Boolean,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onCopyToOther: () -> Unit,
    onMoveToOther: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    allFavorites: Boolean,
    onToggleFavorite: () -> Unit,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        when {
            combined -> ToolbarAction(AppSymbols.DriveFileMove, stringResource(R.string.files_transfer), onTransfer)
            dual -> {
                ToolbarAction(AppSymbols.FileCopy, stringResource(R.string.files_copy_to_other), onCopyToOther, otherPaneReady)
                ToolbarAction(AppSymbols.DriveFileMove, stringResource(R.string.files_move_to_other), onMoveToOther, otherPaneReady)
            }
            else -> {
                ToolbarAction(AppSymbols.ContentCopy, stringResource(R.string.files_copy), onCopy)
                ToolbarAction(AppSymbols.ContentCut, stringResource(R.string.files_cut), onCut)
            }
        }
        AnimatedVisibility(visible = single) {
            Row {
                ToolbarAction(AppSymbols.DriveFileRenameOutline, stringResource(R.string.files_rename), onRename)
                // Share lives in the transfer dialog when the single button is on.
                if (!combined) ToolbarAction(AppSymbols.Share, stringResource(R.string.files_share), onShare)
            }
        }
        ToolbarAction(AppSymbols.Delete, stringResource(R.string.files_delete), onDelete)
        Box {
            ToolbarAction(AppSymbols.MoreVert, stringResource(R.string.files_more_options), { overflowExpanded = true })
            AppMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                val clipboardInMenu = dual || combined
                val groups = if (clipboardInMenu) 2 else 1
                AppMenuGroup(index = 0, count = groups) {
                    AppMenuItem(
                        text = stringResource(
                            if (allFavorites) R.string.files_remove_favorites else R.string.files_add_favorites
                        ),
                        symbol = AppSymbols.Star,
                        onClick = {
                            overflowExpanded = false
                            onToggleFavorite()
                        },
                    )
                }
                if (clipboardInMenu) {
                    AppMenuGroup(index = 1, count = groups) {
                        AppMenuItem(
                            text = stringResource(R.string.files_copy),
                            symbol = AppSymbols.ContentCopy,
                            onClick = {
                                overflowExpanded = false
                                onCopy()
                            },
                        )
                        AppMenuItem(
                            text = stringResource(R.string.files_cut),
                            symbol = AppSymbols.ContentCut,
                            onClick = {
                                overflowExpanded = false
                                onCut()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarAction(symbol: AppSymbol, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
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
