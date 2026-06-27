package pl.dakil.appanalyser.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.BuildConfig
import pl.dakil.appanalyser.R
import androidx.compose.ui.res.stringResource

private const val REPOSITORY_URL = "https://github.com/palinkiewicz/system-analyser/"
private const val AUTHOR_URL = "https://github.com/palinkiewicz/"
private const val AUTHOR_NAME = "palinkiewicz"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        },
        icon = { Icon(Icons.Default.Search, contentDescription = null) },
        title = { Text(stringResource(R.string.about_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.version_x, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.about_how_it_works_description),
                    style = MaterialTheme.typography.bodyMedium
                )
                LinkRow(
                    label = stringResource(R.string.about_repository),
                    linkText = "GitHub",
                    url = REPOSITORY_URL
                )
                LinkRow(
                    label = stringResource(R.string.about_author),
                    linkText = AUTHOR_NAME,
                    url = AUTHOR_URL
                )
            }
        }
    )
}

@Composable
private fun LinkRow(label: String, linkText: String, url: String) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline
        )
    )
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("$label: ")
            }
            withLink(LinkAnnotation.Url(url, linkStyles)) {
                append(linkText)
            }
        },
        style = MaterialTheme.typography.bodyMedium
    )
}
