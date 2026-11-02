package pl.dakil.appanalyser.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.HomeWidgetType
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.PinnedCard
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.domain.UptimeInfo
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
import pl.dakil.appanalyser.ui.components.SystemUptimeCard
import pl.dakil.appanalyser.ui.designsystem.AppMenu
import pl.dakil.appanalyser.ui.designsystem.AppMenuGroup
import pl.dakil.appanalyser.ui.designsystem.AppMenuItem
import pl.dakil.appanalyser.ui.designsystem.CollapsingScaffold
import pl.dakil.appanalyser.ui.designsystem.entrance
import pl.dakil.appanalyser.ui.designsystem.icon.AnimatedAppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.designsystem.rememberHaptics
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.viewmodel.DeviceInfoViewModel

private data class DeviceTab(val title: String, val symbol: AppSymbol)

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
        DeviceTab(stringResource(R.string.device_tab_system), AppSymbols.PhoneAndroid),
        DeviceTab(stringResource(R.string.device_tab_cpu), AppSymbols.Memory),
        DeviceTab(stringResource(R.string.device_tab_battery), AppSymbols.BatteryFull),
        DeviceTab(stringResource(R.string.device_tab_sensors), AppSymbols.Sensors),
        DeviceTab(stringResource(R.string.device_tab_memory), AppSymbols.MemoryAlt),
        DeviceTab(stringResource(R.string.device_tab_display), AppSymbols.FitScreen),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    // A Home card was tapped: open its tab. Handled once per navigation — when this entry is
    // later restored from the bottom bar, the pager keeps the page the user last viewed.
    var handledInitialTab by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialTab) {
        if (!handledInitialTab && initialTab in tabs.indices) {
            pagerState.scrollToPage(initialTab)
        }
        handledInitialTab = true
    }

    // One tick each time the pager lands on a new page, whether tapped or swiped.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { haptics.tick() }
    }

    val systemInfo = viewModel.systemInfo
    CollapsingScaffold(
        title = stringResource(R.string.nav_device),
        subtitle = "${systemInfo.manufacturer} ${systemInfo.model}",
        large = true,
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabStrip(
                tabs = tabs,
                selected = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            )

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
                        systemInfo,
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

/**
 * The pager's tabs as a scrolling row of expressive toggle pills: the selected one fills, morphs
 * fully round and fills its symbol; the row keeps it in view as the pager moves.
 */
@Composable
private fun TabStrip(tabs: List<DeviceTab>, selected: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        listState.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        itemsIndexed(tabs) { index, tab ->
            val checked = index == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(index) },
                icon = {
                    AnimatedAppIcon(
                        tab.symbol,
                        contentDescription = null,
                        filled = checked,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                    )
                },
            ) {
                Text(tab.title, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------

@Composable
private fun TabScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content
        )
    }
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LoadingIndicator(modifier = Modifier.size(64.dp))
    }
}

// ---------------------------------------------------------------------
// Long-press card context menu
// ---------------------------------------------------------------------

/**
 * Wraps a device-info card with a long-press gesture that opens a context menu anchored at
 * the press position, offering to pin the card to the Home screen grid or to the notification
 * shade. The card sinks a little while its menu is open.
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
    index: Int,
    content: @Composable (Modifier) -> Unit
) {
    var pressPosition by remember { mutableStateOf<Offset?>(null) }
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val scale by animateFloatAsState(
        targetValue = if (pressPosition != null) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "cardPress",
    )

    Box(
        Modifier
            .entrance(index)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        content(
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { position ->
                        haptics.longPress()
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
                AppMenu(
                    expanded = true,
                    onDismissRequest = { pressPosition = null },
                    offset = with(density) { DpOffset(0.dp, Spacing.xs) },
                ) {
                    AppMenuGroup {
                        AppMenuItem(
                            text = stringResource(R.string.device_add_to_home),
                            symbol = AppSymbols.AddHome,
                            onClick = {
                                pressPosition = null
                                onAddToHome()
                            }
                        )
                        AppMenuItem(
                            text = stringResource(
                                if (isPinned) R.string.device_unpin_from_notifications
                                else R.string.device_pin_to_notifications
                            ),
                            symbol = if (isPinned) AppSymbols.NotificationsOff else AppSymbols.NotificationsActive,
                            onClick = {
                                pressPosition = null
                                onTogglePin()
                            }
                        )
                        onAnalyseStorage?.let { analyse ->
                            AppMenuItem(
                                text = stringResource(R.string.disk_usage_analyse_storage),
                                symbol = AppSymbols.DonutLarge,
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
    /** [index] is the card's position on its tab, which staggers the cards' entrance. */
    @Composable
    fun Host(
        type: HomeWidgetType,
        sensor: SensorInfo? = null,
        index: Int = 0,
        content: @Composable (Modifier) -> Unit
    ) {
        CardContextMenuHost(
            onAddToHome = { onAddToHome(type, sensor) },
            onTogglePin = { onTogglePin(type, sensor) },
            isPinned = PinnedCard.dedupeKeyOf(type, sensor?.type) in pinnedKeys,
            onAnalyseStorage = onAnalyseStorage.takeIf { type == HomeWidgetType.STORAGE },
            index = index,
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
        menu.Host(HomeWidgetType.SYSTEM_SOFTWARE, index = 1) { SystemSoftwareCard(info, it) }
        menu.Host(HomeWidgetType.SYSTEM_UPTIME, index = 2) { SystemUptimeCard(uptime, it) }
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
        menu.Host(HomeWidgetType.CPU_CLOCKS, index = 1) { CpuClockSpeedsCard(info, it) }
        if (info.temperatures.isNotEmpty()) {
            menu.Host(HomeWidgetType.CPU_TEMPERATURES, index = 2) {
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
        menu.Host(HomeWidgetType.BATTERY_INFO, index = 1) { BatteryDetailsCard(info, temperatureUnit, it) }
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
        sensors.forEachIndexed { index, sensor ->
            menu.Host(HomeWidgetType.SENSOR, sensor, index) {
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
        menu.Host(HomeWidgetType.STORAGE, index = 1) { StorageCard(info, it) }
    }
}

@Composable
private fun DisplayTab(info: DisplayInfo, menu: CardMenu) {
    TabScaffold {
        menu.Host(HomeWidgetType.DISPLAY) { DisplayCard(info, it) }
    }
}
