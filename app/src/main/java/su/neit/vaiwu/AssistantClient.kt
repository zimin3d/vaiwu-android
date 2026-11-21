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

/**
 * Reads at most [maxBytes] from this stream as UTF-8, throwing [IOException]
 * if the response exceeds the limit instead of exhausting memory.
 */
internal fun InputStream.readBoundedUtf8(maxBytes: Int, overflowMessage: String = "Response too large"): String {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
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

class AssistantClient(private val context: Context) {
    fun chat(apiKey: String, petName: String, characterName: String, messages: List<ChatMessage>): String {
        val cleanKey = sanitizeApiKey(apiKey)
        if (cleanKey.isBlank()) throw IOException(context.getString(R.string.assistant_key_missing))
        val history = JSONArray()
        messages.takeLast(12).forEach { message ->
            history.put(JSONObject()
                .put("role", if (message.fromPet) "assistant" else "user")
                .put("content", message.text.take(2_000)))
        }
        val requestMessages = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", context.getString(R.string.assistant_system_prompt, petName, characterName)))
        for (index in 0 until history.length()) requestMessages.put(history.get(index))
        val body = JSONObject()
            .put("model", "openrouter/auto")
            .put("max_tokens", 220)
            .put("messages", requestMessages)
        val connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $cleanKey")
        connection.setRequestProperty("Content-Type", "application/json")
        return try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            if (code !in 200..299) {
                runCatching { connection.errorStream?.close() }
                throw IOException(context.getString(R.string.assistant_http_error, code))
            }
            val response = connection.inputStream.use {
                it.readBoundedUtf8(MaxJsonResponseBytes)
            }
            val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (content.isEmpty()) throw IOException(context.getString(R.string.assistant_empty_reply))
            content.take(4_000)
        } finally {
            connection.disconnect()
        }
    }

    fun weather(latitude: Double, longitude: Double): String {
        val coordinates = formatWeatherCoordinates(latitude, longitude)
        val url = "https://api.open-meteo.com/v1/forecast?$coordinates&current=temperature_2m,apparent_temperature,weather_code&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
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
            context.getString(R.string.weather_report, weatherDescription(context, code), temperature.roundToInt(), feels.roundToInt(), time)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val MaxJsonResponseBytes = 256 * 1024
    }
}

fun nextAlarmAt(hour: Int, minute: Int, nowMs: Long): Long {
    require(hour in 0..23 && minute in 0..59)
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

fun formatReminderTime(timestamp: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))

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
