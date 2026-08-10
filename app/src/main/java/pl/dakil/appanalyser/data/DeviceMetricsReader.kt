package pl.dakil.appanalyser.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.view.WindowManager
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuCore
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.ThermalReading
import pl.dakil.appanalyser.domain.UptimeInfo
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cross-tick state for CPU usage: `/proc/stat` reports cumulative jiffies, so usage is the delta
 * between two consecutive samples. The caller owns the state so it survives across sampling loops.
 */
class CpuSampleState {
    var prevTotal = 0L
    var prevIdle = 0L
}

/**
 * Cross-tick state for the battery current: a FIFO window smoothing out the noisy instantaneous
 * readings, plus the min/max seen for the lifetime of this sampling session.
 */
class BatterySampleState {
    val currentWindow = ArrayDeque<Int>()
    var sessionMin: Int? = null
    var sessionMax: Int? = null
}

/**
 * One-shot, blocking readers for every device metric — no coroutines, no Compose.
 *
 * This is the single source of truth for *how* a metric is read. [DeviceInfoRepository] wraps these
 * in live flows for the UI; the pinned-notification service calls them directly, once per tick, for
 * only the metrics its pinned cards actually need. Keeping one implementation is what stops the
 * notification and the on-screen card from ever showing different numbers.
 *
 * Every function here does blocking I/O (`/proc`, `/sys`, `StatFs`, binder calls) — always call
 * them from [kotlinx.coroutines.Dispatchers.IO] or a background thread.
 */
class DeviceMetricsReader(private val context: Context) {

    // ---------------------------------------------------------------------
    // Static info
    // ---------------------------------------------------------------------

    fun systemInfo(): SystemInfo = SystemInfo(
        manufacturer = Build.MANUFACTURER,
        brand = Build.BRAND,
        model = Build.MODEL,
        device = Build.DEVICE,
        androidVersion = Build.VERSION.RELEASE,
        apiLevel = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank { "—" },
        buildId = Build.DISPLAY,
        kernelVersion = System.getProperty("os.version") ?: "—",
        board = Build.BOARD,
        hardware = Build.HARDWARE
    )

    @Suppress("DEPRECATION")
    fun displayInfo(): DisplayInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = context.resources.displayMetrics
        val display = wm.defaultDisplay

        val widthPx = metrics.widthPixels
        val heightPx = metrics.heightPixels
        val xInches = widthPx / metrics.xdpi
        val yInches = heightPx / metrics.ydpi
        val diagonal = sqrt((xInches * xInches + yInches * yInches).toDouble()).toFloat()

        return DisplayInfo(
            widthPx = widthPx,
            heightPx = heightPx,
            densityDpi = metrics.densityDpi,
            refreshRateHz = display?.refreshRate ?: 0f,
            diagonalInches = diagonal
        )
    }

    // ---------------------------------------------------------------------
    // Uptime
    // ---------------------------------------------------------------------

    fun uptime(): UptimeInfo = UptimeInfo(
        uptimeMillis = SystemClock.elapsedRealtime(),
        awakeMillis = SystemClock.uptimeMillis()
    )

    // ---------------------------------------------------------------------
    // Battery
    // ---------------------------------------------------------------------

    /**
     * Reads the current battery state. [intent] is the last `ACTION_BATTERY_CHANGED` broadcast;
     * pass null to poll the sticky broadcast, which is what a caller without a registered receiver
     * (the notification service) does — see [stickyBatteryIntent].
     */
    fun battery(state: BatterySampleState, intent: Intent?): BatteryInfo {
        val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val rawCurrent = readCurrentNowMilliamps(statusCode)
        val smoothed = if (rawCurrent != null) {
            state.currentWindow.addLast(rawCurrent)
            while (state.currentWindow.size > CURRENT_SMOOTHING_WINDOW) {
                state.currentWindow.removeFirst()
            }
            state.currentWindow.average().roundToInt()
        } else {
            state.currentWindow.clear()
            null
        }
        if (smoothed != null) {
            state.sessionMin = state.sessionMin?.coerceAtMost(smoothed) ?: smoothed
            state.sessionMax = state.sessionMax?.coerceAtLeast(smoothed) ?: smoothed
        }
        return buildBatteryInfo(intent, smoothed, state.sessionMin, state.sessionMax)
    }

    /**
     * Retrieves the current battery state from the sticky broadcast without registering a receiver.
     * A null receiver is exempt from the Android 14 exported/not-exported flag requirement.
     */
    fun stickyBatteryIntent(): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    fun buildBatteryInfo(
        intent: Intent?,
        currentMa: Int?,
        sessionMinMa: Int?,
        sessionMaxMa: Int?
    ): BatteryInfo {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else level

        val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val pluggedCode = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: "—"

        val rawCharge = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeMah = if (rawCharge == Int.MIN_VALUE || rawCharge <= 0) null else rawCharge / 1000

        val power = if (currentMa != null && voltageMv > 0) {
            (voltageMv / 1000f) * (currentMa / 1000f)
        } else null

        return BatteryInfo(
            levelPercent = percent,
            status = batteryStatusLabel(statusCode),
            health = batteryHealthLabel(healthCode),
            technology = technology,
            plugged = batteryPluggedLabel(pluggedCode),
            voltageMillivolts = voltageMv,
            temperatureCelsius = tempTenths / 10f,
            currentNowMilliamps = currentMa,
            sessionMinCurrentMilliamps = sessionMinMa,
            sessionMaxCurrentMilliamps = sessionMaxMa,
            chargeCounterMilliampHours = chargeMah,
            powerWatts = power,
            cycleCount = readCycleCount(intent)
        )
    }

    /**
     * Battery charge cycle count. Available via [BatteryManager.EXTRA_CYCLE_COUNT] from Android 14
     * (API 34); otherwise falls back to the kernel `sysfs` node.
     */
    private fun readCycleCount(intent: Intent?): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fromIntent = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            if (fromIntent > 0) return fromIntent
        }
        return runCatching {
            File("/sys/class/power_supply/battery/cycle_count")
                .takeIf { it.canRead() }?.readText()?.trim()?.toInt()
        }.getOrNull()?.takeIf { it > 0 }
    }

    /**
     * Resolves the instantaneous battery current in mA, normalized so that
     * positive = charging and negative = discharging.
     *
     * Tries [BatteryManager.BATTERY_PROPERTY_CURRENT_NOW] first, then falls back to scraping the
     * kernel `sysfs` power-supply nodes for OEMs that don't implement the public API. Units are
     * auto-detected (some devices report mA directly, others µA), and the sign is reconciled
     * against the reported charging status.
     *
     * Only [Int.MIN_VALUE] means "unsupported" — a genuine 0 mA (idle at full charge) is a real
     * reading and must be reported as such rather than falling through to the sysfs fallback.
     */
    private fun readCurrentNowMilliamps(statusCode: Int): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val apiRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }?.toLong()

        val raw = apiRaw ?: readCurrentNowFromSysfs() ?: return null

        // Unit auto-detection: |value| > 10000 strongly implies microamperes.
        val milliamps = if (abs(raw) > 10000) raw / 1000 else raw
        return normalizeCurrentSign(milliamps.toInt(), statusCode)
    }

    private fun readCurrentNowFromSysfs(): Long? {
        for (path in CURRENT_NOW_SYSFS_PATHS) {
            val value = runCatching {
                File(path).takeIf { it.canRead() }?.readText()?.trim()?.toLong()
            }.getOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun normalizeCurrentSign(value: Int, statusCode: Int): Int {
        val charging = statusCode == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusCode == BatteryManager.BATTERY_STATUS_FULL
        val discharging = statusCode == BatteryManager.BATTERY_STATUS_DISCHARGING ||
            statusCode == BatteryManager.BATTERY_STATUS_NOT_CHARGING
        return when {
            charging && value < 0 -> -value
            discharging && value > 0 -> -value
            else -> value
        }
    }

    private fun batteryStatusLabel(code: Int): String = context.getString(
        when (code) {
            BatteryManager.BATTERY_STATUS_CHARGING -> R.string.device_battery_status_charging
            BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.device_battery_status_discharging
            BatteryManager.BATTERY_STATUS_FULL -> R.string.device_battery_status_full
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.device_battery_status_not_charging
            else -> R.string.device_unknown
        }
    )

    private fun batteryHealthLabel(code: Int): String = context.getString(
        when (code) {
            BatteryManager.BATTERY_HEALTH_GOOD -> R.string.device_battery_health_good
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> R.string.device_battery_health_overheat
            BatteryManager.BATTERY_HEALTH_DEAD -> R.string.device_battery_health_dead
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> R.string.device_battery_health_over_voltage
            BatteryManager.BATTERY_HEALTH_COLD -> R.string.device_battery_health_cold
            else -> R.string.device_unknown
        }
    )

    private fun batteryPluggedLabel(code: Int): String = context.getString(
        when (code) {
            BatteryManager.BATTERY_PLUGGED_AC -> R.string.device_battery_plugged_ac
            BatteryManager.BATTERY_PLUGGED_USB -> R.string.device_battery_plugged_usb
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> R.string.device_battery_plugged_wireless
            else -> R.string.device_battery_plugged_unplugged
        }
    )

    // ---------------------------------------------------------------------
    // CPU
    // ---------------------------------------------------------------------

    /**
     * @param needCores read every core's clock speeds (up to 3 file reads per core — the most
     *   expensive part of a tick, so it is skipped when no pinned card shows clocks and the usage
     *   estimate isn't needed).
     * @param needTemps scan the thermal zones.
     */
    fun cpu(
        state: CpuSampleState,
        needCores: Boolean = true,
        needUsage: Boolean = true,
        needTemps: Boolean = true
    ): CpuInfo {
        val coreCount = Runtime.getRuntime().availableProcessors()

        val usage = if (needUsage) {
            readCpuUsage(state.prevTotal, state.prevIdle).also {
                state.prevTotal = it.total
                state.prevIdle = it.idle
            }
        } else null

        // /proc/stat is SELinux-blocked for apps on Android 8+; when it yields nothing we need the
        // per-core clocks for the frequency-based fallback estimate — but only then.
        val cores = if (needCores || (needUsage && usage?.percent == null)) {
            readCpuCores(coreCount)
        } else emptyList()

        val usagePercent = when {
            !needUsage -> 0
            usage?.percent != null -> usage.percent
            else -> estimateUsageFromFrequencies(cores)
        }

        return CpuInfo(
            soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Build.SOC_MODEL.takeIf { it.isNotBlank() } ?: Build.HARDWARE
            } else Build.HARDWARE,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(", "),
            coreCount = coreCount,
            cores = cores,
            usagePercent = usagePercent,
            temperatures = if (needTemps) readTemperatures() else emptyList()
        )
    }

    private data class CpuUsage(val percent: Int?, val total: Long, val idle: Long)

    private fun readCpuUsage(prevTotal: Long, prevIdle: Long): CpuUsage {
        return try {
            val parts = File("/proc/stat").useLines { lines ->
                lines.first { it.startsWith("cpu ") }
            }.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }

            val idle = parts.getOrElse(3) { 0 } + parts.getOrElse(4) { 0 }
            val total = parts.sum()

            val totalDelta = total - prevTotal
            val idleDelta = idle - prevIdle
            val percent = if (prevTotal > 0 && totalDelta > 0) {
                (((totalDelta - idleDelta).toFloat() / totalDelta) * 100).roundToInt()
                    .coerceIn(0, 100)
            } else 0
            CpuUsage(percent, total, idle)
        } catch (e: Exception) {
            CpuUsage(null, prevTotal, prevIdle)
        }
    }

    /**
     * Approximates overall CPU load from the per-core clock speeds, used when `/proc/stat` is not
     * readable. Each online core contributes `(current - min) / (max - min)`; cores that are offline
     * or report no frequency count as idle (0%). This is an estimate, not a true scheduler-time
     * measurement, but it tracks load well enough for a live readout on modern Android.
     */
    private fun estimateUsageFromFrequencies(cores: List<CpuCore>): Int {
        if (cores.isEmpty()) return 0
        var sum = 0f
        for (core in cores) {
            val range = core.maxFreqKhz - core.minFreqKhz
            sum += if (range > 0 && core.currentFreqKhz > 0) {
                ((core.currentFreqKhz - core.minFreqKhz).toFloat() / range).coerceIn(0f, 1f)
            } else 0f
        }
        return ((sum / cores.size) * 100).roundToInt().coerceIn(0, 100)
    }

    private fun readCpuCores(coreCount: Int): List<CpuCore> = (0 until coreCount).map { i ->
        val base = "/sys/devices/system/cpu/cpu$i/cpufreq"
        CpuCore(
            index = i,
            currentFreqKhz = readLongFile("$base/scaling_cur_freq"),
            minFreqKhz = readLongFile("$base/cpuinfo_min_freq"),
            maxFreqKhz = readLongFile("$base/cpuinfo_max_freq")
        )
    }

    private fun readLongFile(path: String): Long = try {
        File(path).readText().trim().toLong()
    } catch (e: Exception) {
        -1L
    }

    private fun readTemperatures(): List<ThermalReading> {
        val readings = mutableListOf<ThermalReading>()
        try {
            val zones = File("/sys/class/thermal").listFiles { f ->
                f.name.startsWith("thermal_zone")
            } ?: emptyArray()
            for (zone in zones) {
                val raw = runCatching { File(zone, "temp").readText().trim().toFloat() }
                    .getOrNull() ?: continue
                // Values are typically in milli-degrees Celsius; some report degrees directly.
                val celsius = if (raw > 1000) raw / 1000f else raw
                if (celsius <= 0f || celsius > 200f) continue
                val type = runCatching { File(zone, "type").readText().trim() }
                    .getOrNull()?.takeIf { it.isNotBlank() } ?: zone.name
                readings.add(ThermalReading(type, celsius))
            }
        } catch (e: Exception) {
            // SELinux can block access on some devices; battery temp is provided separately.
        }
        return readings
    }

    // ---------------------------------------------------------------------
    // Memory
    // ---------------------------------------------------------------------

    /**
     * @param needRam query [ActivityManager] for RAM figures.
     * @param needStorage stat the data partition. The notification service skips whichever half
     *   no pinned card displays.
     */
    fun memory(needRam: Boolean = true, needStorage: Boolean = true): MemoryInfo {
        var totalRam = 0L
        var availableRam = 0L
        var lowMemory = false
        if (needRam) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            totalRam = mi.totalMem
            availableRam = mi.availMem
            lowMemory = mi.lowMemory
        }

        var totalStorage = 0L
        var availableStorage = 0L
        if (needStorage) {
            val stat = StatFs(Environment.getDataDirectory().path)
            totalStorage = stat.blockCountLong * stat.blockSizeLong
            availableStorage = stat.availableBlocksLong * stat.blockSizeLong
        }

        return MemoryInfo(
            totalRamBytes = totalRam,
            availableRamBytes = availableRam,
            lowMemory = lowMemory,
            totalStorageBytes = totalStorage,
            availableStorageBytes = availableStorage
        )
    }

    // ---------------------------------------------------------------------
    // Sensors
    // ---------------------------------------------------------------------

    fun formatSensorValues(values: FloatArray): String =
        values.take(3).joinToString("   ") { String.format("%.2f", it) }

    fun sensorTypeLabel(type: Int): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer (m/s²)"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope (rad/s)"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer (µT)"
        Sensor.TYPE_LIGHT -> "Light (lx)"
        Sensor.TYPE_PROXIMITY -> "Proximity (cm)"
        Sensor.TYPE_PRESSURE -> "Pressure (hPa)"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature (°C)"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative humidity (%)"
        Sensor.TYPE_GRAVITY -> "Gravity (m/s²)"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration (m/s²)"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        Sensor.TYPE_STEP_COUNTER -> "Step counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step detector"
        else -> context.getString(R.string.device_sensor_other)
    }

    companion object {
        const val CURRENT_SMOOTHING_WINDOW = 5

        // Kernel power-supply nodes that some OEMs expose when the public API returns nothing.
        val CURRENT_NOW_SYSFS_PATHS = listOf(
            "/sys/class/power_supply/battery/current_now",
            "/sys/class/power_supply/battery/charging_current",
            "/sys/class/power_supply/battery/amperage",
            "/sys/data/battery/current_now"
        )
    }
}
