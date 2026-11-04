package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class FileFavoriteTest {

    private val favorites = listOf(
        FileFavorite("/s/Photos", isDirectory = true, colorIndex = 2),
        FileFavorite("/s/Photos/Trip/best.jpg", isDirectory = false),
        FileFavorite("/s/PhotosOld", isDirectory = true),
        FileFavorite("/s/notes.txt", isDirectory = false),
    )

    @Test
    fun `a renamed folder takes its favourite and everything inside along`() {
        val renamed = favorites.renamed("/s/Photos", "/s/Pictures")
        assertEquals(
            listOf("/s/Pictures", "/s/Pictures/Trip/best.jpg", "/s/PhotosOld", "/s/notes.txt"),
            renamed.map { it.path },
        )
        assertEquals(2, renamed.first().colorIndex)
    }

    @Test
    fun `a renamed file moves only itself`() {
        val renamed = favorites.renamed("/s/notes.txt", "/s/todo.txt")
        assertEquals("/s/todo.txt", renamed.last().path)
        assertEquals(favorites.dropLast(1), renamed.dropLast(1))
    }

    @Test
    fun `a name sharing a prefix is not inside the renamed folder`() {
        val renamed = favorites.renamed("/s/Photos", "/s/Pictures")
        assertEquals("/s/PhotosOld", renamed[2].path)
    }

    @Test
    fun `the name is the last path segment`() {
        assertEquals("best.jpg", favorites[1].name)
    }
}
