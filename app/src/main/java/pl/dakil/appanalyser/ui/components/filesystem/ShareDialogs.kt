package pl.dakil.appanalyser.ui.components.filesystem

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.foundation.clickable
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.NetworkInfoProvider
import pl.dakil.appanalyser.data.ShareServerRepository
import pl.dakil.appanalyser.domain.ShareState
import pl.dakil.appanalyser.service.ShareServerManager

/** Lets the user pick between the system chooser and hosting the item over the local network. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareMethodSheet(
    onSendToApp: () -> Unit,
    onShareOverWifi: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            modifier = Modifier.clickable(onClick = onSendToApp),
            headlineContent = { Text(stringResource(R.string.share_method_app)) },
            supportingContent = { Text(stringResource(R.string.share_method_app_summary)) },
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null) },
        )
        ListItem(
            modifier = Modifier.clickable(onClick = onShareOverWifi),
            headlineContent = { Text(stringResource(R.string.share_method_wifi)) },
            supportingContent = { Text(stringResource(R.string.share_method_wifi_summary)) },
            leadingContent = { Icon(Icons.Rounded.Wifi, contentDescription = null) },
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Shown when the device is on neither Wi-Fi nor a hotspot, since neither can be enabled for them. */
@Composable
fun NoNetworkDialog(onOpenHotspotSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_no_network_title)) },
        text = { Text(stringResource(R.string.share_no_network_message)) },
        confirmButton = {
            TextButton(onClick = onOpenHotspotSettings) {
                Text(stringResource(R.string.share_open_hotspot_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_cancel)) }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_running_title, running.sharedName)) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.share_running_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                QrCode(
                    text = running.endpoint.link,
                    modifier = Modifier
                        .size(200.dp)
                        .align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = running.endpoint.url,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.share_running_network, networkLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                running.pin?.let { pin ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.share_running_pin, pin),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(
                    onClick = { copyToClipboard(context, running.endpoint.url) },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = stringResource(R.string.share_copy_link),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onStop) { Text(stringResource(R.string.share_stop)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.files_close)) }
        },
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
