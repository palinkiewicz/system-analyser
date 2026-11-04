package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollMemoryTest {

    private val far = ScrollPosition(40, 12)

    @Test
    fun `a folder never visited starts at the top`() {
        assertEquals(ScrollPosition.Top, ScrollMemory().positionFor("/s/a"))
    }

    @Test
    fun `going up restores where the parent was left`() {
        val memory = ScrollMemory()
        memory.record("/s/a", far, currentPath = "/s/a")
        memory.trimTo("/s/a/child")
        memory.trimTo("/s/a")
        assertEquals(far, memory.positionFor("/s/a"))
    }

    @Test
    fun `a breadcrumb jump several levels up restores that ancestor`() {
        val memory = ScrollMemory()
        memory.record("/s", far, currentPath = "/s")
        memory.trimTo("/s/a")
        memory.record("/s/a", ScrollPosition(3, 0), currentPath = "/s/a")
        memory.trimTo("/s/a/b/c")
        memory.trimTo("/s")
        assertEquals(far, memory.positionFor("/s"))
    }

    @Test
    fun `going back down into a folder starts at its top`() {
        val memory = ScrollMemory()
        memory.record("/s/a/child", far, currentPath = "/s/a/child")
        memory.trimTo("/s/a")
        memory.trimTo("/s/a/child")
        assertEquals(ScrollPosition.Top, memory.positionFor("/s/a/child"))
    }

    @Test
    fun `a late report from a folder that was left upwards is ignored`() {
        val memory = ScrollMemory()
        memory.trimTo("/s/a")
        memory.record("/s/a/child", far, currentPath = "/s/a")
        assertEquals(ScrollPosition.Top, memory.positionFor("/s/a/child"))
    }

    @Test
    fun `jumping to an unrelated folder forgets everything`() {
        val memory = ScrollMemory()
        memory.record("/s/a", far, currentPath = "/s/a")
        memory.trimTo("/s/b")
        memory.trimTo("/s/a")
        assertEquals(ScrollPosition.Top, memory.positionFor("/s/a"))
    }

    @Test
    fun `the volume chooser forgets everything`() {
        val memory = ScrollMemory()
        memory.record("/s", far, currentPath = "/s")
        memory.trimTo(null)
        assertEquals(ScrollPosition.Top, memory.positionFor("/s"))
    }

    @Test
    fun `a name sharing a prefix is not an ancestor`() {
        val memory = ScrollMemory()
        memory.record("/s/a", far, currentPath = "/s/a")
        memory.trimTo("/s/ab")
        memory.trimTo("/s/a")
        assertEquals(ScrollPosition.Top, memory.positionFor("/s/a"))
    }
}
