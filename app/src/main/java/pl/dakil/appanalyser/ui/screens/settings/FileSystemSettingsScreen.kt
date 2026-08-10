package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

/**
 * Home of the experimental File system feature: the master switch that puts its tab in the
 * navigation bar, plus the browser and editor preferences it owns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSystemSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val enabled by viewModel.fileSystemEnabled.collectAsState()
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val confirmDeletion by viewModel.confirmFileDeletion.collectAsState()
    val monospace by viewModel.editorMonospace.collectAsState()
    val wordWrap by viewModel.editorWordWrap.collectAsState()
    val highlighting by viewModel.editorSyntaxHighlighting.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_file_system)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                colors = flatTopAppBarColors()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.settings_file_system_experimental_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SwitchRow(
                title = stringResource(R.string.settings_file_system_enabled),
                summary = stringResource(R.string.settings_file_system_enabled_description),
                checked = enabled,
                onCheckedChange = { viewModel.setFileSystemEnabled(it) }
            )

            SectionHeader(stringResource(R.string.settings_file_system_browsing))
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

            SectionHeader(stringResource(R.string.settings_file_system_editor))
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
    }
}
