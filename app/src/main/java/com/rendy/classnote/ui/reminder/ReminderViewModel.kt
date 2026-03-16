package com.rendy.classnote.ui.reminder

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.repository.ReminderRepository
import com.rendy.classnote.notification.ReminderScheduler
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

    private suspend fun scheduleNotifications(reminderId: Long, triggerTimes: List<Long>) {
        val notifications = triggerTimes.map { time ->
            ReminderNotificationEntity(reminderId = reminderId, triggerAt = time)
        }
        repository.insertNotifications(notifications)
        // 重新查詢以取得自動產生的 id，僅排程未觸發的
        val saved = repository.getNotificationsOnce(reminderId).filter { !it.isFired }
        saved.forEach { ReminderScheduler.scheduleNotification(appContext, it) }
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
