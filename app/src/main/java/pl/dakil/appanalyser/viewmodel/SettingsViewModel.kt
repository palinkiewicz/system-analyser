package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import pl.dakil.appanalyser.data.AppColorTheme
import pl.dakil.appanalyser.data.DarkThemeOption
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.TemperatureUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository.get(application)

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnit
    val simpleSensorView: StateFlow<Boolean> = settings.simpleSensorView
    val colorTheme: StateFlow<AppColorTheme> = settings.colorTheme
    val darkThemeOption: StateFlow<DarkThemeOption> = settings.darkThemeOption
    val pureBlack: StateFlow<Boolean> = settings.pureBlack
    val homeColumns: StateFlow<Int> = settings.homeColumns

    fun setTemperatureUnit(unit: TemperatureUnit) = settings.setTemperatureUnit(unit)
    fun setSimpleSensorView(enabled: Boolean) = settings.setSimpleSensorView(enabled)
    fun setColorTheme(theme: AppColorTheme) = settings.setColorTheme(theme)
    fun setDarkThemeOption(option: DarkThemeOption) = settings.setDarkThemeOption(option)
    fun setPureBlack(enabled: Boolean) = settings.setPureBlack(enabled)
    fun setHomeColumns(columns: Int) = settings.setHomeColumns(columns)
}
