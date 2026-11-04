package pl.dakil.appanalyser.domain

/**
 * How a folder listing is ordered: folders always on top, like the system file manager, then each
 * group by [sort] (ties falling back to the name, so a re-list is stable), reversed by [descending].
 *
 * With [sortFolders] off, folders ignore both and stay A→Z — the "Sort folders" option of the sort
 * menu, for keeping a familiar folder list while sorting files by date or size.
 */
fun fileComparator(sort: FileSortOrder, descending: Boolean, sortFolders: Boolean): Comparator<FileEntry> {
    val byName = Comparator<FileEntry> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
    val chosen: Comparator<FileEntry> = when (sort) {
        FileSortOrder.NAME -> byName
        FileSortOrder.SIZE -> compareBy<FileEntry> { it.sizeBytes }.then(byName)
        FileSortOrder.DATE -> compareBy<FileEntry> { it.lastModified }.then(byName)
        FileSortOrder.TYPE -> compareBy<FileEntry> { it.extension }.then(byName)
    }.let { if (descending) it.reversed() else it }
    return Comparator { a, b ->
        when {
            a.isDirectory != b.isDirectory -> if (a.isDirectory) -1 else 1
            a.isDirectory && !sortFolders -> byName.compare(a, b)
            else -> chosen.compare(a, b)
        }
    }
}
