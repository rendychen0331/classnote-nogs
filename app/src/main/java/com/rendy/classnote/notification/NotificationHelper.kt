package com.rendy.classnote.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.ui.MainActivity
import com.rendy.classnote.ui.ReminderAlarmActivity

object NotificationHelper {

    const val CHANNEL_ID = "classnote_reminders"
    const val CHANNEL_NAME = "提醒事項通知"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "ClassNote 提醒事項推播通知"
            enableVibration(true)
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
        category: String? = null,
        reminderId: Long = -1L,
        fullScreenAlarm: Boolean = true
    ) {
        // 點擊通知 → 跳到提醒列表
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "reminders")
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Full-screen intent → 鎖屏/使用中時顯示全螢幕鬧鐘介面
        val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderAlarmActivity.EXTRA_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_NOTE, body)
            putExtra(ReminderAlarmActivity.EXTRA_CATEGORY, category)
            putExtra(ReminderAlarmActivity.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 10_000,   // 避免與 tap intent 衝突
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "點擊查看提醒詳情" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(tapPendingIntent)

        if (fullScreenAlarm) {
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
        }

        // ── 快速動作按鈕（延後 / 完成）────────────────────────────────────────
        // 延後
        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(ReminderActionReceiver.EXTRA_TITLE, title)
            putExtra(ReminderActionReceiver.EXTRA_NOTE, body)
            putExtra(ReminderActionReceiver.EXTRA_CATEGORY, category)
            putExtra(ReminderActionReceiver.EXTRA_FULL_SCREEN_ALARM, fullScreenAlarm)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 20_000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 完成（只有在有 reminderId 時才顯示）
        if (reminderId >= 0) {
            val completeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_COMPLETE
                putExtra(ReminderActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId)
            }
            val completePendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 30_000,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "完成", completePendingIntent)
        }

        builder.addAction(0, "延後", snoozePendingIntent)

        val notification = builder.build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}
