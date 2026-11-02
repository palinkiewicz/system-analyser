package pl.dakil.appanalyser.ui.components.filesystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.service.ShareServerManager
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.tabular

/** Lets the user pick between the system chooser and hosting the item over the local network. */
@Composable
fun ShareMethodSheet(
    onSendToApp: () -> Unit,
    onShareOverWifi: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            ShareMethodTile(
                symbol = AppSymbols.Send,
                title = stringResource(R.string.share_method_app),
                summary = stringResource(R.string.share_method_app_summary),
                index = 0,
                onClick = onSendToApp,
            )
            ShareMethodTile(
                symbol = AppSymbols.Wifi,
                title = stringResource(R.string.share_method_wifi),
                summary = stringResource(R.string.share_method_wifi_summary),
                index = 1,
                onClick = onShareOverWifi,
            )
        }
    }
}

@Composable
private fun ShareMethodTile(symbol: AppSymbol, title: String, summary: String, index: Int, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, 2),
        colors = segmentedItemColors(),
        leadingContent = {
            ShapedIcon(
                symbol,
                polygon = if (index == 0) MaterialShapes.Cookie9Sided else MaterialShapes.Sunny,
                size = 48.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        supportingContent = { Text(summary) },
    ) { Text(title, style = MaterialTheme.typography.titleMedium) }
}

/** Shown when the device is on neither Wi-Fi nor a hotspot, since neither can be enabled for them. */
@Composable
fun NoNetworkDialog(onOpenHotspotSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(AppSymbols.WifiTethering) },
        title = { Text(stringResource(R.string.share_no_network_title)) },
        text = { Text(stringResource(R.string.share_no_network_message)) },
        confirmButton = {
            Button(onClick = onOpenHotspotSettings, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.share_open_hotspot_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.files_cancel)) }
        },
    )
}

/**
 * Shows [ShareRunningDialog] on its own when a session starts. Close only hides it until the session
 * ends or [requested] is raised by a tap on the share notification, which is the way back to it.
 */
@Composable
fun ShareSessionDialogHost(requested: Boolean, onShown: () -> Unit) {
    val context = LocalContext.current
    val shareState by ShareServerRepository.state.collectAsState()
    val networkInfo = remember(context) { NetworkInfoProvider(context) }
    var dismissed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(shareState) {
        if (shareState is ShareState.Stopped) dismissed = false
    }
    LaunchedEffect(requested) {
        if (requested) {
            dismissed = false
            onShown()
        }
    }

    val running = shareState as? ShareState.Running ?: return
    if (dismissed) return
    ShareRunningDialog(
        running = running,
        networkLabel = networkInfo.label(running.network),
        onStop = { ShareServerManager.stop(context) },
        onDismiss = { dismissed = true },
    )
}

/**
 * The live session: where to point a browser, what kind of connection it is on, and how to stop it.
 * The network is described by kind rather than by name, because reading an SSID would require a
 * location permission this app does not ask for.
 */
@Composable
fun ShareRunningDialog(
    running: ShareState.Running,
    networkLabel: String,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(AppSymbols.WifiTethering, polygon = MaterialShapes.Sunny) },
        title = {
            Text(
                stringResource(R.string.share_running_title, running.sharedName),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text = stringResource(R.string.share_running_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // The code itself stays black on white (QrCode), framed by a tonal plate.
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    QrCode(
                        text = running.endpoint.link,
                        modifier = Modifier
                            .padding(Spacing.m)
                            .size(200.dp),
                    )
                }
                // The link doubles as its copy button.
                Surface(
                    onClick = {
                        haptics.confirm()
                        copyToClipboard(context, running.endpoint.url)
                    },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(start = Spacing.l, end = Spacing.m, top = Spacing.s, bottom = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = running.endpoint.url,
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        AppIcon(
                            AppSymbols.ContentCopy,
                            contentDescription = stringResource(R.string.share_copy_link),
                            modifier = Modifier
                                .padding(start = Spacing.s)
                                .size(18.dp),
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    InfoPill(AppSymbols.Wifi, stringResource(R.string.share_running_network, networkLabel))
                    running.pin?.let { pin ->
                        InfoPill(AppSymbols.Lock, stringResource(R.string.share_running_pin, pin))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStop,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                AppIcon(AppSymbols.StopCircle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Text(stringResource(R.string.share_stop), modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.files_close)) }
        },
    )
}

@Composable
private fun InfoPill(symbol: AppSymbol, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppIcon(symbol, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** A dialog's icon slot, given the app's shaped look. */
@Composable
internal fun DialogIcon(
    symbol: AppSymbol,
    polygon: androidx.graphics.shapes.RoundedPolygon = MaterialShapes.Cookie9Sided,
    error: Boolean = false,
) {
    ShapedIcon(
        symbol,
        polygon = polygon,
        size = 56.dp,
        iconSize = 28.dp,
        containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("url", text))
}

/**
 * Opens the system hotspot page. The tethering screen has no documented action, so the widely
 * supported one is tried first and the wireless-settings screen is the guaranteed fallback.
 */
fun openHotspotSettings(context: Context) {
    val tethering = Intent().setClassName(
        "com.android.settings",
        "com.android.settings.TetherSettings",
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(tethering) }.isSuccess) return
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
