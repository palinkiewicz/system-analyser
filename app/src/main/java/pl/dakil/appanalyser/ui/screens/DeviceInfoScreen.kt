package pl.dakil.appanalyser.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.HomeWidgetType
import kotlin.math.roundToInt
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.UptimeInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.PinnedCard
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit
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
import pl.dakil.appanalyser.ui.components.SystemUptimeCard
import pl.dakil.appanalyser.ui.components.SystemSoftwareCard
import pl.dakil.appanalyser.ui.components.flatTopAppBarColors
import pl.dakil.appanalyser.viewmodel.DeviceInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onAddToHome: (HomeWidgetType, SensorInfo?) -> Unit = { _, _ -> },
    onTogglePin: (HomeWidgetType, SensorInfo?) -> Unit = { _, _ -> },
    /** Opens the disk usage analyser from the Storage card's menu; null hides the entry. */
    onAnalyseStorage: (() -> Unit)? = null,
    initialTab: Int = -1,
    viewModel: DeviceInfoViewModel = viewModel()
) {
    val tabs = listOf(
        stringResource(R.string.device_tab_system),
        stringResource(R.string.device_tab_cpu),
        stringResource(R.string.device_tab_battery),
        stringResource(R.string.device_tab_sensors),
        stringResource(R.string.device_tab_memory),
        stringResource(R.string.device_tab_display),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // A Home card was tapped: open its tab. Handled once per navigation — when this entry is
    // later restored from the bottom bar, the pager keeps the page the user last viewed.
    var handledInitialTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialTab) {
        if (!handledInitialTab && initialTab in tabs.indices) {
            pagerState.scrollToPage(initialTab)
        }
        handledInitialTab = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_info_title)) },
                colors = flatTopAppBarColors()
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title) }
                    )
                }
            }

            val temperatureUnit by viewModel.temperatureUnit.collectAsState()
            val simpleSensorView by viewModel.simpleSensorView.collectAsState()
            val showSensorUnits by viewModel.showSensorUnits.collectAsState()
            val pinnedCards by viewModel.pinnedCards.collectAsState()

            val menu = remember(onAddToHome, onTogglePin, onAnalyseStorage, pinnedCards) {
                CardMenu(onAddToHome, onTogglePin, onAnalyseStorage, pinnedCards.map { it.dedupeKey }.toSet())
            }

            // The pager composes only the visible page (beyondViewportPageCount = 0), so each tab
            // collects its flow only while on screen — off-screen tabs stop refreshing.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> SystemTab(
                        viewModel.systemInfo,
                        viewModel.uptime.collectAsState().value,
                        menu
                    )
                    1 -> CpuTab(viewModel.cpu.collectAsState().value, temperatureUnit, menu)
                    2 -> BatteryTab(viewModel.battery.collectAsState().value, temperatureUnit, menu)
                    3 -> SensorsTab(
                        viewModel.sensors.collectAsState().value,
                        simpleSensorView,
                        showSensorUnits,
                        menu
                    )
                    4 -> MemoryTab(viewModel.memory.collectAsState().value, menu)
                    5 -> DisplayTab(viewModel.displayInfo, menu)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------

@Composable
private fun TabScaffold(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------
// Long-press card context menu
// ---------------------------------------------------------------------

/**
 * Wraps a device-info card with a long-press gesture that opens a context menu anchored at
 * the press position, offering to pin the card to the Home screen grid or to the notification
 * shade.
 *
 * The notification entry is a toggle rather than a disabled "already pinned" item: unpinning is
 * discoverable in the same place the user pinned from.
 */
@Composable
private fun CardContextMenuHost(
    onAddToHome: () -> Unit,
    onTogglePin: () -> Unit,
    isPinned: Boolean,
    onAnalyseStorage: (() -> Unit)?,
    content: @Composable (Modifier) -> Unit
) {
    var pressPosition by remember { mutableStateOf<Offset?>(null) }
    val view = LocalView.current

    Box {
        content(
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { position ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        pressPosition = position
                    }
                )
            }
        )
        pressPosition?.let { position ->
            // Invisible anchor at the press point so the menu opens right where the finger is.
            Box(
                modifier = Modifier.offset {
                    IntOffset(position.x.roundToInt(), position.y.roundToInt())
                }
            ) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { pressPosition = null }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.device_add_to_home)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.AddHome, contentDescription = null)
                        },
                        onClick = {
                            pressPosition = null
                            onAddToHome()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isPinned) R.string.device_unpin_from_notifications
                                    else R.string.device_pin_to_notifications
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                if (isPinned) Icons.Rounded.NotificationsOff
                                else Icons.Rounded.NotificationsActive,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            pressPosition = null
                            onTogglePin()
                        }
                    )
                    onAnalyseStorage?.let { analyse ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.disk_usage_analyse_storage)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.DonutLarge, contentDescription = null)
                            },
                            onClick = {
                                pressPosition = null
                                analyse()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The card actions available on every tab, bundled so adding a menu entry doesn't mean threading
 * another parameter through all six tabs.
 */
@Immutable
private class CardMenu(
    private val onAddToHome: (HomeWidgetType, SensorInfo?) -> Unit,
    private val onTogglePin: (HomeWidgetType, SensorInfo?) -> Unit,
    private val onAnalyseStorage: (() -> Unit)?,
    private val pinnedKeys: Set<String>,
) {
    @Composable
    fun Host(
        type: HomeWidgetType,
        sensor: SensorInfo? = null,
        content: @Composable (Modifier) -> Unit
    ) {
        CardContextMenuHost(
            onAddToHome = { onAddToHome(type, sensor) },
            onTogglePin = { onTogglePin(type, sensor) },
            isPinned = PinnedCard.dedupeKeyOf(type, sensor?.type) in pinnedKeys,
            onAnalyseStorage = onAnalyseStorage.takeIf { type == HomeWidgetType.STORAGE },
            content = content
        )
    }
}

// ---------------------------------------------------------------------
// Tabs — thin compositions of the reusable cards in ui/components
// ---------------------------------------------------------------------

@Composable
private fun SystemTab(info: SystemInfo, uptime: UptimeInfo?, menu: CardMenu) {
    TabScaffold {
        menu.Host(HomeWidgetType.SYSTEM_IDENTITY) { SystemIdentityCard(info, it) }
        menu.Host(HomeWidgetType.SYSTEM_SOFTWARE) { SystemSoftwareCard(info, it) }
        menu.Host(HomeWidgetType.SYSTEM_UPTIME) { SystemUptimeCard(uptime, it) }
    }
}

@Composable
private fun CpuTab(info: CpuInfo?, temperatureUnit: TemperatureUnit, menu: CardMenu) {
    if (info == null) {
        Loading()
        return
    }
    TabScaffold {
        menu.Host(HomeWidgetType.CPU_USAGE) { CpuUsageCard(info, it) }
        menu.Host(HomeWidgetType.CPU_CLOCKS) { CpuClockSpeedsCard(info, it) }
        if (info.temperatures.isNotEmpty()) {
            menu.Host(HomeWidgetType.CPU_TEMPERATURES) {
                CpuTemperaturesCard(info, temperatureUnit, it)
            }
        }
    }
}

@Composable
private fun BatteryTab(info: BatteryInfo?, temperatureUnit: TemperatureUnit, menu: CardMenu) {
    if (info == null) {
        Loading()
        return
    }
    TabScaffold {
        menu.Host(HomeWidgetType.BATTERY_POWER) { AmpereCard(info, it) }
        menu.Host(HomeWidgetType.BATTERY_INFO) { BatteryDetailsCard(info, temperatureUnit, it) }
    }
}

@Composable
private fun SensorsTab(
    sensors: List<SensorInfo>,
    simple: Boolean,
    showUnits: Boolean,
    menu: CardMenu
) {
    if (sensors.isEmpty()) {
        Loading()
        return
    }
    TabScaffold {
        sensors.forEach { sensor ->
            menu.Host(HomeWidgetType.SENSOR, sensor) {
                SensorCard(sensor = sensor, simple = simple, modifier = it, showUnit = showUnits)
            }
        }
    }
}

@Composable
private fun MemoryTab(info: MemoryInfo?, menu: CardMenu) {
    if (info == null) {
        Loading()
        return
    }
    TabScaffold {
        menu.Host(HomeWidgetType.RAM) { RamCard(info, it) }
        menu.Host(HomeWidgetType.STORAGE) { StorageCard(info, it) }
    }
}

@Composable
private fun DisplayTab(info: DisplayInfo, menu: CardMenu) {
    TabScaffold {
        menu.Host(HomeWidgetType.DISPLAY) { DisplayCard(info, it) }
    }
}
