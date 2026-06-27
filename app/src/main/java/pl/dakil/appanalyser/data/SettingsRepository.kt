package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dakil.appanalyser.domain.TemperatureUnit

/**
 * App-wide user settings, persisted in SharedPreferences and exposed as reactive state so any
 * screen observing them updates immediately when a value changes.
 */
class SettingsRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _temperatureUnit = MutableStateFlow(
        TemperatureUnit.fromName(prefs.getString(KEY_TEMPERATURE_UNIT, null))
    )
    val temperatureUnit: StateFlow<TemperatureUnit> = _temperatureUnit.asStateFlow()

    private val _simpleSensorView = MutableStateFlow(prefs.getBoolean(KEY_SIMPLE_SENSOR_VIEW, false))
    val simpleSensorView: StateFlow<Boolean> = _simpleSensorView.asStateFlow()

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMPERATURE_UNIT, unit.name).apply()
        _temperatureUnit.value = unit
    }

    fun setSimpleSensorView(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMPLE_SENSOR_VIEW, enabled).apply()
        _simpleSensorView.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        private const val KEY_SIMPLE_SENSOR_VIEW = "simple_sensor_view"

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
