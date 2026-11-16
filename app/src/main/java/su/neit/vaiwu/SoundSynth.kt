package su.neit.vaiwu

/**
 * Renders the game's chiptune effects with the native synthesizer that the
 * firmware already ships. No audio assets are involved: the PCM16 mono buffer
 * is generated on demand and handed to [SoundPlayer].
 */
object SoundSynth {
    const val SampleRate = 22_050

    /**
     * Returns little-endian PCM16 mono for [effect], or null when the native
     * library is unavailable or rejects the id. Callers treat null as silence.
     */
    fun render(effect: Int): ByteArray? = try {
        nativeRenderSfx(effect, SampleRate)
    } catch (_: Throwable) {
        // A missing/broken native library must never crash the UI.
        null
    }

    init {
        System.loadLibrary("vaiwu_native")
    }

    private external fun nativeRenderSfx(effect: Int, sampleRate: Int): ByteArray?
}
