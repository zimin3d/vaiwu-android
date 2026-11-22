package su.neit.vaiwu

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Chunk size (8 KiB) for bounded UTF-8 HTTP response streaming. */
private const val STREAM_BUFFER_BYTES = 8 * 1024

/** Maximum number of recent chat turns forwarded as context to OpenRouter. */
private const val CHAT_CONTEXT_MESSAGE_LIMIT = 12

/** Character cap per individual chat turn sent to the LLM API. */
private const val CHAT_MESSAGE_MAX_CHARS = 2_000

/** Output token budget requested from OpenRouter to keep replies short and conversational. */
private const val CHAT_MAX_OUTPUT_TOKENS = 220

/** Hard character cap applied to assistant replies before storing or speaking them. */
private const val CHAT_REPLY_MAX_CHARS = 4_000

/** Network timeout settings (ms) for OpenRouter chat and Open-Meteo weather calls. */
private const val HTTP_CONNECT_TIMEOUT_MS = 15_000
private const val CHAT_READ_TIMEOUT_MS = 30_000
private const val WEATHER_READ_TIMEOUT_MS = 15_000

/** Standard 2xx HTTP success status range. */
private val HTTP_SUCCESS_RANGE = 200..299

/** Valid 24-hour clock bounds for alarm scheduling. */
private val VALID_HOURS = 0..23
private val VALID_MINUTES = 0..59

/**
 * Reads at most [maxBytes] from this stream as UTF-8, throwing [IOException]
 * if the response exceeds the limit instead of exhausting memory.
 */
internal fun InputStream.readBoundedUtf8(maxBytes: Int, overflowMessage: String = "Response too large"): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(STREAM_BUFFER_BYTES)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) {
            throw IOException(overflowMessage)
        }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}

/**
 * Formats coordinates rounded to ~1 km precision (`%.2f`), which is sufficient
 * for weather forecasts without exposing exact home location.
 */
internal fun formatWeatherCoordinates(latitude: Double, longitude: Double): String =
    "latitude=${String.format(Locale.US, "%.2f", latitude)}&longitude=${String.format(Locale.US, "%.2f", longitude)}"

/** HTTP client for online LLM chat (OpenRouter) and current weather (Open-Meteo). */
class AssistantClient(private val context: Context) {

    /** Sends the last [CHAT_CONTEXT_MESSAGE_LIMIT] messages to OpenRouter and returns the pet's reply. */
    fun chat(apiKey: String, petName: String, characterName: String, messages: List<ChatMessage>): String {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) throw IOException(context.getString(R.string.assistant_key_missing))
        val history = JSONArray()
        messages.takeLast(CHAT_CONTEXT_MESSAGE_LIMIT).forEach { message ->
            history.put(
                JSONObject()
                    .put("role", if (message.fromPet) "assistant" else "user")
                    .put("content", message.text.take(CHAT_MESSAGE_MAX_CHARS)),
            )
        }
        val requestMessages = JSONArray().put(
            JSONObject()
                .put("role", "system")
                .put("content", context.getString(R.string.assistant_system_prompt, petName, characterName)),
        )
        for (index in 0 until history.length()) requestMessages.put(history.get(index))
        val body = JSONObject()
            .put("model", "openrouter/auto")
            .put("max_tokens", CHAT_MAX_OUTPUT_TOKENS)
            .put("messages", requestMessages)
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
        connection.readTimeout = CHAT_READ_TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $cleanKey")
        connection.setRequestProperty("Content-Type", "application/json")
        return try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code !in HTTP_SUCCESS_RANGE) {
                runCatching { connection.errorStream?.close() }
                throw IOException(context.getString(R.string.assistant_http_error, code))
            }
            val response = connection.inputStream.use {
                it.readBoundedUtf8(MaxJsonResponseBytes)
            }
            val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (content.isEmpty()) throw IOException(context.getString(R.string.assistant_empty_reply))
            content.take(CHAT_REPLY_MAX_CHARS)
        } finally {
            connection.disconnect()
        }
    }

    /** Fetches current temperature, feels-like temperature, and WMO weather summary from Open-Meteo. */
    fun weather(latitude: Double, longitude: Double): String {
        val coordinates = formatWeatherCoordinates(latitude, longitude)
        val url = "https://api.open-meteo.com/v1/forecast?$coordinates&current=temperature_2m,apparent_temperature,weather_code&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = HTTP_CONNECT_TIMEOUT_MS
        connection.readTimeout = WEATHER_READ_TIMEOUT_MS
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                runCatching { connection.errorStream?.close() }
                throw IOException(context.getString(R.string.weather_http_error, connection.responseCode))
            }
            val response = connection.inputStream.use {
                it.readBoundedUtf8(MaxJsonResponseBytes)
            }
            val current = JSONObject(response).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m")
            val feels = current.getDouble("apparent_temperature")
            val code = current.getInt("weather_code")
            val time = current.optString("time")
            context.getString(
                R.string.weather_report,
                weatherDescription(context, code),
                temperature.roundToInt(),
                feels.roundToInt(),
                time,
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        /** Upper bound (256 KiB) for JSON API responses to prevent unbounded memory reads. */
        const val MaxJsonResponseBytes = 256 * 1024
    }
}

/** Computes the next wall-clock epoch millisecond matching `[hour]:[minute]` strictly after [nowMs]. */
fun nextAlarmAt(hour: Int, minute: Int, nowMs: Long): Long {
    require(hour in VALID_HOURS && minute in VALID_MINUTES)
    val calendar = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMs) add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return calendar.timeInMillis
}

/** Formats a scheduled reminder epoch timestamp for chat confirmation messages (`d MMM, HH:mm`). */
fun formatReminderTime(timestamp: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

/** Translates a WMO standard weather code (0..99) returned by Open-Meteo into a localized description. */
private fun weatherDescription(context: Context, code: Int): String = context.getString(
    when (code) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly_cloudy
        3 -> R.string.weather_overcast
        45, 48 -> R.string.weather_fog
        in 51..67, in 80..82 -> R.string.weather_rain
        in 71..77, 85, 86 -> R.string.weather_snow
        in 95..99 -> R.string.weather_thunder
        else -> R.string.weather_unknown
    },
)
