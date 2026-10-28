package pl.dakil.appanalyser.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.data.AppAnalyzerRepository
import pl.dakil.appanalyser.domain.AppDetails
import pl.dakil.appanalyser.domain.AppInfo
import java.io.File

class AppAnalyzerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppAnalyzerRepository(application)

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    val appsList: StateFlow<List<AppInfo>> = combine(
        _installedApps,
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAppDetails = MutableStateFlow<AppDetails?>(null)
    val selectedAppDetails: StateFlow<AppDetails?> = _selectedAppDetails

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _installedApps.value = repository.getInstalledApps(_showSystemApps.value)
            _isLoading.value = false
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleSystemApps(show: Boolean) {
        _showSystemApps.value = show
        loadApps()
    }

    fun analyzeApp(packageName: String) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _selectedAppDetails.value = null
            val details = repository.analyzeApp(packageName)
            _selectedAppDetails.value = details
            _isAnalyzing.value = false
        }
    }
    
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    /**
     * Writes the currently selected app's APK to [destination] (a document URI picked by the user)
     * and reports success through [onResult].
     */
    fun exportApk(destination: Uri, onResult: (Boolean) -> Unit) {
        val apkPath = _selectedAppDetails.value?.apkPath
        if (apkPath == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            val success = repository.exportApk(apkPath, destination)
            _isExporting.value = false
            onResult(success)
        }
    }

    /**
     * Stages the selected app's APK as [fileName] in the cache and hands a shareable
     * `content://` URI to [onReady] (null if the copy failed).
     */
    fun shareApk(fileName: String, onReady: (Uri?) -> Unit) {
        val apkPath = _selectedAppDetails.value?.apkPath
        if (apkPath == null) {
            onReady(null)
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            val uri = repository.createShareableApk(apkPath, fileName)
            _isExporting.value = false
            onReady(uri)
        }
    }

    /**
     * Stages the selected app's APK as [fileName] for the share server and hands the staged file to
     * [onReady] (null if the copy failed).
     */
    fun stageApkForNetworkShare(fileName: String, onReady: (File?) -> Unit) {
        val apkPath = _selectedAppDetails.value?.apkPath
        if (apkPath == null) {
            onReady(null)
            return
        }
        viewModelScope.launch {
            _isExporting.value = true
            val file = repository.createNetworkShareableApk(apkPath, fileName)
            _isExporting.value = false
            onReady(file)
        }
    }

    fun clearSelectedApp() {
        _selectedAppDetails.value = null
    }
}
