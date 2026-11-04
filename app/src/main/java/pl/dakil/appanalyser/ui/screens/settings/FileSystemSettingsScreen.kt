package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.FileDropAction
import pl.dakil.appanalyser.domain.ShareAccessMode
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
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

/** The File system feature's preferences: browser, editor, disk usage and Wi-Fi sharing. */
@Composable
fun FileSystemSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val confirmDeletion by viewModel.confirmFileDeletion.collectAsState()
    val dropAction by viewModel.fileDropAction.collectAsState()
    val confirmTransfer by viewModel.confirmFileTransfer.collectAsState()
    val monospace by viewModel.editorMonospace.collectAsState()
    val wordWrap by viewModel.editorWordWrap.collectAsState()
    val highlighting by viewModel.editorSyntaxHighlighting.collectAsState()
    val shareAccessMode by viewModel.shareAccessMode.collectAsState()
    val shareIdleTimeout by viewModel.shareIdleTimeoutMinutes.collectAsState()
    val scanMaxAge by viewModel.diskScanMaxAgeHours.collectAsState()

    SettingsSubscreen(stringResource(R.string.settings_file_system), onNavigateBack) {
        SectionHeader(stringResource(R.string.settings_file_system_browsing))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_show_hidden_files),
                summary = stringResource(R.string.settings_show_hidden_files_description),
                checked = showHidden,
                onCheckedChange = { viewModel.setShowHiddenFiles(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_confirm_deletion),
                summary = stringResource(R.string.settings_confirm_deletion_description),
                checked = confirmDeletion,
                onCheckedChange = { viewModel.setConfirmFileDeletion(it) }
            )
        }

        SectionHeader(stringResource(R.string.settings_file_system_transfers))
        SegmentedGroup {
            SelectRow(
                title = stringResource(R.string.settings_file_drop_action),
                summary = stringResource(R.string.settings_file_drop_action_description),
                selectedLabel = dropAction.label(),
                options = FileDropAction.entries.map { it to it.label() },
                onSelect = { viewModel.setFileDropAction(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_confirm_transfer),
                summary = stringResource(R.string.settings_confirm_transfer_description),
                checked = confirmTransfer,
                onCheckedChange = { viewModel.setConfirmFileTransfer(it) }
            )
        }

        SectionHeader(stringResource(R.string.settings_file_system_editor))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_editor_monospace),
                summary = stringResource(R.string.settings_editor_monospace_description),
                checked = monospace,
                onCheckedChange = { viewModel.setEditorMonospace(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_editor_word_wrap),
                summary = stringResource(R.string.settings_editor_word_wrap_description),
                checked = wordWrap,
                onCheckedChange = { viewModel.setEditorWordWrap(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_editor_highlighting),
                summary = stringResource(R.string.settings_editor_highlighting_description),
                checked = highlighting,
                onCheckedChange = { viewModel.setEditorSyntaxHighlighting(it) }
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
                valueLabel = { maxAgeLabels[it] }
            )
        }

        SectionHeader(stringResource(R.string.settings_share_section))
        SegmentedGroup {
            SelectRow(
                title = stringResource(R.string.settings_share_access),
                summary = stringResource(R.string.settings_share_access_description),
                selectedLabel = shareAccessMode.label(),
                options = ShareAccessMode.entries.map { it to it.label() },
                onSelect = { viewModel.setShareAccessMode(it) }
            )
            SelectRow(
                title = stringResource(R.string.settings_share_idle),
                summary = stringResource(R.string.settings_share_idle_description),
                selectedLabel = idleTimeoutLabel(shareIdleTimeout),
                options = SettingsRepository.SHARE_IDLE_TIMEOUT_OPTIONS.map {
                    it to idleTimeoutLabel(it)
                },
                onSelect = { viewModel.setShareIdleTimeoutMinutes(it) }
            )
        }
    }
}
