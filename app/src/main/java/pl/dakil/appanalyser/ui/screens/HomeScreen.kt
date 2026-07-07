package pl.dakil.appanalyser.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.ui.components.AmpereCard
import pl.dakil.appanalyser.ui.components.BatteryDetailsCard
import pl.dakil.appanalyser.ui.components.CpuClockSpeedsCard
import pl.dakil.appanalyser.ui.components.CpuTemperaturesCard
import pl.dakil.appanalyser.ui.components.CpuUsageCard
import pl.dakil.appanalyser.ui.components.DisplayCard
import pl.dakil.appanalyser.ui.components.RamCard
import pl.dakil.appanalyser.ui.components.SensorCard
import pl.dakil.appanalyser.ui.components.StorageCard
import pl.dakil.appanalyser.ui.components.SystemIdentityCard
import pl.dakil.appanalyser.ui.components.SystemSoftwareCard
import pl.dakil.appanalyser.ui.components.homegrid.GridMode
import pl.dakil.appanalyser.ui.components.homegrid.WidgetGrid
import pl.dakil.appanalyser.ui.components.homegrid.rememberWidgetGridState
import pl.dakil.appanalyser.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenDeviceTab: (Int) -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val widgets by viewModel.widgets.collectAsState()
    val columns by viewModel.homeColumns.collectAsState()
    val gridState = rememberWidgetGridState()

    BackHandler(enabled = gridState.mode != GridMode.Idle) {
        gridState.exitEditMode()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (widgets.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // Tapping empty space leaves edit mode, like a launcher.
                        .pointerInput(Unit) {
                            detectTapGestures {
                                if (gridState.mode is GridMode.Editing) gridState.exitEditMode()
                            }
                        }
                        .padding(16.dp)
                ) {
                    WidgetGrid(
                        widgets = widgets,
                        columns = columns,
                        state = gridState,
                        onCommit = { viewModel.setWidgets(it) },
                        onRemove = { viewModel.removeWidget(it) },
                        modifier = Modifier.fillMaxWidth()
                    ) { widget ->
                        val shape = if (widget.type == HomeWidgetType.BATTERY_POWER) {
                            MaterialTheme.shapes.extraLarge
                        } else {
                            MaterialTheme.shapes.large
                        }
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .clickable {
                                    if (gridState.mode == GridMode.Idle) {
                                        onOpenDeviceTab(widget.type.deviceTab())
                                    } else {
                                        gridState.exitEditMode()
                                    }
                                }
                        ) {
                            HomeWidgetContent(widget, columns, viewModel)
                        }
                    }
                    Spacer(modifier = Modifier.height(96.dp))
                }
            }

            TrashTarget(
                visible = gridState.mode is GridMode.Dragging,
                hovered = gridState.overTrash,
                onPositioned = { gridState.trashBoundsInRoot = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            )
        }
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
    HomeWidgetType.SYSTEM_SOFTWARE -> 0
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

@Composable
private fun TrashTarget(
    visible: Boolean,
    hovered: Boolean,
    onPositioned: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        val container by animateColorAsState(
            targetValue = if (hovered) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
            label = "trashColor"
        )
        val scale by animateFloatAsState(
            targetValue = if (hovered) 1.15f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "trashScale"
        )
        Surface(
            shape = CircleShape,
            color = container,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.home_remove_widget),
                tint = contentColorFor(container),
                modifier = Modifier.padding(18.dp)
            )
        }
    }
}
