package pl.dakil.appanalyser.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.data.DiskUsageRepository
import pl.dakil.appanalyser.data.FileSystemRepository
import pl.dakil.appanalyser.data.SettingsRepository
import pl.dakil.appanalyser.domain.DiskChartType
import pl.dakil.appanalyser.domain.DiskListMode
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import pl.dakil.appanalyser.domain.DiskScanState
import pl.dakil.appanalyser.domain.DiskUsageItem
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.RemovedEntry
import pl.dakil.appanalyser.domain.ScanCache
import pl.dakil.appanalyser.domain.ScanFreshness
import pl.dakil.appanalyser.domain.StorageRoot

/** The list under the chart, already capped. */
data class DiskUsageRows(
    val items: List<DiskUsageItem> = emptyList(),
    /** Items past the cap, folded into one footer row. */
    val hiddenCount: Int = 0,
    val hiddenBytes: Long = 0L,
)

/**
 * State for the disk usage analyser. The scan itself lives in [DiskUsageRepository]; this only
 * decides which part of it the screen shows (its root and the zoomed-in focus) and what is listed.
 *
 * Nothing here scans on its own: [selectVolume], [analyseAgain] and [rescan] are only called from
 * taps, and arriving with a folder path only scans when no scan covers it, or when the user already
 * confirmed a fresh one ([ARG_RESCAN]) in the browser's prompt.
 */
class DiskUsageViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val diskUsage = DiskUsageRepository.get(application)
    private val files = FileSystemRepository(application)
    private val settings = SettingsRepository.get(application)

    /** Null when opened without a folder: the screen starts on the volume list. */
    private val requestedPath: String? = savedStateHandle.get<String>(ARG_PATH)?.takeIf { it.isNotEmpty() }

    /** The user chose to rescan [requestedPath] rather than look at an outdated scan. */
    private val rescanRequested: Boolean = savedStateHandle.get<Boolean>(ARG_RESCAN) ?: false

    /** A requested folder is scanned once access is confirmed — never again on its own. */
    private var pendingAutoStart = requestedPath != null

    /** The running scan, if any; finished ones are in [scans]. */
    val state: StateFlow<DiskScanState> = diskUsage.activity
    val scans: StateFlow<List<DiskScan>> = diskUsage.scans
    val chartType: StateFlow<DiskChartType> = settings.diskChartType
    val confirmDeletion: StateFlow<Boolean> = settings.confirmFileDeletion

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess

    private val _roots = MutableStateFlow<List<StorageRoot>>(emptyList())
    val roots: StateFlow<List<StorageRoot>> = _roots

    /** The top of what this screen shows (the zoom-out limit); null shows the volume list. */
    private val _viewRoot = MutableStateFlow(initialViewRoot())
    val viewRoot: StateFlow<String?> = _viewRoot

    /** Opened on a folder, the screen starts inside it even when the scan reaches further up. */
    private val _focusPath = MutableStateFlow(requestedPath ?: _viewRoot.value)
    val focusPath: StateFlow<String?> = _focusPath

    private val _listMode = MutableStateFlow(DiskListMode.CONTENTS)
    val listMode: StateFlow<DiskListMode> = _listMode

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val messages: SharedFlow<Int> = _messages

    /**
     * Where the user has been, for system back to retrace. It never holds the folder the screen
     * was opened on, so once the stack is empty, back leaves the analyser from right there.
     */
    private val history = ArrayDeque<String>()

    /** Files directly inside the focus, listed fresh — the scan keeps only directory totals. */
    private val _focusFiles = MutableStateFlow<List<FileEntry>>(emptyList())

    /** The newest finished scan covering this screen's root. */
    val scan: StateFlow<DiskScan?> = combine(diskUsage.scans, _viewRoot) { scans, root ->
        root?.let { ScanCache.newestCovering(scans, it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rootNode: StateFlow<DiskNode?> = combine(scan, _viewRoot) { scan, root ->
        root?.let { scan?.root?.find(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val focusNode: StateFlow<DiskNode?> = combine(scan, _focusPath) { scan, focus ->
        focus?.let { scan?.root?.find(it) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rows: StateFlow<DiskUsageRows> = combine(scan, focusNode, _focusFiles, _listMode) { scan, focus, focusFiles, mode ->
        if (scan == null || focus == null) return@combine DiskUsageRows()
        val all = when (mode) {
            DiskListMode.CONTENTS -> {
                val folders = focus.children.mapIndexed { index, child ->
                    DiskUsageItem(
                        name = child.name,
                        path = child.path,
                        isDirectory = true,
                        sizeBytes = child.sizeBytes,
                        fileCount = child.fileCount,
                        // An empty folder hiding restricted ones reads as restricted itself.
                        restricted = child.restricted || child.sizeUnknown,
                        colorIndex = index,
                        isRestrictedSpace = child.isRestrictedSpace,
                    )
                }
                val loose = focusFiles.map { it.toItem() }
                (folders + loose).sortedByDescending { it.sizeBytes }
            }
            DiskListMode.LARGEST -> scan.largestFiles
                .filter { it.path.startsWith("${focus.path}/") }
                .take(MAX_LARGEST)
                .map {
                    DiskUsageItem(it.name, it.path, isDirectory = false, sizeBytes = it.sizeBytes, fileCount = 1, lastModified = it.lastModified)
                }
        }
        val hidden = all.drop(MAX_ROWS)
        DiskUsageRows(all.take(MAX_ROWS), hidden.size, hidden.sumOf { it.sizeBytes })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DiskUsageRows())

    init {
        viewModelScope.launch {
            _focusPath.collectLatest { loadFocusFiles(it) }
        }
    }

    /** Re-checked on entry and on resume, like the browser; never requests anything itself. */
    fun refreshAccess() {
        val granted = files.hasAccess()
        _hasAccess.value = granted
        if (!granted) return
        if (_roots.value.isEmpty()) viewModelScope.launch { _roots.value = files.storageRoots() }
        if (pendingAutoStart) {
            pendingAutoStart = false
            // A no-op when a scan already covers the folder, unless the user asked for a fresh one.
            requestedPath?.let { diskUsage.analyse(it, force = rescanRequested) }
        }
    }

    // -----------------------------------------------------------------------
    // Scanning — every entry point is a user tap
    // -----------------------------------------------------------------------

    /**
     * Opens a volume from the start page. A current scan is reused; an outdated one is replaced —
     * the tap itself is the request to analyse.
     */
    fun selectVolume(path: String) {
        _viewRoot.value = path
        history.clear()
        openFocus(path)
        val freshness = diskUsage.freshnessFor(path, settings.diskScanMaxAgeHours.value)
        diskUsage.analyse(path, force = freshness is ScanFreshness.Stale)
    }

    /** "Analyse" on the empty state, after a cancelled or failed scan of this screen's root. */
    fun analyseAgain() {
        _viewRoot.value?.let { diskUsage.analyse(it) }
    }

    /** Rescans whatever the current scan started from, so a volume scan stays a volume scan. */
    fun rescan() {
        val path = scan.value?.root?.path ?: _viewRoot.value ?: return
        clearSelection()
        diskUsage.analyse(path, force = true)
    }

    fun cancelScan() = diskUsage.cancel()

    // -----------------------------------------------------------------------
    // Navigation within the scan
    // -----------------------------------------------------------------------

    fun open(path: String) {
        val root = _viewRoot.value ?: return
        // The "Restricted by Android" entry has nothing inside to zoom into.
        if (scan.value?.root?.find(path)?.isRestrictedSpace == true) return
        if (path != root && !path.startsWith("$root/")) return
        val current = _focusPath.value
        if (current == path) return
        current?.let { history.addLast(it) }
        openFocus(path)
    }

    /** One level out, from the chart's centre; a step system back can retrace. */
    fun zoomOut() {
        val root = _viewRoot.value ?: return
        val focus = _focusPath.value ?: return
        if (focus != root) open(focus.substringBeforeLast('/'))
    }

    /**
     * System back: retraces the last step, like the browser's back. With nothing left to retrace
     * — the user is back where the analyser was opened — returns false so the screen is popped.
     * A volume picked on the start page goes back to the start page first.
     */
    fun stepBack(): Boolean {
        val tree = scan.value?.root
        while (history.isNotEmpty()) {
            val previous = history.removeLast()
            // A rescan or delete may have removed it; skip to the step before.
            if (tree == null || tree.find(previous) != null) {
                openFocus(previous)
                return true
            }
        }
        if (requestedPath != null || _viewRoot.value == null) return false
        _viewRoot.value = null
        openFocus(null)
        return true
    }

    fun setListMode(mode: DiskListMode) {
        _listMode.value = mode
        clearSelection()
    }

    fun toggleChartType() {
        settings.setDiskChartType(
            if (chartType.value == DiskChartType.RINGS) DiskChartType.TREEMAP else DiskChartType.RINGS
        )
    }

    private fun openFocus(path: String?) {
        _focusPath.value = path
        clearSelection()
    }

    private suspend fun loadFocusFiles(path: String?) {
        _focusFiles.value = if (path == null) {
            emptyList()
        } else {
            files.list(path, showHidden = true, sort = FileSortOrder.SIZE, descending = true)
                .filterNot { it.isDirectory }
        }
    }

    // -----------------------------------------------------------------------
    // Selection and clean-up
    // -----------------------------------------------------------------------

    fun toggleSelection(path: String) {
        // Restricted folders can't be deleted and the restricted-space entry isn't a folder at all.
        if (rows.value.items.firstOrNull { it.path == path }?.selectable == false) return
        _selection.value = _selection.value.let { if (path in it) it - path else it + path }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectedItems(): List<DiskUsageItem> = rows.value.items.filter { it.path in _selection.value }

    /** Deletes the selection and patches the scan, so the chart updates without a rescan. */
    fun deleteSelection() {
        val targets = selectedItems()
        if (targets.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val ok = files.delete(targets.map { it.path })
            // Even a partly failed delete removed something; patch only what is really gone.
            val removed = targets.filterNot { files.exists(it.path) }.map {
                RemovedEntry(it.path, it.isDirectory, it.sizeBytes, if (it.isDirectory) it.fileCount else 1)
            }
            diskUsage.applyDeleted(removed)
            clearSelection()
            loadFocusFiles(_focusPath.value)
            _isBusy.value = false
            _messages.tryEmit(if (ok) R.string.files_delete_success else R.string.files_delete_failed)
        }
    }

    fun contentUri(path: String) = files.contentUri(path)

    fun mimeType(path: String) = files.mimeType(path)

    private fun initialViewRoot(): String? {
        val path = requestedPath ?: return currentVolumeScanRoot()
        if (rescanRequested) return path
        // Shown inside the scan that covers it, so zooming out can go past the folder.
        return scanRootCovering(path) ?: path
    }

    private fun scanRootCovering(path: String): String? =
        diskUsage.scanFor(path)?.root?.path ?: diskUsage.runningScanCovering(path)

    /**
     * Opening without a path while a whole volume has a current scan (or one is running) goes
     * straight back to it. An outdated one leaves the user on the volume list to choose.
     */
    private fun currentVolumeScanRoot(): String? {
        (diskUsage.activity.value as? DiskScanState.Scanning)?.rootPath
            ?.takeIf { DiskUsageRepository.isVolumeRoot(it) }
            ?.let { return it }
        val maxAge = settings.diskScanMaxAgeHours.value
        return diskUsage.scans.value.firstOrNull {
            DiskUsageRepository.isVolumeRoot(it.root.path) &&
                diskUsage.freshnessFor(it.root.path, maxAge) == ScanFreshness.Fresh
        }?.root?.path
    }

    private fun FileEntry.toItem() = DiskUsageItem(
        name = name,
        path = path,
        isDirectory = false,
        sizeBytes = sizeBytes,
        fileCount = 1,
        lastModified = lastModified,
    )

    companion object {
        const val ARG_PATH = "path"
        const val ARG_RESCAN = "rescan"

        /** Rows rendered under the chart; the rest fold into a single footer. */
        private const val MAX_ROWS = 200
        private const val MAX_LARGEST = 100
    }
}
