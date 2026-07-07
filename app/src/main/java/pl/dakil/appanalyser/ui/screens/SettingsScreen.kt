package pl.dakil.appanalyser.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.ui.screens.settings.NavigationRow
import pl.dakil.appanalyser.ui.screens.settings.SettingRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAppearance: () -> Unit,
    onNavigateToDeviceInfoSettings: () -> Unit,
    onNavigateToHomeSettings: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            NavigationRow(
                title = stringResource(R.string.settings_appearance),
                summary = stringResource(R.string.settings_appearance_summary),
                icon = Icons.Rounded.Brush,
                onClick = onNavigateToAppearance
            )
            NavigationRow(
                title = stringResource(R.string.settings_device_info),
                summary = stringResource(R.string.settings_device_info_summary),
                icon = Icons.Rounded.PhoneAndroid,
                onClick = onNavigateToDeviceInfoSettings
            )
            NavigationRow(
                title = stringResource(R.string.settings_home),
                summary = stringResource(R.string.settings_home_summary),
                icon = Icons.Rounded.GridView,
                onClick = onNavigateToHomeSettings
            )
            SettingRow(
                title = stringResource(R.string.settings_about),
                summary = stringResource(R.string.settings_about_summary),
                leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
                onClick = { showAboutDialog = true }
            )
        }
    }
}
