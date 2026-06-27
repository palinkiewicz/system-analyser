package pl.dakil.appanalyser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val simpleSensorView by viewModel.simpleSensorView.collectAsState()
    var showUnitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            PreferenceCategoryHeader(stringResource(R.string.settings_category_device_info))

            ListItem(
                modifier = Modifier.clickable { showUnitDialog = true },
                headlineContent = { Text(stringResource(R.string.settings_temperature_unit)) },
                supportingContent = { Text(stringResource(temperatureUnit.labelRes())) }
            )

            ListItem(
                modifier = Modifier.clickable {
                    viewModel.setSimpleSensorView(!simpleSensorView)
                },
                headlineContent = { Text(stringResource(R.string.settings_simple_sensor_view)) },
                supportingContent = {
                    Text(stringResource(R.string.settings_simple_sensor_view_description))
                },
                trailingContent = {
                    Switch(
                        checked = simpleSensorView,
                        onCheckedChange = { viewModel.setSimpleSensorView(it) }
                    )
                }
            )
        }
    }

    if (showUnitDialog) {
        TemperatureUnitDialog(
            selected = temperatureUnit,
            onSelect = {
                viewModel.setTemperatureUnit(it)
                showUnitDialog = false
            },
            onDismiss = { showUnitDialog = false }
        )
    }
}

@Composable
private fun PreferenceCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun TemperatureUnitDialog(
    selected: TemperatureUnit,
    onSelect: (TemperatureUnit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
        title = { Text(stringResource(R.string.settings_temperature_unit)) },
        text = {
            Column {
                TemperatureUnit.entries.forEach { unit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = unit == selected,
                                onClick = { onSelect(unit) }
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = unit == selected,
                            onClick = { onSelect(unit) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(unit.labelRes()))
                    }
                }
            }
        }
    )
}

private fun TemperatureUnit.labelRes(): Int = when (this) {
    TemperatureUnit.CELSIUS -> R.string.temperature_unit_celsius
    TemperatureUnit.FAHRENHEIT -> R.string.temperature_unit_fahrenheit
    TemperatureUnit.KELVIN -> R.string.temperature_unit_kelvin
}
