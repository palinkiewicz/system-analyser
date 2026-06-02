package pl.dakil.appanalyser.viewmodel

import android.app.Application
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
    
    fun clearSelectedApp() {
        _selectedAppDetails.value = null
    }
}
