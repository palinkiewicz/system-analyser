package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileDropTest {

    @Test
    fun `drops into a sibling folder`() {
        assertEquals("/s/b", dropDestination(listOf("/s/a/report.txt"), "/s/b"))
    }

    @Test
    fun `refuses an empty payload`() {
        assertNull(dropDestination(emptyList(), "/s/b"))
    }

    @Test
    fun `refuses dropping a folder onto itself`() {
        assertNull(dropDestination(listOf("/s/a"), "/s/a"))
    }

    @Test
    fun `refuses dropping a folder into its own descendant`() {
        assertNull(dropDestination(listOf("/s/a"), "/s/a/inner/deeper"))
    }

    @Test
    fun `refuses the folder every item already lives in`() {
        assertNull(dropDestination(listOf("/s/a/one.txt", "/s/a/two.txt"), "/s/a"))
    }

    @Test
    fun `accepts the current folder when the items come from different parents`() {
        assertEquals("/s/a", dropDestination(listOf("/s/a/one.txt", "/s/b/two.txt"), "/s/a"))
    }

    @Test
    fun `a name sharing a prefix is not a descendant`() {
        assertEquals("/s/foobar", dropDestination(listOf("/s/foo"), "/s/foobar"))
    }

    @Test
    fun `any dragged folder containing the destination refuses the whole drop`() {
        assertNull(dropDestination(listOf("/s/x.txt", "/s/a"), "/s/a/inner"))
    }

    @Test
    fun `normalizes a trailing slash on the destination`() {
        assertEquals("/s/b", dropDestination(listOf("/s/a/report.txt"), "/s/b/"))
    }

    @Test
    fun `transfer copies into the items' own folder as a duplicate but refuses moving there`() {
        val paths = listOf("/s/a/one.txt", "/s/a/two.txt")
        assertTrue(transferAllowed(paths, "/s/a", ClipboardOperation.COPY))
        assertFalse(transferAllowed(paths, "/s/a", ClipboardOperation.MOVE))
    }

    @Test
    fun `transfer refuses a selected folder and its descendants for both operations`() {
        val paths = listOf("/s/x.txt", "/s/a")
        for (operation in ClipboardOperation.entries) {
            assertFalse(transferAllowed(paths, "/s/a", operation))
            assertFalse(transferAllowed(paths, "/s/a/inner", operation))
        }
    }

    @Test
    fun `transfer allows both operations into an unrelated folder`() {
        val paths = listOf("/s/a/report.txt")
        assertTrue(transferAllowed(paths, "/s/foobar", ClipboardOperation.COPY))
        assertTrue(transferAllowed(paths, "/s/foobar", ClipboardOperation.MOVE))
    }

    @Test
    fun `transfer refuses an empty payload`() {
        assertFalse(transferAllowed(emptyList(), "/s/b", ClipboardOperation.COPY))
        assertFalse(transferAllowed(emptyList(), "/s/b", ClipboardOperation.MOVE))
    }
}
