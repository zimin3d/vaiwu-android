package su.neit.vaiwu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun `newer patch versions are detected`() {
        assertTrue(isNewerVersion("0.1.1", "0.1.0"))
        assertTrue(isNewerVersion("1.0", "0.9.9"))
        assertTrue(isNewerVersion("v2.0.0", "1.9.9"))
    }

    @Test
    fun `same and older versions are rejected`() {
        assertFalse(isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.0.9", "0.1.0"))
    }

    @Test
    fun `pre-release builds compare by their numeric core`() {
        assertFalse(isNewerVersion("0.1.0-rc1", "0.1.0"))
        assertTrue(isNewerVersion("0.2.0-beta", "0.1.0"))
    }

    @Test
    fun `non numeric tags are ignored`() {
        assertFalse(isNewerVersion("nightly", "0.1.0"))
        assertFalse(isNewerVersion("0.2.0", "build-42"))
    }
}
