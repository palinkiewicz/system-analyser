package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.FileDropAction
import pl.dakil.appanalyser.domain.ShareAccessMode
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@Composable
private fun idleTimeoutLabel(minutes: Int): String = if (minutes <= 0) {
    stringResource(R.string.settings_share_idle_never)
} else {
    stringResource(R.string.settings_share_idle_minutes_x, minutes)
}

/** "Always current" for 0, otherwise hours below a day and whole days above. */
@Composable
private fun scanMaxAgeLabel(hours: Int): String = when {
    hours <= 0 -> stringResource(R.string.settings_disk_scan_max_age_forever)
    hours < 24 -> pluralStringResource(R.plurals.settings_disk_scan_max_age_hours_x, hours, hours)
    else -> pluralStringResource(R.plurals.settings_disk_scan_max_age_days_x, hours / 24, hours / 24)
}

/**
 * Home of the experimental File system feature: the master switch that puts its tab in the
 * navigation bar, plus the browser and editor preferences it owns.
 */
@Composable
fun FileSystemSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val enabled by viewModel.fileSystemEnabled.collectAsState()
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val confirmDeletion by viewModel.confirmFileDeletion.collectAsState()
    val dropAction by viewModel.fileDropAction.collectAsState()
    val monospace by viewModel.editorMonospace.collectAsState()
    val wordWrap by viewModel.editorWordWrap.collectAsState()
    val highlighting by viewModel.editorSyntaxHighlighting.collectAsState()
    val shareAccessMode by viewModel.shareAccessMode.collectAsState()
    val shareIdleTimeout by viewModel.shareIdleTimeoutMinutes.collectAsState()
    val scanMaxAge by viewModel.diskScanMaxAgeHours.collectAsState()

    SettingsSubscreen(stringResource(R.string.settings_file_system), onNavigateBack) {
        ExperimentalCallout()

        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_file_system_enabled),
                summary = stringResource(R.string.settings_file_system_enabled_description),
                checked = enabled,
                onCheckedChange = { viewModel.setFileSystemEnabled(it) }
            )
        }

        SectionHeader(stringResource(R.string.settings_file_system_browsing))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_show_hidden_files),
                summary = stringResource(R.string.settings_show_hidden_files_description),
                checked = showHidden,
                onCheckedChange = { viewModel.setShowHiddenFiles(it) },
                enabled = enabled
            )
            SwitchRow(
                title = stringResource(R.string.settings_confirm_deletion),
                summary = stringResource(R.string.settings_confirm_deletion_description),
                checked = confirmDeletion,
                onCheckedChange = { viewModel.setConfirmFileDeletion(it) },
                enabled = enabled
            )
        }

        SectionHeader(stringResource(R.string.settings_file_system_dual_pane))
        SegmentedGroup {
            SelectRow(
                title = stringResource(R.string.settings_file_drop_action),
                summary = stringResource(R.string.settings_file_drop_action_description),
                selectedLabel = dropAction.label(),
                options = FileDropAction.entries.map { it to it.label() },
                onSelect = { viewModel.setFileDropAction(it) },
                enabled = enabled
            )
        }

        SectionHeader(stringResource(R.string.settings_file_system_editor))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_editor_monospace),
                summary = stringResource(R.string.settings_editor_monospace_description),
                checked = monospace,
                onCheckedChange = { viewModel.setEditorMonospace(it) },
                enabled = enabled
            )
            SwitchRow(
                title = stringResource(R.string.settings_editor_word_wrap),
                summary = stringResource(R.string.settings_editor_word_wrap_description),
                checked = wordWrap,
                onCheckedChange = { viewModel.setEditorWordWrap(it) },
                enabled = enabled
            )
            SwitchRow(
                title = stringResource(R.string.settings_editor_highlighting),
                summary = stringResource(R.string.settings_editor_highlighting_description),
                checked = highlighting,
                onCheckedChange = { viewModel.setEditorSyntaxHighlighting(it) },
                enabled = enabled
            )
        }

        SectionHeader(stringResource(R.string.settings_disk_usage_section))
        // The slider moves over the option list's indices, so its steps aren't evenly spaced
        // in time: hours first, then days.
        val maxAgeOptions = SettingsRepository.DISK_SCAN_MAX_AGE_OPTIONS
        // Resolved here: SliderRow's label lambda isn't composable.
        val maxAgeLabels = maxAgeOptions.map { scanMaxAgeLabel(it) }
        SegmentedGroup {
            SliderRow(
                title = stringResource(R.string.settings_disk_scan_max_age),
                summary = stringResource(R.string.settings_disk_scan_max_age_description),
                value = maxAgeOptions.indexOf(scanMaxAge).coerceAtLeast(0),
                onValueChange = { viewModel.setDiskScanMaxAgeHours(maxAgeOptions[it]) },
                valueRange = maxAgeOptions.indices,
                valueLabel = { maxAgeLabels[it] },
                enabled = enabled
            )
        }

        SectionHeader(stringResource(R.string.settings_share_section))
        SegmentedGroup {
            SelectRow(
                title = stringResource(R.string.settings_share_access),
                summary = stringResource(R.string.settings_share_access_description),
                selectedLabel = shareAccessMode.label(),
                options = ShareAccessMode.entries.map { it to it.label() },
                onSelect = { viewModel.setShareAccessMode(it) },
                enabled = enabled
            )
            SelectRow(
                title = stringResource(R.string.settings_share_idle),
                summary = stringResource(R.string.settings_share_idle_description),
                selectedLabel = idleTimeoutLabel(shareIdleTimeout),
                options = SettingsRepository.SHARE_IDLE_TIMEOUT_OPTIONS.map {
                    it to idleTimeoutLabel(it)
                },
                onSelect = { viewModel.setShareIdleTimeoutMinutes(it) },
                enabled = enabled
            )
        }
    }
}

/** The "handle with care" note above the master switch. */
@Composable
private fun ExperimentalCallout() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.m),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ShapedIcon(
                AppSymbols.Science,
                polygon = MaterialShapes.Sunny,
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
            Text(
                text = stringResource(R.string.settings_file_system_experimental_warning),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
