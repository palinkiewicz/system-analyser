package pl.dakil.appanalyser.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import pl.dakil.appanalyser.domain.AppDetails
import pl.dakil.appanalyser.domain.DetectedFramework
import pl.dakil.appanalyser.ui.components.DetailRow
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel
import pl.dakil.appanalyser.ui.theme.*
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import java.text.SimpleDateFormat
import java.util.*

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

@OptIn(ExperimentalMaterial3Api::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val context = LocalContext.current
    val exportedMessage = stringResource(R.string.app_details_export_success)
    val exportFailedMessage = stringResource(R.string.app_details_export_failed)
    val shareFailedMessage = stringResource(R.string.app_details_share_failed)
    val shareChooserTitle = stringResource(R.string.app_details_share_apk)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_details_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelectedApp()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    details?.let { appDetails ->
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = {
                                viewModel.shareApk(suggestedApkFileName(appDetails)) { uri ->
                                    if (uri == null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(shareFailedMessage)
                                        }
                                    } else {
                                        context.startActivity(
                                            shareApkChooser(uri, shareChooserTitle)
                                        )
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = stringResource(
                                        R.string.app_details_share_apk
                                    )
                                )
                            }
                            IconButton(onClick = {
                                exportLauncher.launch(suggestedApkFileName(appDetails))
                            }) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(
                                        R.string.app_details_export_apk
                                    )
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (isAnalyzing || details == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            details?.let { appDetails ->
                AppDetailsContent(
                    details = appDetails,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    onFrameworkClick = { framework ->
                        selectedFramework = framework
                        scope.launch { sheetState.show() }
                    }
                )
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
    }
}

@Composable
fun AppDetailsContent(
    details: AppDetails,
    modifier: Modifier = Modifier,
    onFrameworkClick: (DetectedFramework) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val painter = rememberDrawablePainter(drawable = details.appInfo.icon)
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = details.appInfo.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = details.appInfo.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = stringResource(R.string.app_details_frameworks_detected),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        details.detectedFrameworks.forEach { framework ->
            FrameworkCard(framework = framework, onClick = { onFrameworkClick(framework) })
        }

        Text(
            text = stringResource(R.string.app_details_app_information),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(stringResource(R.string.app_details_size), Formatter.formatFileSize(context, details.sizeBytes))
                DetailRow(stringResource(R.string.app_details_version), "${details.versionName} (${details.versionCode})")
                DetailRow(stringResource(R.string.app_details_target_sdk), details.targetSdk.toString())
                DetailRow(stringResource(R.string.app_details_min_sdk), details.minSdk.toString())
                DetailRow(stringResource(R.string.app_details_first_install), dateFormat.format(Date(details.firstInstallTime)))
                DetailRow(stringResource(R.string.app_details_last_update), dateFormat.format(Date(details.lastUpdateTime)))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun FrameworkCard(framework: DetectedFramework, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val (badgeColor, textColor) = when {
        framework.probability >= 75 -> {
            if (isDark) ProbabilityGreenContainerDark to ProbabilityOnGreenContainerDark
            else ProbabilityGreenContainerLight to ProbabilityOnGreenContainerLight
        }
        framework.probability >= 50 -> {
            if (isDark) ProbabilityYellowContainerDark to ProbabilityOnYellowContainerDark
            else ProbabilityYellowContainerLight to ProbabilityOnYellowContainerLight
        }
        framework.probability >= 25 -> {
            if (isDark) ProbabilityOrangeContainerDark to ProbabilityOnOrangeContainerDark
            else ProbabilityOrangeContainerLight to ProbabilityOnOrangeContainerLight
        }
        else -> {
            if (isDark) ProbabilityRedContainerDark to ProbabilityOnRedContainerDark
            else ProbabilityRedContainerLight to ProbabilityOnRedContainerLight
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(framework.techStack.displayNameRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = badgeColor),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "${framework.probability}%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                }
            }

            if (framework.matchedFiles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val maxVisibleFiles = 5
                    framework.matchedFiles.take(maxVisibleFiles).forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = file,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (framework.matchedFiles.size > maxVisibleFiles) {
                        val remaining = framework.matchedFiles.size - maxVisibleFiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Spacer(modifier = Modifier.width(22.dp))
                            Text(
                                text = stringResource(
                                    R.string.app_details_framework_more_files,
                                    remaining
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
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
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(framework.techStack.displayNameRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = stringResource(R.string.app_details_description),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = stringResource(framework.techStack.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (framework.matchedFiles.isNotEmpty()) {
            Text(
                text = stringResource(R.string.app_details_framework_files_found),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    framework.matchedFiles.forEach { file ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 8.dp, top = 1.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = file,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
