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

    fun speak(text: String) {
        if (speechReady) tts.speak(text.take(4_000), TextToSpeech.QUEUE_FLUSH, null, "vaiwu-chat")
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
}
