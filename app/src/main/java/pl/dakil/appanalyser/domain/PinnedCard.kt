package pl.dakil.appanalyser.domain

/**
 * A device-info card pinned to the notification shade. Reuses [HomeWidgetType] so a card is
 * described the same way whether it lives on the Home grid or in a notification.
 *
 * [notificationId] is allocated once at pin time and persisted, so it stays stable across process
 * death — a cold-started receiver can cancel the right notification without rebuilding any state.
 */
data class PinnedCard(
    val id: String,
    val notificationId: Int,
    val type: HomeWidgetType,
    /** [android.hardware.Sensor.getType] — only for [HomeWidgetType.SENSOR] cards. */
    val sensorType: Int? = null,
    /** Sensor name: display label and fallback when the sensor disappears. */
    val sensorName: String? = null,
) {
    /** Identity for deduplication: one notification per card, per sensor for sensor cards. */
    val dedupeKey: String get() = dedupeKeyOf(type, sensorType)

    companion object {
        /** First id handed out; high enough not to collide with anything else the app posts. */
        const val FIRST_NOTIFICATION_ID = 2000

        fun dedupeKeyOf(type: HomeWidgetType, sensorType: Int?): String =
            if (type == HomeWidgetType.SENSOR) "SENSOR:$sensorType" else type.name
    }
}
