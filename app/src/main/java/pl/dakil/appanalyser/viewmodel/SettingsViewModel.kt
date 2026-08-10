package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import pl.dakil.appanalyser.data.AppColorTheme
import pl.dakil.appanalyser.data.DarkThemeOption
import pl.dakil.appanalyser.data.PinnedNotificationsRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.PinnedCard
import pl.dakil.appanalyser.domain.ShareAccessMode
import pl.dakil.appanalyser.domain.TemperatureUnit
import pl.dakil.appanalyser.service.PinnedNotificationsManager

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SettingsRepository.get(application)
    private val pinnedNotifications = PinnedNotificationsRepository.get(application)

    val temperatureUnit: StateFlow<TemperatureUnit> = settings.temperatureUnit
    val simpleSensorView: StateFlow<Boolean> = settings.simpleSensorView
    val showSensorUnits: StateFlow<Boolean> = settings.showSensorUnits
    val colorTheme: StateFlow<AppColorTheme> = settings.colorTheme
    val darkThemeOption: StateFlow<DarkThemeOption> = settings.darkThemeOption
    val pureBlack: StateFlow<Boolean> = settings.pureBlack
    val homeColumns: StateFlow<Int> = settings.homeColumns
    val notificationIntervalSeconds: StateFlow<Int> = settings.notificationIntervalSeconds
    val pinnedCards: StateFlow<List<PinnedCard>> = pinnedNotifications.pinned
    val fileSystemEnabled: StateFlow<Boolean> = settings.fileSystemEnabled
    val showHiddenFiles: StateFlow<Boolean> = settings.showHiddenFiles
    val confirmFileDeletion: StateFlow<Boolean> = settings.confirmFileDeletion
    val editorMonospace: StateFlow<Boolean> = settings.editorMonospace
    val editorWordWrap: StateFlow<Boolean> = settings.editorWordWrap
    val editorSyntaxHighlighting: StateFlow<Boolean> = settings.editorSyntaxHighlighting
    val shareAccessMode: StateFlow<ShareAccessMode> = settings.shareAccessMode
    val shareIdleTimeoutMinutes: StateFlow<Int> = settings.shareIdleTimeoutMinutes

    fun setTemperatureUnit(unit: TemperatureUnit) = settings.setTemperatureUnit(unit)
    fun setSimpleSensorView(enabled: Boolean) = settings.setSimpleSensorView(enabled)
    fun setShowSensorUnits(enabled: Boolean) = settings.setShowSensorUnits(enabled)
    fun setColorTheme(theme: AppColorTheme) = settings.setColorTheme(theme)
    fun setDarkThemeOption(option: DarkThemeOption) = settings.setDarkThemeOption(option)
    fun setPureBlack(enabled: Boolean) = settings.setPureBlack(enabled)
    fun setHomeColumns(columns: Int) = settings.setHomeColumns(columns)
    fun setNotificationIntervalSeconds(seconds: Int) =
        settings.setNotificationIntervalSeconds(seconds)

    fun setFileSystemEnabled(enabled: Boolean) = settings.setFileSystemEnabled(enabled)
    fun setShowHiddenFiles(enabled: Boolean) = settings.setShowHiddenFiles(enabled)
    fun setConfirmFileDeletion(enabled: Boolean) = settings.setConfirmFileDeletion(enabled)
    fun setEditorMonospace(enabled: Boolean) = settings.setEditorMonospace(enabled)
    fun setEditorWordWrap(enabled: Boolean) = settings.setEditorWordWrap(enabled)
    fun setEditorSyntaxHighlighting(enabled: Boolean) = settings.setEditorSyntaxHighlighting(enabled)

    fun setShareAccessMode(mode: ShareAccessMode) = settings.setShareAccessMode(mode)
    fun setShareIdleTimeoutMinutes(minutes: Int) = settings.setShareIdleTimeoutMinutes(minutes)

    fun unpinCard(id: String) = PinnedNotificationsManager.unpin(getApplication(), id)
}
