package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.DiskUsageRepository
import pl.dakil.appanalyser.data.FavoritesRepository
import pl.dakil.appanalyser.data.FileSystemRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.ClipboardOperation
import pl.dakil.appanalyser.domain.FileClipboard
import pl.dakil.appanalyser.domain.DualPaneStyle
import pl.dakil.appanalyser.domain.FileCompactView
import pl.dakil.appanalyser.domain.FileDropAction
import pl.dakil.appanalyser.domain.FileFavorite
import pl.dakil.appanalyser.domain.FileOperationProgress
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.PaneId
import pl.dakil.appanalyser.domain.StorageRoot
import java.io.File

/**
 * State for the File system browser: its one or two [FilePane]s, which of them is active, and what's
 * on the clipboard. Scoped to the tab's back stack entry, so both panes survive tab switches.
 *
 * Folder-level actions (create, paste, delete, rename, the selection bars) always act on the active
 * pane; in single pane mode that is the primary one.
 */
class FileSystemViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = FileSystemRepository(application)
    private val settings = SettingsRepository.get(application)
    private val diskUsage = DiskUsageRepository.get(application)
    private val favoritesRepository = FavoritesRepository.get(application)

    val favorites: StateFlow<List<FileFavorite>> = favoritesRepository.favorites

    /** Favourites whose target was gone the last time the home was shown. */
    private val _missingFavorites = MutableStateFlow<Set<String>>(emptySet())
    val missingFavorites: StateFlow<Set<String>> = _missingFavorites

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots

    val primary = FilePane(PaneId.PRIMARY, viewModelScope, repository, settings, _roots)
    val secondary = FilePane(PaneId.SECONDARY, viewModelScope, repository, settings, _roots)
    private val panes get() = listOf(primary, secondary)

    private val _activePaneId = MutableStateFlow(PaneId.PRIMARY)
    val activePaneId: StateFlow<PaneId> = _activePaneId

    val activePane: FilePane get() = pane(_activePaneId.value)

    fun pane(id: PaneId): FilePane = if (id == PaneId.PRIMARY) primary else secondary

    /** The running copy, move or delete, if any; only one runs at a time. */
    private val _operation = MutableStateFlow<FileOperationProgress?>(null)
    val operation: StateFlow<FileOperationProgress?> = _operation
    private var operationJob: Job? = null

    val isBusy: Boolean get() = operationJob?.isActive == true

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
    val dualPane: StateFlow<Boolean> = settings.filesDualPane
    val paneSplit: StateFlow<Float> = settings.filesPaneSplit
    val dropAction: StateFlow<FileDropAction> = settings.fileDropAction
    val confirmTransfer: StateFlow<Boolean> = settings.confirmFileTransfer
    val compactView: StateFlow<FileCompactView> = settings.fileCompactView
    val dualPaneStyle: StateFlow<DualPaneStyle> = settings.dualPaneStyle

    /** Re-checks the permission and loads whatever the current locations are. */
    fun refreshAccess() {
        val granted = repository.hasAccess()
        val changed = _hasAccess.value != granted
        _hasAccess.value = granted
        if (granted && (changed || _roots.value.isEmpty())) {
            viewModelScope.launch {
                _roots.value = repository.storageRoots()
                refreshAll()
            }
        }
    }

    private fun refreshAll() = panes.forEach { it.refresh() }

    /**
     * Every mutation lands here afterwards with the paths it touched; the disk usage scans covering
     * them can't know what changed, so they are flagged for the analyser to offer a rescan. Both
     * panes re-list, since either of them may be showing a touched folder.
     */
    private fun refreshAfterChange(changed: Collection<String>) {
        diskUsage.markStale(changed)
        refreshAll()
    }

    // -----------------------------------------------------------------------
    // Dual pane
    // -----------------------------------------------------------------------

    fun setDualPane(enabled: Boolean) {
        if (enabled == dualPane.value) return
        if (enabled) {
            // A first split starts both panes in the same folder, as Total Commander does.
            if (secondary.currentPath.value == null) primary.currentPath.value?.let { secondary.open(it) }
        } else {
            // Collapsing keeps the folder the user was working in, not whatever sat on top.
            if (_activePaneId.value == PaneId.SECONDARY) primary.swapWith(secondary)
            _activePaneId.value = PaneId.PRIMARY
            secondary.clearSelection()
        }
        settings.setFilesDualPane(enabled)
    }

    /** Leaves dual pane mode showing [keep]'s folder, whichever pane was active. */
    fun collapseTo(keep: PaneId) {
        _activePaneId.value = keep
        setDualPane(false)
    }

    fun setActivePane(id: PaneId) {
        _activePaneId.value = id
    }

    fun setPaneSplit(fraction: Float) = settings.setFilesPaneSplit(fraction)

    /** Trades the two panes' locations; the active highlight stays on the same side. */
    fun swapPanes() = primary.swapWith(secondary)

    /** Opens the active pane's folder in the other pane too (Total Commander's "="). */
    fun syncOtherPane() = mirror(activePane.id)

    /** Opens [from]'s folder in the other pane as well. */
    fun mirror(from: PaneId) {
        val path = pane(from).currentPath.value ?: return
        pane(from.other).open(path)
    }

    /**
     * Copies or moves [paths] into [destination] — a drop, or one of the "to other pane" actions.
     * The [source] pane's selection is spent once the transfer starts.
     */
    fun transfer(paths: List<String>, destination: String, operation: ClipboardOperation, source: PaneId) {
        if (paths.isEmpty() || isBusy) return
        pane(source).clearSelection()
        runOperation(changed = paths + destination) { report ->
            val ok = when (operation) {
                ClipboardOperation.COPY -> repository.copy(paths, destination, report)
                ClipboardOperation.MOVE -> repository.move(paths, destination, report)
            }
            when (operation) {
                ClipboardOperation.COPY -> if (ok) R.string.files_copy_success else R.string.files_copy_failed
                ClipboardOperation.MOVE -> if (ok) R.string.files_move_success else R.string.files_move_failed
            }
        }
    }

    /**
     * Runs one long operation: its progress is published for the progress card, and its result
     * message, or "Cancelled", goes to the snackbar. Whatever happened, the touched paths re-list.
     */
    private fun runOperation(
        changed: Collection<String>,
        block: suspend (report: (FileOperationProgress) -> Unit) -> Int,
    ) {
        operationJob = viewModelScope.launch {
            try {
                _messages.tryEmit(block { _operation.value = it })
            } catch (e: CancellationException) {
                _messages.tryEmit(R.string.files_operation_cancelled)
                throw e
            } finally {
                _operation.value = null
                refreshAfterChange(changed)
            }
        }
    }

    /** Stops the running operation; the item in progress is rolled back, finished ones stay. */
    fun cancelOperation() {
        operationJob?.cancel()
    }

    // -----------------------------------------------------------------------
    // Clipboard
    // -----------------------------------------------------------------------

    fun copySelection() = stage(ClipboardOperation.COPY)

    fun cutSelection() = stage(ClipboardOperation.MOVE)

    private fun stage(operation: ClipboardOperation) {
        val pane = activePane
        if (pane.selection.value.isEmpty()) return
        _clipboard.value = FileClipboard(pane.selection.value.toList(), operation)
        pane.clearSelection()
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    /** Pastes the clipboard into the active pane; [operation] overrides the staged one. */
    fun paste(operation: ClipboardOperation? = null) {
        val clip = _clipboard.value?.let { if (operation != null) it.copy(operation = operation) else it } ?: return
        val destination = activePane.currentPath.value ?: return
        if (isBusy) return
        _clipboard.value = null
        runOperation(changed = clip.paths + destination) { report ->
            val ok = when (clip.operation) {
                ClipboardOperation.COPY -> repository.copy(clip.paths, destination, report)
                ClipboardOperation.MOVE -> repository.move(clip.paths, destination, report)
            }
            if (ok) R.string.files_paste_success else R.string.files_paste_failed
        }
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    fun createFolder(name: String) {
        val parent = activePane.currentPath.value ?: return
        viewModelScope.launch {
            val ok = repository.createFolder(parent, name)
            _messages.tryEmit(if (ok) R.string.files_create_success else R.string.files_create_failed)
            refreshAfterChange(listOf(parent))
        }
    }

    fun createFile(name: String) {
        val parent = activePane.currentPath.value ?: return
        viewModelScope.launch {
            val ok = repository.createFile(parent, name)
            _messages.tryEmit(if (ok) R.string.files_create_success else R.string.files_create_failed)
            refreshAfterChange(listOf(parent))
        }
    }

    fun rename(path: String, newName: String) {
        val pane = activePane
        viewModelScope.launch {
            val ok = repository.rename(path, newName)
            if (ok) File(path).parent?.let { favoritesRepository.onRenamed(path, "$it/$newName") }
            _messages.tryEmit(if (ok) R.string.files_rename_success else R.string.files_rename_failed)
            pane.clearSelection()
            refreshAfterChange(listOf(path))
        }
    }

    fun deleteSelection() {
        val pane = activePane
        val paths = pane.selection.value.toList()
        if (paths.isEmpty() || isBusy) return
        pane.clearSelection()
        runOperation(changed = paths) { report ->
            if (repository.delete(paths, report)) R.string.files_delete_success else R.string.files_delete_failed
        }
    }

    // -----------------------------------------------------------------------
    // Favourites
    // -----------------------------------------------------------------------

    /** Re-checks which favourites still exist; called whenever a pane shows the home. */
    fun checkFavorites() {
        viewModelScope.launch {
            val paths = favorites.value.map { it.path }
            _missingFavorites.value = withContext(Dispatchers.IO) {
                paths.filterNot { File(it).exists() }.toSet()
            }
        }
    }

    fun isFavorite(path: String) = favoritesRepository.isFavorite(path)

    /** Stars or unstars the active pane's current folder. */
    fun toggleCurrentFolderFavorite() {
        val path = activePane.currentPath.value ?: return
        if (favoritesRepository.isFavorite(path)) {
            favoritesRepository.remove(listOf(path))
            _messages.tryEmit(R.string.files_favorite_removed)
        } else {
            favoritesRepository.add(listOf(path to true))
            _messages.tryEmit(R.string.files_favorite_added)
        }
    }

    /**
     * Stars the active pane's selection, or unstars it when every selected item is already a
     * favourite (the toolbar offers whichever of the two applies).
     */
    fun toggleSelectionFavorite() {
        val pane = activePane
        val selected = pane.selection.value
        if (selected.isEmpty()) return
        if (selected.all { favoritesRepository.isFavorite(it) }) {
            favoritesRepository.remove(selected)
            _messages.tryEmit(R.string.files_favorite_removed)
        } else {
            val byPath = pane.entries.value.associateBy { it.path }
            favoritesRepository.add(selected.map { it to (byPath[it]?.isDirectory ?: File(it).isDirectory) })
            _messages.tryEmit(R.string.files_favorite_added)
        }
        pane.clearSelection()
    }

    fun removeFavorite(path: String) = favoritesRepository.remove(listOf(path))

    fun setFavoriteColor(path: String, colorIndex: Int) = favoritesRepository.setColor(path, colorIndex)

    // -----------------------------------------------------------------------
    // Settings pass-through (the browser owns sort/hidden, the settings screen the rest)
    // -----------------------------------------------------------------------

    fun setSortOrder(order: FileSortOrder) {
        settings.setFileSortOrder(order)
        refreshAll()
    }

    fun setSortDescending(descending: Boolean) {
        settings.setFileSortDescending(descending)
        refreshAll()
    }

    fun setShowHidden(enabled: Boolean) {
        settings.setShowHiddenFiles(enabled)
        refreshAll()
    }

    // -----------------------------------------------------------------------
    // Helpers used by the screen
    // -----------------------------------------------------------------------

    fun contentUri(path: String) = repository.contentUri(path)

    fun mimeType(path: String) = repository.mimeType(path)

    suspend fun directorySize(path: String) = repository.directorySize(path)

    /** Whether the analyser can show [path] from its cached scan, or should ask to scan it. */
    fun diskScanFreshness(path: String) = diskUsage.freshnessFor(path, settings.diskScanMaxAgeHours.value)

    suspend fun childCount(path: String) = repository.childCount(path)
}
