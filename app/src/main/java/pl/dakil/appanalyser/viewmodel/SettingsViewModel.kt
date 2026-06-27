package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.TemperatureUnit

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository.get(application)

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnit
    val simpleSensorView: StateFlow<Boolean> = settings.simpleSensorView

    fun setTemperatureUnit(unit: TemperatureUnit) = settings.setTemperatureUnit(unit)
    fun setSimpleSensorView(enabled: Boolean) = settings.setSimpleSensorView(enabled)
}
