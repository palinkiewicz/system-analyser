package pl.dakil.appanalyser.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.dakil.appanalyser.domain.FileSortOrder
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

    /** How often the pinned-card notifications re-sample and re-render, in seconds. */
    private val _notificationIntervalSeconds = MutableStateFlow(
        prefs.getInt(KEY_NOTIFICATION_INTERVAL, DEFAULT_NOTIFICATION_INTERVAL)
            .coerceIn(MIN_NOTIFICATION_INTERVAL, MAX_NOTIFICATION_INTERVAL)
    )
    val notificationIntervalSeconds: StateFlow<Int> = _notificationIntervalSeconds.asStateFlow()

    /** Master switch for the experimental File system tab. */
    private val _fileSystemEnabled = MutableStateFlow(prefs.getBoolean(KEY_FILE_SYSTEM_ENABLED, false))
    val fileSystemEnabled: StateFlow<Boolean> = _fileSystemEnabled.asStateFlow()

    private val _fileSortOrder = MutableStateFlow(
        runCatching { FileSortOrder.valueOf(prefs.getString(KEY_FILE_SORT_ORDER, null)!!) }
            .getOrDefault(FileSortOrder.NAME)
    )
    val fileSortOrder: StateFlow<FileSortOrder> = _fileSortOrder.asStateFlow()

    private val _fileSortDescending = MutableStateFlow(prefs.getBoolean(KEY_FILE_SORT_DESCENDING, false))
    val fileSortDescending: StateFlow<Boolean> = _fileSortDescending.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean(KEY_SHOW_HIDDEN_FILES, false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _confirmFileDeletion = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_FILE_DELETION, true))
    val confirmFileDeletion: StateFlow<Boolean> = _confirmFileDeletion.asStateFlow()

    private val _editorMonospace = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_MONOSPACE, true))
    val editorMonospace: StateFlow<Boolean> = _editorMonospace.asStateFlow()

    private val _editorWordWrap = MutableStateFlow(prefs.getBoolean(KEY_EDITOR_WORD_WRAP, true))
    val editorWordWrap: StateFlow<Boolean> = _editorWordWrap.asStateFlow()

    private val _editorSyntaxHighlighting =
        MutableStateFlow(prefs.getBoolean(KEY_EDITOR_SYNTAX_HIGHLIGHTING, true))
    val editorSyntaxHighlighting: StateFlow<Boolean> = _editorSyntaxHighlighting.asStateFlow()

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

    fun setNotificationIntervalSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(MIN_NOTIFICATION_INTERVAL, MAX_NOTIFICATION_INTERVAL)
        prefs.edit().putInt(KEY_NOTIFICATION_INTERVAL, clamped).apply()
        _notificationIntervalSeconds.value = clamped
    }

    fun setFileSystemEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FILE_SYSTEM_ENABLED, enabled).apply()
        _fileSystemEnabled.value = enabled
    }

    fun setFileSortOrder(order: FileSortOrder) {
        prefs.edit().putString(KEY_FILE_SORT_ORDER, order.name).apply()
        _fileSortOrder.value = order
    }

    fun setFileSortDescending(descending: Boolean) {
        prefs.edit().putBoolean(KEY_FILE_SORT_DESCENDING, descending).apply()
        _fileSortDescending.value = descending
    }

    fun setShowHiddenFiles(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN_FILES, enabled).apply()
        _showHiddenFiles.value = enabled
    }

    fun setConfirmFileDeletion(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_FILE_DELETION, enabled).apply()
        _confirmFileDeletion.value = enabled
    }

    fun setEditorMonospace(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_MONOSPACE, enabled).apply()
        _editorMonospace.value = enabled
    }

    fun setEditorWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_WORD_WRAP, enabled).apply()
        _editorWordWrap.value = enabled
    }

    fun setEditorSyntaxHighlighting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EDITOR_SYNTAX_HIGHLIGHTING, enabled).apply()
        _editorSyntaxHighlighting.value = enabled
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
        private const val KEY_NOTIFICATION_INTERVAL = "notification_interval_seconds"
        private const val KEY_FILE_SYSTEM_ENABLED = "file_system_enabled"
        private const val KEY_FILE_SORT_ORDER = "file_sort_order"
        private const val KEY_FILE_SORT_DESCENDING = "file_sort_descending"
        private const val KEY_SHOW_HIDDEN_FILES = "show_hidden_files"
        private const val KEY_CONFIRM_FILE_DELETION = "confirm_file_deletion"
        private const val KEY_EDITOR_MONOSPACE = "editor_monospace"
        private const val KEY_EDITOR_WORD_WRAP = "editor_word_wrap"
        private const val KEY_EDITOR_SYNTAX_HIGHLIGHTING = "editor_syntax_highlighting"

        const val MIN_HOME_COLUMNS = 1
        const val MAX_HOME_COLUMNS = 5
        const val DEFAULT_HOME_COLUMNS = 2

        const val MIN_NOTIFICATION_INTERVAL = 1
        const val MAX_NOTIFICATION_INTERVAL = 15
        const val DEFAULT_NOTIFICATION_INTERVAL = 3

        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}
