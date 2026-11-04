package pl.dakil.appanalyser.domain

/** A single directory child, captured at listing time (never re-stat'ed while rendering). */
data class FileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val isHidden: Boolean,
) {
    /** Lowercase extension without the dot, empty for directories and extension-less files. */
    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()
}

enum class FileSortOrder { NAME, SIZE, DATE, TYPE }

enum class ClipboardOperation { COPY, MOVE }

/** The two browsers of dual pane mode; [PRIMARY] is also the only one in single pane mode. */
enum class PaneId {
    PRIMARY, SECONDARY;

    val other: PaneId get() = if (this == PRIMARY) SECONDARY else PRIMARY
}

/** What a drag-and-drop between panes does: asks each time, or always moves or copies. */
enum class FileDropAction { ASK, MOVE, COPY }

/**
 * How dual pane mode uses a portrait phone: both panes stacked at once, or one at a time with a
 * swipe between them. Wide layouts always show both side by side.
 */
enum class DualPaneStyle { SPLIT, SWIPE }

/** When the file list drops to dense one-line rows: always, only while split in two, or never. */
enum class FileCompactView { ALWAYS, DUAL_PANE, NEVER }

/** Files staged by Copy/Cut, waiting for a Paste in some other directory. */
data class FileClipboard(
    val paths: List<String>,
    val operation: ClipboardOperation,
)

/** A browsable storage volume — internal storage or a removable card. */
data class StorageRoot(
    val label: String,
    val path: String,
    val isRemovable: Boolean,
    /** Capacity from `StatFs`; 0 when the volume doesn't report it. */
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

/** Outcome of loading a file into the text editor. */
sealed interface TextLoadResult {
    data class Success(val text: String) : TextLoadResult
    data class TooLarge(val sizeBytes: Long) : TextLoadResult
    data object Binary : TextLoadResult
    data object Failed : TextLoadResult
}
