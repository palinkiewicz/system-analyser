package pl.dakil.appanalyser.data

import android.content.Context
import android.os.Build
import android.os.StatFs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pl.dakil.appanalyser.R
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import pl.dakil.appanalyser.domain.DiskScanState
import pl.dakil.appanalyser.domain.RemovedEntry
import pl.dakil.appanalyser.domain.ScanCache
import pl.dakil.appanalyser.domain.ScanFreshness
import java.io.File

/**
 * Holds the disk usage scans the app remembers, in memory only, plus the one that may be running.
 *
 * Scans run strictly on request — nothing here starts one by itself, and a finished scan is reused
 * (including for any folder inside it) until the user asks for a rescan. Several are kept at once
 * (see [ScanCache] for how they merge), so scanning `/Documents` doesn't forget `/Music`. The walk
 * lives in this process-wide scope rather than in a ViewModel, so stepping back from the analyser
 * doesn't throw away a half-finished scan; [cancel] is called when the app leaves the foreground.
 */
class DiskUsageRepository private constructor(context: Context) {

    /** Resources only — a Context in a process-wide singleton is what lint calls a leak. */
    private val resources = context.applicationContext.resources

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scanner = DiskScanner(isRestricted = ::isPlatformRestricted)

    /** Finished scans, newest first. */
    private val _scans = MutableStateFlow<List<DiskScan>>(emptyList())
    val scans: StateFlow<List<DiskScan>> = _scans.asStateFlow()

    /** The scan in progress (or the last failure); finished results go to [scans]. */
    private val _activity = MutableStateFlow<DiskScanState>(DiskScanState.Idle)
    val activity: StateFlow<DiskScanState> = _activity.asStateFlow()

    @Volatile
    private var job: Job? = null

    /** The most recent finished scan containing [path]. */
    fun scanFor(path: String): DiskScan? = ScanCache.newestCovering(_scans.value, path)

    /** The running scan's root, when it contains [path]. */
    fun runningScanCovering(path: String): String? =
        (_activity.value as? DiskScanState.Scanning)?.rootPath?.takeIf { path == it || path.startsWith("$it/") }

    /** True when a finished or running scan already covers [path]. */
    fun covers(path: String): Boolean = scanFor(path) != null || runningScanCovering(path) != null

    /**
     * Scans [path], unless a scan already covers it and [force] is off. Only one scan runs at a
     * time — a new one cancels the running walk — but finished scans are all kept.
     */
    fun analyse(path: String, force: Boolean = false) {
        if (!force && covers(path)) return
        job?.cancel()
        _activity.value = DiskScanState.Scanning(path, files = 0, bytes = 0, currentDir = path)
        // Started lazily so `job` is assigned before the body runs: every write below is gated on
        // still being the current scan, or a cancelled walk could overwrite its replacement.
        val scanJob = scope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            fun publish(state: DiskScanState) {
                if (job === self) _activity.value = state
            }
            try {
                val scan = scanner.scan(File(path)) { progress ->
                    publish(
                        DiskScanState.Scanning(
                            rootPath = path,
                            files = progress.files,
                            bytes = progress.bytes,
                            currentDir = progress.currentDir,
                        )
                    )
                }
                ensureActive()
                if (scan == null) {
                    publish(DiskScanState.Failed(path))
                    return@launch
                }
                val stat = runCatching { StatFs(path) }.getOrNull()
                val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
                val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
                val finished = scan.copy(
                    root = withRestrictedSpace(scan.root, total, free),
                    volumeTotalBytes = total,
                    volumeFreeBytes = free,
                )
                if (job === self) {
                    _scans.update {
                        ScanCache.add(it, finished, MAX_SCANS, DiskScanner.LARGEST_FILES_LIMIT)
                    }
                    _activity.value = DiskScanState.Idle
                }
            } catch (e: CancellationException) {
                // Earlier results were never touched, so cancelling a rescan keeps them.
                publish(DiskScanState.Idle)
                throw e
            }
        }
        job = scanJob
        scanJob.start()
    }

    /**
     * On a whole-volume scan, whatever the volume reports as used but no readable folder accounts
     * for becomes one "Restricted by Android" entry, so the chart adds up to the real usage. It sits
     * at the root rather than inside `Android/`: most of it is installed apps, their private data
     * and system data, which live on the same partition but in no folder we can see. A subfolder
     * scan has no such reference figure, so it gets nothing.
     */
    private fun withRestrictedSpace(root: DiskNode, total: Long, free: Long): DiskNode {
        if (total <= 0 || !isVolumeRoot(root.path)) return root
        val unaccounted = (total - free) - root.sizeBytes
        return root.withRestrictedSpace(unaccounted, resources.getString(R.string.disk_usage_restricted))
    }

    /** How the newest scan of [path] (if any) relates to it, given the user's staleness limit. */
    fun freshnessFor(path: String, maxAgeHours: Int): ScanFreshness =
        ScanFreshness.of(
            scan = scanFor(path),
            path = path,
            maxAgeHours = maxAgeHours,
            now = System.currentTimeMillis(),
        )

    /** Stops a running scan; finished ones are kept. */
    fun cancel() {
        if (_activity.value is DiskScanState.Scanning) job?.cancel()
    }

    /** Patches deletions made from the analyser into every scan, so no rescan is needed. */
    fun applyDeleted(removed: List<RemovedEntry>) {
        _scans.update { ScanCache.applyDeleted(it, removed) }
    }

    /**
     * Something changed on disk outside the analyser, at [changed] paths; the scans covering them
     * offer a rescan instead of guessing.
     */
    fun markStale(changed: Collection<String>) {
        _scans.update { ScanCache.markStale(it, changed) }
    }

    /**
     * Android 11+ hides other apps' `Android/data` and `Android/obb` even from All files access,
     * so their totals are only what we're allowed to see.
     */
    private fun isPlatformRestricted(path: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && RESTRICTED_PATH.matches(path)

    companion object {
        private val RESTRICTED_PATH = Regex("^/storage/(emulated/\\d+|[^/]+)/Android/(data|obb)$")
        private val VOLUME_ROOT = Regex("^/storage/(emulated/\\d+|[^/]+)$")

        /** Scans remembered at once; the oldest goes first. Each holds directory totals only. */
        private const val MAX_SCANS = 8

        /** Volume roots are `/storage/emulated/<user>` or `/storage/<uuid>`. */
        fun isVolumeRoot(path: String) = VOLUME_ROOT.matches(path)

        @Volatile
        private var instance: DiskUsageRepository? = null

        fun get(context: Context): DiskUsageRepository =
            instance ?: synchronized(this) {
                instance ?: DiskUsageRepository(context.applicationContext).also { instance = it }
            }
    }
}
