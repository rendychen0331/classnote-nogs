package com.rendy.classnote.data

import android.content.Context
import android.util.Log
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object MsTodoSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "MsTodoSyncManager"
    private const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    val SCOPES = listOf("Tasks.Read", "User.Read")

    suspend fun sync(
        context: Context,
        token: String,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            val lists = fetchTaskLists(token)
            var imported = 0
            var skipped = 0

            for (list in lists) {
                val listId = list.getString("id")
                val listName = list.optString("displayName", "MS To Do")
                val tasks = fetchTasks(token, listId)

                for (task in tasks) {
                    val taskId = task.getString("id")
                    val externalId = "mstodo:$taskId"

                    if (dao.findByExternalId(externalId) != null) {
                        skipped++
                        continue
                    }

                    val title = task.optString("title", "").trim()
                    if (title.isEmpty()) { skipped++; continue }

                    val status = task.optString("status", "")
                    if (status == "completed") { skipped++; continue }

                    val (dueDate, dueTime) = parseDueDateTime(task)

                    val note = task.optString("body.content", "").let { body ->
                        if (body.isNotBlank()) body.trim() else ""
                    }.let { raw ->
                        try {
                            val bodyObj = task.optJSONObject("body")
                            bodyObj?.optString("content", "")?.trim() ?: raw
                        } catch (_: Exception) { raw }
                    }

                    val reminder = ReminderEntity(
                        title = title,
                        note = note,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        externalId = externalId,
                        syncSource = "mstodo",
                        sourceName = listName,
                        category = "REMINDER"
                    )
                    val id = dao.insertReminder(reminder)
                    if (id > 0) {
                        ApiLogger.log("MsTodo", "匯入：$title", "OK", 0L, true)
                        scheduleDefaultNotifications(context, reminder.copy(id = id), dueDate, dueTime, notificationDao)
                        imported++
                    }
                }
            }
            SyncResult.Success(imported, skipped)
        } catch (e: Exception) {
            Log.e(TAG, "sync error", e)
            ApiLogger.log("MsTodo", "sync", e.message ?: "error", 0L, false)
            SyncResult.Error(e.message ?: "同步失敗")
        }
    }

    private fun fetchTaskLists(token: String): List<JSONObject> {
        val url = URL("$GRAPH_BASE/me/todo/lists?\$top=50")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun fetchTasks(token: String, listId: String): List<JSONObject> {
        val url = URL("$GRAPH_BASE/me/todo/lists/$listId/tasks?\$top=100&\$filter=status ne 'completed'")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        val arr = JSONObject(body).optJSONArray("value") ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun parseDueDateTime(task: JSONObject): Pair<String?, String?> {
        val dueDateStr = task.optString("dueDateTime", "")
        if (dueDateStr.isBlank()) return Pair(null, null)
        return try {
            val dt = LocalDateTime.parse(dueDateStr.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val date = dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val time = if (dt.hour == 0 && dt.minute == 0) null
                       else dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            Pair(date, time)
        } catch (_: Exception) {
            try {
                val date = LocalDate.parse(dueDateStr.take(10)).format(DateTimeFormatter.ISO_LOCAL_DATE)
                Pair(date, null)
            } catch (_: Exception) { Pair(null, null) }
        }
    }

    private suspend fun scheduleDefaultNotifications(
        context: Context,
        reminder: ReminderEntity,
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
            val base = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(timeParts[0], timeParts[1]))
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
            val entity = ReminderNotificationEntity(reminderId = reminder.id, triggerAt = adjustedMillis)
            val id = notificationDao.insertNotification(entity)
            ReminderScheduler.scheduleNotification(context, entity.copy(id = id))
        }
    }
}
