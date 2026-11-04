package pl.dakil.appanalyser.domain

enum class FileOperationKind { COPY, MOVE, DELETE }

/**
 * A snapshot of a running copy, move or delete. [items] counts what the user picked (the top-level
 * paths); [doneFiles]/[totalFiles] and [doneBytes]/[totalBytes] count everything inside them.
 * Deletes don't measure bytes, so their [fraction] goes by files.
 */
data class FileOperationProgress(
    val kind: FileOperationKind,
    val items: Int,
    val doneFiles: Int = 0,
    val totalFiles: Int = 0,
    val doneBytes: Long = 0L,
    val totalBytes: Long = 0L,
    /** The file being worked on right now; null while the operation is still measuring. */
    val currentName: String? = null,
) {
    val fraction: Float
        get() = when {
            totalBytes > 0 -> (doneBytes.toDouble() / totalBytes).toFloat()
            totalFiles > 0 -> doneFiles.toFloat() / totalFiles
            else -> 0f
        }.coerceIn(0f, 1f)
}
