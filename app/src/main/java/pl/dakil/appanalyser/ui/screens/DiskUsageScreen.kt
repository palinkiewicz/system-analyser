package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.DiskChartType
import pl.dakil.appanalyser.domain.DiskListMode
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import pl.dakil.appanalyser.domain.DiskScanState
import pl.dakil.appanalyser.domain.DiskUsageItem
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.canPostNotifications
import pl.dakil.appanalyser.ui.components.diskusage.ChartPalette
import pl.dakil.appanalyser.ui.components.diskusage.DiskUsageRow
import pl.dakil.appanalyser.ui.components.diskusage.RingsChart
import pl.dakil.appanalyser.ui.components.diskusage.TreemapChart
import pl.dakil.appanalyser.ui.components.diskusage.rememberChartPalette
import pl.dakil.appanalyser.ui.components.filesystem.AllFilesAccessGate
import pl.dakil.appanalyser.ui.components.filesystem.Breadcrumbs
import pl.dakil.appanalyser.ui.components.filesystem.DeleteConfirmDialog
import pl.dakil.appanalyser.ui.components.filesystem.EDITABLE_EXTENSIONS
import pl.dakil.appanalyser.ui.components.filesystem.NoNetworkDialog
import pl.dakil.appanalyser.ui.components.filesystem.ShareMethodSheet
import pl.dakil.appanalyser.ui.components.filesystem.openFile
import pl.dakil.appanalyser.ui.components.filesystem.openHotspotSettings
import pl.dakil.appanalyser.ui.components.filesystem.shareFile
import pl.dakil.appanalyser.ui.designsystem.AppLinearProgress
import pl.dakil.appanalyser.ui.designsystem.BackButton
import pl.dakil.appanalyser.ui.designsystem.ConnectedToggleRow
import pl.dakil.appanalyser.ui.designsystem.EmptyState
import pl.dakil.appanalyser.ui.designsystem.RollingNumber
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.ToggleOption
import pl.dakil.appanalyser.ui.designsystem.UsageBar
import pl.dakil.appanalyser.ui.designsystem.appTopBarColors
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.DiskUsageRows
import pl.dakil.appanalyser.viewmodel.DiskUsageViewModel

/**
 * The disk usage analyser: a volume list, then a Baobab-style chart (rings or treemap) of the
 * scanned tree with the focus folder's contents — or its largest files — listed underneath.
 *
 * Opening the screen never scans by itself; see [DiskUsageViewModel].
 */
@Composable
fun DiskUsageScreen(
    onNavigateBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    /** Opens the browser at a folder. */
    onShowInBrowser: (String) -> Unit,
    viewModel: DiskUsageViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasAccess by viewModel.hasAccess.collectAsState()
    val state by viewModel.state.collectAsState()
    val scans by viewModel.scans.collectAsState()
    val viewRoot by viewModel.viewRoot.collectAsState()
    val scan by viewModel.scan.collectAsState()
    val rootNode by viewModel.rootNode.collectAsState()
    val focusNode by viewModel.focusNode.collectAsState()
    val roots by viewModel.roots.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val confirmDeletion by viewModel.confirmDeletion.collectAsState()
    val chartType by viewModel.chartType.collectAsState()

    // Like the browser: the All files grant happens in a settings page that reports nothing back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refreshAccess() }

    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(resources.getString(it)) }
    }
    val openFailedMessage = stringResource(R.string.files_open_failed)

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Sharing goes through the same method sheet as the browser and App details. The path is
    // captured when the sheet opens, because starting the server clears the selection.
    var shareTarget by remember { mutableStateOf<String?>(null) }
    var showNoNetwork by remember { mutableStateOf(false) }
    val shareState by ShareServerRepository.state.collectAsState()
    val networkInfo = remember(context) { NetworkInfoProvider(context) }
    // The running dialog lives in AppNavigation; once a session is up the target is spent.
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

    // Requested only now, the moment before starting the server, as everywhere else.
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

    // System back retraces the user's steps (like the browser); the top bar's arrow leaves at once.
    BackHandler {
        when {
            selection.isNotEmpty() -> viewModel.clearSelection()
            !viewModel.stepBack() -> onNavigateBack()
        }
    }

    val showingResult = viewRoot != null && rootNode != null && focusNode != null
    val volumeLabel = roots.firstOrNull { it.path == viewRoot }?.label
    val haptics = rememberHaptics()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
          AnimatedContent(targetState = selection.isNotEmpty(), label = "usageTopBar") { selecting ->
            if (selecting) {
                val selected = viewModel.selectedItems()
                val single = selected.singleOrNull()
                SelectionBar(
                    count = selection.size,
                    onClose = { viewModel.clearSelection() },
                    // Folders too: over Wi-Fi the server lists and serves them, as in the browser.
                    onShare = single?.let { item -> { shareTarget = item.path } },
                    onShowInBrowser = single?.let { item ->
                        { onShowInBrowser(if (item.isDirectory) item.path else item.path.substringBeforeLast('/')) }
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
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.disk_usage_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { BackButton(onNavigateBack) },
                    actions = {
                        AnimatedVisibility(
                            visible = showingResult,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Rings or treemap, as a two-icon connected toggle.
                                ConnectedToggleRow(
                                    options = listOf(
                                        ToggleOption(
                                            DiskChartType.RINGS,
                                            stringResource(R.string.disk_usage_show_rings),
                                            AppSymbols.DonutLarge,
                                            iconOnly = true,
                                        ),
                                        ToggleOption(
                                            DiskChartType.TREEMAP,
                                            stringResource(R.string.disk_usage_show_treemap),
                                            AppSymbols.Dashboard,
                                            iconOnly = true,
                                        ),
                                    ),
                                    selected = chartType,
                                    onSelect = { if (it != chartType) viewModel.toggleChartType() },
                                    fillWidth = false,
                                )
                                IconButton(onClick = { viewModel.rescan() }, shapes = IconButtonDefaults.shapes()) {
                                    AppIcon(AppSymbols.Refresh, contentDescription = stringResource(R.string.disk_usage_rescan))
                                }
                            }
                        }
                    },
                    colors = appTopBarColors(),
                )
            }
          }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val currentState = state
            val root = viewRoot
            val focusPath by viewModel.focusPath.collectAsState()
            // The location is always on screen once there is one — while scanning too.
            if (hasAccess && root != null) {
                Breadcrumbs(
                    path = focusPath ?: root,
                    roots = roots.map { it.path to it.label },
                    onSegmentClick = { viewModel.open(it) },
                    minPath = root,
                )
            }
            Box(Modifier.weight(1f)) { when {
                !hasAccess -> AllFilesAccessGate(onGranted = { viewModel.refreshAccess() })
                root == null -> VolumeList(
                    roots = roots,
                    state = currentState,
                    scans = scans,
                    onSelect = { viewModel.selectVolume(it) },
                )
                currentState is DiskScanState.Scanning && covers(currentState.rootPath, root) -> ScanProgress(
                    state = currentState,
                    label = roots.firstOrNull { it.path == currentState.rootPath }?.label
                        ?: currentState.rootPath.substringAfterLast('/'),
                    onCancel = { viewModel.cancelScan() },
                )
                showingResult -> {
                    val result = scan!!
                    AnalysisContent(
                        scan = result,
                        root = rootNode!!,
                        focus = focusNode!!,
                        rootLabel = volumeLabel ?: root.substringAfterLast('/'),
                        chartType = chartType,
                        viewModel = viewModel,
                        isBusy = isBusy,
                        onOpenItem = { item ->
                            when {
                                selection.isNotEmpty() -> viewModel.toggleSelection(item.path)
                                item.isDirectory -> viewModel.open(item.path)
                                item.name.substringAfterLast('.', "").lowercase() in EDITABLE_EXTENSIONS ->
                                    onOpenEditor(item.path)
                                else -> openFile(context, viewModel.contentUri(item.path), viewModel.mimeType(item.path)) {
                                    scope.launch { snackbarHostState.showSnackbar(openFailedMessage) }
                                }
                            }
                        },
                    )
                }
                currentState is DiskScanState.Failed && currentState.rootPath == root -> AnalyseState(
                    symbol = AppSymbols.Error,
                    title = stringResource(R.string.disk_usage_failed),
                    message = null,
                    onAnalyse = { viewModel.analyseAgain() },
                )
                else -> AnalyseState(
                    symbol = AppSymbols.DonutLarge,
                    title = stringResource(R.string.disk_usage_not_analysed_title),
                    message = stringResource(R.string.disk_usage_not_analysed_message),
                    onAnalyse = { viewModel.analyseAgain() },
                )
            } }
        }
    }

    // Only while nothing is hosted yet; once the server is up the running dialog wins.
    shareTarget?.let { target ->
        if (shareState !is ShareState.Running && !showNoNetwork) {
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
}

private fun covers(scanRoot: String, path: String) = path == scanRoot || path.startsWith("$scanRoot/")

// ---------------------------------------------------------------------------
// Start page
// ---------------------------------------------------------------------------

@Composable
private fun VolumeList(
    roots: List<StorageRoot>,
    state: DiskScanState,
    scans: List<DiskScan>,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        item {
            Text(
                text = stringResource(R.string.disk_usage_volumes_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.m),
            )
        }
        itemsIndexed(roots, key = { _, root -> root.path }) { index, root ->
            val scanning = state is DiskScanState.Scanning && state.rootPath == root.path
            val status = when {
                scanning -> stringResource(R.string.disk_usage_analysing)
                else -> scans.firstOrNull { it.root.path == root.path }?.let {
                    stringResource(R.string.disk_usage_analysed_ago, relativeTime(it.scannedAt))
                }
            }
            SegmentedListItem(
                onClick = { onSelect(root.path) },
                shapes = ListItemDefaults.segmentedShapes(index, roots.size),
                colors = segmentedItemColors(),
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        if (root.totalBytes > 0) {
                            UsageBar(
                                root.usedBytes.toFloat() / root.totalBytes,
                                modifier = Modifier.padding(top = Spacing.xs),
                            )
                            Text(
                                stringResource(
                                    R.string.disk_usage_used_of,
                                    Formatter.formatShortFileSize(context, root.usedBytes),
                                    Formatter.formatShortFileSize(context, root.totalBytes),
                                )
                            )
                        }
                        status?.let {
                            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                leadingContent = {
                    ShapedIcon(
                        if (root.isRemovable) AppSymbols.SdCard else AppSymbols.Smartphone,
                        polygon = if (root.isRemovable) MaterialShapes.Cookie6Sided else MaterialShapes.Cookie9Sided,
                        size = 48.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
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

// ---------------------------------------------------------------------------
// Scanning / empty states
// ---------------------------------------------------------------------------

@Composable
private fun ScanProgress(state: DiskScanState.Scanning, label: String, onCancel: () -> Unit) {
    val context = LocalContext.current
    CenteredColumn {
        ContainedLoadingIndicator(modifier = Modifier.size(112.dp))
        Text(
            text = stringResource(R.string.disk_usage_scanning_title, label),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        RollingNumber(
            text = "${filesLabel(state.files)} · ${Formatter.formatShortFileSize(context, state.bytes)}",
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = state.currentDir.removePrefix(state.rootPath).ifEmpty { "/" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) {
            AppIcon(AppSymbols.Close, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Text(stringResource(R.string.files_cancel), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
        }
    }
}

@Composable
private fun AnalyseState(
    symbol: AppSymbol,
    title: String,
    message: String?,
    onAnalyse: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(symbol = symbol, title = title, body = message, polygon = MaterialShapes.Sunny) {
            Button(
                onClick = onAnalyse,
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                AppIcon(AppSymbols.DonutLarge, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.disk_usage_analyse), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) { content() }
    }
}

// ---------------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------------

@Composable
private fun AnalysisContent(
    scan: DiskScan,
    root: DiskNode,
    focus: DiskNode,
    rootLabel: String,
    chartType: DiskChartType,
    viewModel: DiskUsageViewModel,
    isBusy: Boolean,
    onOpenItem: (DiskUsageItem) -> Unit,
) {
    val palette = rememberChartPalette()
    val rows by viewModel.rows.collectAsState()
    val listMode by viewModel.listMode.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val listState = rememberLazyListState()

    // A new focus starts at the top, where the chart is.
    LaunchedEffect(focus.path) {
        if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
    }

    val chart: @Composable (Modifier) -> Unit = { modifier ->
        Crossfade(targetState = chartType, modifier = modifier, label = "chart") { type ->
            when (type) {
                DiskChartType.RINGS -> RingsChart(
                    root = root,
                    focus = focus,
                    palette = palette,
                    onOpen = { viewModel.open(it.path) },
                    onUp = { viewModel.zoomOut() },
                    modifier = Modifier.fillMaxWidth(),
                ) { RingsCenter(focus, if (focus.path == root.path) rootLabel else focus.name) }
                DiskChartType.TREEMAP -> TreemapChart(
                    focus = focus,
                    palette = palette,
                    filesLabel = stringResource(R.string.disk_usage_files_here),
                    onOpen = { viewModel.open(it.path) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }
        }
    }

    val header: LazyListScope.() -> Unit = {
        item(key = "summary") { Summary(scan, focus, onRescan = { viewModel.rescan() }) }
        item(key = "modes") {
            ModeSelector(listMode) { viewModel.setListMode(it) }
        }
    }

    val list: LazyListScope.() -> Unit = {
        listRows(
            rows = rows,
            focus = focus,
            listMode = listMode,
            palette = palette,
            selection = selection,
            onClick = onOpenItem,
            onLongClick = { viewModel.toggleSelection(it.path) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = isBusy) { AppLinearProgress(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter)) }

        BoxWithConstraints(Modifier.weight(1f)) {
            val wide = maxWidth > maxHeight && maxWidth >= 600.dp
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { chart(Modifier.fillMaxWidth()) }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(0.55f),
                        contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.l),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        header()
                        list()
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    item(key = "chart") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.s, vertical = Spacing.s),
                            contentAlignment = Alignment.Center,
                        ) { chart(Modifier.widthIn(max = 420.dp)) }
                    }
                    header()
                    list()
                }
            }
        }
    }
}

@Composable
private fun RingsCenter(focus: DiskNode, label: String) {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RollingNumber(
            text = Formatter.formatShortFileSize(context, focus.sizeBytes),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Summary(scan: DiskScan, focus: DiskNode, onRescan: () -> Unit) {
    val context = LocalContext.current
    val parts = buildList {
        add(Formatter.formatShortFileSize(context, focus.sizeBytes))
        add(filesLabel(focus.fileCount.toLong()))
        if (scan.volumeTotalBytes > 0) {
            add(
                stringResource(
                    R.string.disk_usage_free_of,
                    Formatter.formatShortFileSize(context, scan.volumeFreeBytes),
                    Formatter.formatShortFileSize(context, scan.volumeTotalBytes),
                )
            )
        }
        add(stringResource(R.string.disk_usage_scanned_ago, relativeTime(scan.scannedAt)))
    }
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Text(
            text = parts.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (scan.stale) {
            Spacer(Modifier.height(Spacing.m))
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ShapedIcon(
                        AppSymbols.History,
                        polygon = MaterialShapes.Cookie6Sided,
                        size = 32.dp,
                        iconSize = 18.dp,
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                    Text(
                        text = stringResource(R.string.disk_usage_stale),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Spacing.m),
                    )
                    FilledTonalButton(onClick = onRescan, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.disk_usage_rescan))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: DiskListMode, onSelect: (DiskListMode) -> Unit) {
    ConnectedToggleRow(
        options = listOf(
            ToggleOption(DiskListMode.CONTENTS, stringResource(R.string.disk_usage_contents), AppSymbols.FolderOpen),
            ToggleOption(DiskListMode.LARGEST, stringResource(R.string.disk_usage_largest), AppSymbols.Sort),
        ),
        selected = mode,
        onSelect = onSelect,
        modifier = Modifier.padding(vertical = Spacing.s),
    )
}

private fun LazyListScope.listRows(
    rows: DiskUsageRows,
    focus: DiskNode,
    listMode: DiskListMode,
    palette: ChartPalette,
    selection: Set<String>,
    onClick: (DiskUsageItem) -> Unit,
    onLongClick: (DiskUsageItem) -> Unit,
) {
    if (rows.items.isEmpty()) {
        item(key = "empty") {
            Text(
                text = stringResource(
                    if (listMode == DiskListMode.LARGEST) R.string.disk_usage_largest_empty else R.string.disk_usage_empty
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
        }
        return
    }
    val total = focus.sizeBytes.coerceAtLeast(1)
    itemsIndexed(rows.items, key = { _, item -> item.path }) { index, item ->
        val color = when {
            listMode == DiskListMode.LARGEST -> MaterialTheme.colorScheme.primary
            item.isRestrictedSpace -> palette.restricted
            item.isDirectory -> palette.color(item.colorIndex)
            else -> palette.files
        }
        DiskUsageRow(
            item = item,
            share = item.sizeBytes.toFloat() / total,
            color = color,
            selected = item.path in selection,
            selectionMode = selection.isNotEmpty(),
            onClick = { onClick(item) },
            onLongClick = { onLongClick(item) },
            index = index,
            count = rows.items.size,
            modifier = Modifier.animateItem(),
            // In the largest-files list the folder matters more than a file count of one.
            detail = if (listMode == DiskListMode.LARGEST) {
                item.path.removePrefix("${focus.path}/").substringBeforeLast('/', "").ifEmpty { null }
            } else {
                null
            },
        )
    }
    if (rows.hiddenCount > 0) {
        item(key = "more") {
            val context = LocalContext.current
            Text(
                text = pluralStringResource(
                    R.plurals.disk_usage_more_items_x,
                    rows.hiddenCount,
                    rows.hiddenCount,
                    Formatter.formatShortFileSize(context, rows.hiddenBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Bars and helpers
// ---------------------------------------------------------------------------

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onShare: (() -> Unit)?,
    onShowInBrowser: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    TopAppBar(
        title = {
            AnimatedContent(targetState = count, label = "usageSelectionCount") {
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
            onShare?.let {
                IconButton(onClick = it, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.Share, contentDescription = stringResource(R.string.files_share))
                }
            }
            onShowInBrowser?.let {
                IconButton(onClick = it, shapes = IconButtonDefaults.shapes()) {
                    AppIcon(AppSymbols.FolderOpen, contentDescription = stringResource(R.string.disk_usage_show_in_browser))
                }
            }
            IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                AppIcon(AppSymbols.Delete, contentDescription = stringResource(R.string.files_delete))
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

@Composable
private fun filesLabel(count: Long): String {
    val clamped = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(
        R.plurals.disk_usage_files_x,
        clamped,
        NumberFormat.getIntegerInstance().format(count),
    )
}

/** "5 minutes ago", or "just now" where DateUtils would say "0 minutes ago". */
@Composable
private fun relativeTime(time: Long): String {
    val now = System.currentTimeMillis()
    if (now - time < DateUtils.MINUTE_IN_MILLIS) return stringResource(R.string.disk_usage_just_now)
    return DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
}
