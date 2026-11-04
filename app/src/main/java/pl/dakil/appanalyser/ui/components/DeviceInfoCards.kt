package pl.dakil.appanalyser.ui.components

import android.hardware.Sensor
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.domain.UptimeInfo
import pl.dakil.appanalyser.ui.designsystem.MorphShape
import pl.dakil.appanalyser.ui.designsystem.RollingNumber
import pl.dakil.appanalyser.ui.designsystem.ShapedIcon
import pl.dakil.appanalyser.ui.designsystem.UsageBar
import pl.dakil.appanalyser.ui.designsystem.decorativeMotion
import pl.dakil.appanalyser.ui.designsystem.decorativeSpec
import pl.dakil.appanalyser.ui.designsystem.icon.AppIcon
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbol
import pl.dakil.appanalyser.ui.designsystem.icon.AppSymbols
import pl.dakil.appanalyser.ui.theme.Spacing
import pl.dakil.appanalyser.ui.theme.tabular

// ---------------------------------------------------------------------
// Building blocks shared by the Device Info tabs and the Home widgets
// ---------------------------------------------------------------------

object DeviceCardDefaults {
    /** Every device card's shape; Home clips its grid cells (ripple, edit outline) to the same. */
    val shape: Shape
        @Composable get() = MaterialTheme.shapes.extraLarge
}

/**
 * The tonal card every device metric lives in, optionally headed by a shaped [symbol] and
 * [title]. Its content must support intrinsic measurement: Home sizes grid rows with it.
 */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    symbol: AppSymbol? = null,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DeviceCardDefaults.shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (title != null) CardHeader(title, symbol, polygon)
            content()
        }
    }
}

@Composable
private fun CardHeader(title: String, symbol: AppSymbol?, polygon: RoundedPolygon) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = Spacing.xs),
    ) {
        if (symbol != null) {
            ShapedIcon(
                symbol,
                polygon = polygon,
                size = 32.dp,
                iconSize = 18.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.m))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Placeholder body so a card keeps occupying its cell while data is loading. */
@Composable
private fun ColumnScope.CardLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        LoadingIndicator(modifier = Modifier.size(40.dp))
    }
}

/**
 * A label/value line: side by side when both fit on one line, otherwise the value drops under its
 * label, so narrow Home cells never break a word in half. [live] values roll their digits as they
 * change (uptime, clocks, sensors); static ones stay plain text so they can wrap.
 */
@Composable
fun DetailRow(label: String, value: String, live: Boolean = false) {
    val valueStyle = MaterialTheme.typography.bodyMediumEmphasized.tabular()
    val gap = with(LocalDensity.current) { Spacing.m.roundToPx() }
    Layout(
        content = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(contentAlignment = Alignment.TopEnd) {
                if (live) {
                    RollingNumber(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Text(
                        text = value,
                        style = valueStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        },
        measurePolicy = remember(gap) { DetailRowMeasurePolicy(gap) },
    )
}

/**
 * Measures [DetailRow]. A plain policy (no subcomposition) with real intrinsics, because the Home
 * grid sizes its rows from its cards' intrinsic heights.
 */
private class DetailRowMeasurePolicy(private val gap: Int) : MeasurePolicy {
    private fun fitsInline(label: IntrinsicMeasurable, value: IntrinsicMeasurable, width: Int): Boolean =
        label.maxIntrinsicWidth(Constraints.Infinity) + gap + value.maxIntrinsicWidth(Constraints.Infinity) <= width

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val (label, value) = measurables
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        return if (fitsInline(label, value, width)) {
            val l = label.measure(loose)
            val v = value.measure(loose)
            layout(width, maxOf(l.height, v.height)) {
                l.place(0, 0)
                v.place(width - v.width, 0)
            }
        } else {
            val l = label.measure(loose)
            val v = value.measure(loose.copy(minWidth = width))
            layout(width, l.height + v.height) {
                l.place(0, 0)
                v.place(0, l.height)
            }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        height(measurables, width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        height(measurables, width)

    private fun height(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val (label, value) = measurables
        return if (fitsInline(label, value, width)) {
            maxOf(label.minIntrinsicHeight(width), value.minIntrinsicHeight(width))
        } else {
            label.minIntrinsicHeight(width) + value.minIntrinsicHeight(width)
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOf { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables[0].maxIntrinsicWidth(height) + gap + measurables[1].maxIntrinsicWidth(height)
}

/** The card's headline figure ("42%", "5.1 GB") with an optional caption under it. */
@Composable
private fun BigValue(value: String, caption: String? = null) {
    Column {
        RollingNumber(
            value,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (caption != null) {
            Text(
                caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun signedMilliamps(value: Int): String =
    "${if (value >= 0) "+" else ""}$value mA"

/** Formats a millisecond duration as `1d 02:03:04` (day part omitted below 24h). */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val days = totalSeconds / 86_400
    val hours = (totalSeconds % 86_400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val clock = String.format("%02d:%02d:%02d", hours, minutes, seconds)
    return if (days > 0) "${days}d $clock" else clock
}

fun formatTemperature(celsius: Float, unit: TemperatureUnit): String =
    String.format("%.1f %s", unit.fromCelsius(celsius), unit.symbol)

// ---------------------------------------------------------------------
// System cards
// ---------------------------------------------------------------------

@Composable
fun SystemIdentityCard(info: SystemInfo, modifier: Modifier = Modifier) {
    InfoCard(modifier, stringResource(R.string.device_system_identity_title), AppSymbols.PhoneAndroid) {
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
    InfoCard(
        modifier,
        stringResource(R.string.device_system_software_title),
        AppSymbols.Android,
        MaterialShapes.Cookie6Sided,
    ) {
        DetailRow(
            stringResource(R.string.device_system_android_version),
            "${info.androidVersion} (API ${info.apiLevel})"
        )
        DetailRow(stringResource(R.string.device_system_security_patch), info.securityPatch)
        DetailRow(stringResource(R.string.device_system_build), info.buildId)
        DetailRow(stringResource(R.string.device_system_kernel), info.kernelVersion)
    }
}

@Composable
fun SystemUptimeCard(info: UptimeInfo?, modifier: Modifier = Modifier) {
    InfoCard(
        modifier,
        stringResource(R.string.device_system_uptime_title),
        AppSymbols.Timer,
        MaterialShapes.Clover4Leaf,
    ) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        BigValue(formatDuration(info.uptimeMillis))
        DetailRow(
            stringResource(R.string.device_system_awake),
            formatDuration(info.awakeMillis),
            live = true
        )
        DetailRow(
            stringResource(R.string.device_system_deep_sleep),
            formatDuration(info.deepSleepMillis),
            live = true
        )
    }
}

// ---------------------------------------------------------------------
// CPU cards
// ---------------------------------------------------------------------

@Composable
fun CpuUsageCard(info: CpuInfo?, modifier: Modifier = Modifier) {
    InfoCard(modifier, stringResource(R.string.device_cpu_usage), AppSymbols.Speed, MaterialShapes.Sunny) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        BigValue("${info.usagePercent}%")
        UsageBar(info.usagePercent / 100f, modifier = Modifier.padding(bottom = Spacing.xs))
        DetailRow(stringResource(R.string.device_cpu_soc), info.soc)
        DetailRow(stringResource(R.string.device_cpu_abis), info.supportedAbis)
        DetailRow(stringResource(R.string.device_cpu_cores), info.coreCount.toString())
    }
}

@Composable
fun CpuClockSpeedsCard(info: CpuInfo?, modifier: Modifier = Modifier) {
    InfoCard(modifier, stringResource(R.string.device_cpu_clock_speeds), AppSymbols.Memory) {
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
                value + max,
                live = true
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
    InfoCard(
        modifier,
        stringResource(R.string.device_cpu_temperatures),
        AppSymbols.Thermostat,
        MaterialShapes.Pill,
    ) {
        when {
            info == null -> CardLoading()
            info.temperatures.isEmpty() -> Text(
                text = stringResource(R.string.device_value_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> info.temperatures.forEach { temp ->
                DetailRow(temp.name, formatTemperature(temp.celsius, temperatureUnit), live = true)
            }
        }
    }
}

// ---------------------------------------------------------------------
// Battery cards
// ---------------------------------------------------------------------

/**
 * The "Ampere" hero: the live current in big rolling type beside a badge that morphs between a
 * sunny burst (charging) and a cookie (discharging).
 *
 * @param compact single-column layout: smaller readout, stats stacked as label/value rows
 *   instead of the three-across tiles that overflow narrow cells.
 */
@Composable
fun AmpereCard(info: BatteryInfo?, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DeviceCardDefaults.shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) Spacing.l else Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            Text(
                text = stringResource(R.string.device_battery_ampere_title),
                style = MaterialTheme.typography.labelLargeEmphasized,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                if (!compact) ChargeBadge(charging)
                RollingNumber(
                    text = signedMilliamps(info.currentNowMilliamps),
                    style = if (compact) {
                        MaterialTheme.typography.headlineMediumEmphasized
                    } else {
                        MaterialTheme.typography.displayMediumEmphasized
                    },
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
                            signedMilliamps(info.sessionMinCurrentMilliamps),
                            live = true
                        )
                        DetailRow(
                            stringResource(R.string.device_battery_session_max),
                            signedMilliamps(info.sessionMaxCurrentMilliamps),
                            live = true
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            SessionChip(
                                stringResource(R.string.device_battery_session_min),
                                signedMilliamps(info.sessionMinCurrentMilliamps),
                            )
                            SessionChip(
                                stringResource(R.string.device_battery_session_max),
                                signedMilliamps(info.sessionMaxCurrentMilliamps),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xs))
            val powerValue = info.powerWatts?.let { String.format("%.2f W", it) } ?: "—"
            val capacityValue = info.chargeCounterMilliampHours?.let { "$it mAh" } ?: "—"
            val voltageValue =
                if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else "—"
            if (compact) {
                DetailRow(stringResource(R.string.device_battery_power), powerValue, live = true)
                DetailRow(stringResource(R.string.device_battery_capacity), capacityValue, live = true)
                DetailRow(stringResource(R.string.device_battery_voltage), voltageValue, live = true)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    AmpereStat(
                        label = stringResource(R.string.device_battery_power),
                        value = powerValue,
                        shape = MaterialTheme.shapes.large.copy(
                            topEnd = MaterialTheme.shapes.extraSmall.topEnd,
                            bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    AmpereStat(
                        label = stringResource(R.string.device_battery_capacity),
                        value = capacityValue,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.weight(1f),
                    )
                    AmpereStat(
                        label = stringResource(R.string.device_battery_voltage),
                        value = voltageValue,
                        shape = MaterialTheme.shapes.large.copy(
                            topStart = MaterialTheme.shapes.extraSmall.topStart,
                            bottomStart = MaterialTheme.shapes.extraSmall.bottomStart,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Sunny burst while charging, a calm cookie while discharging; morphs and turns on change. */
@Composable
private fun ChargeBadge(charging: Boolean) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val progress by animateFloatAsState(
        targetValue = if (charging) 1f else 0f,
        animationSpec = decorativeSpec(MaterialTheme.motionScheme.slowSpatialSpec()),
        label = "chargeMorph",
    )
    val spin = if (decorativeMotion) 90f else 0f
    val container = if (charging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (charging) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Surface(
            color = container,
            shape = MorphShape(morph, progress),
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = progress * spin },
        ) {}
        AppIcon(
            if (charging) AppSymbols.Bolt else AppSymbols.BatteryFull,
            contentDescription = null,
            filled = true,
            tint = content,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun SessionChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RollingNumber(
            value,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AmpereStat(label: String, value: String, shape: Shape, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = Spacing.m, horizontal = Spacing.xs),
        ) {
            RollingNumber(
                text = value,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BatteryDetailsCard(
    info: BatteryInfo?,
    temperatureUnit: TemperatureUnit,
    modifier: Modifier = Modifier
) {
    InfoCard(
        modifier,
        stringResource(R.string.device_tab_battery),
        AppSymbols.BatteryFull,
        MaterialShapes.Pill,
    ) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        BigValue("${info.levelPercent}%", caption = info.status)
        UsageBar(info.levelPercent / 100f, modifier = Modifier.padding(bottom = Spacing.xs))
        DetailRow(stringResource(R.string.device_battery_health), info.health)
        DetailRow(stringResource(R.string.device_battery_technology), info.technology)
        DetailRow(stringResource(R.string.device_battery_power_source), info.plugged)
        DetailRow(
            stringResource(R.string.device_battery_voltage),
            if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else "—",
            live = true
        )
        DetailRow(
            stringResource(R.string.device_battery_temperature),
            formatTemperature(info.temperatureCelsius, temperatureUnit),
            live = true
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

/** Measurement unit symbol for a sensor type, or null when it has no meaningful unit. */
internal fun sensorUnit(type: Int): String? = when (type) {
    Sensor.TYPE_ACCELEROMETER,
    Sensor.TYPE_GRAVITY,
    Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
    Sensor.TYPE_GYROSCOPE -> "rad/s"
    Sensor.TYPE_MAGNETIC_FIELD -> "µT"
    Sensor.TYPE_LIGHT -> "lx"
    Sensor.TYPE_PROXIMITY -> "cm"
    Sensor.TYPE_PRESSURE -> "hPa"
    Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
    Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
    else -> null
}

@Composable
fun SensorCard(
    sensor: SensorInfo?,
    simple: Boolean,
    modifier: Modifier = Modifier,
    fallbackName: String? = null,
    showUnit: Boolean = true
) {
    InfoCard(
        modifier,
        title = sensor?.name ?: fallbackName.orEmpty(),
        symbol = AppSymbols.Sensors,
        polygon = MaterialShapes.Cookie4Sided,
    ) {
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
        val unit = if (showUnit && sensor.values != "—") sensorUnit(sensor.type) else null
        DetailRow(
            stringResource(R.string.device_sensor_value),
            if (unit != null) "${sensor.values} $unit" else sensor.values,
            live = true
        )
    }
}

// ---------------------------------------------------------------------
// Memory cards
// ---------------------------------------------------------------------

@Composable
fun RamCard(info: MemoryInfo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(modifier, stringResource(R.string.device_memory_ram), AppSymbols.MemoryAlt) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
        BigValue(
            fmt(info.usedRamBytes),
            caption = stringResource(R.string.device_memory_used_of_x, fmt(info.totalRamBytes)),
        )
        UsageBar(
            if (info.totalRamBytes > 0) info.usedRamBytes.toFloat() / info.totalRamBytes else 0f,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        DetailRow(stringResource(R.string.device_memory_total), fmt(info.totalRamBytes))
        DetailRow(stringResource(R.string.device_memory_used), fmt(info.usedRamBytes), live = true)
        DetailRow(stringResource(R.string.device_memory_available), fmt(info.availableRamBytes), live = true)
    }
}

@Composable
fun StorageCard(info: MemoryInfo?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(
        modifier,
        stringResource(R.string.device_memory_storage),
        AppSymbols.HardDrive,
        MaterialShapes.Cookie6Sided,
    ) {
        if (info == null) {
            CardLoading()
            return@InfoCard
        }
        fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
        BigValue(
            fmt(info.usedStorageBytes),
            caption = stringResource(R.string.device_memory_used_of_x, fmt(info.totalStorageBytes)),
        )
        UsageBar(
            if (info.totalStorageBytes > 0) info.usedStorageBytes.toFloat() / info.totalStorageBytes else 0f,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        DetailRow(stringResource(R.string.device_memory_total), fmt(info.totalStorageBytes))
        DetailRow(stringResource(R.string.device_memory_used), fmt(info.usedStorageBytes), live = true)
        DetailRow(
            stringResource(R.string.device_memory_available),
            fmt(info.availableStorageBytes),
            live = true
        )
    }
}

// ---------------------------------------------------------------------
// Display card
// ---------------------------------------------------------------------

@Composable
fun DisplayCard(info: DisplayInfo, modifier: Modifier = Modifier) {
    InfoCard(
        modifier,
        stringResource(R.string.device_tab_display),
        AppSymbols.FitScreen,
        MaterialShapes.Square,
    ) {
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
