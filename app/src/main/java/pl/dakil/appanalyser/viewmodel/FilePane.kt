package pl.dakil.appanalyser.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.data.FileSystemRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.domain.StorageRoot
import java.io.File

/**
 * One browser of the Files tab: where it is, what's in it and what's selected. Single pane mode
 * shows only the [PaneId.PRIMARY] one; dual pane mode adds a second, fully independent instance.
 * Everything shared between the two (clipboard, busy state, messages) stays in the ViewModel.
 */
class FilePane(
    val id: PaneId,
    private val scope: CoroutineScope,
    private val repository: FileSystemRepository,
    private val settings: SettingsRepository,
    private val roots: StateFlow<List<StorageRoot>>,
) {
    /** Null while sitting on the volume chooser at the top level. */
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    fun refresh() {
        val path = _currentPath.value ?: return
        scope.launch {
            _isLoading.value = true
            val listed = repository.list(
                path = path,
                showHidden = settings.showHiddenFiles.value,
                sort = settings.fileSortOrder.value,
                descending = settings.fileSortDescending.value,
            )
            // A navigation that landed while this listing ran owns the pane now.
            if (_currentPath.value != path) return@launch
            _entries.value = listed
            // Drop selection entries that no longer exist after a delete or move.
            val present = listed.mapTo(mutableSetOf()) { it.path }
            _selection.value = _selection.value.intersect(present)
            _isLoading.value = false
        }
    }

    fun open(path: String) {
        _currentPath.value = path
        _selection.value = emptySet()
        refresh()
    }

    /** Steps out of the current folder, stopping at the volume chooser. Returns false at the top. */
    fun navigateUp(): Boolean {
        val path = _currentPath.value ?: return false
        if (roots.value.any { it.path == path }) {
            _currentPath.value = null
            _entries.value = emptyList()
            _selection.value = emptySet()
            _isLoading.value = false
            return true
        }
        val parent = File(path).parentFile?.absolutePath ?: return false
        open(parent)
        return true
    }

    /** Trades location, listing and selection with [other], for the dual pane "Swap panes". */
    fun swapWith(other: FilePane) {
        val path = _currentPath.value
        val entries = _entries.value
        val selection = _selection.value
        _currentPath.value = other._currentPath.value
        _entries.value = other._entries.value
        _selection.value = other._selection.value
        other._currentPath.value = path
        other._entries.value = entries
        other._selection.value = selection
        refresh()
        other.refresh()
    }

    // -----------------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------------

    fun toggleSelection(path: String) {
        _selection.value = _selection.value.let { if (path in it) it - path else it + path }
    }

    fun select(path: String) {
        _selection.value = _selection.value + path
    }

    fun selectAll() {
        _selection.value = _entries.value.mapTo(mutableSetOf()) { it.path }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }
}
