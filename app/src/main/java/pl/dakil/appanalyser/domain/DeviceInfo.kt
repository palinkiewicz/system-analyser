package pl.dakil.appanalyser.domain

data class SystemInfo(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val apiLevel: Int,
    val securityPatch: String,
    val buildId: String,
    val kernelVersion: String,
    val board: String,
    val hardware: String
)

data class ThermalReading(
    val name: String,
    val celsius: Float
)

data class CpuCore(
    val index: Int,
    val currentFreqKhz: Long,
    val minFreqKhz: Long,
    val maxFreqKhz: Long
)

data class CpuInfo(
    val soc: String,
    val supportedAbis: String,
    val coreCount: Int,
    val cores: List<CpuCore>,
    val usagePercent: Int,
    val temperatures: List<ThermalReading>
)

data class BatteryInfo(
    val levelPercent: Int,
    val status: String,
    val health: String,
    val technology: String,
    val plugged: String,
    val voltageMillivolts: Int,
    val temperatureCelsius: Float,
    /** Instantaneous current in mA. Positive = charging, negative = discharging. */
    val currentNowMilliamps: Int?,
    /** Lowest current in mA observed since the screen was opened. */
    val sessionMinCurrentMilliamps: Int?,
    /** Highest current in mA observed since the screen was opened. */
    val sessionMaxCurrentMilliamps: Int?,
    /** Remaining charge in mAh, or null if not reported. */
    val chargeCounterMilliampHours: Int?,
    /** Derived instantaneous power in watts (voltage x current). */
    val powerWatts: Float?,
    /** Battery charge cycle count, or null if not reported by the device. */
    val cycleCount: Int?
)

data class MemoryInfo(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long
) {
    val usedRamBytes: Long get() = totalRamBytes - availableRamBytes
    val usedStorageBytes: Long get() = totalStorageBytes - availableStorageBytes
}

data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val refreshRateHz: Float,
    val diagonalInches: Float
)

data class SensorInfo(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeName: String,
    val values: String
)
