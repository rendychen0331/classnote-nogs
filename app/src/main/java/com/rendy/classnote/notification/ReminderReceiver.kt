package com.rendy.classnote.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rendy.classnote.ClassNoteApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getLongExtra(EXTRA_NOTIFICATION_ID, -1L)
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        if (notificationId < 0 || reminderId < 0) return

        val app = context.applicationContext as ClassNoteApplication
        val reminderRepo = app.reminderRepository

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = reminderRepo.getReminderById(reminderId) ?: return@launch
                if (reminder.isCompleted) return@launch

                NotificationHelper.showReminderNotification(
                    context,
                    notificationId.toInt(),
                    reminder.title,
                    reminder.note.ifBlank { "點擊查看提醒詳情" }
                )

                reminderRepo.markNotificationFired(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
