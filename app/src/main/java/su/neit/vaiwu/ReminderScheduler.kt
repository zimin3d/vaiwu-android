package su.neit.vaiwu

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun schedule(timestamp: Long, text: String): Long {
        val id = preferences.getInt("next_id", 1).coerceAtLeast(1)
        preferences.edit().putInt("next_id", if (id == Int.MAX_VALUE) 1 else id + 1)
            .putLong("time_$id", timestamp).putString("text_$id", text.take(240)).apply()
        scheduleOne(id, timestamp, text)
        return timestamp
    }

    fun cancelAll() {
        preferences.all.keys.mapNotNull { key ->
            key.removePrefix("time_").toIntOrNull().takeIf { key.startsWith("time_") }
        }.forEach { id ->
            alarmManager.cancel(pendingIntent(id, ""))
        }
        preferences.edit().clear().apply()
    }

    fun restoreAfterBoot() {
        val now = System.currentTimeMillis()
        preferences.all.filterKeys { it.startsWith("time_") }.forEach { (key, value) ->
            val id = key.removePrefix("time_").toIntOrNull() ?: return@forEach
            val timestamp = value as? Long ?: return@forEach
            if (timestamp > now) {
                scheduleOne(id, timestamp, preferences.getString("text_$id", "Напоминание") ?: "Напоминание")
            } else {
                preferences.edit().remove(key).remove("text_$id").apply()
            }
        }
    }

    private fun scheduleOne(id: Int, timestamp: Long, text: String) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent(id, text))
    }

    private fun pendingIntent(id: Int, text: String): PendingIntent = PendingIntent.getBroadcast(
        context, id,
        Intent(context, ReminderReceiver::class.java).putExtra("text", text).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val PreferencesName = "reminders"
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler(context).restoreAfterBoot()
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            ChannelId, "Напоминания Vaiwu", NotificationManager.IMPORTANCE_DEFAULT,
        ))
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Vaiwu напоминает")
            .setContentText(intent.getStringExtra("text") ?: "Напоминание")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
        val id = intent.getIntExtra("id", 0)
        if (id != 0) {
            context.getSharedPreferences(ReminderScheduler.PreferencesName, Context.MODE_PRIVATE)
                .edit().remove("time_$id").remove("text_$id").apply()
        }
    }

    companion object {
        const val ChannelId = "vaiwu_reminders"
    }
}
