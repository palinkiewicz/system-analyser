package pl.dakil.appanalyser.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pl.dakil.appanalyser.domain.FileEntry
import pl.dakil.appanalyser.domain.FileSortOrder
import pl.dakil.appanalyser.domain.StorageRoot
import pl.dakil.appanalyser.domain.TextLoadResult
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Plain `java.io.File` access for the experimental File system tab.
 *
 * Every entry point is suspending and hops to [Dispatchers.IO]; none of them throw — failures come
 * back as `false`/`null`/a failure result so the UI can show a message instead of crashing on the
 * many paths the OS refuses even with All files access (`/proc`, `/data`, unmounted volumes).
 */
class FileSystemRepository(private val context: Context) {

    // -----------------------------------------------------------------------
    // Access
    // -----------------------------------------------------------------------

    /**
     * Android 11+ gates unscoped access behind All files access; Android 10 has no such concept and
     * relies on the legacy storage permission plus `requestLegacyExternalStorage`.
     */
    fun hasAccess(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /** Mounted volumes worth showing at the top level: internal storage plus any removable cards. */
    suspend fun storageRoots(): List<StorageRoot> = withContext(Dispatchers.IO) {
        val roots = mutableListOf<StorageRoot>()
        val internal = runCatching { Environment.getExternalStorageDirectory() }.getOrNull()
        if (internal != null && internal.isDirectory) {
            roots += StorageRoot(
                label = context.getString(pl.dakil.appanalyser.R.string.files_internal_storage),
                path = internal.absolutePath,
                isRemovable = false,
            )
        }
        runCatching {
            val manager = context.getSystemService(StorageManager::class.java)
            manager?.storageVolumes.orEmpty()
                .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                .forEach { volume ->
                    val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        null
                    }
                    if (directory != null && directory.isDirectory) {
                        roots += StorageRoot(
                            label = volume.getDescription(context)
                                ?: context.getString(pl.dakil.appanalyser.R.string.files_sd_card),
                            path = directory.absolutePath,
                            isRemovable = true,
                        )
                    }
                }
        }
        roots.map { root ->
            runCatching { StatFs(root.path) }.getOrNull()?.let { stat ->
                root.copy(
                    totalBytes = stat.blockCountLong * stat.blockSizeLong,
                    freeBytes = stat.availableBlocksLong * stat.blockSizeLong,
                )
            } ?: root
        }
    }

    // -----------------------------------------------------------------------
    // Listing
    // -----------------------------------------------------------------------

    /** Children of [path], directories first, then ordered by [sort]. Never recurses. */
    suspend fun list(
        path: String,
        showHidden: Boolean,
        sort: FileSortOrder,
        descending: Boolean,
    ): List<FileEntry> = withContext(Dispatchers.IO) {
        val children = runCatching { File(path).listFiles() }.getOrNull() ?: return@withContext emptyList()
        val entries = children.asSequence()
            .filter { showHidden || !it.isHidden }
            .map { file ->
                FileEntry(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(),
                    isHidden = file.isHidden,
                )
            }
            .toList()

        // Ties always fall back to the name, so a re-list of equal-sized files is stable.
        val byName = Comparator<FileEntry> { a, b ->
            String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name)
        }
        val comparator: Comparator<FileEntry> = when (sort) {
            FileSortOrder.NAME -> byName
            FileSortOrder.SIZE -> compareBy<FileEntry> { it.sizeBytes }.then(byName)
            FileSortOrder.DATE -> compareBy<FileEntry> { it.lastModified }.then(byName)
            FileSortOrder.TYPE -> compareBy<FileEntry> { it.extension }.then(byName)
        }
        // Folders stay pinned to the top regardless of direction, like the system file manager.
        entries.sortedWith(
            compareByDescending<FileEntry> { it.isDirectory }
                .then(if (descending) comparator.reversed() else comparator)
        )
    }

    suspend fun exists(path: String): Boolean = withContext(Dispatchers.IO) { File(path).exists() }

    suspend fun isDirectory(path: String): Boolean = withContext(Dispatchers.IO) { File(path).isDirectory }

    /** Recursive byte total, for the properties dialog only — never during listing. */
    suspend fun directorySize(path: String): Long = withContext(Dispatchers.IO) {
        runCatching { File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
    }

    /** Immediate child count, for the properties dialog. */
    suspend fun childCount(path: String): Int = withContext(Dispatchers.IO) {
        runCatching { File(path).listFiles()?.size ?: 0 }.getOrDefault(0)
    }

    // -----------------------------------------------------------------------
    // Mutations
    // -----------------------------------------------------------------------

    suspend fun createFolder(parent: String, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidName(name)) return@withContext false
        runCatching {
            val target = File(parent, name)
            !target.exists() && target.mkdir()
        }.getOrDefault(false)
    }

    suspend fun createFile(parent: String, name: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidName(name)) return@withContext false
        runCatching {
            val target = File(parent, name)
            !target.exists() && target.createNewFile()
        }.getOrDefault(false)
    }

    suspend fun rename(path: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isValidName(newName)) return@withContext false
        runCatching {
            val source = File(path)
            val target = File(source.parentFile, newName)
            !target.exists() && source.renameTo(target)
        }.getOrDefault(false)
    }

    /** Deletes files and whole directory trees. Returns false if any item failed. */
    suspend fun delete(paths: Collection<String>): Boolean = withContext(Dispatchers.IO) {
        var allOk = true
        for (path in paths) {
            coroutineContext.ensureActive()
            val ok = runCatching {
                val file = File(path)
                if (file.isDirectory) file.deleteRecursively() else file.delete()
            }.getOrDefault(false)
            if (!ok) allOk = false
        }
        allOk
    }

    /**
     * Copies [sources] into [destinationDir], de-colliding names. [onProgress] is called with
     * (done, total) after each top-level item.
     */
    suspend fun copy(
        sources: List<String>,
        destinationDir: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Boolean = transfer(sources, destinationDir, move = false, onProgress = onProgress)

    /**
     * Moves [sources] into [destinationDir]. Tries a rename first and falls back to copy+delete
     * when the destination sits on a different volume.
     */
    suspend fun move(
        sources: List<String>,
        destinationDir: String,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Boolean = transfer(sources, destinationDir, move = true, onProgress = onProgress)

    private suspend fun transfer(
        sources: List<String>,
        destinationDir: String,
        move: Boolean,
        onProgress: (Int, Int) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        var allOk = true
        sources.forEachIndexed { index, sourcePath ->
            coroutineContext.ensureActive()
            val ok = runCatching {
                val source = File(sourcePath)
                if (!source.exists()) return@runCatching false
                // Copying a folder into itself would recurse forever.
                if (source.isDirectory && isInside(destinationDir, sourcePath)) return@runCatching false
                // Moving something into the folder it already lives in is a no-op, not a duplicate.
                if (move && source.parentFile?.absolutePath == File(destinationDir).absolutePath) {
                    return@runCatching true
                }
                val target = uniqueTarget(File(destinationDir), source.name)
                if (move && source.renameTo(target)) return@runCatching true
                if (source.isDirectory) {
                    source.copyRecursively(target, overwrite = false)
                } else {
                    source.copyTo(target, overwrite = false)
                }
                if (move) {
                    if (source.isDirectory) source.deleteRecursively() else source.delete()
                } else {
                    true
                }
            }.getOrDefault(false)
            if (!ok) allOk = false
            onProgress(index + 1, sources.size)
        }
        allOk
    }

    /** True when [candidate] is [ancestor] itself or lives underneath it. */
    private fun isInside(candidate: String, ancestor: String): Boolean {
        val normalizedAncestor = File(ancestor).absolutePath
        val normalizedCandidate = File(candidate).absolutePath
        return normalizedCandidate == normalizedAncestor ||
            normalizedCandidate.startsWith("$normalizedAncestor${File.separator}")
    }

    /** `report.txt` → `report (1).txt` when the name is taken. */
    private fun uniqueTarget(directory: File, name: String): File {
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

    /** Spaces are legal in file names; path separators and the dot entries are not. */
    private fun isValidName(name: String): Boolean =
        name.isNotBlank() && name.trim() == name &&
            name != "." && name != ".." && !name.contains('/')

    // -----------------------------------------------------------------------
    // Text
    // -----------------------------------------------------------------------

    /**
     * Reads [path] as UTF-8 text. Refuses binaries (a NUL byte in the first [BINARY_SNIFF_BYTES])
     * and anything over [limitBytes], so the editor can never blow up the heap on a video file.
     */
    suspend fun readText(path: String, limitBytes: Long = MAX_EDITABLE_BYTES): TextLoadResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path)
                if (!file.isFile || !file.canRead()) return@withContext TextLoadResult.Failed
                if (file.length() > limitBytes) return@withContext TextLoadResult.TooLarge(file.length())
                val isBinary = file.inputStream().use { stream ->
                    val head = ByteArray(BINARY_SNIFF_BYTES)
                    val read = stream.read(head)
                    (0 until read.coerceAtLeast(0)).any { head[it] == 0.toByte() }
                }
                if (isBinary) TextLoadResult.Binary else TextLoadResult.Success(file.readText())
            }.getOrDefault(TextLoadResult.Failed)
        }

    /** Writes through a sibling temp file so a failed write cannot truncate the original. */
    suspend fun writeText(path: String, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(path)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(text)
            if (target.exists() && !target.delete()) {
                temp.delete()
                return@runCatching false
            }
            temp.renameTo(target)
        }.getOrDefault(false)
    }

    /** Writes editor content out to a user-chosen SAF destination ("Save as"). */
    suspend fun writeTextTo(destination: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destination)?.use { it.write(text.toByteArray()) } != null
        }.getOrDefault(false)
    }

    // -----------------------------------------------------------------------
    // Sharing
    // -----------------------------------------------------------------------

    /** Content URI other apps can read; null when the path sits outside the declared file paths. */
    fun contentUri(path: String): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(path))
    }.getOrNull()

    fun mimeType(path: String): String {
        val extension = path.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
    }

    companion object {
        /** Files above this are offered read-only-with-a-warning rather than loaded silently. */
        const val MAX_EDITABLE_BYTES = 5L * 1024 * 1024

        /** Absolute ceiling for "open anyway" on a large file. */
        const val MAX_FORCED_BYTES = 20L * 1024 * 1024

        private const val BINARY_SNIFF_BYTES = 8 * 1024
    }
}
