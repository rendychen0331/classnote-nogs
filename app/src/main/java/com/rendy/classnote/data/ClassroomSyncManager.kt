package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.classroom.Classroom
import com.google.api.services.classroom.ClassroomScopes
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

object ClassroomSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "ClassroomSyncManager"

    private fun buildClassroomService(context: Context, email: String): Classroom {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(ClassroomScopes.CLASSROOM_COURSES_READONLY, ClassroomScopes.CLASSROOM_COURSEWORK_ME_READONLY)
        ).apply { selectedAccountName = email }
        return Classroom.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote")
            .build()
    }

    /**
     * 從 Google Classroom 同步所有課程的作業，匯入為提醒事項。
     * 防重複邏輯：
     * 1. 同來源：externalId = "classroom:{courseWorkId}"，已存在則 skip
     * 2. 跨來源：title + dueDate 已存在（如 Gmail 已匯入），skip 避免重複
     */
    suspend fun sync(context: Context, email: String, dao: ReminderDao, notificationDao: ReminderNotificationDao): SyncResult =
        withContext(Dispatchers.IO) {
            if (email.isBlank()) return@withContext SyncResult.NoPermission
            try {
                val service = buildClassroomService(context, email)

                // 取得所有已加入的課程
                val courses = service.courses().list()
                    .setStudentId("me")
                    .setPageSize(50)
                    .execute()
                    .courses

                if (courses.isNullOrEmpty()) return@withContext SyncResult.Success(0, 0)

                var imported = 0
                var skipped = 0

                for (course in courses) {
                    val courseId = course.id ?: continue
                    val courseName = course.name ?: ""

                    // 取得該課程的作業清單
                    val courseWorks = runCatching {
                        service.courses().courseWork().list(courseId)
                            .setPageSize(50)
                            .execute()
                            .courseWork
                    }.getOrNull() ?: continue

                    for (cw in courseWorks) {
                        val cwId = cw.id ?: continue
                        val title = cw.title?.trim() ?: continue
                        val externalId = "classroom:$cwId"

                        // 防重複 1：同 externalId
                        if (dao.findByExternalId(externalId) != null) { skipped++; continue }

                        // 轉換截止日期與時間（API 回傳 UTC，轉換為本地時區）
                        val dueDate: String?
                        val dueTime: String?
                        val rawDate = cw.dueDate
                        val rawTime = cw.dueTime
                        if (rawDate != null && rawDate.year != null && rawDate.month != null && rawDate.day != null) {
                            val utcHours = rawTime?.hours ?: 0
                            val utcMinutes = rawTime?.minutes ?: 0
                            val utcDt = LocalDateTime.of(rawDate.year, rawDate.month, rawDate.day, utcHours, utcMinutes)
                            val localDt = utcDt.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
                            dueDate = "%04d-%02d-%02d".format(localDt.year, localDt.monthValue, localDt.dayOfMonth)
                            dueTime = if (rawTime != null) "%02d:%02d".format(localDt.hour, localDt.minute) else null
                        } else {
                            dueDate = null
                            dueTime = null
                        }

                        // 防重複 2：跨來源（title + dueDate 已存在）
                        // M-3 fix: dueDate 為 null 時改用 IS NULL 查詢，避免去重失效
                        val crossSourceDuplicate = if (dueDate != null) {
                            dao.findByTitleAndDueDate(title, dueDate) != null
                        } else {
                            dao.findByTitleWithNullDueDate(title) != null
                        }
                        if (crossSourceDuplicate) { skipped++; continue }

                        val note = cw.description
                            ?.lines()
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() && it != title }
                            ?.joinToString("\n")
                            ?.trim()
                            .orEmpty()
                        val reminderId = dao.insertReminder(
                            ReminderEntity(
                                title = title,
                                note = note,
                                dueDate = dueDate,
                                dueTime = dueTime,
                                category = "HOMEWORK",
                                externalId = externalId,
                                syncSource = "classroom",
                                sourceName = courseName.ifBlank { null }
                            )
                        )
                        scheduleDefaultNotifications(context, reminderId, dueDate, dueTime, notificationDao)
                        imported++
                    }
                }

                ApiLogger.log("Classroom(同步)", "sync", "匯入$imported 略過$skipped", 0, true)
                if (imported > 0) com.rendy.classnote.widget.ClassNoteWidget.refreshAll(context)
                SyncResult.Success(imported, skipped)
            } catch (e: Exception) {
                Log.e(TAG, "Classroom sync failed", e)
                ApiLogger.log("Classroom(同步)", "sync", e.message ?: "同步失敗", 0, false)
                SyncResult.Error(e.message ?: "同步失敗")
            }
        }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminderId: Long,
        dueDate: String?,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        if (dueDate == null) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val prefs = AppPreferences(context)
        val triggerTimes: List<Long> = if (dueTime != null) {
            val timeParts = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(
                LocalDate.parse(dueDate),
                LocalTime.of(timeParts[0], timeParts[1])
            )
            listOf(
                base.minusDays(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusHours(1).atZone(zone).toInstant().toEpochMilli(),
                base.minusMinutes(1).atZone(zone).toInstant().toEpochMilli()
            )
        } else {
            val base = LocalDate.parse(dueDate).atTime(prefs.defaultRemindHour, prefs.defaultRemindMinute)
            listOf(base.atZone(zone).toInstant().toEpochMilli())
        }
        val pendingTimes = notificationDao.getAllPendingNotifications().map { it.triggerAt }.toMutableSet()
        triggerTimes.map { ReminderScheduler.clampToQuietHours(it, prefs, zone) }.filter { it > now }.forEach { millis ->
            var adjustedMillis = millis
            while (pendingTimes.contains(adjustedMillis)) adjustedMillis += 60_000L
            pendingTimes.add(adjustedMillis)
            val entity = ReminderNotificationEntity(reminderId = reminderId, triggerAt = adjustedMillis)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
        }
    }
}
