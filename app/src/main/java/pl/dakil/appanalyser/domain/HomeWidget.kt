package pl.dakil.appanalyser.domain

/** Every device-info card that can be pinned to the Home screen grid. */
enum class HomeWidgetType {
    SYSTEM_IDENTITY,
    SYSTEM_SOFTWARE,
    CPU_USAGE,
    CPU_CLOCKS,
    CPU_TEMPERATURES,
    BATTERY_POWER,
    BATTERY_INFO,
    RAM,
    STORAGE,
    DISPLAY,
    SENSOR,
}

/**
 * A card placed on the Home grid. The list order in the layout repository is the placement
 * order — cells are assigned by first-fit packing, so no x/y coordinates are stored.
 * Spans are clamped to the current column count at layout time without mutating stored data.
 */
data class HomeWidget(
    val id: String,
    val type: HomeWidgetType,
    /** [android.hardware.Sensor.getType] — only for [HomeWidgetType.SENSOR] widgets. */
    val sensorType: Int? = null,
    /** Sensor name: primary match key and fallback label when the sensor disappears. */
    val sensorName: String? = null,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
) {
    companion object {
        const val MAX_ROW_SPAN = 3
    }
}
