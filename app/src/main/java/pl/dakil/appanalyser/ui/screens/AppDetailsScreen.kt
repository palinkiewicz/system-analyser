package pl.dakil.appanalyser.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.AppDetails
import pl.dakil.appanalyser.domain.DetectedFramework
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.components.DetailRow
import pl.dakil.appanalyser.ui.components.InfoCard
import pl.dakil.appanalyser.ui.components.canPostNotifications
import pl.dakil.appanalyser.ui.components.filesystem.NoNetworkDialog
import pl.dakil.appanalyser.ui.components.filesystem.ShareMethodSheet
import pl.dakil.appanalyser.ui.components.filesystem.openHotspotSettings
import pl.dakil.appanalyser.ui.designsystem.BackButton
import pl.dakil.appanalyser.ui.designsystem.PolygonShape
import pl.dakil.appanalyser.ui.designsystem.RollingNumber
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.UsageBar
import pl.dakil.appanalyser.ui.designsystem.appTopBarColors
import pl.dakil.appanalyser.ui.designsystem.entrance
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.pressScale
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** `com.example.app_1.2.apk`, with characters a file system may reject stripped out. */
private fun suggestedApkFileName(details: AppDetails): String {
    val base = "${details.appInfo.packageName}_${details.versionName}"
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "$base.apk"
}

/** Share sheet for a staged APK; the read grant is what lets the target app open the URI. */
private fun shareApkChooser(uri: Uri, title: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = APK_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

@Composable
fun AppDetailsScreen(
    viewModel: AppAnalyzerViewModel,
    packageName: String,
    onNavigateBack: () -> Unit
) {
    val details by viewModel.selectedAppDetails.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    val isExporting by viewModel.isExporting.collectAsState()

    var selectedFramework by remember { mutableStateOf<DetectedFramework?>(null) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val exportedMessage = stringResource(R.string.app_details_export_success)
    val exportFailedMessage = stringResource(R.string.app_details_export_failed)
    val shareFailedMessage = stringResource(R.string.app_details_share_failed)
    val shareChooserTitle = stringResource(R.string.app_details_share_apk)

    // Wi-Fi sharing is part of the experimental file-system feature; while that is off the button
    // goes straight to the system chooser, as it always did.
    val settings = remember(context) { SettingsRepository.get(context) }
    val fileSystemEnabled by settings.fileSystemEnabled.collectAsState()
    val networkInfo = remember(context) { NetworkInfoProvider(context) }
    var showShareMethod by remember { mutableStateOf(false) }
    var showNoNetwork by remember { mutableStateOf(false) }
    val notificationsBlockedMessage = stringResource(R.string.share_notification_permission_required)

    fun shareToApp(appDetails: AppDetails) {
        viewModel.shareApk(suggestedApkFileName(appDetails)) { uri ->
            if (uri == null) {
                scope.launch { snackbarHostState.showSnackbar(shareFailedMessage) }
            } else {
                context.startActivity(shareApkChooser(uri, shareChooserTitle))
            }
        }
    }

    // The network is checked before staging so a copy is never made for a share that cannot start.
    // The running dialog itself lives in AppNavigation and appears once the service is up.
    fun startWifiShare() {
        val appDetails = details ?: return
        if (networkInfo.resolve() == null) {
            showNoNetwork = true
            return
        }
        viewModel.stageApkForNetworkShare(suggestedApkFileName(appDetails)) { file ->
            if (file == null) {
                scope.launch { snackbarHostState.showSnackbar(shareFailedMessage) }
            } else {
                ShareServerManager.start(context, file.absolutePath)
            }
        }
    }

    // Requested only now, the moment before starting the server, as in the file browser.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Starting without it would leave a server running that the user can neither see nor stop.
        if (granted) {
            startWifiShare()
        } else {
            scope.launch { snackbarHostState.showSnackbar(notificationsBlockedMessage) }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(APK_MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.exportApk(uri) { success ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (success) exportedMessage else exportFailedMessage
                )
            }
        }
    }

    LaunchedEffect(packageName) {
        viewModel.analyzeApp(packageName)
    }

    val scrollState = rememberScrollState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val heroHeightPx = with(LocalDensity.current) { 180.dp.toPx() }
    // The hero header already names the app; the bar only takes the name once it scrolls away.
    val titleVisible by remember { derivedStateOf { scrollState.value > heroHeightPx } }
    val titleAlpha by animateFloatAsState(
        targetValue = if (titleVisible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "titleAlpha",
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        details?.appInfo?.name ?: stringResource(R.string.app_details_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer { alpha = titleAlpha },
                    )
                },
                navigationIcon = {
                    BackButton {
                        viewModel.clearSelectedApp()
                        onNavigateBack()
                    }
                },
                colors = appTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        AnimatedContent(
            targetState = if (isAnalyzing) null else details,
            contentKey = { it == null },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            label = "details",
        ) { appDetails ->
            if (appDetails == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(Modifier.size(72.dp))
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    AppDetailsContent(
                        details = appDetails,
                        isExporting = isExporting,
                        onShare = {
                            if (fileSystemEnabled) {
                                showShareMethod = true
                            } else {
                                shareToApp(appDetails)
                            }
                        },
                        onExport = { exportLauncher.launch(suggestedApkFileName(appDetails)) },
                        modifier = Modifier
                            .widthIn(max = Spacing.maxContentWidth)
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = Spacing.gutter),
                        onFrameworkClick = { framework ->
                            selectedFramework = framework
                            scope.launch { sheetState.show() }
                        }
                    )
                }
            }
        }

        // Bottom Sheet
        selectedFramework?.let { framework ->
            ModalBottomSheet(
                onDismissRequest = {
                    scope.launch { sheetState.hide() }
                    selectedFramework = null
                },
                sheetState = sheetState,
            ) {
                FrameworkDetailSheet(framework = framework)
            }
        }

        if (showShareMethod) {
            ShareMethodSheet(
                onSendToApp = {
                    showShareMethod = false
                    details?.let { shareToApp(it) }
                },
                onShareOverWifi = {
                    showShareMethod = false
                    if (context.canPostNotifications()) {
                        startWifiShare()
                    } else {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onDismiss = { showShareMethod = false },
            )
        }

        if (showNoNetwork) {
            NoNetworkDialog(
                onOpenHotspotSettings = {
                    showNoNetwork = false
                    openHotspotSettings(context)
                },
                onDismiss = { showNoNetwork = false },
            )
        }
    }
}

@Composable
fun AppDetailsContent(
    details: AppDetails,
    isExporting: Boolean,
    onShare: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
    onFrameworkClick: (DetectedFramework) -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { SimpleDateFormat("MMM dd, yyyy HH:mm", locale) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Hero(details)

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            FilledTonalButton(
                onClick = onShare,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                AppIcon(AppSymbols.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(
                    stringResource(R.string.app_details_share_apk),
                    maxLines = 1,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
            Button(
                onClick = onExport,
                enabled = !isExporting,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
                contentPadding = ButtonDefaults.MediumContentPadding,
            ) {
                AnimatedContent(targetState = isExporting, label = "exporting") { exporting ->
                    if (exporting) {
                        LoadingIndicator(Modifier.size(ButtonDefaults.IconSize + 4.dp))
                    } else {
                        AppIcon(AppSymbols.Download, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    }
                }
                Text(
                    stringResource(R.string.app_details_export_apk),
                    maxLines = 1,
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }

        Text(
            text = stringResource(R.string.app_details_frameworks_detected),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.l, top = Spacing.s)
        )

        details.detectedFrameworks.forEachIndexed { index, framework ->
            FrameworkCard(
                framework = framework,
                top = index == 0,
                onClick = { onFrameworkClick(framework) },
                modifier = Modifier.entrance(index),
            )
        }

        InfoCard(
            modifier = Modifier.padding(top = Spacing.s).entrance(details.detectedFrameworks.size),
            title = stringResource(R.string.app_details_app_information),
            symbol = AppSymbols.Info,
        ) {
            DetailRow(stringResource(R.string.app_details_size), Formatter.formatFileSize(context, details.sizeBytes))
            DetailRow(stringResource(R.string.app_details_version), "${details.versionName} (${details.versionCode})")
            DetailRow(stringResource(R.string.app_details_target_sdk), details.targetSdk.toString())
            DetailRow(stringResource(R.string.app_details_min_sdk), details.minSdk.toString())
            DetailRow(stringResource(R.string.app_details_first_install), dateFormat.format(Date(details.firstInstallTime)))
            DetailRow(stringResource(R.string.app_details_last_update), dateFormat.format(Date(details.lastUpdateTime)))
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** The app's icon on a cookie that settles into place, its name, package and quick facts. */
@Composable
private fun Hero(details: AppDetails) {
    val rotation = remember { Animatable(-60f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { rotation.animateTo(0f, spec) }
    val cookie = remember { PolygonShape(MaterialShapes.Cookie12Sided) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = cookie,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = rotation.value },
            ) {}
            Image(
                painter = rememberDrawablePainter(drawable = details.appInfo.icon),
                contentDescription = null,
                modifier = Modifier.size(72.dp)
            )
        }
        Text(
            text = details.appInfo.name,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s),
        )
        Text(
            text = details.appInfo.packageName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.s),
        ) {
            FactPill("v${details.versionName}")
            FactPill("API ${details.targetSdk}")
            if (details.appInfo.isSystemApp) FactPill(stringResource(R.string.app_list_system_app_label))
        }
    }
}

@Composable
private fun FactPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .padding(horizontal = Spacing.m, vertical = Spacing.xs),
        )
    }
}

/** A confidence tier: the colour the probability is drawn in and the word describing it. */
private data class Confidence(val color: Color, val label: Int)

@Composable
private fun confidenceOf(probability: Int): Confidence = when {
    probability >= 75 -> Confidence(MaterialTheme.colorScheme.primary, R.string.app_details_confidence_very_likely)
    probability >= 50 -> Confidence(MaterialTheme.colorScheme.tertiary, R.string.app_details_confidence_likely)
    probability >= 25 -> Confidence(MaterialTheme.colorScheme.secondary, R.string.app_details_confidence_possible)
    else -> Confidence(MaterialTheme.colorScheme.outline, R.string.app_details_confidence_unlikely)
}

/**
 * One detected framework: name, a counting-up probability on a wavy bar and the files that gave it
 * away. The [top] match is the hero, on the primary container.
 */
@Composable
fun FrameworkCard(
    framework: DetectedFramework,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    top: Boolean = false,
) {
    val confidence = confidenceOf(framework.probability)
    val interactionSource = remember { MutableInteractionSource() }
    val container = if (top) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (top) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    // Counts up from zero the first time the card appears.
    val shown = remember { Animatable(0f) }
    val countSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(framework.probability) { shown.animateTo(framework.probability.toFloat(), countSpec) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interactionSource),
        shape = if (top) MaterialTheme.shapes.extraLargeIncreased else MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        interactionSource = interactionSource,
    ) {
        Column(
            modifier = Modifier.padding(if (top) Spacing.xl else Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            if (top) {
                Text(
                    stringResource(R.string.app_details_top_match),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = content.copy(alpha = 0.8f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShapedIcon(
                    AppSymbols.DeployedCode,
                    polygon = if (top) MaterialShapes.Sunny else MaterialShapes.Cookie6Sided,
                    size = if (top) 48.dp else 36.dp,
                    containerColor = if (top) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (top) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    Text(
                        text = stringResource(framework.techStack.displayNameRes),
                        style = if (top) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleMediumEmphasized,
                    )
                    Text(
                        text = stringResource(confidence.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (top) content else confidence.color,
                    )
                }
                RollingNumber(
                    text = "${shown.value.toInt()}%",
                    style = if (top) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineSmallEmphasized,
                    color = if (top) content else confidence.color,
                )
            }
            UsageBar(
                fraction = framework.probability / 100f,
                color = if (top) MaterialTheme.colorScheme.primary else confidence.color,
                trackColor = if (top) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            if (framework.matchedFiles.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    modifier = Modifier.padding(top = Spacing.xs),
                ) {
                    val maxVisibleFiles = if (top) 5 else 3
                    framework.matchedFiles.take(maxVisibleFiles).forEach { file ->
                        Text(
                            text = file,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = content.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                    if (framework.matchedFiles.size > maxVisibleFiles) {
                        Text(
                            text = stringResource(
                                R.string.app_details_framework_more_files,
                                framework.matchedFiles.size - maxVisibleFiles
                            ),
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            color = if (top) content else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FrameworkDetailSheet(framework: DetectedFramework) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.l)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                AppSymbols.DeployedCode,
                polygon = MaterialShapes.Sunny,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(Modifier.padding(start = Spacing.l)) {
                Text(
                    text = stringResource(framework.techStack.displayNameRes),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(
                    text = "${framework.probability}% · " + stringResource(confidenceOf(framework.probability).label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        SheetSection(stringResource(R.string.app_details_description)) {
            Text(
                text = stringResource(framework.techStack.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (framework.matchedFiles.isNotEmpty()) {
            SheetSection(stringResource(R.string.app_details_framework_files_found)) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    framework.matchedFiles.forEach { file ->
                        Text(
                            text = file,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs),
        )
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(Modifier.padding(Spacing.l)) { content() }
        }
    }
}
