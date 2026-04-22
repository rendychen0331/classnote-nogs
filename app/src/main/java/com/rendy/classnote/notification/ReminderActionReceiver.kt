package com.rendy.classnote.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.ui.ReminderAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 處理通知上的快速動作按鈕（延後 / 完成）。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.rendy.classnote.ACTION_SNOOZE"
        const val ACTION_COMPLETE = "com.rendy.classnote.ACTION_COMPLETE"

        const val EXTRA_NOTIFICATION_ID = "action_notification_id"
        const val EXTRA_REMINDER_ID = "action_reminder_id"
        const val EXTRA_TITLE = "action_title"
        const val EXTRA_NOTE = "action_note"
        const val EXTRA_CATEGORY = "action_category"
        const val EXTRA_FULL_SCREEN_ALARM = "action_full_screen_alarm"

        private const val TAG = "ReminderActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId < 0) return

        when (intent.action) {
            ACTION_SNOOZE -> handleSnooze(context, intent, notificationId)
            ACTION_COMPLETE -> handleComplete(context, intent, notificationId)
        }
    }

    // ── 延後 ────────────────────────────────────────────────────────────────

    private fun handleSnooze(context: Context, intent: Intent, notificationId: Int) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        val fullScreenAlarm = intent.getBooleanExtra(EXTRA_FULL_SCREEN_ALARM, true)

        val app = context.applicationContext as? ClassNoteApplication
        val snoozeMinutes = app?.appPreferences?.snoozeDurationMinutes ?: 10
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.rendy.classnote.REMINDER_ALARM"
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, notificationId.toLong())
            putExtra(ReminderAlarmActivity.EXTRA_TITLE, title)
            putExtra(ReminderAlarmActivity.EXTRA_NOTE, note)
            putExtra(ReminderAlarmActivity.EXTRA_CATEGORY, category)
            putExtra("is_snooze", true)
            putExtra("full_screen_alarm", fullScreenAlarm)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 200_000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent
            )
            Log.d(TAG, "Snoozed for $snoozeMinutes min: $title")
        } catch (_: SecurityException) {
            Log.e(TAG, "Cannot schedule snooze: SCHEDULE_EXACT_ALARM not granted")
        }

        // 關閉通知 + 讓全螢幕 Activity 也關閉
        dismissNotification(context, notificationId)
        sendAlarmDismiss(context, notificationId)
    }

    // ── 完成 ────────────────────────────────────────────────────────────────

    private fun handleComplete(context: Context, intent: Intent, notificationId: Int) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0) return

        val app = context.applicationContext as ClassNoteApplication
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.reminderRepository.markCompleted(reminderId)
                Log.d(TAG, "Reminder $reminderId marked complete")
            } catch (e: Exception) {
                Log.e(TAG, "completeReminder failed", e)
            } finally {
                pendingResult.finish()
            }
        }

        // 關閉通知 + 讓全螢幕 Activity 也關閉
        dismissNotification(context, notificationId)
        sendAlarmDismiss(context, notificationId)
    }

    private fun dismissNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(notificationId)
    }

    private fun sendAlarmDismiss(context: Context, notificationId: Int) {
        val intent = Intent(com.rendy.classnote.ui.ReminderAlarmActivity.ACTION_DISMISS).apply {
            putExtra(com.rendy.classnote.ui.ReminderAlarmActivity.EXTRA_DISMISS_NOTIFICATION_ID, notificationId)
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
