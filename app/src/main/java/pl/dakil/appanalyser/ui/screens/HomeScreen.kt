package pl.dakil.appanalyser.ui.screens

import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.ui.components.AmpereCard
import pl.dakil.appanalyser.ui.components.BatteryDetailsCard
import pl.dakil.appanalyser.ui.components.CpuClockSpeedsCard
import pl.dakil.appanalyser.ui.components.CpuTemperaturesCard
import pl.dakil.appanalyser.ui.components.CpuUsageCard
import pl.dakil.appanalyser.ui.components.DeviceCardDefaults
import pl.dakil.appanalyser.ui.components.DisplayCard
import pl.dakil.appanalyser.ui.components.RamCard
import pl.dakil.appanalyser.ui.components.SensorCard
import pl.dakil.appanalyser.ui.components.StorageCard
import pl.dakil.appanalyser.ui.components.SystemIdentityCard
import pl.dakil.appanalyser.ui.components.SystemSoftwareCard
import pl.dakil.appanalyser.ui.components.SystemUptimeCard
import pl.dakil.appanalyser.ui.components.homegrid.GridMode
import pl.dakil.appanalyser.ui.components.homegrid.WidgetGrid
import pl.dakil.appanalyser.ui.components.homegrid.rememberWidgetGridState
import pl.dakil.appanalyser.ui.designsystem.CollapsingScaffold
import pl.dakil.appanalyser.ui.designsystem.EmptyState
import pl.dakil.appanalyser.ui.designsystem.MorphShape
import pl.dakil.appanalyser.ui.designsystem.decorativeMotion
import pl.dakil.appanalyser.ui.designsystem.decorativeSpec
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.pressScale
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    onOpenDeviceTab: (Int) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val widgets by viewModel.widgets.collectAsState()
    val columns by viewModel.homeColumns.collectAsState()
    val gridState = rememberWidgetGridState()
    val editing = gridState.mode != GridMode.Idle

    BackHandler(enabled = editing) {
        gridState.exitEditMode()
    }

    CollapsingScaffold(
        title = stringResource(greetingRes()),
        // Static on purpose: the header must not collect any flow, so a metric is only polled
        // while a card of its family is on the grid.
        subtitle = if (editing) stringResource(R.string.home_edit_hint) else rememberDateLine(),
        large = true,
        actions = {
            AnimatedContent(targetState = editing, label = "homeAction") { isEditing ->
                if (isEditing) {
                    Button(
                        onClick = { gridState.exitEditMode() },
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = Spacing.s),
                    ) {
                        AppIcon(AppSymbols.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(
                            stringResource(R.string.home_edit_done),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                } else {
                    IconButton(onClick = { onOpenDeviceTab(0) }, shapes = IconButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.AddHome, contentDescription = stringResource(R.string.home_add_cards))
                    }
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (widgets.isEmpty()) {
                EmptyState(
                    symbol = AppSymbols.Widgets,
                    title = stringResource(R.string.home_empty_title),
                    body = stringResource(R.string.home_empty_hint),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FilledTonalButton(onClick = { onOpenDeviceTab(0) }, shapes = ButtonDefaults.shapes()) {
                        AppIcon(AppSymbols.PhoneAndroid, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(
                            stringResource(R.string.home_browse_cards),
                            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .widthIn(max = Spacing.maxContentWidth)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // Tapping empty space leaves edit mode, like a launcher.
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (gridState.mode is GridMode.Editing) gridState.exitEditMode()
                            }
                        }
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.s)
                ) {
                    WidgetGrid(
                        widgets = widgets,
                        columns = columns,
                        state = gridState,
                        onCommit = { viewModel.setWidgets(it) },
                        onRemove = { viewModel.removeWidget(it) },
                        modifier = Modifier.fillMaxWidth()
                    ) { widget ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .pressScale(interactionSource, pressedScale = 0.97f)
                                .clip(DeviceCardDefaults.shape)
                                .clickable(interactionSource = interactionSource, indication = ripple()) {
                                    if (gridState.mode == GridMode.Idle) {
                                        onOpenDeviceTab(widget.type.deviceTab())
                                    } else {
                                        gridState.exitEditMode()
                                    }
                                },
                            // Keep passing the grid's fixed cell size down so the card
                            // surface fills every row it spans.
                            propagateMinConstraints = true
                        ) {
                            HomeWidgetContent(widget, columns, viewModel)
                        }
                    }
                    Spacer(modifier = Modifier.height(112.dp))
                }
            }

            TrashTarget(
                visible = gridState.mode is GridMode.Dragging,
                hovered = gridState.overTrash,
                onPositioned = { gridState.trashBoundsInRoot = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.xl)
            )
        }
    }
}

@Composable
private fun greetingRes(): Int = when (LocalDateTime.now().hour) {
    in 5..11 -> R.string.home_greeting_morning
    in 12..16 -> R.string.home_greeting_afternoon
    in 17..21 -> R.string.home_greeting_evening
    else -> R.string.home_greeting_night
}

/** "Monday, November 2 · Pixel 9": today's date in the user's locale, then the device model. */
@Composable
private fun rememberDateLine(): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern, locale))
        "${date.replaceFirstChar { it.titlecase(locale) }} · ${Build.MODEL}"
    }
}

/**
 * Renders the card for a Home widget. Each card collects its own flow, so a metric is only
 * polled while a widget of its family is composed on the grid (WhileSubscribed stops the rest).
 */
@Composable
private fun HomeWidgetContent(
    widget: HomeWidget,
    columns: Int,
    viewModel: HomeViewModel
) {
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()

    when (widget.type) {
        HomeWidgetType.SYSTEM_IDENTITY -> SystemIdentityCard(viewModel.systemInfo)
        HomeWidgetType.SYSTEM_SOFTWARE -> SystemSoftwareCard(viewModel.systemInfo)
        HomeWidgetType.DISPLAY -> DisplayCard(viewModel.displayInfo)

        HomeWidgetType.SYSTEM_UPTIME -> {
            val uptime by viewModel.uptime.collectAsState()
            SystemUptimeCard(uptime)
        }

        HomeWidgetType.CPU_USAGE,
        HomeWidgetType.CPU_CLOCKS,
        HomeWidgetType.CPU_TEMPERATURES -> {
            val cpu by viewModel.cpu.collectAsState()
            when (widget.type) {
                HomeWidgetType.CPU_USAGE -> CpuUsageCard(cpu)
                HomeWidgetType.CPU_CLOCKS -> CpuClockSpeedsCard(cpu)
                else -> CpuTemperaturesCard(cpu, temperatureUnit)
            }
        }

        HomeWidgetType.BATTERY_POWER,
        HomeWidgetType.BATTERY_INFO -> {
            val battery by viewModel.battery.collectAsState()
            if (widget.type == HomeWidgetType.BATTERY_POWER) {
                AmpereCard(
                    info = battery,
                    compact = widget.columnSpan.coerceAtMost(columns) == 1
                )
            } else {
                BatteryDetailsCard(battery, temperatureUnit)
            }
        }

        HomeWidgetType.RAM,
        HomeWidgetType.STORAGE -> {
            val memory by viewModel.memory.collectAsState()
            if (widget.type == HomeWidgetType.RAM) RamCard(memory) else StorageCard(memory)
        }

        HomeWidgetType.SENSOR -> {
            val sensors by viewModel.sensors.collectAsState()
            val simple by viewModel.simpleSensorView.collectAsState()
            val showUnits by viewModel.showSensorUnits.collectAsState()
            val sensor = sensors.firstOrNull { it.name == widget.sensorName }
                ?: sensors.firstOrNull { it.type == widget.sensorType }
            SensorCard(
                sensor = sensor,
                simple = simple,
                fallbackName = widget.sensorName,
                showUnit = showUnits
            )
        }
    }
}

/** The Device Info pager page showing this widget's card. */
private fun HomeWidgetType.deviceTab(): Int = when (this) {
    HomeWidgetType.SYSTEM_IDENTITY,
    HomeWidgetType.SYSTEM_SOFTWARE,
    HomeWidgetType.SYSTEM_UPTIME -> 0
    HomeWidgetType.CPU_USAGE,
    HomeWidgetType.CPU_CLOCKS,
    HomeWidgetType.CPU_TEMPERATURES -> 1
    HomeWidgetType.BATTERY_POWER,
    HomeWidgetType.BATTERY_INFO -> 2
    HomeWidgetType.SENSOR -> 3
    HomeWidgetType.RAM,
    HomeWidgetType.STORAGE -> 4
    HomeWidgetType.DISPLAY -> 5
}

/**
 * The drop-to-remove target shown while a card is dragged: a circle that blooms into a burst and
 * turns from error container to error once the card hovers it.
 */
@Composable
private fun TrashTarget(
    visible: Boolean,
    hovered: Boolean,
    onPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
        exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier
    ) {
        val container by animateColorAsState(
            targetValue = if (hovered) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "trashColor"
        )
        val progress by animateFloatAsState(
            targetValue = if (hovered) 1f else 0f,
            animationSpec = decorativeSpec(MaterialTheme.motionScheme.fastSpatialSpec()),
            label = "trashMorph"
        )
        // Without decorative motion the target still swells and turns red when hovered, untilted.
        val tilt = if (decorativeMotion) 30f else 0f
        val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst) }
        Surface(
            shape = MorphShape(morph, progress),
            color = container,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    val scale = 1f + 0.15f * progress
                    scaleX = scale
                    scaleY = scale
                    rotationZ = tilt * progress
                }
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    AppSymbols.Delete,
                    contentDescription = stringResource(R.string.home_remove_widget),
                    tint = contentColorFor(container),
                    filled = hovered,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer { rotationZ = -tilt * progress }
                )
            }
        }
    }
}
