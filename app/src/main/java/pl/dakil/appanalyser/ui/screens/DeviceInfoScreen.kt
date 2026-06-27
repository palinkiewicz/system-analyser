package pl.dakil.appanalyser.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.viewmodel.DeviceInfoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onNavigateBack: () -> Unit,
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.device_info_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
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

            // The pager composes only the visible page (beyondViewportPageCount = 0), so each tab
            // collects its flow only while on screen — off-screen tabs stop refreshing.
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> SystemTab(viewModel.systemInfo)
                    1 -> CpuTab(viewModel.cpu.collectAsState().value, temperatureUnit)
                    2 -> BatteryTab(viewModel.battery.collectAsState().value, temperatureUnit)
                    3 -> SensorsTab(viewModel.sensors.collectAsState().value, simpleSensorView)
                    4 -> MemoryTab(viewModel.memory.collectAsState().value)
                    5 -> DisplayTab(viewModel.displayInfo)
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
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

// ---------------------------------------------------------------------
// System tab
// ---------------------------------------------------------------------

@Composable
private fun SystemTab(info: SystemInfo) {
    TabScaffold {
        InfoCard {
            DetailRow(stringResource(R.string.device_system_manufacturer), info.manufacturer)
            DetailRow(stringResource(R.string.device_system_brand), info.brand)
            DetailRow(stringResource(R.string.device_system_model), info.model)
            DetailRow(stringResource(R.string.device_system_device), info.device)
            DetailRow(stringResource(R.string.device_system_board), info.board)
            DetailRow(stringResource(R.string.device_system_hardware), info.hardware)
        }
        InfoCard {
            DetailRow(
                stringResource(R.string.device_system_android_version),
                "${info.androidVersion} (API ${info.apiLevel})"
            )
            DetailRow(stringResource(R.string.device_system_security_patch), info.securityPatch)
            DetailRow(stringResource(R.string.device_system_build), info.buildId)
            DetailRow(stringResource(R.string.device_system_kernel), info.kernelVersion)
        }
    }
}

// ---------------------------------------------------------------------
// CPU tab
// ---------------------------------------------------------------------

@Composable
private fun CpuTab(info: CpuInfo?, temperatureUnit: TemperatureUnit) {
    if (info == null) {
        Loading()
        return
    }
    TabScaffold {
        InfoCard {
            DetailRow(stringResource(R.string.device_cpu_soc), info.soc)
            DetailRow(stringResource(R.string.device_cpu_abis), info.supportedAbis)
            DetailRow(stringResource(R.string.device_cpu_cores), info.coreCount.toString())
            DetailRow(
                stringResource(R.string.device_cpu_usage),
                "${info.usagePercent}%"
            )
            LinearProgressIndicator(
                progress = { info.usagePercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionLabel(stringResource(R.string.device_cpu_clock_speeds))
        InfoCard {
            info.cores.forEach { core ->
                val value = if (core.currentFreqKhz > 0) {
                    "${core.currentFreqKhz / 1000} MHz"
                } else {
                    stringResource(R.string.device_value_unavailable)
                }
                val max = if (core.maxFreqKhz > 0) " / ${core.maxFreqKhz / 1000} MHz" else ""
                DetailRow(
                    stringResource(R.string.device_cpu_core_x, core.index),
                    value + max
                )
            }
        }

        if (info.temperatures.isNotEmpty()) {
            SectionLabel(stringResource(R.string.device_cpu_temperatures))
            InfoCard {
                info.temperatures.forEach { temp ->
                    DetailRow(temp.name, formatTemperature(temp.celsius, temperatureUnit))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
// Battery tab (with Ampere widget)
// ---------------------------------------------------------------------

@Composable
private fun BatteryTab(info: BatteryInfo?, temperatureUnit: TemperatureUnit) {
    if (info == null) {
        Loading()
        return
    }
    TabScaffold {
        AmpereCard(info)

        InfoCard {
            DetailRow(stringResource(R.string.device_battery_level), "${info.levelPercent}%")
            LinearProgressIndicator(
                progress = { info.levelPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
            DetailRow(stringResource(R.string.device_battery_status), info.status)
            DetailRow(stringResource(R.string.device_battery_health), info.health)
            DetailRow(stringResource(R.string.device_battery_technology), info.technology)
            DetailRow(stringResource(R.string.device_battery_power_source), info.plugged)
            DetailRow(
                stringResource(R.string.device_battery_voltage),
                if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else "—"
            )
            DetailRow(
                stringResource(R.string.device_battery_temperature),
                formatTemperature(info.temperatureCelsius, temperatureUnit)
            )
            DetailRow(
                stringResource(R.string.device_battery_cycle_count),
                info.cycleCount?.toString()
                    ?: stringResource(R.string.device_value_unavailable)
            )
        }
    }
}

@Composable
private fun AmpereCard(info: BatteryInfo) {
    val charging = (info.currentNowMilliamps ?: 0) >= 0
    val accent = if (charging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.device_battery_ampere_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (info.currentNowMilliamps == null) {
                Text(
                    text = stringResource(R.string.device_battery_current_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = signedMilliamps(info.currentNowMilliamps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    text = stringResource(
                        if (charging) R.string.device_battery_charging_now
                        else R.string.device_battery_discharging_now
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (info.sessionMinCurrentMilliamps != null && info.sessionMaxCurrentMilliamps != null) {
                    Text(
                        text = stringResource(R.string.device_battery_session_min) +
                            " ${signedMilliamps(info.sessionMinCurrentMilliamps)}   ·   " +
                            stringResource(R.string.device_battery_session_max) +
                            " ${signedMilliamps(info.sessionMaxCurrentMilliamps)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AmpereStat(
                    label = stringResource(R.string.device_battery_power),
                    value = info.powerWatts?.let { String.format("%.2f W", it) } ?: "—"
                )
                AmpereStat(
                    label = stringResource(R.string.device_battery_capacity),
                    value = info.chargeCounterMilliampHours?.let { "$it mAh" } ?: "—"
                )
                AmpereStat(
                    label = stringResource(R.string.device_battery_voltage),
                    value = if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else "—"
                )
            }
        }
    }
}

private fun signedMilliamps(value: Int): String =
    "${if (value >= 0) "+" else ""}$value mA"

private fun formatTemperature(celsius: Float, unit: TemperatureUnit): String =
    String.format("%.1f %s", unit.fromCelsius(celsius), unit.symbol)

@Composable
private fun AmpereStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------
// Sensors tab
// ---------------------------------------------------------------------

@Composable
private fun SensorsTab(sensors: List<SensorInfo>, simple: Boolean) {
    if (sensors.isEmpty()) {
        Loading()
        return
    }
    TabScaffold {
        sensors.forEach { sensor ->
            InfoCard {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!simple) {
                    Text(
                        text = sensor.typeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DetailRow(stringResource(R.string.device_sensor_vendor), sensor.vendor)
                }
                DetailRow(stringResource(R.string.device_sensor_value), sensor.values)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Memory tab
// ---------------------------------------------------------------------

@Composable
private fun MemoryTab(info: MemoryInfo?) {
    if (info == null) {
        Loading()
        return
    }
    val context = LocalContext.current
    fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    TabScaffold {
        SectionLabel(stringResource(R.string.device_memory_ram))
        InfoCard {
            DetailRow(stringResource(R.string.device_memory_total), fmt(info.totalRamBytes))
            DetailRow(stringResource(R.string.device_memory_used), fmt(info.usedRamBytes))
            DetailRow(stringResource(R.string.device_memory_available), fmt(info.availableRamBytes))
            LinearProgressIndicator(
                progress = {
                    if (info.totalRamBytes > 0)
                        info.usedRamBytes.toFloat() / info.totalRamBytes else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionLabel(stringResource(R.string.device_memory_storage))
        InfoCard {
            DetailRow(stringResource(R.string.device_memory_total), fmt(info.totalStorageBytes))
            DetailRow(stringResource(R.string.device_memory_used), fmt(info.usedStorageBytes))
            DetailRow(
                stringResource(R.string.device_memory_available),
                fmt(info.availableStorageBytes)
            )
            LinearProgressIndicator(
                progress = {
                    if (info.totalStorageBytes > 0)
                        info.usedStorageBytes.toFloat() / info.totalStorageBytes else 0f
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ---------------------------------------------------------------------
// Display tab
// ---------------------------------------------------------------------

@Composable
private fun DisplayTab(info: DisplayInfo) {
    TabScaffold {
        InfoCard {
            DetailRow(
                stringResource(R.string.device_display_resolution),
                "${info.widthPx} x ${info.heightPx} px"
            )
            DetailRow(stringResource(R.string.device_display_density), "${info.densityDpi} dpi")
            DetailRow(
                stringResource(R.string.device_display_refresh_rate),
                String.format("%.0f Hz", info.refreshRateHz)
            )
            DetailRow(
                stringResource(R.string.device_display_size),
                String.format("%.1f\"", info.diagonalInches)
            )
        }
    }
}
