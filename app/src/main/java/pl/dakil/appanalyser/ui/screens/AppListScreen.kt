package pl.dakil.appanalyser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.AppInfo
import pl.dakil.appanalyser.ui.designsystem.CollapsingScaffold
import pl.dakil.appanalyser.ui.designsystem.EmptyState
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.AppAnalyzerViewModel

@Composable
fun AppListScreen(
    viewModel: AppAnalyzerViewModel,
    onNavigateToDetails: (String) -> Unit
) {
    val apps by viewModel.appsList.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val showSystemApps by viewModel.showSystemApps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val haptics = rememberHaptics()

    CollapsingScaffold(
        title = stringResource(R.string.nav_apps),
        subtitle = if (isLoading) null else pluralStringResource(R.plurals.app_list_count_x, apps.size, apps.size),
        large = true,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = Spacing.maxContentWidth)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Spacing.gutter,
                    end = Spacing.gutter,
                    top = Spacing.xs,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                item(key = "search", contentType = "search") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.s),
                        modifier = Modifier.padding(bottom = Spacing.m),
                    ) {
                        SearchField(
                            query = searchQuery,
                            onQueryChange = { viewModel.updateSearchQuery(it) },
                        )
                        FilterChip(
                            selected = showSystemApps,
                            onClick = {
                                haptics.toggle(!showSystemApps)
                                viewModel.toggleSystemApps(!showSystemApps)
                            },
                            label = { Text(stringResource(R.string.app_list_show_system_apps)) },
                            leadingIcon = {
                                AppIcon(
                                    if (showSystemApps) AppSymbols.Check else AppSymbols.Android,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            shape = CircleShape,
                        )
                    }
                }

                when {
                    isLoading -> item(key = "loading", contentType = "loading") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xxl * 2),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator(Modifier.size(72.dp))
                        }
                    }

                    apps.isEmpty() -> item(key = "empty", contentType = "empty") {
                        EmptyState(
                            symbol = AppSymbols.Search,
                            title = stringResource(R.string.app_list_no_results),
                            modifier = Modifier.animateItem(),
                        )
                    }

                    else -> itemsIndexed(apps, key = { _, app -> app.packageName }, contentType = { _, _ -> "app" }) { index, app ->
                        AppListItem(
                            app = app,
                            index = index,
                            count = apps.size,
                            onClick = { onNavigateToDetails(app.packageName) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/** A pill-shaped search field on a tonal container, with a clear button while there is a query. */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.app_list_search_apps)) },
        leadingIcon = { AppIcon(AppSymbols.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                IconButton(onClick = { onQueryChange("") }) {
                    AppIcon(AppSymbols.Close, contentDescription = stringResource(R.string.app_list_clear_search))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun AppListItem(
    app: AppInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = 0,
    count: Int = 1,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        modifier = modifier,
        leadingContent = {
            Image(
                painter = rememberDrawablePainter(drawable = app.icon),
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = app.packageName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.isSystemApp) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
                        ) {
                            AppIcon(AppSymbols.Android, contentDescription = null, modifier = Modifier.size(12.dp))
                            Text(
                                text = stringResource(R.string.app_list_system_app_label),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            AppIcon(AppSymbols.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    ) {
        Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
