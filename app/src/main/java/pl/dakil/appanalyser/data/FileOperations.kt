package pl.dakil.appanalyser.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.domain.FileOperationKind
import pl.dakil.appanalyser.domain.FileOperationProgress
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.CoroutineContext

/**
 * Copy, move and delete with real progress, in plain `java.io`/`java.nio` so it runs under JVM
 * tests. Nothing here throws except cancellation; failures come back as `false`.
 *
 * Every top-level item ends up either fully done or not there at all: when copying an item fails or
 * the coroutine is cancelled, whatever had been written for that item is removed again (the source
 * of a move is only deleted after its copy completed, so nothing is ever lost). Items finished
 * before that point stay where they are. Symlinks are never followed.
 */
object FileOperations {

    private const val BUFFER_BYTES = 256 * 1024

    /** Progress is published at most this often, plus at every file boundary that matters. */
    private const val REPORT_INTERVAL_NANOS = 50_000_000L

    suspend fun copy(
        sources: List<String>,
        destinationDir: String,
        onProgress: (FileOperationProgress) -> Unit = {},
    ): Boolean = transfer(sources, destinationDir, move = false, onProgress)

    /**
     * Renames where it can, which is instant and needs no measuring, and falls back to a copy with
     * progress followed by a delete when the destination sits on a different volume.
     */
    suspend fun move(
        sources: List<String>,
        destinationDir: String,
        onProgress: (FileOperationProgress) -> Unit = {},
    ): Boolean = transfer(sources, destinationDir, move = true, onProgress)

    /** Deletes files and whole trees, bottom-up, reporting each file. */
    suspend fun delete(
        paths: Collection<String>,
        onProgress: (FileOperationProgress) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val reporter = Reporter(FileOperationProgress(FileOperationKind.DELETE, paths.size), onProgress)
        val totals = paths.map { measure(File(it).toPath()) }
        reporter.update { it.copy(totalFiles = totals.sumOf { t -> t.files }) }
        var allOk = true
        for (path in paths) {
            context.ensureActive()
            if (!deleteTree(File(path).toPath(), context, reporter)) allOk = false
        }
        reporter.flush()
        allOk
    }

    private suspend fun transfer(
        sources: List<String>,
        destinationDir: String,
        move: Boolean,
        onProgress: (FileOperationProgress) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        val context = currentCoroutineContext()
        val kind = if (move) FileOperationKind.MOVE else FileOperationKind.COPY
        val reporter = Reporter(FileOperationProgress(kind, sources.size), onProgress)
        val destination = File(destinationDir)
        var allOk = true

        // First the items that need no copying at all, so a same-volume move never measures.
        val pending = mutableListOf<File>()
        for (path in sources) {
            context.ensureActive()
            val source = File(path)
            when {
                !Files.exists(source.toPath(), LinkOption.NOFOLLOW_LINKS) -> allOk = false
                // Copying a folder into itself would recurse forever.
                source.isDirectory && isInside(destinationDir, path) -> allOk = false
                // Moving something into the folder it already lives in is a no-op, not a duplicate.
                move && source.parentFile?.absolutePath == destination.absolutePath -> Unit
                move && runCatching { source.renameTo(uniqueTarget(destination, source.name)) }.getOrDefault(false) -> Unit
                else -> pending += source
            }
        }
        if (pending.isEmpty()) return@withContext allOk

        val totals = pending.map { measure(it.toPath()) }
        reporter.update {
            it.copy(totalFiles = totals.sumOf { t -> t.files }, totalBytes = totals.sumOf { t -> t.bytes })
        }
        val buffer = ByteArray(BUFFER_BYTES)
        for (source in pending) {
            context.ensureActive()
            val target = uniqueTarget(destination, source.name)
            val copied = try {
                copyTree(source.toPath(), target.toPath(), buffer, context, reporter)
                true
            } catch (e: IOException) {
                false
            } catch (e: Throwable) {
                // Cancellation (or anything unexpected): take back this item's partial copy.
                removeQuietly(target.toPath())
                throw e
            }
            if (!copied) {
                removeQuietly(target.toPath())
                allOk = false
            } else if (move && !deleteTree(source.toPath(), context, reporter = null)) {
                allOk = false
            }
        }
        reporter.flush()
        allOk
    }

    // -----------------------------------------------------------------------
    // Walking
    // -----------------------------------------------------------------------

    private class Totals(var files: Int = 0, var bytes: Long = 0L)

    /** Regular files and links count as files; directories don't. Unreadable parts are skipped. */
    private fun measure(root: Path): Totals {
        val totals = Totals()
        runCatching {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    totals.files++
                    if (attrs.isRegularFile) totals.bytes += attrs.size()
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException) = FileVisitResult.CONTINUE
            })
        }
        return totals
    }

    /** Throws [IOException] on the first failure; the caller removes the partial target. */
    private fun copyTree(
        source: Path,
        target: Path,
        buffer: ByteArray,
        context: CoroutineContext,
        reporter: Reporter,
    ) {
        Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                context.ensureActive()
                Files.createDirectories(target.resolve(source.relativize(dir).toString()))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                context.ensureActive()
                val destination = if (file == source) target else target.resolve(source.relativize(file).toString())
                reporter.update(force = true) { it.copy(currentName = file.fileName.toString()) }
                if (attrs.isSymbolicLink || !attrs.isRegularFile) {
                    Files.copy(file, destination, LinkOption.NOFOLLOW_LINKS)
                } else {
                    copyFile(file, destination, buffer, context, reporter)
                }
                reporter.update { it.copy(doneFiles = it.doneFiles + 1) }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = throw exc
        })
    }

    private fun copyFile(
        source: Path,
        target: Path,
        buffer: ByteArray,
        context: CoroutineContext,
        reporter: Reporter,
    ) {
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(target).use { output ->
                while (true) {
                    context.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    reporter.update { it.copy(doneBytes = it.doneBytes + read) }
                }
            }
        }
        runCatching { target.toFile().setLastModified(source.toFile().lastModified()) }
    }

    /** Deletes [root] and everything under it without following links; false if anything stayed. */
    private fun deleteTree(root: Path, context: CoroutineContext, reporter: Reporter?): Boolean {
        var allOk = true
        runCatching {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    context.ensureActive()
                    reporter?.update(force = true) { it.copy(currentName = file.fileName.toString()) }
                    if (!runCatching { Files.delete(file) }.isSuccess) allOk = false
                    reporter?.update { it.copy(doneFiles = it.doneFiles + 1) }
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (!runCatching { Files.delete(dir) }.isSuccess) allOk = false
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    allOk = false
                    return FileVisitResult.CONTINUE
                }
            })
        }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it else allOk = false }
        return allOk
    }

    private fun removeQuietly(path: Path) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            runCatching { deleteTree(path, kotlin.coroutines.EmptyCoroutineContext, reporter = null) }
        }
    }

    // -----------------------------------------------------------------------
    // Naming
    // -----------------------------------------------------------------------

    /** True when [candidate] is [ancestor] itself or lives underneath it. */
    fun isInside(candidate: String, ancestor: String): Boolean {
        val normalizedAncestor = File(ancestor).absolutePath
        val normalizedCandidate = File(candidate).absolutePath
        return normalizedCandidate == normalizedAncestor ||
            normalizedCandidate.startsWith("$normalizedAncestor${File.separator}")
    }

    /** `report.txt` → `report (1).txt` when the name is taken. */
    fun uniqueTarget(directory: File, name: String): File {
        var candidate = File(directory, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val suffixed = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            candidate = File(directory, suffixed)
            index++
        }
        return candidate
    }

    // -----------------------------------------------------------------------
    // Reporting
    // -----------------------------------------------------------------------

    /** Accumulates progress and hands it on at a bounded rate, so the UI isn't flooded per chunk. */
    private class Reporter(
        private var progress: FileOperationProgress,
        private val onProgress: (FileOperationProgress) -> Unit,
    ) {
        private var lastReport = 0L

        init {
            onProgress(progress)
        }

        fun update(force: Boolean = false, change: (FileOperationProgress) -> FileOperationProgress) {
            progress = change(progress)
            val now = System.nanoTime()
            if (force || now - lastReport >= REPORT_INTERVAL_NANOS) {
                lastReport = now
                onProgress(progress)
            }
        }

        fun flush() = onProgress(progress)
    }
}
