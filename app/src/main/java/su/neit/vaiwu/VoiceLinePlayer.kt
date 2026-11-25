package su.neit.vaiwu

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Lightweight `.ogg` voice-line player for character reactions.
 *
 * Checks an active downloaded pack's `voices/<hash>.ogg` first, falling back to
 * bundled `assets/voices/<character>/<hash>.ogg`. Silently skips missing lines.
 */
class VoiceLinePlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(characterId: Int, text: String, packVoiceFile: File?) {
        if (text.isBlank()) return
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            val configured = when {
                packVoiceFile != null && packVoiceFile.isFile -> {
                    mp.setDataSource(packVoiceFile.absolutePath)
                    true
                }
                else -> {
                    val slug = characterIds.getOrNull(characterId)
                    val assetPath = slug?.let { "voices/$it/${voiceLineKey(text)}.ogg" }
                    if (assetPath != null) {
                        runCatching {
                            context.assets.openFd(assetPath).use { afd ->
                                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                            true
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                }
            }
            if (!configured) {
                mp.release()
                return
            }
            mp.setOnCompletionListener { completed ->
                if (player === completed) player = null
                runCatching { completed.release() }
            }
            mp.setOnErrorListener { failed, _, _ ->
                if (player === failed) player = null
                runCatching { failed.release() }
                true
            }
            mp.prepare()
            player = mp
            mp.start()
        } catch (_: Exception) {
            runCatching { mp.release() }
        }
    }

    fun stop() {
        val current = player ?: return
        player = null
        runCatching {
            if (current.isPlaying) current.stop()
        }
        runCatching { current.release() }
    }

    fun release() = stop()
}
