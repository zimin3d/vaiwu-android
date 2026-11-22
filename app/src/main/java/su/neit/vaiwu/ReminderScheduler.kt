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

/** Persists and schedules local alarm/reminder notifications via Android's [AlarmManager]. */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    /** Stores and schedules a one-shot reminder at [timestamp] ms with text capped at [MaxReminderTextLength]. */
    fun schedule(timestamp: Long, text: String): Long {
        val id = preferences.getInt("next_id", 1).coerceAtLeast(1)
        preferences.edit().putInt("next_id", if (id == Int.MAX_VALUE) 1 else id + 1)
            .putLong("time_$id", timestamp).putString("text_$id", text.take(MaxReminderTextLength)).apply()
        scheduleOne(id, timestamp, text)
        return timestamp
    }

    /** Cancels every pending alarm scheduled by this app and clears stored reminder metadata. */
    fun cancelAll() {
        preferences.all.keys.mapNotNull { key ->
            key.removePrefix("time_").toIntOrNull().takeIf { key.startsWith("time_") }
        }.forEach { id ->
            alarmManager.cancel(pendingIntent(id, ""))
        }
        preferences.edit().clear().apply()
    }

    /** Re-registers all future reminders after a device reboot and prunes expired ones. */
    fun restoreAfterBoot() {
        val now = System.currentTimeMillis()
        val fallback = context.getString(R.string.reminder_default_text)
        preferences.all.filterKeys { it.startsWith("time_") }.forEach { (key, value) ->
            val id = key.removePrefix("time_").toIntOrNull() ?: return@forEach
            val timestamp = value as? Long ?: return@forEach
            if (timestamp > now) {
                scheduleOne(id, timestamp, preferences.getString("text_$id", fallback) ?: fallback)
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
        Intent(context, ReminderReceiver::class.java)
            .setAction(ActionReminder)
            .putExtra("text", text)
            .putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val PreferencesName = "reminders"
        const val ActionReminder = "su.neit.vaiwu.ACTION_REMINDER"
        /** Maximum length (chars) persisted per reminder notification body. */
        private const val MaxReminderTextLength = 240
    }
}

/** Broadcast receiver that posts reminder notifications and restores alarms on `BOOT_COMPLETED`. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                ReminderScheduler(context).restoreAfterBoot()
                return
            }
            ReminderScheduler.ActionReminder -> Unit
            else -> return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            ChannelId, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_DEFAULT,
        ))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        val id = intent.getIntExtra("id", 0)
        val openApp = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(intent.getStringExtra("text") ?: context.getString(R.string.reminder_default_text))
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val notificationId = if (id != 0) id else System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        if (id != 0) {
            context.getSharedPreferences(ReminderScheduler.PreferencesName, Context.MODE_PRIVATE)
                .edit().remove("time_$id").remove("text_$id").apply()
        }
    }

    companion object {
        const val ChannelId = "vaiwu_reminders"
    }
}
