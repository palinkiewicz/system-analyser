package pl.dakil.appanalyser.service

import android.content.Context
import android.text.format.Formatter
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
import pl.dakil.appanalyser.ui.components.formatDuration
import pl.dakil.appanalyser.ui.components.formatTemperature
import pl.dakil.appanalyser.ui.components.sensorUnit
import pl.dakil.appanalyser.ui.components.signedMilliamps

/**
 * The metrics a card needs in order to render. Deriving this set from the pinned list lets a tick
 * sample only what is actually on screen — a lone uptime card must not touch `/proc`, `StatFs`,
 * the battery service or any sensor.
 */
enum class MetricGroup { UPTIME, CPU_USAGE, CPU_CLOCKS, CPU_TEMPS, BATTERY, RAM, STORAGE, SENSORS }

/**
 * The metric group a card samples every tick, or null when the card is static (its values cannot
 * change while the process lives, so it is rendered once and never recomputed).
 */
fun HomeWidgetType.metricGroup(): MetricGroup? = when (this) {
    HomeWidgetType.SYSTEM_IDENTITY,
    HomeWidgetType.SYSTEM_SOFTWARE,
    HomeWidgetType.DISPLAY -> null
    HomeWidgetType.SYSTEM_UPTIME -> MetricGroup.UPTIME
    HomeWidgetType.CPU_USAGE -> MetricGroup.CPU_USAGE
    HomeWidgetType.CPU_CLOCKS -> MetricGroup.CPU_CLOCKS
    HomeWidgetType.CPU_TEMPERATURES -> MetricGroup.CPU_TEMPS
    HomeWidgetType.BATTERY_POWER, HomeWidgetType.BATTERY_INFO -> MetricGroup.BATTERY
    HomeWidgetType.RAM -> MetricGroup.RAM
    HomeWidgetType.STORAGE -> MetricGroup.STORAGE
    HomeWidgetType.SENSOR -> MetricGroup.SENSORS
}

/** Everything sampled during one tick. Every field is null when no pinned card needed it. */
data class MetricSnapshot(
    val system: SystemInfo? = null,
    val display: DisplayInfo? = null,
    val uptime: UptimeInfo? = null,
    val cpu: CpuInfo? = null,
    val battery: BatteryInfo? = null,
    val memory: MemoryInfo? = null,
    val sensors: Map<Int, SensorInfo> = emptyMap(),
)

/**
 * A card rendered to notification text. Compared against the previous render to skip re-posting
 * notifications whose content did not change — the notification manager drops enqueues past
 * roughly five per second per package, so every avoided post matters.
 */
data class RenderedCard(
    val title: String,
    val summary: String,
    val lines: List<Pair<String, String>>,
) {
    /** Expanded body: one `Label: value` per line, shown by `BigTextStyle`. */
    fun bigText(): String = lines.joinToString("\n") { "${it.first}: ${it.second}" }
}

private const val DASH = "—"

/** Title shown for a pinned card, in both the notification and the settings list. */
fun cardTitle(context: Context, card: PinnedCard): String = when (card.type) {
    HomeWidgetType.SYSTEM_IDENTITY -> context.getString(R.string.notification_card_system_identity)
    HomeWidgetType.SYSTEM_SOFTWARE -> context.getString(R.string.notification_card_system_software)
    HomeWidgetType.SYSTEM_UPTIME -> context.getString(R.string.device_system_uptime_title)
    HomeWidgetType.CPU_USAGE -> context.getString(R.string.device_cpu_usage)
    HomeWidgetType.CPU_CLOCKS -> context.getString(R.string.device_cpu_clock_speeds)
    HomeWidgetType.CPU_TEMPERATURES -> context.getString(R.string.device_cpu_temperatures)
    HomeWidgetType.BATTERY_POWER -> context.getString(R.string.device_battery_ampere_title)
    HomeWidgetType.BATTERY_INFO -> context.getString(R.string.device_tab_battery)
    HomeWidgetType.RAM -> context.getString(R.string.device_memory_ram)
    HomeWidgetType.STORAGE -> context.getString(R.string.device_memory_storage)
    HomeWidgetType.DISPLAY -> context.getString(R.string.device_tab_display)
    HomeWidgetType.SENSOR -> card.sensorName ?: context.getString(R.string.device_sensor_other)
}

/**
 * Renders a pinned card to notification text. Returns null when the data this card needs was not
 * sampled this tick (still loading, or the metric is unavailable), leaving the previous
 * notification content in place rather than flashing a placeholder.
 */
fun renderCard(
    context: Context,
    card: PinnedCard,
    snapshot: MetricSnapshot,
    temperatureUnit: TemperatureUnit,
    showSensorUnits: Boolean,
): RenderedCard? {
    val title = cardTitle(context, card)
    fun str(id: Int) = context.getString(id)
    fun unavailable() = str(R.string.device_value_unavailable)

    return when (card.type) {
        HomeWidgetType.SYSTEM_IDENTITY -> snapshot.system?.let { info ->
            RenderedCard(
                title = title,
                summary = "${info.manufacturer} ${info.model}",
                lines = listOf(
                    str(R.string.device_system_manufacturer) to info.manufacturer,
                    str(R.string.device_system_brand) to info.brand,
                    str(R.string.device_system_model) to info.model,
                    str(R.string.device_system_device) to info.device,
                    str(R.string.device_system_board) to info.board,
                    str(R.string.device_system_hardware) to info.hardware,
                )
            )
        }

        HomeWidgetType.SYSTEM_SOFTWARE -> snapshot.system?.let { info ->
            RenderedCard(
                title = title,
                summary = "Android ${info.androidVersion} (API ${info.apiLevel})",
                lines = listOf(
                    str(R.string.device_system_android_version)
                        to "${info.androidVersion} (API ${info.apiLevel})",
                    str(R.string.device_system_security_patch) to info.securityPatch,
                    str(R.string.device_system_build) to info.buildId,
                    str(R.string.device_system_kernel) to info.kernelVersion,
                )
            )
        }

        HomeWidgetType.SYSTEM_UPTIME -> snapshot.uptime?.let { info ->
            RenderedCard(
                title = title,
                summary = formatDuration(info.uptimeMillis),
                lines = listOf(
                    str(R.string.device_system_uptime) to formatDuration(info.uptimeMillis),
                    str(R.string.device_system_awake) to formatDuration(info.awakeMillis),
                    str(R.string.device_system_deep_sleep) to formatDuration(info.deepSleepMillis),
                )
            )
        }

        HomeWidgetType.CPU_USAGE -> snapshot.cpu?.let { info ->
            RenderedCard(
                title = title,
                summary = "${info.usagePercent}%  ·  ${info.soc}",
                lines = listOf(
                    str(R.string.device_cpu_usage) to "${info.usagePercent}%",
                    str(R.string.device_cpu_soc) to info.soc,
                    str(R.string.device_cpu_cores) to info.coreCount.toString(),
                    str(R.string.device_cpu_abis) to info.supportedAbis,
                )
            )
        }

        HomeWidgetType.CPU_CLOCKS -> snapshot.cpu?.let { info ->
            val fastest = info.cores.maxOfOrNull { it.currentFreqKhz } ?: -1L
            RenderedCard(
                title = title,
                summary = if (fastest > 0) "${fastest / 1000} MHz" else unavailable(),
                lines = info.cores.map { core ->
                    val current = if (core.currentFreqKhz > 0) {
                        "${core.currentFreqKhz / 1000} MHz"
                    } else unavailable()
                    val max = if (core.maxFreqKhz > 0) " / ${core.maxFreqKhz / 1000} MHz" else ""
                    context.getString(R.string.device_cpu_core_x, core.index) to (current + max)
                }
            )
        }

        HomeWidgetType.CPU_TEMPERATURES -> snapshot.cpu?.let { info ->
            val hottest = info.temperatures.maxByOrNull { it.celsius }
            RenderedCard(
                title = title,
                summary = hottest?.let { formatTemperature(it.celsius, temperatureUnit) }
                    ?: unavailable(),
                lines = info.temperatures.map {
                    it.name to formatTemperature(it.celsius, temperatureUnit)
                }.ifEmpty { listOf(title to unavailable()) }
            )
        }

        // Current is shown whenever the device reports it — including a genuine 0 mA, which is a
        // real reading (idle at full charge), not a missing one.
        HomeWidgetType.BATTERY_POWER -> snapshot.battery?.let { info ->
            val current = info.currentNowMilliamps
                ?.let { signedMilliamps(it) }
                ?: str(R.string.device_battery_current_unavailable)
            val power = info.powerWatts?.let { String.format("%.2f W", it) } ?: DASH
            val capacity = info.chargeCounterMilliampHours?.let { "$it mAh" } ?: DASH
            val voltage =
                if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else DASH
            val direction = str(
                if ((info.currentNowMilliamps ?: 0) >= 0) R.string.device_battery_charging_now
                else R.string.device_battery_discharging_now
            )

            RenderedCard(
                title = title,
                summary = if (info.currentNowMilliamps != null) {
                    "$current  ·  $power  ·  $direction"
                } else current,
                lines = buildList {
                    add(str(R.string.device_battery_ampere_title) to current)
                    if (info.currentNowMilliamps != null) {
                        add(str(R.string.device_battery_status) to direction)
                    }
                    add(str(R.string.device_battery_power) to power)
                    add(str(R.string.device_battery_capacity) to capacity)
                    add(str(R.string.device_battery_voltage) to voltage)
                    val min = info.sessionMinCurrentMilliamps
                    val max = info.sessionMaxCurrentMilliamps
                    if (min != null && max != null) {
                        add(str(R.string.device_battery_session_min) to signedMilliamps(min))
                        add(str(R.string.device_battery_session_max) to signedMilliamps(max))
                    }
                }
            )
        }

        HomeWidgetType.BATTERY_INFO -> snapshot.battery?.let { info ->
            RenderedCard(
                title = title,
                summary = "${info.levelPercent}%  ·  ${info.status}",
                lines = listOf(
                    str(R.string.device_battery_level) to "${info.levelPercent}%",
                    str(R.string.device_battery_status) to info.status,
                    str(R.string.device_battery_health) to info.health,
                    str(R.string.device_battery_technology) to info.technology,
                    str(R.string.device_battery_power_source) to info.plugged,
                    str(R.string.device_battery_voltage) to
                        if (info.voltageMillivolts > 0) "${info.voltageMillivolts / 1000f} V" else DASH,
                    str(R.string.device_battery_temperature) to
                        formatTemperature(info.temperatureCelsius, temperatureUnit),
                    str(R.string.device_battery_cycle_count) to
                        (info.cycleCount?.toString() ?: unavailable()),
                )
            )
        }

        HomeWidgetType.RAM -> snapshot.memory?.let { info ->
            fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
            RenderedCard(
                title = title,
                summary = "${fmt(info.usedRamBytes)} / ${fmt(info.totalRamBytes)}",
                lines = listOf(
                    str(R.string.device_memory_used) to fmt(info.usedRamBytes),
                    str(R.string.device_memory_available) to fmt(info.availableRamBytes),
                    str(R.string.device_memory_total) to fmt(info.totalRamBytes),
                )
            )
        }

        HomeWidgetType.STORAGE -> snapshot.memory?.let { info ->
            fun fmt(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
            RenderedCard(
                title = title,
                summary = "${fmt(info.usedStorageBytes)} / ${fmt(info.totalStorageBytes)}",
                lines = listOf(
                    str(R.string.device_memory_used) to fmt(info.usedStorageBytes),
                    str(R.string.device_memory_available) to fmt(info.availableStorageBytes),
                    str(R.string.device_memory_total) to fmt(info.totalStorageBytes),
                )
            )
        }

        HomeWidgetType.DISPLAY -> snapshot.display?.let { info ->
            RenderedCard(
                title = title,
                summary = "${info.widthPx} x ${info.heightPx} px",
                lines = listOf(
                    str(R.string.device_display_resolution) to "${info.widthPx} x ${info.heightPx} px",
                    str(R.string.device_display_density) to "${info.densityDpi} dpi",
                    str(R.string.device_display_refresh_rate) to
                        String.format("%.0f Hz", info.refreshRateHz),
                    str(R.string.device_display_size) to
                        String.format("%.1f\"", info.diagonalInches),
                )
            )
        }

        HomeWidgetType.SENSOR -> {
            val sensor = card.sensorType?.let { snapshot.sensors[it] }
            if (sensor == null) {
                // The sensor is gone (or not sampled yet): keep the notification rather than
                // letting the card vanish, matching how SensorCard handles a missing sensor.
                RenderedCard(
                    title = title,
                    summary = str(R.string.home_sensor_unavailable),
                    lines = listOf(str(R.string.device_sensor_value) to unavailable())
                )
            } else {
                val unit = if (showSensorUnits && sensor.values != DASH) {
                    sensorUnit(sensor.type)
                } else null
                val value = if (unit != null) "${sensor.values} $unit" else sensor.values
                RenderedCard(
                    title = sensor.name,
                    summary = value,
                    lines = listOf(
                        str(R.string.device_sensor_value) to value,
                        str(R.string.device_sensor_vendor) to sensor.vendor,
                    )
                )
            }
        }
    }
}
