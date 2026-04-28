package com.rendy.classnote.ui.reminder

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
            notifyWidgets()
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
            notifyWidgets()
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
        try {
            repository.markCompleted(reminderId)
            cancelPendingNotifications(reminderId)
            notifyWidgets()
        } catch (e: Exception) {
            android.util.Log.e("ReminderViewModel", "completeReminder failed", e)
        }
    }

    /** 刪除提醒 */
    fun deleteReminder(reminder: ReminderEntity) = viewModelScope.launch {
        try {
            cancelPendingNotifications(reminder.id)
            repository.deleteReminder(reminder)
            notifyWidgets()
        } catch (e: Exception) {
            android.util.Log.e("ReminderViewModel", "deleteReminder failed", e)
        }
    }

    private fun notifyWidgets() = ClassNoteWidget.refreshAll(appContext)

    private suspend fun scheduleNotifications(reminderId: Long, triggerTimes: List<Long>) {
        val now = System.currentTimeMillis()
        val notifications = triggerTimes
            .filter { it > now }
            .map { time -> ReminderNotificationEntity(reminderId = reminderId, triggerAt = time) }
        if (notifications.isEmpty()) return
        // dedup 後插入，回傳含 id 與調整後 triggerAt 的 entity 清單
        val insertedEntities = repository.insertNotificationsDeduped(notifications)
        insertedEntities.forEach { entity ->
            ReminderScheduler.scheduleNotification(appContext, entity)
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
