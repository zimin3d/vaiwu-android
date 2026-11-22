package su.neit.vaiwu

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import java.util.Locale

/** Manages Android speech-to-text recognition, Russian TTS playback, and runtime audio/notification permissions. */
class VoiceController(
    private val activity: ComponentActivity,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var speechReady = false
    private lateinit var tts: TextToSpeech
    private val recognizer = if (SpeechRecognizer.isRecognitionAvailable(activity)) {
        SpeechRecognizer.createSpeechRecognizer(activity)
    } else {
        null
    }
    private val audioPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecognition() else onError(activity.getString(R.string.voice_need_microphone))
    }
    private val notificationPermission = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) onError(activity.getString(R.string.voice_need_notifications))
    }

    init {
        tts = TextToSpeech(activity) { status ->
            speechReady = status == TextToSpeech.SUCCESS
            if (speechReady) tts.language = Locale("ru")
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
                    ?: onError(activity.getString(R.string.voice_not_recognized))
            }
            override fun onError(error: Int) = onError(activity.getString(R.string.voice_recognition_failed))
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    /** Starts Russian speech recognition after verifying microphone permission. */
    fun listen() {
        if (recognizer == null) {
            onError(activity.getString(R.string.voice_unavailable))
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecognition()
        } else {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Speaks [text] via Android TTS (truncated to [MaxTtsUtteranceChars] to stay within platform IPC limits). */
    fun speak(text: String) {
        if (speechReady) tts.speak(text.take(MaxTtsUtteranceChars), TextToSpeech.QUEUE_FLUSH, null, "vaiwu-chat")
    }

    /** Prompts for `POST_NOTIFICATIONS` on Android 13+ (API 33+) so scheduled reminders can appear. */
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Releases the speech recognizer and TTS engine when the host activity is destroyed. */
    fun close() {
        recognizer?.destroy()
        tts.stop()
        tts.shutdown()
    }

    private fun startRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        recognizer?.startListening(intent)
    }

    private companion object {
        /** Android `TextToSpeech.getMaxSpeechInputLength()` safe ceiling (4,000 chars). */
        const val MaxTtsUtteranceChars = 4_000
    }
}
