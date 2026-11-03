package pl.dakil.appanalyser.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StandardFolderTest {

    @Test
    fun `recognises top-level folders of internal storage`() {
        assertEquals(StandardFolder.MUSIC, StandardFolder.of("/storage/emulated/0/Music"))
        assertEquals(StandardFolder.DOWNLOAD, StandardFolder.of("/storage/emulated/0/Download"))
        assertEquals(StandardFolder.ANDROID, StandardFolder.of("/storage/emulated/0/Android"))
    }

    @Test
    fun `recognises folders of a secondary user and a removable card`() {
        assertEquals(StandardFolder.DCIM, StandardFolder.of("/storage/emulated/10/DCIM"))
        assertEquals(StandardFolder.DCIM, StandardFolder.of("/storage/1A2B-3C4D/DCIM"))
    }

    @Test
    fun `ignores case`() {
        assertEquals(StandardFolder.PICTURES, StandardFolder.of("/storage/1A2B-3C4D/pictures"))
        assertEquals(StandardFolder.CAMERA, StandardFolder.of("/storage/1A2B-3C4D/dcim/CAMERA"))
    }

    @Test
    fun `recognises the camera subfolders`() {
        assertEquals(StandardFolder.CAMERA, StandardFolder.of("/storage/emulated/0/DCIM/Camera"))
        assertEquals(StandardFolder.SCREENSHOTS, StandardFolder.of("/storage/emulated/0/DCIM/Screenshots"))
        assertEquals(StandardFolder.SCREENSHOTS, StandardFolder.of("/storage/emulated/0/Pictures/Screenshots"))
    }

    @Test
    fun `ignores standard names away from the volume root`() {
        assertNull(StandardFolder.of("/storage/emulated/0/Backup/Music"))
        assertNull(StandardFolder.of("/storage/emulated/0/Android/media/Music"))
        assertNull(StandardFolder.of("/storage/emulated/0/Music/Camera"))
        assertNull(StandardFolder.of("/data/Music"))
    }

    @Test
    fun `does not mistake the emulated user folder for a volume child`() {
        assertNull(StandardFolder.of("/storage/emulated/0"))
        assertNull(StandardFolder.of("/storage/emulated/Music"))
    }

    @Test
    fun `ignores other folders`() {
        assertNull(StandardFolder.of("/storage/emulated/0/WhatsApp"))
        assertNull(StandardFolder.of("/storage/emulated/0/DCIM/Instagram"))
    }
}
