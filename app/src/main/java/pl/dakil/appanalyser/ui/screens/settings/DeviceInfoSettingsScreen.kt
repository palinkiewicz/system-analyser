package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@Composable
fun DeviceInfoSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val simpleSensorView by viewModel.simpleSensorView.collectAsState()
    val showSensorUnits by viewModel.showSensorUnits.collectAsState()

    SettingsSubscreen(stringResource(R.string.settings_device_info), onNavigateBack) {
        SegmentedGroup {
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
