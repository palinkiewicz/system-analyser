package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.HomeWidgetType
import java.util.UUID

/**
 * Persists the Home screen widget grid (which cards, their order and spans) in SharedPreferences
 * as a JSON array. Exposed as reactive state so the Home screen updates immediately when a card
 * is added from the Device Info screen.
 */
class HomeLayoutRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _widgets = MutableStateFlow(load())
    val widgets: StateFlow<List<HomeWidget>> = _widgets.asStateFlow()

    fun setWidgets(widgets: List<HomeWidget>) {
        prefs.edit().putString(KEY_HOME_WIDGETS, toJson(widgets)).apply()
        _widgets.value = widgets
    }

    fun addWidget(widget: HomeWidget) = setWidgets(_widgets.value + widget)

    fun removeWidget(id: String) = setWidgets(_widgets.value.filterNot { it.id == id })

    fun updateWidget(widget: HomeWidget) =
        setWidgets(_widgets.value.map { if (it.id == widget.id) widget else it })

    private fun load(): List<HomeWidget> {
        val json = prefs.getString(KEY_HOME_WIDGETS, null) ?: return defaultWidgets()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    // Skip entries whose type is unknown (e.g. written by a newer app version).
                    val type = runCatching {
                        HomeWidgetType.valueOf(obj.getString(FIELD_TYPE))
                    }.getOrNull() ?: continue
                    add(
                        HomeWidget(
                            id = obj.optString(FIELD_ID, UUID.randomUUID().toString()),
                            type = type,
                            sensorType = if (obj.has(FIELD_SENSOR_TYPE)) obj.getInt(FIELD_SENSOR_TYPE) else null,
                            sensorName = if (obj.has(FIELD_SENSOR_NAME)) obj.getString(FIELD_SENSOR_NAME) else null,
                            columnSpan = obj.optInt(FIELD_COLS, 1).coerceAtLeast(1),
                            rowSpan = obj.optInt(FIELD_ROWS, 1).coerceIn(1, HomeWidget.MAX_ROW_SPAN),
                        )
                    )
                }
            }
        }.getOrDefault(defaultWidgets())
    }

    private fun toJson(widgets: List<HomeWidget>): String {
        val array = JSONArray()
        widgets.forEach { widget ->
            array.put(
                JSONObject().apply {
                    put(FIELD_ID, widget.id)
                    put(FIELD_TYPE, widget.type.name)
                    widget.sensorType?.let { put(FIELD_SENSOR_TYPE, it) }
                    widget.sensorName?.let { put(FIELD_SENSOR_NAME, it) }
                    put(FIELD_COLS, widget.columnSpan)
                    put(FIELD_ROWS, widget.rowSpan)
                }
            )
        }
        return array.toString()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_HOME_WIDGETS = "home_widgets"

        private const val FIELD_ID = "id"
        private const val FIELD_TYPE = "type"
        private const val FIELD_SENSOR_TYPE = "sensorType"
        private const val FIELD_SENSOR_NAME = "sensorName"
        private const val FIELD_COLS = "cols"
        private const val FIELD_ROWS = "rows"

        /**
         * First-fit packing at 2 columns lays these out as:
         * row 1: power usage full width; row 2: RAM + battery info; row 3: CPU usage + battery info.
         */
        private fun defaultWidgets() = listOf(
            HomeWidget(UUID.randomUUID().toString(), HomeWidgetType.BATTERY_POWER, columnSpan = 2, rowSpan = 1),
            HomeWidget(UUID.randomUUID().toString(), HomeWidgetType.RAM, columnSpan = 1, rowSpan = 1),
            HomeWidget(UUID.randomUUID().toString(), HomeWidgetType.BATTERY_INFO, columnSpan = 1, rowSpan = 2),
            HomeWidget(UUID.randomUUID().toString(), HomeWidgetType.CPU_USAGE, columnSpan = 1, rowSpan = 1),
        )

        @Volatile
        private var instance: HomeLayoutRepository? = null

        fun get(context: Context): HomeLayoutRepository =
            instance ?: synchronized(this) {
                instance ?: HomeLayoutRepository(context.applicationContext).also { instance = it }
            }
    }
}
