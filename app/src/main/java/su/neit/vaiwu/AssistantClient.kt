package su.neit.vaiwu

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AssistantClient {
    fun chat(apiKey: String, petName: String, characterName: String, messages: List<ChatMessage>): String {
        if (apiKey.isBlank()) throw IOException("Добавь ключ OpenRouter в настройках")
        val history = JSONArray()
        messages.takeLast(12).forEach { message ->
            history.put(JSONObject()
                .put("role", if (message.fromPet) "assistant" else "user")
                .put("content", message.text.take(2_000)))
        }
        val requestMessages = JSONArray().put(JSONObject()
            .put("role", "system")
            .put("content", "Ты $petName, виртуальный питомец $characterName. Отвечай по-русски, коротко, дружелюбно и безопасно. Не притворяйся человеком."))
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
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.setRequestProperty("Content-Type", "application/json")
        return try {
            connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IOException("OpenRouter: HTTP $code")
            val content = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
            if (content.isEmpty()) throw IOException("Пустой ответ OpenRouter")
            content.take(4_000)
        } finally {
            connection.disconnect()
        }
    }

    fun weather(latitude: Double, longitude: Double): String {
        val coordinates = "latitude=${String.format(Locale.US, "%.5f", latitude)}&longitude=${String.format(Locale.US, "%.5f", longitude)}"
        val url = "https://api.open-meteo.com/v1/forecast?$coordinates&current=temperature_2m,apparent_temperature,weather_code&timezone=auto"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Погода: HTTP ${connection.responseCode}")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val current = JSONObject(response).getJSONObject("current")
            val temperature = current.getDouble("temperature_2m")
            val feels = current.getDouble("apparent_temperature")
            val code = current.getInt("weather_code")
            val time = current.optString("time")
            "${weatherDescription(code)}: ${temperature.roundToInt()}°C, ощущается как ${feels.roundToInt()}°C. Местное время $time."
        } finally {
            connection.disconnect()
        }
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

private fun weatherDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1, 2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    in 51..67, in 80..82 -> "Дождь"
    in 71..77, 85, 86 -> "Снег"
    in 95..99 -> "Гроза"
    else -> "Погода"
}
