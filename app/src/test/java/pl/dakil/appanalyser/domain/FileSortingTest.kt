package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSortingTest {

    private fun folder(name: String, modified: Long) =
        FileEntry(name, "/s/$name", isDirectory = true, sizeBytes = 0, lastModified = modified, isHidden = false)

    private fun file(name: String, size: Long, modified: Long) =
        FileEntry(name, "/s/$name", isDirectory = false, sizeBytes = size, lastModified = modified, isHidden = false)

    private val entries = listOf(
        file("b.txt", size = 30, modified = 1),
        folder("Music", modified = 3),
        file("a.txt", size = 10, modified = 2),
        folder("alarms", modified = 1),
        folder("Zeta", modified = 2),
    )

    private fun sorted(sort: FileSortOrder, descending: Boolean, sortFolders: Boolean) =
        entries.sortedWith(fileComparator(sort, descending, sortFolders)).map { it.name }

    @Test
    fun `folders follow the chosen sort when sorted`() {
        assertEquals(
            listOf("alarms", "Zeta", "Music", "b.txt", "a.txt"),
            sorted(FileSortOrder.DATE, descending = false, sortFolders = true),
        )
    }

    @Test
    fun `sorted folders follow descending too`() {
        assertEquals(
            listOf("Music", "Zeta", "alarms", "a.txt", "b.txt"),
            sorted(FileSortOrder.DATE, descending = true, sortFolders = true),
        )
    }

    @Test
    fun `unsorted folders stay A to Z under any order`() {
        for (sort in FileSortOrder.entries) {
            for (descending in listOf(false, true)) {
                assertEquals(
                    listOf("alarms", "Music", "Zeta"),
                    sorted(sort, descending, sortFolders = false).take(3),
                )
            }
        }
    }

    @Test
    fun `files keep the chosen order either way`() {
        for (sortFolders in listOf(false, true)) {
            assertEquals(listOf("b.txt", "a.txt"), sorted(FileSortOrder.SIZE, descending = true, sortFolders).drop(3))
            assertEquals(listOf("b.txt", "a.txt"), sorted(FileSortOrder.DATE, descending = false, sortFolders).drop(3))
        }
    }

    @Test
    fun `folders always come first`() {
        assertEquals(
            listOf(true, true, true, false, false),
            entries.sortedWith(fileComparator(FileSortOrder.SIZE, descending = true, sortFolders = true)).map { it.isDirectory },
        )
    }
}
