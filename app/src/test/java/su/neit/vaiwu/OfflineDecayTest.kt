package su.neit.vaiwu

import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineDecayTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `short absence keeps the saved timestamp`() {
        val savedAt = now - 5 * 60_000L

        assertEquals(savedAt, OfflineDecay.effectiveSaveTime(savedAt, now, 100f))
    }

    @Test
    fun `long absence is capped by the hunger budget`() {
        val savedAt = now - 48 * 60 * 60_000L

        // 99 hunger points / 0.5 per minute = 198 minutes.
        assertEquals(
            now - 198 * 60_000L,
            OfflineDecay.effectiveSaveTime(savedAt, now, 100f),
        )
    }

    @Test
    fun `wall clock cap applies when hunger would allow more`() {
        val savedAt = now - 48 * 60 * 60_000L

        assertEquals(
            now - OfflineDecay.MaxOfflineMs,
            OfflineDecay.effectiveSaveTime(savedAt, now, 100_000f),
        )
    }

    @Test
    fun `low hunger caps catch-up before starvation`() {
        val savedAt = now - 48 * 60 * 60_000L

        val effective = OfflineDecay.effectiveSaveTime(savedAt, now, 11f)

        // 10 hunger points / 0.5 per minute = 20 minutes.
        assertEquals(now - 20 * 60_000L, effective)
    }

    @Test
    fun `hunger at the minimum skips offline decay entirely`() {
        val savedAt = now - 60 * 60_000L

        assertEquals(now, OfflineDecay.effectiveSaveTime(savedAt, now, 0f))
    }

    @Test
    fun `clock skew never moves the timestamp into the future`() {
        val savedAt = now + 60_000L

        assertEquals(now, OfflineDecay.effectiveSaveTime(savedAt, now, 100f))
    }

    @Test
    fun `missing timestamp falls back to now`() {
        assertEquals(now, OfflineDecay.effectiveSaveTime(0L, now, 100f))
    }
}
