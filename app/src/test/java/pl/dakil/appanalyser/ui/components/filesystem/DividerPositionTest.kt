package pl.dakil.appanalyser.ui.components.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.dakil.appanalyser.domain.PaneId

class DividerPositionTest {

    @Test
    fun `inside the limits the divider follows the finger`() {
        val position = dividerPosition(0.3f)
        assertEquals(0.3f, position.shown, 1e-6f)
        assertNull(position.keep)
    }

    @Test
    fun `near the middle it snaps to an even split`() {
        assertEquals(0.5f, dividerPosition(0.52f).shown, 1e-6f)
    }

    @Test
    fun `past a limit it resists without arming`() {
        val position = dividerPosition(0.14f)
        assertTrue(position.shown > 0.14f && position.shown < 0.2f)
        assertNull(position.keep)
    }

    @Test
    fun `dragged to the top it closes the top pane, keeping the bottom one`() {
        assertEquals(PaneId.SECONDARY, dividerPosition(0.05f).keep)
    }

    @Test
    fun `dragged to the bottom it keeps the top pane`() {
        assertEquals(PaneId.PRIMARY, dividerPosition(0.96f).keep)
    }

    @Test
    fun `the squeezed pane never disappears before release`() {
        assertTrue(dividerPosition(0f).shown > 0f)
        assertTrue(dividerPosition(1f).shown < 1f)
    }
}
