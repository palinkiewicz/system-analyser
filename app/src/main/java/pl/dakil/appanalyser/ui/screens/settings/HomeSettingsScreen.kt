package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@Composable
fun HomeSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val homeColumns by viewModel.homeColumns.collectAsState()

    SettingsSubscreen(stringResource(R.string.settings_home), onNavigateBack) {
        SegmentedGroup {
            SliderRow(
                title = stringResource(R.string.settings_home_columns),
                summary = stringResource(R.string.settings_home_columns_description),
                value = homeColumns,
                onValueChange = { viewModel.setHomeColumns(it) },
                valueRange = SettingsRepository.MIN_HOME_COLUMNS..SettingsRepository.MAX_HOME_COLUMNS
            )
        }
    }
}
