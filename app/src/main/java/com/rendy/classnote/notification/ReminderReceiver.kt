package com.rendy.classnote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.ui.ReminderAlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        private const val TAG = "ReminderReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getLongExtra(EXTRA_NOTIFICATION_ID, -1L)
        val isSnooze = intent.getBooleanExtra("is_snooze", false)

        // Snooze 情境：title/note/category 直接從 intent 取（不查 DB）
        if (isSnooze) {
            val title = intent.getStringExtra(ReminderAlarmActivity.EXTRA_TITLE) ?: return
            val note = intent.getStringExtra(ReminderAlarmActivity.EXTRA_NOTE) ?: ""
            val category = intent.getStringExtra(ReminderAlarmActivity.EXTRA_CATEGORY)
            val fullScreenAlarm = intent.getBooleanExtra("full_screen_alarm", true)
            showAlarm(context, notificationId.toInt(), title, note, category, fullScreenAlarm = fullScreenAlarm)
            return
        }

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (notificationId < 0 || reminderId < 0) return

        val app = context.applicationContext as ClassNoteApplication
        val reminderRepo = app.reminderRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderRepo.getReminderById(reminderId) ?: return@launch
                if (reminder.isCompleted) return@launch

                showAlarm(
                    context,
                    notificationId.toInt(),
                    reminder.title,
                    reminder.note,
                    reminder.category,
                    reminderId,
                    fullScreenAlarm = reminder.fullScreenAlarm
                )

                reminderRepo.markNotificationFired(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 顯示提醒：
     * 1. 若 fullScreenAlarmEnabled 且有 SYSTEM_ALERT_WINDOW 權限 → 直接啟動全螢幕 Activity
     *    （螢幕亮著也能彈出，不依賴 setFullScreenIntent）
     * 2. 同時發送通知（作為備援 + 讓使用者可以從通知欄關閉）
     */
    private fun showAlarm(
        context: Context,
        notificationId: Int,
        title: String,
        note: String,
        category: String?,
        reminderId: Long = -1L,
        fullScreenAlarm: Boolean = true
    ) {
        val app = context.applicationContext as? ClassNoteApplication
        // 全域設定 AND 個別設定 都要開才顯示全螢幕
        val globalEnabled = app?.appPreferences?.fullScreenAlarmEnabled ?: true
        val shouldFullScreen = fullScreenAlarm && globalEnabled

        // 永遠發通知（備援 + 使用者可從通知欄看到）
        NotificationHelper.showReminderNotification(
            context, notificationId, title, note, category, reminderId,
            fullScreenAlarm = shouldFullScreen
        )

        if (!shouldFullScreen) return

        // 有 SYSTEM_ALERT_WINDOW 權限時直接啟動 Activity（螢幕亮著也有效）
        if (Settings.canDrawOverlays(context)) {
            Log.d(TAG, "Launching ReminderAlarmActivity directly (overlay permission granted)")
            val alarmIntent = Intent(context, ReminderAlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(ReminderAlarmActivity.EXTRA_TITLE, title)
                putExtra(ReminderAlarmActivity.EXTRA_NOTE, note)
                putExtra(ReminderAlarmActivity.EXTRA_CATEGORY, category)
                putExtra(ReminderAlarmActivity.EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(ReminderAlarmActivity.EXTRA_FULL_SCREEN_ALARM, true)
            }
            context.startActivity(alarmIntent)
        } else {
            // 未授權 SYSTEM_ALERT_WINDOW，依賴 setFullScreenIntent（鎖屏時有效）
            Log.d(TAG, "No overlay permission, relying on setFullScreenIntent (lock screen only)")
        }
    }
}
