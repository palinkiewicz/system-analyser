package pl.dakil.appanalyser.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import pl.dakil.appanalyser.domain.AppDetails
import pl.dakil.appanalyser.domain.DetectedFramework
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import pl.dakil.appanalyser.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailsScreen(
    viewModel: AppAnalyzerViewModel,
    packageName: String,
    onNavigateBack: () -> Unit
) {
    val details by viewModel.selectedAppDetails.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    LaunchedEffect(packageName) {
        viewModel.analyzeApp(packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.clearSelectedApp()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
fun AppDetailsContent(details: AppDetails, modifier: Modifier = Modifier) {
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
            text = "Frameworks detected",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        details.detectedFrameworks.forEach { framework ->
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            text = framework.techStack.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        // Probability Badge
                        val isDark = isSystemInDarkTheme()
                        val (badgeColor, textColor) = when {
                            framework.probability >= 75 -> { // Green
                                if (isDark) {
                                    ProbabilityGreenContainerDark to ProbabilityOnGreenContainerDark
                                } else {
                                    ProbabilityGreenContainerLight to ProbabilityOnGreenContainerLight
                                }
                            }
                            framework.probability >= 50 -> { // Yellow
                                if (isDark) {
                                    ProbabilityYellowContainerDark to ProbabilityOnYellowContainerDark
                                } else {
                                    ProbabilityYellowContainerLight to ProbabilityOnYellowContainerLight
                                }
                            }
                            framework.probability >= 25 -> { // Orange
                                if (isDark) {
                                    ProbabilityOrangeContainerDark to ProbabilityOnOrangeContainerDark
                                } else {
                                    ProbabilityOrangeContainerLight to ProbabilityOnOrangeContainerLight
                                }
                            }
                            else -> { // Red
                                if (isDark) {
                                    ProbabilityRedContainerDark to ProbabilityOnRedContainerDark
                                } else {
                                    ProbabilityRedContainerLight to ProbabilityOnRedContainerLight
                                }
                            }
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = badgeColor),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
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
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val maxVisibleFiles = 5
                            val filesToShow = framework.matchedFiles.take(maxVisibleFiles)
                            
                            filesToShow.forEach { file ->
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
                                        text = "+ $remaining more files",
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

        Text(
            text = "App information",
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
                DetailRow("Size", Formatter.formatFileSize(context, details.sizeBytes))
                DetailRow("Version", "${details.versionName} (${details.versionCode})")
                DetailRow("Target SDK", details.targetSdk.toString())
                DetailRow("Min SDK", details.minSdk.toString())
                DetailRow("First Install", dateFormat.format(Date(details.firstInstallTime)))
                DetailRow("Last Update", dateFormat.format(Date(details.lastUpdateTime)))
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}
