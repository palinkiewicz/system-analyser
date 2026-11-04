package pl.dakil.appanalyser.ui.screens.settings

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.AppColorTheme
import pl.dakil.appanalyser.data.DarkThemeOption
import pl.dakil.appanalyser.data.ProgressStyle
import pl.dakil.appanalyser.data.StartTab
import pl.dakil.appanalyser.ui.designsystem.ConnectedToggleRow
import pl.dakil.appanalyser.ui.designsystem.MorphShape
import pl.dakil.appanalyser.ui.designsystem.SegmentedGroup
import pl.dakil.appanalyser.ui.designsystem.ToggleOption
import pl.dakil.appanalyser.ui.designsystem.bleedHorizontally
import pl.dakil.appanalyser.ui.designsystem.decorativeMotion
import pl.dakil.appanalyser.ui.designsystem.decorativeSpec
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.pressScale
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.theme.LocalDarkTheme
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.colorSchemeFor
import pl.dakil.appanalyser.viewmodel.SettingsViewModel

@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val colorTheme by viewModel.colorTheme.collectAsState()
    val darkThemeOption by viewModel.darkThemeOption.collectAsState()
    val pureBlack by viewModel.pureBlack.collectAsState()
    val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
    val startTab by viewModel.startTab.collectAsState()
    val animatedNumbers by viewModel.animatedNumbers.collectAsState()
    val decorativeAnimations by viewModel.decorativeAnimations.collectAsState()
    val progressStyle by viewModel.progressStyle.collectAsState()
    val haptics = rememberHaptics()

    SettingsSubscreen(stringResource(R.string.settings_appearance), onNavigateBack) {
        SectionHeader(stringResource(R.string.settings_theme_colors))

        val availableThemes = AppColorTheme.entries.filter {
            it != AppColorTheme.DYNAMIC || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }
        // Bleeds into the page gutters so swatches scroll edge to edge.
        LazyRow(
            modifier = Modifier.bleedHorizontally(Spacing.gutter),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m)
        ) {
            items(availableThemes) { theme ->
                ThemeColorSwatch(
                    theme = theme,
                    isSelected = theme == colorTheme,
                    onClick = {
                        if (theme != colorTheme) haptics.tick()
                        viewModel.setColorTheme(theme)
                    }
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_dark_mode))
        ConnectedToggleRow(
            options = listOf(
                ToggleOption(DarkThemeOption.FOLLOW_SYSTEM, DarkThemeOption.FOLLOW_SYSTEM.label(), AppSymbols.Contrast),
                ToggleOption(DarkThemeOption.LIGHT, DarkThemeOption.LIGHT.label(), AppSymbols.LightMode),
                ToggleOption(DarkThemeOption.DARK, DarkThemeOption.DARK.label(), AppSymbols.DarkMode),
            ),
            selected = darkThemeOption,
            onSelect = { viewModel.setDarkThemeOption(it) },
            modifier = Modifier.padding(bottom = Spacing.s),
        )
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_pure_black),
                summary = stringResource(R.string.settings_pure_black_description),
                checked = pureBlack,
                onCheckedChange = { viewModel.setPureBlack(it) },
                enabled = darkThemeOption != DarkThemeOption.LIGHT
            )
        }

        SectionHeader(stringResource(R.string.settings_general))
        SegmentedGroup {
            SelectRow(
                title = stringResource(R.string.settings_start_screen),
                summary = stringResource(R.string.settings_start_screen_description),
                selectedLabel = startTab.label(),
                options = StartTab.entries.map { it to it.label() },
                onSelect = { viewModel.setStartTab(it) }
            )
        }

        SectionHeader(stringResource(R.string.settings_feel))
        SegmentedGroup {
            SwitchRow(
                title = stringResource(R.string.settings_haptics),
                summary = stringResource(R.string.settings_haptics_description),
                checked = hapticsEnabled,
                onCheckedChange = { viewModel.setHapticsEnabled(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_animated_numbers),
                summary = stringResource(R.string.settings_animated_numbers_description),
                checked = animatedNumbers,
                onCheckedChange = { viewModel.setAnimatedNumbers(it) }
            )
            SwitchRow(
                title = stringResource(R.string.settings_decorative_animations),
                summary = stringResource(R.string.settings_decorative_animations_description),
                checked = decorativeAnimations,
                onCheckedChange = { viewModel.setDecorativeAnimations(it) }
            )
            SelectRow(
                title = stringResource(R.string.settings_progress_style),
                summary = stringResource(R.string.settings_progress_style_description),
                selectedLabel = progressStyle.label(),
                options = ProgressStyle.entries.map { it to it.label() },
                onSelect = { viewModel.setProgressStyle(it) }
            )
        }
    }
}

/**
 * A color swatch in the style of Android 16's theme picker: the top half shows the theme's
 * primary color, the bottom quadrants its secondary and tertiary containers. Selecting one morphs
 * it from a circle into a cookie and gives it a little spin.
 */
@Composable
private fun ThemeColorSwatch(
    theme: AppColorTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = colorSchemeFor(colorTheme = theme, darkTheme = LocalDarkTheme.current)
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = decorativeSpec(MaterialTheme.motionScheme.defaultSpatialSpec()),
        label = "swatchMorph",
    )
    val interactionSource = remember { MutableInteractionSource() }
    // The spin is decoration; without it the selected swatch still shows as a cookie.
    val spin = if (decorativeMotion) SWATCH_SPIN else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .semantics { selected = isSelected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .pressScale(interactionSource, pressedScale = 0.9f)
                .graphicsLayer { rotationZ = progress * spin }
                .clip(MorphShape(morph, progress)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(scheme.primary, startAngle = 180f, sweepAngle = 180f, useCenter = true)
                drawArc(scheme.secondaryContainer, startAngle = 90f, sweepAngle = 90f, useCenter = true)
                drawArc(scheme.tertiaryContainer, startAngle = 0f, sweepAngle = 90f, useCenter = true)
            }
            AppIcon(
                AppSymbols.Check,
                contentDescription = null,
                tint = scheme.onPrimary,
                filled = true,
                modifier = Modifier
                    .graphicsLayer {
                        // Counter-rotate so the check stays upright while the cookie spins.
                        rotationZ = -progress * spin
                        scaleX = progress
                        scaleY = progress
                        alpha = progress.coerceIn(0f, 1f)
                    }
                    .size(28.dp)
            )
        }
        Text(
            text = theme.label(),
            style = if (isSelected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}


private const val SWATCH_SPIN = 40f
