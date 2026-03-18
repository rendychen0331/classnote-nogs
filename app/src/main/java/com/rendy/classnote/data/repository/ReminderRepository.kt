package com.rendy.classnote.data.repository

import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import kotlinx.coroutines.flow.Flow

class ReminderRepository(
    private val reminderDao: ReminderDao,
    private val notificationDao: ReminderNotificationDao
) {
    // ── 提醒事項 ──────────────────────────────────────────────────────────

    fun getActiveReminders(): Flow<List<ReminderEntity>> =
        reminderDao.getActiveReminders()

    fun getRemindersByCourse(courseId: Long): Flow<List<ReminderEntity>> =
        reminderDao.getRemindersByCourse(courseId)

    suspend fun getReminderById(id: Long): ReminderEntity? =
        reminderDao.getReminderById(id)

    suspend fun insertReminder(reminder: ReminderEntity): Long =
        reminderDao.insertReminder(reminder)

    suspend fun updateReminder(reminder: ReminderEntity) =
        reminderDao.updateReminder(reminder)

    suspend fun markCompleted(id: Long) =
        reminderDao.markCompleted(id)

    suspend fun deleteReminder(reminder: ReminderEntity) =
        reminderDao.deleteReminder(reminder)

    suspend fun cleanupCompleted() =
        reminderDao.deleteCompletedReminders()

    suspend fun getActiveRemindersOnce(): List<ReminderEntity> =
        reminderDao.getActiveRemindersOnce()

    // ── 通知時間 ──────────────────────────────────────────────────────────

    fun getNotificationsForReminder(reminderId: Long): Flow<List<ReminderNotificationEntity>> =
        notificationDao.getNotificationsForReminder(reminderId)

    suspend fun getNotificationsOnce(reminderId: Long): List<ReminderNotificationEntity> =
        notificationDao.getNotificationsOnce(reminderId)

    suspend fun getAllPendingNotifications(): List<ReminderNotificationEntity> =
        notificationDao.getAllPendingNotifications()

    suspend fun insertNotification(notification: ReminderNotificationEntity): Long =
        notificationDao.insertNotification(notification)

    suspend fun insertNotifications(notifications: List<ReminderNotificationEntity>): List<Long> =
        notificationDao.insertAll(notifications)

    suspend fun markNotificationFired(id: Long) =
        notificationDao.markFired(id)

    suspend fun deleteNotificationsForReminder(reminderId: Long) =
        notificationDao.deleteNotificationsForReminder(reminderId)
}
