package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderNotificationDao {

    @Query("SELECT * FROM reminder_notifications WHERE reminderId = :reminderId ORDER BY triggerAt")
    fun getNotificationsForReminder(reminderId: Long): Flow<List<ReminderNotificationEntity>>

    @Query("SELECT * FROM reminder_notifications WHERE reminderId = :reminderId ORDER BY triggerAt")
    suspend fun getNotificationsOnce(reminderId: Long): List<ReminderNotificationEntity>

    @Query("SELECT * FROM reminder_notifications WHERE isFired = 0 AND triggerAt > :now ORDER BY triggerAt")
    suspend fun getPendingNotifications(now: Long): List<ReminderNotificationEntity>

    @Query("SELECT * FROM reminder_notifications WHERE isFired = 0 ORDER BY triggerAt")
    suspend fun getAllPendingNotifications(): List<ReminderNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: ReminderNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<ReminderNotificationEntity>)

    @Query("UPDATE reminder_notifications SET isFired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Delete
    suspend fun deleteNotification(notification: ReminderNotificationEntity)

    @Query("DELETE FROM reminder_notifications WHERE reminderId = :reminderId")
    suspend fun deleteNotificationsForReminder(reminderId: Long)
}
