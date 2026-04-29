package com.rendy.classnote.notification

import android.app.Notification
import android.os.Handler
import androidx.core.app.NotificationCompat
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ClaudeApi
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.data.remote.MimoApi
import com.rendy.classnote.data.remote.OpenAiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ClassNoteNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenKeys = mutableSetOf<String>()

    private val handler = Handler(Looper.getMainLooper())
    private val pendingBatch = mutableListOf<GeminiApi.NotificationInput>()
    private val processBatchRunnable = Runnable { flushBatch() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.notificationListenerAutoAdd) return

        // 確認至少有一個啟用的 model 有 key
        val hasAnyKey = with(prefs) {
            (geminiEnabled && geminiApiKey.isNotBlank()) ||
            (mimoEnabled   && mimoApiKey.isNotBlank())   ||
            (claudeEnabled && claudeApiKey.isNotBlank()) ||
            (openaiEnabled && openaiApiKey.isNotBlank())
        }
        if (!hasAnyKey) return

        if (sbn.packageName == packageName) return

        val monitored = prefs.monitoredPackages
        if (monitored.isNotEmpty() && !monitored.contains(sbn.packageName)) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return

        // 優先用 MessagingStyle 取完整訊息（LINE、Discord、WhatsApp 等聊天 App）
        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(sbn.notification)
        val text = if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            messagingStyle.messages
                .takeLast(3)
                .joinToString("\n") { it.text?.toString() ?: "" }
                .trim()
        } else {
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim()
        } ?: return

        if (title.isBlank() || text.isBlank()) return

        // 記錄看過的頻道名稱（供 UI 設定白名單）
        prefs.addSeenChannel(sbn.packageName, title)

        // 頻道白名單過濾（不透過 AI，直接用 title 比對）
        val channelWhitelist = prefs.getMonitoredChannels()[sbn.packageName]
        if (!channelWhitelist.isNullOrEmpty() && !channelWhitelist.contains(title)) return

        val dedupeKey = "${sbn.packageName}|$title|${text.take(100)}"
        val isNew = synchronized(seenKeys) {
            if (seenKeys.contains(dedupeKey)) false
            else {
                seenKeys.add(dedupeKey)
                if (seenKeys.size > 200) seenKeys.clear()
                true
            }
        }
        if (!isNew) return

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (_: Exception) { sbn.packageName }

        synchronized(pendingBatch) {
            pendingBatch.add(GeminiApi.NotificationInput(appLabel, title, text))
        }

        // Reset debounce timer — flush 3 seconds after last notification arrives
        handler.removeCallbacks(processBatchRunnable)
        handler.postDelayed(processBatchRunnable, BATCH_DELAY_MS)
    }

    private fun flushBatch() {
        val batch = synchronized(pendingBatch) {
            if (pendingBatch.isEmpty()) return
            val copy = pendingBatch.toList()
            pendingBatch.clear()
            copy
        }

        NotificationHelper.createAiStatusChannel(applicationContext)
        NotificationHelper.showAiProcessing(applicationContext, batch.size)

        scope.launch {
            try {
                val prefs = AppPreferences(applicationContext)
                val provider = prefs.preferredChatProvider
                val results: List<GeminiApi.EventInfo?> = when {
                    provider == "mimo"   && prefs.mimoEnabled   && prefs.mimoApiKey.isNotBlank()   ->
                        MimoApi.analyzeNotifications(prefs.mimoApiKey, batch)
                    provider == "claude" && prefs.claudeEnabled && prefs.claudeApiKey.isNotBlank() ->
                        ClaudeApi.analyzeNotifications(prefs.claudeApiKey, batch)
                    provider == "openai" && prefs.openaiEnabled && prefs.openaiApiKey.isNotBlank() ->
                        OpenAiApi.analyzeNotifications(prefs.openaiApiKey, batch)
                    prefs.geminiEnabled  && prefs.geminiApiKey.isNotBlank()                        ->
                        GeminiApi.analyzeNotifications(prefs.geminiApiKey, batch)
                    prefs.mimoEnabled    && prefs.mimoApiKey.isNotBlank()                          ->
                        MimoApi.analyzeNotifications(prefs.mimoApiKey, batch)
                    prefs.claudeEnabled  && prefs.claudeApiKey.isNotBlank()                        ->
                        ClaudeApi.analyzeNotifications(prefs.claudeApiKey, batch)
                    prefs.openaiEnabled  && prefs.openaiApiKey.isNotBlank()                        ->
                        OpenAiApi.analyzeNotifications(prefs.openaiApiKey, batch)
                    else -> { NotificationHelper.cancelAiStatus(applicationContext); return@launch }
                }
                val db = ClassNoteDatabase.getDatabase(applicationContext)
                val dao = db.reminderDao()
                val notifDao = db.reminderNotificationDao()

                val addedTitles = mutableListOf<String>()
                val recognizedTitles = mutableListOf<String>()
                results.forEachIndexed { i, event ->
                    if (event == null) return@forEachIndexed
                    recognizedTitles.add(event.title)
                    try {
                        val duplicate = if (event.dueDate != null) {
                            dao.findByTitleAndDueDate(event.title, event.dueDate) != null
                        } else {
                            dao.findByTitleWithNullDueDate(event.title) != null
                        }
                        if (duplicate) return@forEachIndexed

                        val fallbackNote = batch.getOrNull(i)?.text?.take(300) ?: ""
                        val reminderId = dao.insertReminder(
                            ReminderEntity(
                                title = event.title,
                                note = event.note.ifBlank { fallbackNote },
                                dueDate = event.dueDate,
                                dueTime = event.dueTime,
                                category = event.category,
                                syncSource = "notify",
                                sourceName = batch.getOrNull(i)?.let { input ->
                                    val groupName = input.title.trim()
                                    if (groupName.isNotBlank() && groupName != input.appLabel)
                                        "${input.appLabel}・$groupName"
                                    else input.appLabel
                                }
                            )
                        )

                        if (event.dueDate != null) {
                            scheduleDefaultNotifications(reminderId, event.dueDate, event.dueTime, notifDao)
                        }
                        addedTitles.add(event.title)
                        Log.i(TAG, "Auto-added from notification: ${event.title}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save event from batch[$i]", e)
                    }
                }

                if (addedTitles.isNotEmpty()) {
                    NotificationHelper.showAiResult(applicationContext, addedTitles.size, addedTitles)
                    com.rendy.classnote.widget.ClassNoteWidget.refreshAll(applicationContext)
                } else {
                    NotificationHelper.showAiNoResult(applicationContext, recognizedTitles)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process notification batch", e)
                NotificationHelper.cancelAiStatus(applicationContext)
            }
        }
    }

    private suspend fun scheduleDefaultNotifications(
        reminderId: Long,
        dueDate: String,
        dueTime: String?,
        notificationDao: ReminderNotificationDao
    ) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val prefs = AppPreferences(applicationContext)
        val triggerTimes: List<Long> = if (dueTime != null) {
            val (h, m) = dueTime.split(":").map { it.toInt() }
            val base = LocalDateTime.of(LocalDate.parse(dueDate), LocalTime.of(h, m))
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
            ReminderScheduler.scheduleNotification(applicationContext, entity.copy(id = id))
        }
    }

    companion object {
        private const val TAG = "ClassNoteNotifListener"
        private const val BATCH_DELAY_MS = 10_000L
    }
}
