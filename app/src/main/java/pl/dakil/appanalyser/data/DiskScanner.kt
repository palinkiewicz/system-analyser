package pl.dakil.appanalyser.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.domain.DiskFile
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/**
 * Walks a directory tree once and builds a [DiskNode] tree of directory totals, plus the largest
 * files found on the way.
 *
 * Pure JVM on purpose (`java.nio.file`, no `android.*`), so it runs under unit tests:
 * - one `lstat` per entry via [Files.readAttributes] instead of `File`'s separate
 *   isDirectory/length/lastModified calls;
 * - symbolic links are never followed, which also rules out cycles;
 * - the top [PARALLEL_DEPTH] levels fan out across [dispatcher], everything deeper is walked
 *   sequentially inside its branch so we never spawn a coroutine per directory.
 */
class DiskScanner(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(PARALLELISM),
    private val largestFilesLimit: Int = LARGEST_FILES_LIMIT,
    /** Directories the platform hides from us even when listing succeeds (Android/data and co). */
    private val isRestricted: (String) -> Boolean = { false },
) {

    class Progress(val files: Long, val bytes: Long, val currentDir: String)

    /**
     * Scans [root]. [onProgress] is sampled on a fixed tick rather than called per file, so a
     * collector never sees more than a few updates a second. Returns null when [root] is not a
     * readable directory. Cancellation is checked once per directory.
     */
    suspend fun scan(root: File, onProgress: (Progress) -> Unit = {}): DiskScan? = coroutineScope {
        val rootPath = root.toPath()
        if (!Files.isDirectory(rootPath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(rootPath)) {
            return@coroutineScope null
        }
        val walk = Walk()
        val ticker = launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                onProgress(walk.progress())
            }
        }
        try {
            val node = withContext(dispatcher) { walk.directory(rootPath, depth = 0) }
            DiskScan(
                root = node,
                largestFiles = walk.largestFiles(),
                scannedAt = System.currentTimeMillis(),
            )
        } finally {
            ticker.cancel()
        }
    }

    /** Per-scan mutable state, shared by every branch of one walk. */
    private inner class Walk {
        private val files = AtomicLong()
        private val bytes = AtomicLong()

        @Volatile
        private var currentDir = ""

        /** Min-heap of the biggest files so far; [threshold] lets most files skip the lock. */
        private val largest = PriorityQueue<DiskFile>(largestFilesLimit + 1, compareBy { it.sizeBytes })

        @Volatile
        private var threshold = -1L

        fun progress() = Progress(files.get(), bytes.get(), currentDir)

        fun largestFiles(): List<DiskFile> = synchronized(largest) {
            largest.sortedByDescending { it.sizeBytes }
        }

        private fun offerLargest(file: Path, attributes: BasicFileAttributes) {
            val size = attributes.size()
            if (largestFilesLimit <= 0 || size <= threshold) return
            synchronized(largest) {
                largest += DiskFile(
                    name = file.fileName.toString(),
                    path = file.toString(),
                    sizeBytes = size,
                    lastModified = attributes.lastModifiedTime().toMillis(),
                )
                if (largest.size > largestFilesLimit) largest.poll()
                if (largest.size == largestFilesLimit) threshold = largest.peek()!!.sizeBytes
            }
        }

        suspend fun directory(dir: Path, depth: Int): DiskNode {
            coroutineContext.ensureActive()
            val path = dir.toString()
            currentDir = path

            val subdirectories = ArrayList<Path>()
            var ownBytes = 0L
            var ownCount = 0
            var restricted = isRestricted(path)
            try {
                Files.newDirectoryStream(dir).use { stream ->
                    for (child in stream) {
                        val attributes = try {
                            Files.readAttributes(
                                child,
                                BasicFileAttributes::class.java,
                                LinkOption.NOFOLLOW_LINKS,
                            )
                        } catch (_: IOException) {
                            continue
                        } catch (_: SecurityException) {
                            continue
                        }
                        when {
                            // `add`, not `+=`: Path is itself an Iterable<Path>, so `+=` would resolve to plus(Iterable).
                            attributes.isDirectory -> subdirectories.add(child)
                            // Symlinks, sockets and devices are neither followed nor counted.
                            attributes.isRegularFile -> {
                                ownBytes += attributes.size()
                                ownCount++
                                offerLargest(child, attributes)
                            }
                        }
                    }
                }
            } catch (_: IOException) {
                restricted = true
            } catch (_: SecurityException) {
                restricted = true
            }
            files.addAndGet(ownCount.toLong())
            bytes.addAndGet(ownBytes)

            val children = if (depth < PARALLEL_DEPTH && subdirectories.size > 1) {
                coroutineScope {
                    subdirectories.map { async { directory(it, depth + 1) } }.awaitAll()
                }
            } else {
                subdirectories.map { directory(it, depth + 1) }
            }

            return DiskNode(
                name = dir.fileName?.toString() ?: path,
                path = path,
                sizeBytes = ownBytes + children.sumOf { it.sizeBytes },
                fileCount = ownCount + children.sumOf { it.fileCount },
                ownFilesBytes = ownBytes,
                ownFileCount = ownCount,
                restricted = restricted,
                children = children.sortedByDescending { it.sizeBytes },
            )
        }
    }

    companion object {
        /** Concurrent directory listings; flash storage stops scaling well past a handful. */
        private const val PARALLELISM = 4

        /** Levels that fan out across [PARALLELISM]; deeper levels are walked within their branch. */
        private const val PARALLEL_DEPTH = 2

        const val LARGEST_FILES_LIMIT = 300

        private const val PROGRESS_INTERVAL_MS = 150L
    }
}
