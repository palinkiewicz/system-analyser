package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.service.cardTitle
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val interval by viewModel.notificationIntervalSeconds.collectAsState()
    val pinnedCards by viewModel.pinnedCards.collectAsState()
    val context = LocalContext.current
    val intervalLabel = stringResource(R.string.settings_notifications_seconds_x, interval)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
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
            SliderRow(
                title = stringResource(R.string.settings_notifications_interval),
                summary = stringResource(R.string.settings_notifications_interval_description),
                value = interval,
                onValueChange = { viewModel.setNotificationIntervalSeconds(it) },
                valueRange = SettingsRepository.MIN_NOTIFICATION_INTERVAL..
                    SettingsRepository.MAX_NOTIFICATION_INTERVAL,
                // SliderRow only ever labels the current value, which intervalLabel already holds.
                valueLabel = { intervalLabel }
            )

            SectionHeader(stringResource(R.string.settings_notifications_pinned))
            if (pinnedCards.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_notifications_pinned_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                pinnedCards.forEach { card ->
                    SettingRow(
                        // Same title function the notification uses, so the two never diverge.
                        title = cardTitle(context, card),
                        trailing = {
                            IconButton(onClick = { viewModel.unpinCard(card.id) }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription =
                                        stringResource(R.string.settings_notifications_unpin)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
