package pl.dakil.appanalyser.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import pl.dakil.appanalyser.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAppList: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit
) {
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            HomeListItem(
                title = stringResource(R.string.home_analyse_an_app),
                subtitle = stringResource(R.string.home_analyse_an_app_description),
                icon = Icons.Default.Search,
                onClick = onNavigateToAppList
            )
            HomeListItem(
                title = stringResource(R.string.home_device_info),
                subtitle = stringResource(R.string.home_device_info_description),
                icon = Icons.Default.PhoneAndroid,
                onClick = onNavigateToDeviceInfo
            )
            HomeListItem(
                title = stringResource(R.string.home_about_this_app),
                subtitle = stringResource(R.string.home_about_this_app_description),
                icon = Icons.Default.Info,
                onClick = { showAboutDialog = true }
            )
        }
    }
}

@Composable
fun HomeListItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}
