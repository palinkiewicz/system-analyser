package pl.dakil.appanalyser.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.service.cardTitle
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.SettingsViewModel


@Composable
fun NotificationsSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val interval by viewModel.notificationIntervalSeconds.collectAsState()
    val pinnedCards by viewModel.pinnedCards.collectAsState()
    val context = LocalContext.current
    val intervalLabel = stringResource(R.string.settings_notifications_seconds_x, interval)
    val haptics = rememberHaptics()

    SettingsSubscreen(stringResource(R.string.settings_notifications), onNavigateBack) {
        SegmentedGroup {
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
        }

        SectionHeader(stringResource(R.string.settings_notifications_pinned))
        AnimatedContent(targetState = pinnedCards.isEmpty(), label = "pinnedEmpty") { empty ->
            if (empty) {
                Text(
                    text = stringResource(R.string.settings_notifications_pinned_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s)
                )
            } else {
                SegmentedGroup {
                    pinnedCards.forEach { card ->
                        SettingRow(
                            // Same title function the notification uses, so the two never diverge.
                            title = cardTitle(context, card),
                            leading = {
                                ShapedIcon(
                                    AppSymbols.NotificationsActive,
                                    polygon = MaterialShapes.Cookie6Sided,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            },
                            trailing = {
                                IconButton(onClick = {
                                    haptics.confirm()
                                    viewModel.unpinCard(card.id)
                                }) {
                                    AppIcon(
                                        AppSymbols.Close,
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
}
