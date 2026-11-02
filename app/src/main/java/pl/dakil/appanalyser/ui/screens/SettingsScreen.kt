package pl.dakil.appanalyser.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.BuildConfig
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.screens.settings.NavigationRow
import pl.dakil.appanalyser.ui.screens.settings.SectionHeader
import pl.dakil.appanalyser.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDeviceInfoSettings: () -> Unit,
    onNavigateToHomeSettings: () -> Unit,
    onNavigateToNotificationsSettings: () -> Unit,
    onNavigateToFileSystemSettings: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = Spacing.gutter)
                .padding(bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Header()

            SectionHeader(stringResource(R.string.settings_section_personalize))
            SegmentedGroup {
                NavigationRow(
                    title = stringResource(R.string.settings_appearance),
                    summary = stringResource(R.string.settings_appearance_summary),
                    icon = AppSymbols.Palette,
                    onClick = onNavigateToAppearance
                )
                NavigationRow(
                    title = stringResource(R.string.settings_home),
                    summary = stringResource(R.string.settings_home_summary),
                    icon = AppSymbols.Widgets,
                    onClick = onNavigateToHomeSettings
                )
            }

            SectionHeader(stringResource(R.string.settings_section_features))
            SegmentedGroup {
                NavigationRow(
                    title = stringResource(R.string.settings_device_info),
                    summary = stringResource(R.string.settings_device_info_summary),
                    icon = AppSymbols.PhoneAndroid,
                    onClick = onNavigateToDeviceInfoSettings
                )
                NavigationRow(
                    title = stringResource(R.string.settings_notifications),
                    summary = stringResource(R.string.settings_notifications_summary),
                    icon = AppSymbols.Notifications,
                    onClick = onNavigateToNotificationsSettings
                )
                NavigationRow(
                    title = stringResource(R.string.settings_file_system),
                    summary = stringResource(R.string.settings_file_system_summary),
                    icon = AppSymbols.FolderOpen,
                    badge = stringResource(R.string.settings_experimental_badge),
                    onClick = onNavigateToFileSystemSettings
                )
            }

            SectionHeader(stringResource(R.string.settings_section_about))
            SegmentedGroup {
                NavigationRow(
                    title = stringResource(R.string.settings_about),
                    summary = stringResource(R.string.settings_about_summary),
                    icon = AppSymbols.Info,
                    onClick = { showAboutDialog = true }
                )
            }
        }
    }
}

/** The app's "business card": its glyph in a cookie that settles into place, name and tagline. */
@Composable
private fun Header() {
    // A small flourish: the cookie settles into place each time the tab is opened.
    val rotation = remember { Animatable(-45f) }
    val spring = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { rotation.animateTo(0f, spring) }
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xxl, bottom = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ShapedIcon(
            symbol = AppSymbols.Search,
            polygon = MaterialShapes.Cookie12Sided,
            size = 96.dp,
            iconSize = 48.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.graphicsLayer { rotationZ = rotation.value },
        )
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmallEmphasized,
            // No Surface/Scaffold provides a content color here, so set it explicitly.
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = Spacing.l).semantics { heading() },
        )
        Text(
            stringResource(R.string.settings_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.s, start = Spacing.l, end = Spacing.l),
        )
        Text(
            stringResource(R.string.version_x, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}
