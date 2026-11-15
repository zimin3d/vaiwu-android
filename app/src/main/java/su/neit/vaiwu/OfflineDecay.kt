package su.neit.vaiwu

/**
 * Limits applied while the game is not running.
 *
 * The native simulation drains hunger at 0.5 points per minute
 * (`kHungerPerMin` in `VaiwuPet.cpp`), so a pet left alone for a few hours
 * could otherwise starve to death while the app is closed. Catch-up decay is
 * capped by both wall-clock time and the saved hunger value.
 */
internal object OfflineDecay {
    const val MaxOfflineMs = 12L * 60L * 60L * 1000L
    const val MinOfflineHunger = 1.0f
    const val HungerDecayPerMinute = 0.5f

    /**
     * Returns the timestamp the simulation should resume from. A valid past
     * [savedAt] is moved forward by at most `MaxOfflineMs` and at most to the
     * point where [hunger] would reach [MinOfflineHunger]. Missing or future
     * timestamps fall back to [now].
     */
    fun effectiveSaveTime(savedAt: Long, now: Long, hunger: Float): Long {
        if (savedAt <= 0L || now <= savedAt) return now
        val hungerBudgetMs = (
            (hunger - MinOfflineHunger).coerceAtLeast(0f) /
                HungerDecayPerMinute * 60_000f
            ).toLong()
        val allowedMs = minOf(MaxOfflineMs, hungerBudgetMs)
        return maxOf(savedAt, now - allowedMs)
    }
}
