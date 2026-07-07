package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import pl.dakil.appanalyser.data.DeviceInfoRepository
import pl.dakil.appanalyser.data.HomeLayoutRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.BatteryInfo
import pl.dakil.appanalyser.domain.CpuInfo
import pl.dakil.appanalyser.domain.DisplayInfo
import pl.dakil.appanalyser.domain.HomeWidget
import pl.dakil.appanalyser.domain.MemoryInfo
import pl.dakil.appanalyser.domain.SensorInfo
import pl.dakil.appanalyser.domain.SystemInfo
import pl.dakil.appanalyser.domain.TemperatureUnit

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceInfoRepository(application)
    private val settings = SettingsRepository.get(application)
    private val layout = HomeLayoutRepository.get(application)

    val systemInfo: SystemInfo = repository.getSystemInfo()
    val displayInfo: DisplayInfo = repository.getDisplayInfo()

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnit
    val simpleSensorView: StateFlow<Boolean> = settings.simpleSensorView
    val homeColumns: StateFlow<Int> = settings.homeColumns

    val widgets: StateFlow<List<HomeWidget>> = layout.widgets

    val battery: StateFlow<BatteryInfo?> = repository.batteryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cpu: StateFlow<CpuInfo?> = repository.cpuFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val sensors: StateFlow<List<SensorInfo>> = repository.sensorsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memory: StateFlow<MemoryInfo?> = repository.memoryFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setWidgets(widgets: List<HomeWidget>) = layout.setWidgets(widgets)
    fun removeWidget(id: String) = layout.removeWidget(id)
    fun updateWidget(widget: HomeWidget) = layout.updateWidget(widget)
}
