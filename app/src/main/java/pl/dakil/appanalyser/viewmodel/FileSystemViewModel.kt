package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.FileSystemRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.FileClipboard
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.StorageRoot
import java.io.File

/**
 * State for the File system browser: where we are, what's in it, what's selected and what's on the
 * clipboard. Scoped to the tab's back stack entry, so the current folder survives tab switches.
 */
class FileSystemViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileSystemRepository(application)
    private val settings = SettingsRepository.get(application)

    /** Null while sitting on the volume chooser at the top level. */
    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath

    private val _entries = MutableStateFlow<List<FileEntry>>(emptyList())
    val entries: StateFlow<List<FileEntry>> = _entries

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    private val _clipboard = MutableStateFlow<FileClipboard?>(null)
    val clipboard: StateFlow<FileClipboard?> = _clipboard

    /**
     * Whether All files access is currently granted. Only ever refreshed by the screen itself
     * (on entry and on resume) — nothing here triggers a permission request.
     */
    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    /** One-shot string resources for the snackbar. */
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages

    val sortOrder: StateFlow<FileSortOrder> = settings.fileSortOrder
    val sortDescending: StateFlow<Boolean> = settings.fileSortDescending
    val showHidden: StateFlow<Boolean> = settings.showHiddenFiles
    val confirmDeletion: StateFlow<Boolean> = settings.confirmFileDeletion

    /** Re-checks the permission and loads whatever the current location is. */
    fun refreshAccess() {
        val granted = repository.hasAccess()
        val changed = _hasAccess.value != granted
        _hasAccess.value = granted
        if (granted && (changed || _roots.value.isEmpty())) {
            viewModelScope.launch {
                _roots.value = repository.storageRoots()
                refresh()
            }
        }
    }

    fun refresh() {
        val path = _currentPath.value ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _entries.value = repository.list(
                path = path,
                showHidden = showHidden.value,
                sort = sortOrder.value,
                descending = sortDescending.value,
            )
            // Drop selection entries that no longer exist after a delete or move.
            val present = _entries.value.mapTo(mutableSetOf()) { it.path }
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
        if (_roots.value.any { it.path == path }) {
            _currentPath.value = null
            _entries.value = emptyList()
            _selection.value = emptySet()
            return true
        }
        val parent = File(path).parentFile?.absolutePath ?: return false
        open(parent)
        return true
    }

    // -----------------------------------------------------------------------
    // Selection
    // -----------------------------------------------------------------------

    fun toggleSelection(path: String) {
        _selection.value = _selection.value.let { if (path in it) it - path else it + path }
    }

    fun selectAll() {
        _selection.value = _entries.value.mapTo(mutableSetOf()) { it.path }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    // -----------------------------------------------------------------------
    // Clipboard
    // -----------------------------------------------------------------------

    fun copySelection() {
        if (_selection.value.isEmpty()) return
        _clipboard.value = FileClipboard(_selection.value.toList(), ClipboardOperation.COPY)
        clearSelection()
    }

    fun cutSelection() {
        if (_selection.value.isEmpty()) return
        _clipboard.value = FileClipboard(_selection.value.toList(), ClipboardOperation.MOVE)
        clearSelection()
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun paste() {
        val clip = _clipboard.value ?: return
        val destination = _currentPath.value ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val ok = when (clip.operation) {
                ClipboardOperation.COPY -> repository.copy(clip.paths, destination)
                ClipboardOperation.MOVE -> repository.move(clip.paths, destination)
            }
            _isBusy.value = false
            _clipboard.value = null
            _messages.tryEmit(if (ok) R.string.files_paste_success else R.string.files_paste_failed)
            refresh()
        }
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    fun createFolder(name: String) {
        val parent = _currentPath.value ?: return
        viewModelScope.launch {
            val ok = repository.createFolder(parent, name)
            _messages.tryEmit(if (ok) R.string.files_create_success else R.string.files_create_failed)
            refresh()
        }
    }

    fun createFile(name: String) {
        val parent = _currentPath.value ?: return
        viewModelScope.launch {
            val ok = repository.createFile(parent, name)
            _messages.tryEmit(if (ok) R.string.files_create_success else R.string.files_create_failed)
            refresh()
        }
    }

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            val ok = repository.rename(path, newName)
            _messages.tryEmit(if (ok) R.string.files_rename_success else R.string.files_rename_failed)
            clearSelection()
            refresh()
        }
    }

    fun deleteSelection() {
        val paths = _selection.value.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val ok = repository.delete(paths)
            _isBusy.value = false
            _messages.tryEmit(if (ok) R.string.files_delete_success else R.string.files_delete_failed)
            clearSelection()
            refresh()
        }
    }

    // -----------------------------------------------------------------------
    // Settings pass-through (the browser owns sort/hidden, the settings screen the rest)
    // -----------------------------------------------------------------------

    fun setSortOrder(order: FileSortOrder) {
        settings.setFileSortOrder(order)
        refresh()
    }

    fun setSortDescending(descending: Boolean) {
        settings.setFileSortDescending(descending)
        refresh()
    }

    fun setShowHidden(enabled: Boolean) {
        settings.setShowHiddenFiles(enabled)
        refresh()
    }

    // -----------------------------------------------------------------------
    // Helpers used by the screen
    // -----------------------------------------------------------------------

    fun contentUri(path: String) = repository.contentUri(path)

    fun mimeType(path: String) = repository.mimeType(path)

    suspend fun directorySize(path: String) = repository.directorySize(path)

    suspend fun childCount(path: String) = repository.childCount(path)
}
