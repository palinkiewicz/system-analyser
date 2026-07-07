package pl.dakil.appanalyser.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit

// ---------------------------------------------------------------------
// Building blocks shared by the Device Info tabs and the Home widgets
// ---------------------------------------------------------------------

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

/** Card title used when a card carries its own label (Home widgets, RAM/Storage). */
@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

/** Placeholder body so a card keeps occupying its cell while data is loading. */
@Composable
private fun ColumnScope.CardLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

fun signedMilliamps(value: Int): String =
    "${if (value >= 0) "+" else ""}$value mA"

fun formatTemperature(celsius: Float, unit: TemperatureUnit): String =
    String.format("%.1f %s", unit.fromCelsius(celsius), unit.symbol)

// ---------------------------------------------------------------------
// System cards
// ---------------------------------------------------------------------

@Composable
fun SystemIdentityCard(info: SystemInfo, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        DetailRow(stringResource(R.string.device_system_manufacturer), info.manufacturer)
        DetailRow(stringResource(R.string.device_system_brand), info.brand)
        DetailRow(stringResource(R.string.device_system_model), info.model)
        DetailRow(stringResource(R.string.device_system_device), info.device)
        DetailRow(stringResource(R.string.device_system_board), info.board)
        DetailRow(stringResource(R.string.device_system_hardware), info.hardware)
    }
}

@Composable
fun SystemSoftwareCard(info: SystemInfo, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        DetailRow(
            stringResource(R.string.device_system_android_version),
            "${info.androidVersion} (API ${info.apiLevel})"
        )
        DetailRow(stringResource(R.string.device_system_security_patch), info.securityPatch)
        DetailRow(stringResource(R.string.device_system_build), info.buildId)
        DetailRow(stringResource(R.string.device_system_kernel), info.kernelVersion)
    }
}

// ---------------------------------------------------------------------
// CPU cards
// ---------------------------------------------------------------------

@Composable
fun CpuUsageCard(info: CpuInfo?, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
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
}

@Composable
fun CpuClockSpeedsCard(info: CpuInfo?, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        CardTitle(stringResource(R.string.device_cpu_clock_speeds))
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
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
}

@Composable
fun CpuTemperaturesCard(
    info: CpuInfo?,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    InfoCard(modifier) {
        CardTitle(stringResource(R.string.device_cpu_temperatures))
        when {
            info == null -> CardLoading()
            info.temperatures.isEmpty() -> Text(
                text = stringResource(R.string.device_value_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> info.temperatures.forEach { temp ->
                DetailRow(temp.name, formatTemperature(temp.celsius, temperatureUnit))
            }
        }
    }
}

// ---------------------------------------------------------------------
// Battery cards
// ---------------------------------------------------------------------

/**
 * @param compact single-column layout: smaller readout, stats stacked as label/value rows
 *   instead of the three-across row that overflows narrow cells.
 */
@Composable
fun AmpereCard(info: BatteryInfo?, modifier: Modifier = Modifier, compact: Boolean = false) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 16.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.device_battery_ampere_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (info == null) {
                CardLoading()
                return@Column
            }
            val charging = (info.currentNowMilliamps ?: 0) >= 0
            val accent = if (charging) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            if (info.currentNowMilliamps == null) {
                Text(
                    text = stringResource(R.string.device_battery_current_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = signedMilliamps(info.currentNowMilliamps),
                    style = if (compact) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.displaySmall
                    },
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
                    if (compact) {
                        DetailRow(
                            stringResource(R.string.device_battery_session_min),
                            signedMilliamps(info.sessionMinCurrentMilliamps)
                        )
                        DetailRow(
                            stringResource(R.string.device_battery_session_max),
                            signedMilliamps(info.sessionMaxCurrentMilliamps)
                        )
                    } else {
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
            }

            Spacer(Modifier.height(4.dp))
            val powerValue = info.powerWatts?.let { String.format("%.2f W", it) } ?: "—"
            val capacityValue = info.chargeCounterMilliampHours?.let { "$it mAh" } ?: "—"
            val voltageValue =
                if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else "—"
            if (compact) {
                DetailRow(stringResource(R.string.device_battery_power), powerValue)
                DetailRow(stringResource(R.string.device_battery_capacity), capacityValue)
                DetailRow(stringResource(R.string.device_battery_voltage), voltageValue)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AmpereStat(
                        label = stringResource(R.string.device_battery_power),
                        value = powerValue
                    )
                    AmpereStat(
                        label = stringResource(R.string.device_battery_capacity),
                        value = capacityValue
                    )
                    AmpereStat(
                        label = stringResource(R.string.device_battery_voltage),
                        value = voltageValue
                    )
                }
            }
        }
    }
}

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

@Composable
fun BatteryDetailsCard(
    info: BatteryInfo?,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    InfoCard(modifier) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
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

// ---------------------------------------------------------------------
// Sensor card
// ---------------------------------------------------------------------

@Composable
fun SensorCard(
    sensor: SensorInfo?,
    simple: Boolean,
    modifier: Modifier = Modifier,
    fallbackName: String? = null
) {
    InfoCard(modifier) {
        Text(
            text = sensor?.name ?: fallbackName.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        if (sensor == null) {
            Text(
                text = stringResource(R.string.home_sensor_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@InfoCard
        }
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

// ---------------------------------------------------------------------
// Memory cards
// ---------------------------------------------------------------------

@Composable
fun RamCard(info: MemoryInfo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(modifier) {
        CardTitle(stringResource(R.string.device_memory_ram))
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
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
}

@Composable
fun StorageCard(info: MemoryInfo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(modifier) {
        CardTitle(stringResource(R.string.device_memory_storage))
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
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

// ---------------------------------------------------------------------
// Display card
// ---------------------------------------------------------------------

@Composable
fun DisplayCard(info: DisplayInfo, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
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
