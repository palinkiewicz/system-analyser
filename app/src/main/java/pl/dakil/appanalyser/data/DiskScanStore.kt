package pl.dakil.appanalyser.data

import pl.dakil.appanalyser.domain.DiskFile
import pl.dakil.appanalyser.domain.DiskNode
import pl.dakil.appanalyser.domain.DiskScan
import pl.dakil.appanalyser.domain.ScanCache
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Keeps the remembered disk usage scans across process death, one file per scan in [directory]
 * (the app's cache dir, so Android may clear it whenever it likes — a lost file just means a
 * rescan). The files mirror the in-memory list exactly: [sync] writes what changed and deletes
 * every file whose scan is no longer remembered, so superseded scans never linger on disk.
 *
 * Plain `java.io` with no `android.*`, so it is unit-tested off-device.
 */
class DiskScanStore(
    private val directory: File,
    /** Localized name for the synthetic "Restricted by Android" entry, resolved at load time. */
    private val restrictedLabel: () -> String,
) {

    /** The scan object last written for each file, so unchanged scans aren't rewritten. */
    private val written = HashMap<String, DiskScan>()

    /**
     * Every readable scan on disk, folded through [ScanCache.add] oldest first so the usual rules
     * (supersede, graft, cap) hold even for files left behind by an older version. Unreadable or
     * outdated files are deleted.
     */
    fun load(limit: Int, largestLimit: Int): List<DiskScan> {
        val files = directory.listFiles { file -> file.name.endsWith(EXTENSION) }.orEmpty()
        val loaded = files.mapNotNull { file ->
            val scan = try {
                file.inputStream().buffered().use { read(DataInputStream(it)) }
            } catch (_: IOException) {
                null
            } catch (_: RuntimeException) {
                null
            }
            if (scan == null) file.delete()
            scan
        }
        val scans = loaded.sortedBy { it.scannedAt }
            .fold(emptyList<DiskScan>()) { acc, scan -> ScanCache.add(acc, scan, limit, largestLimit) }
        written.clear()
        scans.forEach { written[fileNameFor(it.root.path)] = it }
        deleteAllBut(written.keys)
        return scans
    }

    /** Makes the directory match [scans]: writes new or changed ones, deletes the rest. */
    fun sync(scans: List<DiskScan>) {
        directory.mkdirs()
        val keep = HashSet<String>()
        for (scan in scans) {
            val name = fileNameFor(scan.root.path)
            keep += name
            if (written[name] === scan) continue
            if (writeAtomically(File(directory, name), scan)) written[name] = scan
        }
        written.keys.retainAll(keep)
        deleteAllBut(keep)
    }

    private fun deleteAllBut(keep: Set<String>) {
        directory.listFiles().orEmpty()
            .filter { it.name !in keep }
            .forEach { it.delete() }
    }

    /** Through a temp file and a rename, so a crash mid-write never leaves a half file to load. */
    private fun writeAtomically(target: File, scan: DiskScan): Boolean {
        val temp = File(directory, "${target.name}.tmp")
        return try {
            DataOutputStream(BufferedOutputStream(temp.outputStream())).use { write(it, scan) }
            temp.renameTo(target) || run { temp.delete(); false }
        } catch (_: IOException) {
            temp.delete()
            false
        }
    }

    // -----------------------------------------------------------------------
    // Format: magic, version, header, largest files, then the tree depth-first. Children store
    // only their last path segment; full paths are rebuilt from the parent while reading.
    // -----------------------------------------------------------------------

    private fun write(out: DataOutputStream, scan: DiskScan) {
        out.writeInt(MAGIC)
        out.writeInt(VERSION)
        out.writeUTF(scan.root.path)
        out.writeLong(scan.scannedAt)
        out.writeBoolean(scan.stale)
        out.writeLong(scan.volumeTotalBytes)
        out.writeLong(scan.volumeFreeBytes)
        out.writeInt(scan.largestFiles.size)
        scan.largestFiles.forEach {
            out.writeUTF(it.path)
            out.writeLong(it.sizeBytes)
            out.writeLong(it.lastModified)
        }
        writeNode(out, scan.root)
    }

    private fun writeNode(out: DataOutputStream, node: DiskNode) {
        out.writeUTF(node.path.substringAfterLast('/'))
        out.writeLong(node.sizeBytes)
        out.writeInt(node.fileCount)
        out.writeLong(node.ownFilesBytes)
        out.writeInt(node.ownFileCount)
        out.writeByte((if (node.restricted) FLAG_RESTRICTED else 0) or (if (node.isRestrictedSpace) FLAG_RESTRICTED_SPACE else 0))
        out.writeInt(node.children.size)
        node.children.forEach { writeNode(out, it) }
    }

    /** Null for a file that isn't ours or is from another format version. */
    private fun read(input: DataInputStream): DiskScan? {
        if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
        val rootPath = input.readUTF()
        val scannedAt = input.readLong()
        val stale = input.readBoolean()
        val total = input.readLong()
        val free = input.readLong()
        val largest = List(input.readInt()) {
            val path = input.readUTF()
            DiskFile(
                name = path.substringAfterLast('/'),
                path = path,
                sizeBytes = input.readLong(),
                lastModified = input.readLong(),
            )
        }
        val root = readNode(input, parentPath = null, rootPath = rootPath)
        return DiskScan(root, largest, scannedAt, stale, total, free)
    }

    private fun readNode(input: DataInputStream, parentPath: String?, rootPath: String): DiskNode {
        val segment = input.readUTF()
        val sizeBytes = input.readLong()
        val fileCount = input.readInt()
        val ownFilesBytes = input.readLong()
        val ownFileCount = input.readInt()
        val flags = input.readByte().toInt()
        val path = if (parentPath == null) rootPath else "$parentPath/$segment"
        val restrictedSpace = flags and FLAG_RESTRICTED_SPACE != 0
        val children = List(input.readInt()) { readNode(input, path, rootPath) }
        return DiskNode(
            // The scanner names a directory after its last segment; the synthetic entry gets the
            // label in the current language.
            name = if (restrictedSpace) restrictedLabel() else segment,
            path = path,
            sizeBytes = sizeBytes,
            fileCount = fileCount,
            ownFilesBytes = ownFilesBytes,
            ownFileCount = ownFileCount,
            restricted = flags and FLAG_RESTRICTED != 0,
            children = children,
            isRestrictedSpace = restrictedSpace,
        )
    }

    /** A stable, filesystem-safe name per scanned root. */
    private fun fileNameFor(rootPath: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rootPath.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) } + EXTENSION
    }

    private companion object {
        const val EXTENSION = ".scan"
        const val MAGIC = 0x44555343 // "DUSC"
        const val VERSION = 1
        const val FLAG_RESTRICTED = 1
        const val FLAG_RESTRICTED_SPACE = 2
    }
}
