package pl.dakil.appanalyser.domain

/**
 * One directory in a disk usage scan. Only directories are kept — files are folded into
 * [ownFilesBytes] — so a whole volume costs tens of thousands of nodes rather than one per file.
 *
 * Deliberately not a data class: StateFlow compares new values with `equals`, and a structural
 * comparison of a whole volume tree on every emission would be wasted work.
 */
class DiskNode(
    val name: String,
    val path: String,
    /** Recursive total of every regular file underneath. */
    val sizeBytes: Long,
    /** Recursive regular-file count. */
    val fileCount: Int,
    /** Bytes of the files sitting directly in this directory. */
    val ownFilesBytes: Long,
    val ownFileCount: Int,
    /** Android refused (some of) the listing, so the totals are a lower bound. */
    val restricted: Boolean = false,
    /** Subdirectories, largest first. */
    val children: List<DiskNode> = emptyList(),
    /**
     * Not a real directory: the space the volume reports as used that no readable folder accounts
     * for (apps, their private data, system data, hidden folders). See [withRestrictedSpace].
     */
    val isRestrictedSpace: Boolean = false,
) {
    /** This folder or one below it is restricted. Computed once, children being built first. */
    val containsRestricted: Boolean = restricted || children.any { it.containsRestricted }

    /**
     * Nothing visible here, but a restricted folder is (e.g. `Android` holding only `data` and
     * `obb`): the size is unknown, not zero.
     */
    val sizeUnknown: Boolean get() = containsRestricted && sizeBytes == 0L

    /** True when [other] is this directory or lies underneath it. */
    fun contains(other: String): Boolean = other == path || other.startsWith("$path/")

    /** The node at [target], or null when it isn't part of this subtree (or isn't a directory). */
    fun find(target: String): DiskNode? {
        var node = this
        if (!node.contains(target)) return null
        while (node.path != target) {
            node = node.children.firstOrNull { it.contains(target) } ?: return null
        }
        return node
    }

    /** Nodes from this one down to [target], both inclusive; empty when [target] isn't inside. */
    fun chainTo(target: String): List<DiskNode> {
        if (!contains(target)) return emptyList()
        val chain = mutableListOf(this)
        while (chain.last().path != target) {
            chain += chain.last().children.firstOrNull { it.contains(target) } ?: return emptyList()
        }
        return chain
    }

    /**
     * A copy with [removed] (a deleted file or directory) subtracted. Only the ancestor chain is
     * rebuilt; every untouched subtree is shared with the original.
     */
    fun without(removed: RemovedEntry): DiskNode {
        if (!contains(removed.path) || removed.path == path) return this
        val parentPath = removed.path.substringBeforeLast('/')
        val directChild = removed.isDirectory && parentPath == path
        val directFile = !removed.isDirectory && parentPath == path
        val newChildren = when {
            directChild -> children.filterNot { it.path == removed.path }
            directFile -> children
            else -> children.map { if (it.contains(removed.path)) it.without(removed) else it }
                .sortedByDescending { it.sizeBytes }
        }
        return DiskNode(
            name = name,
            path = path,
            sizeBytes = (sizeBytes - removed.sizeBytes).coerceAtLeast(0),
            fileCount = (fileCount - removed.fileCount).coerceAtLeast(0),
            ownFilesBytes = if (directFile) (ownFilesBytes - removed.sizeBytes).coerceAtLeast(0) else ownFilesBytes,
            ownFileCount = if (directFile) (ownFileCount - 1).coerceAtLeast(0) else ownFileCount,
            restricted = restricted,
            children = newChildren,
            isRestrictedSpace = isRestrictedSpace,
        )
    }

    /**
     * A copy with [replacement] (a fresh scan of one folder inside this tree) grafted in place of
     * that folder, ancestors' totals recomputed from their children. A folder that didn't exist at
     * scan time is added under its parent, if the parent is known.
     */
    fun withSubtree(replacement: DiskNode): DiskNode {
        val target = replacement.path
        if (target == path) return replacement
        if (!contains(target)) return this
        val child = children.firstOrNull { it.contains(target) }
        val newChildren = when {
            child != null -> children.map { if (it === child) it.withSubtree(replacement) else it }
            target.substringBeforeLast('/') == path -> children + replacement
            else -> return this
        }.sortedByDescending { it.sizeBytes }
        return DiskNode(
            name = name,
            path = path,
            sizeBytes = ownFilesBytes + newChildren.sumOf { it.sizeBytes },
            fileCount = ownFileCount + newChildren.sumOf { it.fileCount },
            ownFilesBytes = ownFilesBytes,
            ownFileCount = ownFileCount,
            restricted = restricted,
            children = newChildren,
            isRestrictedSpace = isRestrictedSpace,
        )
    }

    /**
     * A copy with a synthetic [isRestrictedSpace] child of [bytes] named [name], so the totals
     * match what the volume reports as used. No-op for a non-positive gap.
     */
    fun withRestrictedSpace(bytes: Long, name: String): DiskNode {
        if (bytes <= 0) return this
        val restrictedSpace = DiskNode(
            name = name,
            // NUL can't appear in a real file name, so this never collides with a folder.
            path = "$path/$RESTRICTED_SPACE_SEGMENT",
            sizeBytes = bytes,
            fileCount = 0,
            ownFilesBytes = 0,
            ownFileCount = 0,
            isRestrictedSpace = true,
        )
        return DiskNode(
            name = this.name,
            path = path,
            sizeBytes = sizeBytes + bytes,
            fileCount = fileCount,
            ownFilesBytes = ownFilesBytes,
            ownFileCount = ownFileCount,
            restricted = restricted,
            children = (children + restrictedSpace).sortedByDescending { it.sizeBytes },
            isRestrictedSpace = isRestrictedSpace,
        )
    }

    companion object {
        private const val RESTRICTED_SPACE_SEGMENT = "\u0000restricted"
    }
}

/** Something deleted from inside a scan, with the totals it contributed. */
data class RemovedEntry(
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    /** Files it accounted for: 1 for a file, the recursive count for a directory. */
    val fileCount: Int,
)

/** A single file in the "largest files" list. */
data class DiskFile(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/** A finished scan. Held in memory only; never persisted. */
class DiskScan(
    val root: DiskNode,
    /** Largest files first, capped during the walk. */
    val largestFiles: List<DiskFile>,
    val scannedAt: Long,
    /** Something changed on disk (outside the analyser) since the scan. */
    val stale: Boolean = false,
    /** Capacity of the volume holding [root], when the platform reports it. */
    val volumeTotalBytes: Long = 0L,
    val volumeFreeBytes: Long = 0L,
) {
    fun copy(
        root: DiskNode = this.root,
        largestFiles: List<DiskFile> = this.largestFiles,
        stale: Boolean = this.stale,
        volumeTotalBytes: Long = this.volumeTotalBytes,
        volumeFreeBytes: Long = this.volumeFreeBytes,
    ) = DiskScan(root, largestFiles, scannedAt, stale, volumeTotalBytes, volumeFreeBytes)
}

/**
 * What the scanner is doing right now. Finished scans aren't part of it — they live in a
 * [ScanCache] list, so starting another scan never forgets them.
 */
sealed interface DiskScanState {
    /** No scan running. */
    data object Idle : DiskScanState

    data class Scanning(
        val rootPath: String,
        val files: Long,
        val bytes: Long,
        val currentDir: String,
    ) : DiskScanState

    /** The root itself could not be read. */
    data class Failed(val rootPath: String) : DiskScanState
}

/**
 * Rules for the finished scans kept in memory, as pure functions over a list (newest first) so
 * they're unit-tested. Several scans coexist — `/Music` and `/Documents` scanned one after the other
 * are both remembered; a scan that contains older ones replaces them; a fresh scan inside an older,
 * bigger one is kept and also grafted into it, so the two never disagree.
 */
object ScanCache {
    /** The most recent scan containing [path]. */
    fun newestCovering(scans: List<DiskScan>, path: String): DiskScan? =
        scans.filter { it.root.contains(path) }.maxByOrNull { it.scannedAt }

    fun add(scans: List<DiskScan>, scan: DiskScan, limit: Int, largestLimit: Int): List<DiskScan> {
        val root = scan.root.path
        val kept = scans
            // Same folder or anything inside it: the new scan supersedes it.
            .filterNot { scan.root.contains(it.root.path) }
            .map { older ->
                if (!older.root.contains(root)) return@map older
                older.copy(
                    root = older.root.withSubtree(scan.root),
                    largestFiles = (older.largestFiles.filterNot { scan.root.contains(it.path) } + scan.largestFiles)
                        .sortedByDescending { it.sizeBytes }
                        .take(largestLimit),
                )
            }
        return (listOf(scan) + kept).sortedByDescending { it.scannedAt }.take(limit)
    }

    /** Deletions made in the analyser, patched into every scan that contains them. */
    fun applyDeleted(scans: List<DiskScan>, removed: List<RemovedEntry>): List<DiskScan> = scans.map { scan ->
        val relevant = removed.filter { scan.root.contains(it.path) && it.path != scan.root.path }
        if (relevant.isEmpty()) return@map scan
        var root = scan.root
        relevant.forEach { root = root.without(it) }
        scan.copy(
            root = root,
            largestFiles = scan.largestFiles.filterNot { file ->
                relevant.any { file.path == it.path || file.path.startsWith("${it.path}/") }
            },
        )
    }

    /** Flags the scans that [changed] paths fall inside of (or that sit inside a changed path). */
    fun markStale(scans: List<DiskScan>, changed: Collection<String>): List<DiskScan> = scans.map { scan ->
        val affected = changed.any { scan.root.contains(it) || scan.root.path.startsWith("$it/") }
        if (affected && !scan.stale) scan.copy(stale = true) else scan
    }
}

/** How a cached scan relates to a folder the user wants to look at. */
sealed interface ScanFreshness {
    /** No scan covers the folder. */
    data object None : ScanFreshness

    /** A scan covers it but is older than the configured limit. */
    data class Stale(val scannedAt: Long) : ScanFreshness

    data object Fresh : ScanFreshness

    companion object {
        /** [maxAgeHours] of 0 means a scan never goes stale. */
        fun of(scan: DiskScan?, path: String, maxAgeHours: Int, now: Long): ScanFreshness = when {
            scan == null || !scan.root.contains(path) -> None
            maxAgeHours > 0 && now - scan.scannedAt > maxAgeHours * 3_600_000L -> Stale(scan.scannedAt)
            else -> Fresh
        }
    }
}

/** How the analyser draws the tree. */
enum class DiskChartType { RINGS, TREEMAP }

/** Which list sits under the chart. */
enum class DiskListMode { CONTENTS, LARGEST }

/** One row under the chart: a subdirectory from the scan or a file listed fresh from disk. */
data class DiskUsageItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    /** Recursive file count for directories; 1 for files. */
    val fileCount: Int,
    val lastModified: Long = 0L,
    val restricted: Boolean = false,
    /** Position among the focus's subdirectories, which picks the chart colour; -1 for files. */
    val colorIndex: Int = -1,
    /** The synthetic "Restricted by Android" entry: not openable, selectable or deletable. */
    val isRestrictedSpace: Boolean = false,
) {
    /** A restricted folder in which nothing was visible: shown as "? B" rather than 0. */
    val sizeUnknown: Boolean get() = restricted && sizeBytes == 0L

    /** Only real, readable things can be selected for clean-up. */
    val selectable: Boolean get() = !isRestrictedSpace && !restricted
}
