package pl.dakil.appanalyser.ui.components.filesystem

import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileFavorite
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.ui.components.diskusage.ChartPalette
import pl.dakil.appanalyser.ui.designsystem.AppMenu
import pl.dakil.appanalyser.ui.designsystem.AppMenuGroup
import pl.dakil.appanalyser.ui.designsystem.AppMenuItem
import pl.dakil.appanalyser.ui.designsystem.AppMenuLabel
import pl.dakil.appanalyser.ui.designsystem.MorphShape
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.UsageBar
import pl.dakil.appanalyser.ui.designsystem.entrance
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.pressScale
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.designsystem.segmentedItemColors
import pl.dakil.appanalyser.ui.theme.Spacing
import java.io.File

/** A favourite's colours: its badge container and what is drawn on it. */
@Immutable
data class FavoriteTint(val container: Color, val content: Color, val accent: Color)

/**
 * Favourite colours, taken from the same theme-derived hues as the disk usage charts rather than
 * hardcoded, so every theme (and Material You) gets a set that belongs to it. The theme tint
 * ([FileFavorite.AUTO_COLOR]) is the primary container.
 */
@Immutable
class FavoritePalette(private val scheme: ColorScheme) {
    private val chart = ChartPalette.from(scheme)

    fun tint(index: Int): FavoriteTint = if (index < 0) {
        FavoriteTint(scheme.primaryContainer, scheme.onPrimaryContainer, scheme.primary)
    } else {
        val color = chart.color(index % FileFavorite.COLOR_COUNT)
        FavoriteTint(color, chart.contentColorFor(color), color)
    }
}

@Composable
fun rememberFavoritePalette(): FavoritePalette {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme) { FavoritePalette(scheme) }
}

/** What can be done with a favourite from the home. */
class FavoriteActions(
    val onOpen: (FileFavorite) -> Unit,
    val onShowInFolder: (FileFavorite) -> Unit,
    val onColor: (FileFavorite) -> Unit,
    val onRemove: (FileFavorite) -> Unit,
)

/**
 * The Files home: the storage volumes to start from, then the user's favourite folders and files
 * as coloured cards, two or more to a row as the width allows.
 */
@Composable
fun FilesHome(
    roots: List<StorageRoot>,
    favorites: List<FileFavorite>,
    missing: Set<String>,
    palette: FavoritePalette,
    horizontalPadding: Dp,
    bottomPadding: Dp,
    onOpenRoot: (String) -> Unit,
    actions: FavoriteActions,
    onShown: () -> Unit,
) {
    // Whether each favourite still exists is worth knowing every time the home comes up.
    LaunchedEffect(favorites) { onShown() }
    val context = LocalContext.current
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, top = Spacing.s, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "storageHeader", span = full) { HomeSectionHeader(stringResource(R.string.files_storage_section)) }
        itemsIndexed(roots, key = { _, root -> root.path }, span = { _, _ -> GridItemSpan(maxLineSpan) }) { index, root ->
            StorageRootTile(root, index, roots.size, context) { onOpenRoot(root.path) }
        }
        item(key = "favoritesHeader", span = full) { HomeSectionHeader(stringResource(R.string.files_favorites_section)) }
        if (favorites.isEmpty()) {
            item(key = "favoritesEmpty", span = full) { FavoritesEmpty() }
        } else {
            itemsIndexed(favorites, key = { _, favorite -> "fav:" + favorite.path }) { index, favorite ->
                // A starred volume reads by its label, not by its mount point's last segment ("0").
                val volume = roots.firstOrNull { it.path == favorite.path }
                FavoriteCard(
                    favorite = favorite,
                    name = volume?.label ?: favorite.name,
                    location = volume?.path ?: locationLabel(favorite.path, roots),
                    available = favorite.path !in missing,
                    tint = palette.tint(favorite.colorIndex),
                    actions = actions,
                    modifier = Modifier
                        .animateItem()
                        .padding(vertical = Spacing.xs / 2)
                        .entrance(index),
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s)
            .semantics { heading() },
    )
}

/** A volume as a tile showing how full it is. */
@Composable
private fun StorageRootTile(
    root: StorageRoot,
    index: Int,
    count: Int,
    context: android.content.Context,
    onOpen: () -> Unit,
) {
    SegmentedListItem(
        onClick = onOpen,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = segmentedItemColors(),
        leadingContent = {
            ShapedIcon(
                if (root.isRemovable) AppSymbols.SdCard else AppSymbols.Smartphone,
                polygon = if (root.isRemovable) MaterialShapes.Cookie6Sided else MaterialShapes.Cookie9Sided,
                size = 48.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(root.path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                if (root.totalBytes > 0) {
                    UsageBar(root.usedBytes.toFloat() / root.totalBytes)
                    Text(
                        stringResource(
                            R.string.disk_usage_used_of,
                            Formatter.formatShortFileSize(context, root.usedBytes),
                            Formatter.formatShortFileSize(context, root.totalBytes),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        },
        trailingContent = { AppIcon(AppSymbols.ChevronRight, contentDescription = null) },
    ) {
        Text(root.label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * One favourite: its glyph in a cookie of its colour, its name and where it lives. A tap opens it;
 * a long press offers its colour, its folder (for files) and removal. A target that has gone away
 * stays, faded and marked unavailable, so it can still be removed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoriteCard(
    favorite: FileFavorite,
    name: String,
    location: String,
    available: Boolean,
    tint: FavoriteTint,
    actions: FavoriteActions,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val entry = remember(favorite) { favorite.asEntry() }
    val shape = MaterialTheme.shapes.large

    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = { if (available) actions.onOpen(favorite) else menuOpen = true },
                    onLongClick = {
                        haptics.longPress()
                        menuOpen = true
                    },
                    onLongClickLabel = stringResource(R.string.files_more_options),
                )
                .padding(Spacing.m)
                .alpha(if (available) 1f else UNAVAILABLE_ALPHA),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ShapedIcon(
                symbol = iconForEntry(entry),
                polygon = entryPolygon(entry),
                size = 44.dp,
                iconSize = 24.dp,
                containerColor = tint.container,
                contentColor = tint.content,
                filled = favorite.isDirectory,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.padding(top = Spacing.xs),
            )
            Text(
                text = if (available) location else stringResource(R.string.files_favorite_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = if (available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }

        AppMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AppMenuGroup {
                AppMenuLabel(name)
                AppMenuItem(
                    text = stringResource(R.string.files_favorite_color),
                    symbol = AppSymbols.Palette,
                    onClick = {
                        menuOpen = false
                        actions.onColor(favorite)
                    },
                )
                if (!favorite.isDirectory && available) {
                    AppMenuItem(
                        text = stringResource(R.string.files_show_in_folder),
                        symbol = AppSymbols.FolderOpen,
                        onClick = {
                            menuOpen = false
                            actions.onShowInFolder(favorite)
                        },
                    )
                }
                AppMenuItem(
                    text = stringResource(R.string.files_remove_favorites),
                    symbol = AppSymbols.Star,
                    onClick = {
                        menuOpen = false
                        haptics.confirm()
                        actions.onRemove(favorite)
                    },
                )
            }
        }
    }
}

/** Shown instead of the cards until something has been starred. */
@Composable
private fun FavoritesEmpty() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(Spacing.l),
    ) {
        ShapedIcon(
            AppSymbols.FolderSpecial,
            polygon = MaterialShapes.Cookie7Sided,
            size = 48.dp,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.files_favorites_empty_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.files_favorites_empty_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Picks a favourite's colour: the theme tint and the palette's hues as swatches; the chosen one
 * morphs from a circle into a cookie, like the theme picker in Appearance.
 */
@Composable
fun FavoriteColorSheet(
    favorite: FileFavorite,
    palette: FavoritePalette,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val entry = remember(favorite) { favorite.asEntry() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Text(
                stringResource(R.string.files_favorite_color_title, favorite.name),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.fillMaxWidth(),
            ) {
                (listOf(FileFavorite.AUTO_COLOR) + (0 until FileFavorite.COLOR_COUNT)).forEach { index ->
                    ColorSwatch(
                        entry = entry,
                        tint = palette.tint(index),
                        label = if (index < 0) {
                            stringResource(R.string.files_favorite_color_auto)
                        } else {
                            stringResource(R.string.files_favorite_color_x, index + 1)
                        },
                        selected = favorite.colorIndex == index,
                        onClick = {
                            if (favorite.colorIndex != index) haptics.tick()
                            onPick(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(entry: FileEntry, tint: FavoriteTint, label: String, selected: Boolean, onClick: () -> Unit) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        if (selected) 1f else 0f,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "swatchMorph",
    )
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .semantics { this.selected = selected }
            .clickable(interactionSource = interactionSource, indication = null, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .pressScale(interactionSource, pressedScale = 0.9f)
                .graphicsLayer { rotationZ = progress * 40f }
                .background(tint.container, MorphShape(morph, progress)),
        ) {
            AppIcon(
                if (selected) AppSymbols.Check else iconForEntry(entry),
                contentDescription = null,
                tint = tint.content,
                filled = true,
                modifier = Modifier
                    .graphicsLayer { rotationZ = -progress * 40f }
                    .size(24.dp),
            )
        }
        Text(
            label,
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

/** "Internal storage › Download" for something inside a volume; the raw parent path otherwise. */
private fun locationLabel(path: String, roots: List<StorageRoot>): String {
    val parent = File(path).parent ?: return path
    val root = roots.firstOrNull { parent == it.path || parent.startsWith(it.path + "/") } ?: return parent
    val relative = parent.removePrefix(root.path).trim('/')
    return if (relative.isEmpty()) root.label else "${root.label} › ${relative.replace("/", " › ")}"
}

/** Just enough of an entry for the shared glyph and shape helpers. */
private fun FileFavorite.asEntry() = FileEntry(
    name = name,
    path = path,
    isDirectory = isDirectory,
    sizeBytes = 0L,
    lastModified = 0L,
    isHidden = false,
)

private const val UNAVAILABLE_ALPHA = 0.5f
