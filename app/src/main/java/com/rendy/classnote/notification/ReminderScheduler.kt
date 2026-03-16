package com.rendy.classnote.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity

object ReminderScheduler {

    private const val ACTION_REMINDER = "com.rendy.classnote.REMINDER_ALARM"

    fun scheduleNotification(context: Context, notification: ReminderNotificationEntity) {
        if (notification.triggerAt <= System.currentTimeMillis()) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = buildIntent(context, notification)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (notification.id and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notification.triggerAt,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // SCHEDULE_EXACT_ALARM 未授權，略過排程（資料已存入 DB）
        }
    }

    fun cancelNotification(context: Context, notificationId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (notificationId and 0x7FFFFFFF).toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context, notifications: List<ReminderNotificationEntity>) {
        val now = System.currentTimeMillis()
        notifications.filter { !it.isFired && it.triggerAt > now }
            .forEach { scheduleNotification(context, it) }
    }

    private fun buildIntent(context: Context, notification: ReminderNotificationEntity): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notification.id)
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, notification.reminderId)
        }
}
