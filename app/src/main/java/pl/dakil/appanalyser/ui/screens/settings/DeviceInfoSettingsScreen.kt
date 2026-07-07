package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val simpleSensorView by viewModel.simpleSensorView.collectAsState()
    val showSensorUnits by viewModel.showSensorUnits.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_device_info)) },
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
            SelectRow(
                title = stringResource(R.string.settings_temperature_unit),
                selectedLabel = temperatureUnit.label(),
                options = TemperatureUnit.entries.map { it to it.label() },
                onSelect = { viewModel.setTemperatureUnit(it) }
            )

            SwitchRow(
                title = stringResource(R.string.settings_simple_sensor_view),
                summary = stringResource(R.string.settings_simple_sensor_view_description),
                checked = simpleSensorView,
                onCheckedChange = { viewModel.setSimpleSensorView(it) }
            )

            SwitchRow(
                title = stringResource(R.string.settings_sensor_units),
                summary = stringResource(R.string.settings_sensor_units_description),
                checked = showSensorUnits,
                onCheckedChange = { viewModel.setShowSensorUnits(it) }
            )
        }
    }
}
