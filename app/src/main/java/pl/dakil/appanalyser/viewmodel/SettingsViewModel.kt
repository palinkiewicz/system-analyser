package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow
import pl.dakil.appanalyser.data.AppColorTheme
import pl.dakil.appanalyser.data.DarkThemeOption
import pl.dakil.appanalyser.data.PinnedNotificationsRepository
import pl.dakil.appanalyser.data.ProgressStyle
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.data.StartTab
import pl.dakil.appanalyser.domain.DualPaneStyle
import pl.dakil.appanalyser.domain.FileCompactView
import pl.dakil.appanalyser.domain.FileDropAction
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
    val hapticsEnabled: StateFlow<Boolean> = settings.hapticsEnabled
    val startTab: StateFlow<StartTab> = settings.startTab
    val animatedNumbers: StateFlow<Boolean> = settings.animatedNumbers
    val decorativeAnimations: StateFlow<Boolean> = settings.decorativeAnimations
    val progressStyle: StateFlow<ProgressStyle> = settings.progressStyle
    val homeColumns: StateFlow<Int> = settings.homeColumns
    val notificationIntervalSeconds: StateFlow<Int> = settings.notificationIntervalSeconds
    val pinnedCards: StateFlow<List<PinnedCard>> = pinnedNotifications.pinned
    val showHiddenFiles: StateFlow<Boolean> = settings.showHiddenFiles
    val confirmFileDeletion: StateFlow<Boolean> = settings.confirmFileDeletion
    val fileCompactView: StateFlow<FileCompactView> = settings.fileCompactView
    val fileDropAction: StateFlow<FileDropAction> = settings.fileDropAction
    val dualPaneStyle: StateFlow<DualPaneStyle> = settings.dualPaneStyle
    val confirmFileTransfer: StateFlow<Boolean> = settings.confirmFileTransfer
    val filesTransferDialog: StateFlow<Boolean> = settings.filesTransferDialog
    val editorMonospace: StateFlow<Boolean> = settings.editorMonospace
    val editorWordWrap: StateFlow<Boolean> = settings.editorWordWrap
    val editorSyntaxHighlighting: StateFlow<Boolean> = settings.editorSyntaxHighlighting
    val shareAccessMode: StateFlow<ShareAccessMode> = settings.shareAccessMode
    val shareIdleTimeoutMinutes: StateFlow<Int> = settings.shareIdleTimeoutMinutes
    val diskScanMaxAgeHours: StateFlow<Int> = settings.diskScanMaxAgeHours

    fun setTemperatureUnit(unit: TemperatureUnit) = settings.setTemperatureUnit(unit)
    fun setSimpleSensorView(enabled: Boolean) = settings.setSimpleSensorView(enabled)
    fun setShowSensorUnits(enabled: Boolean) = settings.setShowSensorUnits(enabled)
    fun setColorTheme(theme: AppColorTheme) = settings.setColorTheme(theme)
    fun setDarkThemeOption(option: DarkThemeOption) = settings.setDarkThemeOption(option)
    fun setPureBlack(enabled: Boolean) = settings.setPureBlack(enabled)
    fun setHapticsEnabled(enabled: Boolean) = settings.setHapticsEnabled(enabled)
    fun setStartTab(tab: StartTab) = settings.setStartTab(tab)
    fun setAnimatedNumbers(enabled: Boolean) = settings.setAnimatedNumbers(enabled)
    fun setDecorativeAnimations(enabled: Boolean) = settings.setDecorativeAnimations(enabled)
    fun setProgressStyle(style: ProgressStyle) = settings.setProgressStyle(style)
    fun setHomeColumns(columns: Int) = settings.setHomeColumns(columns)
    fun setNotificationIntervalSeconds(seconds: Int) =
        settings.setNotificationIntervalSeconds(seconds)

    fun setShowHiddenFiles(enabled: Boolean) = settings.setShowHiddenFiles(enabled)
    fun setConfirmFileDeletion(enabled: Boolean) = settings.setConfirmFileDeletion(enabled)
    fun setFileCompactView(mode: FileCompactView) = settings.setFileCompactView(mode)
    fun setFileDropAction(action: FileDropAction) = settings.setFileDropAction(action)
    fun setDualPaneStyle(style: DualPaneStyle) = settings.setDualPaneStyle(style)
    fun setConfirmFileTransfer(enabled: Boolean) = settings.setConfirmFileTransfer(enabled)
    fun setFilesTransferDialog(enabled: Boolean) = settings.setFilesTransferDialog(enabled)
    fun setEditorMonospace(enabled: Boolean) = settings.setEditorMonospace(enabled)
    fun setEditorWordWrap(enabled: Boolean) = settings.setEditorWordWrap(enabled)
    fun setEditorSyntaxHighlighting(enabled: Boolean) = settings.setEditorSyntaxHighlighting(enabled)

    fun setShareAccessMode(mode: ShareAccessMode) = settings.setShareAccessMode(mode)
    fun setShareIdleTimeoutMinutes(minutes: Int) = settings.setShareIdleTimeoutMinutes(minutes)
    fun setDiskScanMaxAgeHours(hours: Int) = settings.setDiskScanMaxAgeHours(hours)

    fun unpinCard(id: String) = PinnedNotificationsManager.unpin(getApplication(), id)
}
