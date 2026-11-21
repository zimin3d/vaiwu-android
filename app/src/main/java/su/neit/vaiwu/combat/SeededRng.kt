package su.neit.vaiwu.combat

/**
 * Pure deterministic PRNG stored directly inside [CombatState] so Undo and
 * dry-run enemy intent previews never mutate external state or re-roll dice.
 */
data class SeededRng(val seed: Long) {
    /** Advances the LCG and returns a value in `[0.0, 1.0)` plus the next state. */
    fun nextDouble(): Pair<Double, SeededRng> {
        val next = (seed * MULTIPLIER + INCREMENT) and MASK
        val value = (next ushr 16).toDouble() / 65536.0
        return value to SeededRng(next)
    }

    /** Returns an integer uniformly in `[minInclusive, maxInclusive]` plus the next state. */
    fun nextInt(minInclusive: Int, maxInclusive: Int): Pair<Int, SeededRng> {
        if (maxInclusive <= minInclusive) return minInclusive to this
        val (u, next) = nextDouble()
        val span = maxInclusive - minInclusive + 1
        val value = minInclusive + (u * span).toInt().coerceIn(0, span - 1)
        return value to next
    }

    companion object {
        private const val MULTIPLIER = 1664525L
        private const val INCREMENT = 1013904223L
        private const val MASK = 0xFFFFFFFFL

        fun fromSeed(seed: Long): SeededRng = SeededRng((seed and MASK) or 1L)
    }
}
