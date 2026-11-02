package pl.dakil.appanalyser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.BuildConfig
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing

private const val REPOSITORY_URL = "https://github.com/palinkiewicz/system-analyser/"
private const val AUTHOR_URL = "https://github.com/palinkiewicz/"
private const val AUTHOR_NAME = "palinkiewicz"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.about_close))
            }
        },
        icon = {
            ShapedIcon(
                AppSymbols.Search,
                polygon = MaterialShapes.Cookie12Sided,
                size = 56.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(
                    text = stringResource(R.string.version_x, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.about_how_it_works_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    LinkButton(
                        label = stringResource(R.string.about_repository),
                        symbol = AppSymbols.Code,
                        url = REPOSITORY_URL,
                        modifier = Modifier.weight(1f),
                    )
                    LinkButton(
                        label = AUTHOR_NAME,
                        symbol = AppSymbols.Person,
                        url = AUTHOR_URL,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    )
}

@Composable
private fun LinkButton(label: String, symbol: AppSymbol, url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    FilledTonalButton(
        onClick = { runCatching { uriHandler.openUri(url) } },
        shapes = ButtonDefaults.shapes(),
        modifier = modifier,
    ) {
        AppIcon(symbol, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Text(label, maxLines = 1, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
    }
}
