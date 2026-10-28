package pl.dakil.appanalyser.ui.components.filesystem

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.FileEntry

/** Create-file, create-folder and rename all share this single-field dialog. */
@Composable
fun NameInputDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val invalid = name.isNotEmpty() &&
        (name.trim() != name || name.contains('/') || name == "." || name == "..")
    val canConfirm = name.isNotBlank() && !invalid

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                isError = invalid,
                supportingText = if (invalid) {
                    { Text(stringResource(R.string.files_name_invalid)) }
                } else null,
                label = { Text(stringResource(R.string.files_name)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_cancel)) }
        },
    )
}

@Composable
fun DeleteConfirmDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_delete_title)) },
        text = { Text(pluralStringResource(R.plurals.files_delete_message, count, count)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.files_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_cancel)) }
        },
    )
}

/**
 * Asked before the analyser scans [folderName], when no current scan covers it. Only that folder
 * is scanned, never the whole volume. With an outdated scan ([lastScannedAt]) the user may also
 * look at the old numbers instead.
 */
@Composable
fun ScanFolderDialog(
    folderName: String,
    /** The folder is a whole volume, so "only this folder" would be misleading. */
    isVolume: Boolean,
    lastScannedAt: Long?,
    onScan: () -> Unit,
    onShowLastScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.disk_usage_scan_prompt_title, folderName)) },
        text = {
            Text(
                if (lastScannedAt == null) {
                    stringResource(
                        if (isVolume) R.string.disk_usage_scan_prompt_volume_message
                        else R.string.disk_usage_scan_prompt_message
                    )
                } else {
                    stringResource(
                        if (isVolume) R.string.disk_usage_scan_prompt_volume_stale
                        else R.string.disk_usage_scan_prompt_stale,
                        DateUtils.getRelativeTimeSpanString(
                            lastScannedAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ),
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onScan) { Text(stringResource(R.string.disk_usage_analyse)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_cancel)) }
            if (lastScannedAt != null) {
                TextButton(onClick = onShowLastScan) {
                    Text(stringResource(R.string.disk_usage_show_last_scan))
                }
            }
        },
    )
}

/**
 * Details for a single entry. Folder size and child count are passed in already resolved, since
 * computing them walks the tree and must not happen on the main thread.
 */
@Composable
fun PropertiesDialog(
    entry: FileEntry,
    folderSizeBytes: Long?,
    childCount: Int?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                PropertyLine(stringResource(R.string.files_property_path), entry.path)
                PropertyLine(
                    stringResource(R.string.files_property_type),
                    stringResource(
                        if (entry.isDirectory) R.string.files_type_folder else R.string.files_type_file
                    ),
                )
                val size = if (entry.isDirectory) folderSizeBytes else entry.sizeBytes
                PropertyLine(
                    stringResource(R.string.files_property_size),
                    size?.let { Formatter.formatFileSize(context, it) }
                        ?: stringResource(R.string.files_calculating),
                )
                if (entry.isDirectory) {
                    PropertyLine(
                        stringResource(R.string.files_property_items),
                        childCount?.toString() ?: stringResource(R.string.files_calculating),
                    )
                }
                PropertyLine(
                    stringResource(R.string.files_property_modified),
                    DateUtils.formatDateTime(
                        context,
                        entry.lastModified,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                            DateUtils.FORMAT_SHOW_YEAR,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_close)) }
        },
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Asked before leaving the editor with unsaved changes. */
@Composable
fun UnsavedChangesDialog(onSave: () -> Unit, onDiscard: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_unsaved_title)) },
        text = { Text(stringResource(R.string.editor_unsaved_message)) },
        confirmButton = { TextButton(onClick = onSave) { Text(stringResource(R.string.editor_save)) } },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.editor_discard)) }
        },
    )
}

@Composable
fun GoToLineDialog(lineCount: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    val line = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_go_to_line)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { input -> value = input.filter { it.isDigit() }.take(9) },
                singleLine = true,
                label = { Text(stringResource(R.string.editor_line_of_x, lineCount)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { line?.let(onConfirm) },
                enabled = line != null && line >= 1,
            ) { Text(stringResource(R.string.editor_go)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_cancel)) }
        },
    )
}
