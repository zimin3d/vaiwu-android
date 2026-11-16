package su.neit.vaiwu

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays the synthesized chiptune effects on a single background thread.
 *
 * A short [AudioTrack] is created per effect. The clips are tiny, and this
 * keeps playback independent from route/sample-rate changes while never
 * touching the UI thread. Requests are queued; when the queue is saturated the
 * oldest request is dropped so the newest feedback (the one the player is
 * waiting for) is still heard.
 */
class SoundPlayer {
    @Volatile
    var enabled: Boolean = true

    private val pending = ArrayBlockingQueue<ByteArray>(MaxPending)
    private val closed = AtomicBoolean(false)
    private val lock = Any()
    private var worker: Thread? = null

    /** Queues [effect] for playback. Safe to call from the UI thread. */
    fun play(effect: Int) {
        if (!enabled || closed.get()) return
        val pcm = try {
            SoundSynth.render(effect)
        } catch (_: Throwable) {
            // A missing native library must degrade to silence, not a crash.
            null
        } ?: return
        if (pcm.isEmpty()) return
        if (!pending.offer(pcm)) {
            pending.poll()
            pending.offer(pcm)
        }
        ensureWorker()
    }

    /** Stops accepting effects and releases the worker. */
    fun close() {
        closed.set(true)
        pending.clear()
        synchronized(lock) {
            worker?.interrupt()
            worker = null
        }
    }

    private fun ensureWorker() {
        if (closed.get()) return
        synchronized(lock) {
            val current = worker
            if (current != null && current.isAlive) return
            val thread = Thread(::drain, "vaiwu-sfx")
            thread.isDaemon = true
            worker = thread
            thread.start()
        }
    }

    private fun drain() {
        while (!closed.get()) {
            val pcm = try {
                pending.poll(WorkerIdleMs, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                return
            } ?: continue
            // The toggle may have been switched off while the request waited.
            if (!enabled) continue
            playNow(pcm)
        }
    }

    private fun playNow(pcm: ByteArray) {
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SoundSynth.SampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
        } catch (_: Throwable) {
            return
        }
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            val durationMs = pcm.size.toLong() * 1000L / (SoundSynth.SampleRate * 2L)
            Thread.sleep(durationMs + TailMs)
        } catch (_: InterruptedException) {
            // Closing: fall through and release the track.
        } catch (_: Throwable) {
            // A route change can kill playback; the effect is simply skipped.
        } finally {
            try {
                track.stop()
            } catch (_: Throwable) {
                // A track that never started is already stopped.
            }
            try {
                track.release()
            } catch (_: Throwable) {
                // Ignore release races during shutdown.
            }
        }
    }

    private companion object {
        const val MaxPending = 6
        const val WorkerIdleMs = 500L
        const val TailMs = 30L
    }
}
