package com.rendy.classnote.ui.reminder

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.repository.ReminderRepository
import com.rendy.classnote.notification.ReminderScheduler
import com.rendy.classnote.widget.ClassNoteWidget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderViewModel(
    private val repository: ReminderRepository,
    private val appContext: Context
) : ViewModel() {

    val activeReminders: StateFlow<List<ReminderEntity>> = repository.getActiveReminders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 新增提醒，並排程所有通知 */
    fun addReminder(reminder: ReminderEntity, notificationTimes: List<Long>) =
        viewModelScope.launch {
            val reminderId = repository.insertReminder(reminder)
            scheduleNotifications(reminderId, notificationTimes)
        }

    /** 新增提醒（suspend 版，確保存完才返回） */
    suspend fun addReminderAndWait(reminder: ReminderEntity, notificationTimes: List<Long>) {
        withContext(NonCancellable) {
            val reminderId = repository.insertReminder(reminder)
            scheduleNotifications(reminderId, notificationTimes)
        }
        notifyWidgets()
    }

    /** 更新提醒：先取消舊通知再重新排程 */
    fun updateReminder(reminder: ReminderEntity, notificationTimes: List<Long>) =
        viewModelScope.launch {
            repository.updateReminder(reminder)
            cancelPendingNotifications(reminder.id)
            scheduleNotifications(reminder.id, notificationTimes)
        }

    /** 更新提醒（suspend 版，確保存完才返回） */
    suspend fun updateReminderAndWait(reminder: ReminderEntity, notificationTimes: List<Long>) {
        withContext(NonCancellable) {
            repository.updateReminder(reminder)
            cancelPendingNotifications(reminder.id)
            scheduleNotifications(reminder.id, notificationTimes)
        }
        notifyWidgets()
    }

    /** 標記完成（從列表消失），並取消所有未觸發通知 */
    fun completeReminder(reminderId: Long) = viewModelScope.launch {
        repository.markCompleted(reminderId)
        cancelPendingNotifications(reminderId)
    }

    /** 刪除提醒 */
    fun deleteReminder(reminder: ReminderEntity) = viewModelScope.launch {
        cancelPendingNotifications(reminder.id)
        repository.deleteReminder(reminder)
    }

    private fun notifyWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(ComponentName(appContext, ClassNoteWidget::class.java))
        for (id in ids) ClassNoteWidget.updateWidget(appContext, manager, id)
    }

    private suspend fun scheduleNotifications(reminderId: Long, triggerTimes: List<Long>) {
        val now = System.currentTimeMillis()
        val notifications = triggerTimes
            .filter { it > now }
            .map { time -> ReminderNotificationEntity(reminderId = reminderId, triggerAt = time) }
        if (notifications.isEmpty()) return
        // insertAll 回傳自動產生的 id，直接用於排程，不需重查
        val insertedIds = repository.insertNotifications(notifications)
        insertedIds.zip(notifications).forEach { (id, entity) ->
            ReminderScheduler.scheduleNotification(appContext, entity.copy(id = id))
        }
    }

    private suspend fun cancelPendingNotifications(reminderId: Long) {
        val pending = repository.getNotificationsOnce(reminderId).filter { !it.isFired }
        pending.forEach { ReminderScheduler.cancelNotification(appContext, it.id) }
        repository.deleteNotificationsForReminder(reminderId)
    }

    class Factory(
        private val repository: ReminderRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ReminderViewModel(repository, appContext) as T
    }
}
