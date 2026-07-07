package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dakil.appanalyser.domain.TemperatureUnit

enum class AppColorTheme { DAKILS_ANALYSER, DYNAMIC, OCEAN, LAVENDER, SUNSET, ROSE, TEAL }

enum class DarkThemeOption { FOLLOW_SYSTEM, LIGHT, DARK }

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

    private val _showSensorUnits = MutableStateFlow(prefs.getBoolean(KEY_SHOW_SENSOR_UNITS, true))
    val showSensorUnits: StateFlow<Boolean> = _showSensorUnits.asStateFlow()

    private val _colorTheme = MutableStateFlow(
        runCatching { AppColorTheme.valueOf(prefs.getString(KEY_COLOR_THEME, null)!!) }
            .getOrDefault(AppColorTheme.DAKILS_ANALYSER)
    )
    val colorTheme: StateFlow<AppColorTheme> = _colorTheme.asStateFlow()

    private val _darkThemeOption = MutableStateFlow(
        runCatching { DarkThemeOption.valueOf(prefs.getString(KEY_DARK_THEME_OPTION, null)!!) }
            .getOrDefault(DarkThemeOption.FOLLOW_SYSTEM)
    )
    val darkThemeOption: StateFlow<DarkThemeOption> = _darkThemeOption.asStateFlow()

    private val _pureBlack = MutableStateFlow(prefs.getBoolean(KEY_PURE_BLACK, false))
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _homeColumns = MutableStateFlow(
        prefs.getInt(KEY_HOME_COLUMNS, DEFAULT_HOME_COLUMNS).coerceIn(MIN_HOME_COLUMNS, MAX_HOME_COLUMNS)
    )
    val homeColumns: StateFlow<Int> = _homeColumns.asStateFlow()

    fun setTemperatureUnit(unit: TemperatureUnit) {
        prefs.edit().putString(KEY_TEMPERATURE_UNIT, unit.name).apply()
        _temperatureUnit.value = unit
    }

    fun setSimpleSensorView(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIMPLE_SENSOR_VIEW, enabled).apply()
        _simpleSensorView.value = enabled
    }

    fun setShowSensorUnits(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SENSOR_UNITS, enabled).apply()
        _showSensorUnits.value = enabled
    }

    fun setColorTheme(theme: AppColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, theme.name).apply()
        _colorTheme.value = theme
    }

    fun setDarkThemeOption(option: DarkThemeOption) {
        prefs.edit().putString(KEY_DARK_THEME_OPTION, option.name).apply()
        _darkThemeOption.value = option
    }

    fun setPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PURE_BLACK, enabled).apply()
        _pureBlack.value = enabled
    }

    fun setHomeColumns(columns: Int) {
        val clamped = columns.coerceIn(MIN_HOME_COLUMNS, MAX_HOME_COLUMNS)
        prefs.edit().putInt(KEY_HOME_COLUMNS, clamped).apply()
        _homeColumns.value = clamped
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_TEMPERATURE_UNIT = "temperature_unit"
        private const val KEY_SIMPLE_SENSOR_VIEW = "simple_sensor_view"
        private const val KEY_SHOW_SENSOR_UNITS = "show_sensor_units"
        private const val KEY_COLOR_THEME = "color_theme"
        private const val KEY_DARK_THEME_OPTION = "dark_theme_option"
        private const val KEY_PURE_BLACK = "pure_black"
        private const val KEY_HOME_COLUMNS = "home_columns"

        const val MIN_HOME_COLUMNS = 1
        const val MAX_HOME_COLUMNS = 5
        const val DEFAULT_HOME_COLUMNS = 2

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
