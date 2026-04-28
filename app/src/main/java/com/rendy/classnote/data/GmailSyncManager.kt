package com.rendy.classnote.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.MessagePart
import com.rendy.classnote.data.local.dao.ReminderDao
import com.rendy.classnote.data.local.dao.ReminderNotificationDao
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.local.entity.ReminderNotificationEntity
import com.rendy.classnote.data.remote.ApiLogger
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object GmailSyncManager {

    sealed class SyncResult {
        data class Success(val imported: Int, val skipped: Int) : SyncResult()
        data class Error(val message: String) : SyncResult()
        object NoPermission : SyncResult()
    }

    private const val TAG = "GmailSyncManager"

    // 截止日期 — 結構化格式：【截止日期】\n4月20日（含可選時間）
    private val DUE_DATE_BLOCK = Regex("""【截止日期】\s*\n+(\d+)月(\d+)日(?:\s*(上午|下午)?\s*(\d{1,2})[：:](\d{2}))?""")
    // 截止日期 — 舊格式備援：截止日期：4月20日 上午 11:59
    private val DUE_DATE_INLINE = Regex("""截止日期[：:]\s*(\d+)月(\d+)日(?:\s*(上午|下午)?\s*(\d{1,2})[：:](\d{2}))?""")
    // 內文標題 — 結構化格式：【作業標題】\n<完整標題>
    private val BODY_TITLE_BLOCK = Regex("""【作業標題】\s*\n+([^\n【]+)""")
    // 內文標題 — 英文備援：[Assignment Title]\n<title>
    private val BODY_TITLE_EN_BLOCK = Regex("""\[Assignment\s+[Tt]itle\]\s*\n+([^\n\[]+)""")
    // Fallback：主旨中的標題（主旨可能被截斷）
    private val SUBJECT_TITLE_ZH = Regex("""新作業[：:][「「]([^」\n]+)[」」]""")
    private val SUBJECT_TITLE_EN = Regex("""New assignment:\s+(.+)""", RegexOption.IGNORE_CASE)
    // 課程名稱 — 底部結構：「前往課堂\n<link>\n{課程名稱}\n」
    private val COURSE_IN_BODY = Regex("""前往課堂\n<[^\n]+>\n([^\n]+)\n""")
    // 課程名稱 — 頂部結構：「{課程名稱}\n<link>\n新作業」（更常見的格式）
    private val COURSE_AT_TOP = Regex("""([^\n]+)\n<[^\n]+>\n(?:新作業|[Nn]ew\s+assignment)""")
    // 雜訊行：行內只要包含以下關鍵字就過濾（Classroom 通知固定格式）
    private val NOISE_LINE = Regex(
        """<[^>]*>|https?://|前往課堂|Go to Classroom|取消訂閱|unsubscribe|Google LLC""" +
        """|通知設定|瞭解詳情|Learn more|張貼日期|張貼者|不想接收""" +
        """|您曾表示|如果您不想|您。如果|You're receiving|To unsubscribe""" +
        """|^新作業$|^New assignment$|^Posted$|^Due date$""",
        RegexOption.IGNORE_CASE
    )

    /** 確認帳號是否已授予 Gmail 讀取權限。 */
    fun hasGmailPermission(context: Context, account: GoogleSignInAccount): Boolean =
        GoogleSignIn.hasPermissions(account, Scope(GmailScopes.GMAIL_READONLY))

    private fun buildGmailService(context: Context, account: GoogleSignInAccount): Gmail {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(GmailScopes.GMAIL_READONLY)
        ).apply { selectedAccount = account.account }
        return Gmail.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("ClassNote")
            .build()
    }

    /**
     * 掃描 Gmail 中來自 Google Classroom 的作業通知，匯入為提醒事項。
     * 已匯入（externalId 相符）的郵件自動略過，不重複新增。
     */
    suspend fun sync(
        context: Context,
        account: GoogleSignInAccount,
        dao: ReminderDao,
        notificationDao: ReminderNotificationDao,
        includeForwarded: Boolean = false
    ): SyncResult = withContext(Dispatchers.IO) {
            if (!hasGmailPermission(context, account)) return@withContext SyncResult.NoPermission
            try {
                val gmail = buildGmailService(context, account)

                val queries = mutableListOf(
                    "from:no-reply@classroom.google.com (subject:新作業 OR subject:\"new assignment\")"
                )
                if (includeForwarded) {
                    queries += "(subject:新作業 OR subject:\"new assignment\") -from:no-reply@classroom.google.com"
                }

                var imported = 0
                var skipped = 0

                for (query in queries) {
                    val messages = gmail.users().messages().list("me")
                        .setQ(query)
                        .setMaxResults(50L)
                        .execute()
                        .messages ?: continue

                    for (msgRef in messages) {
                        val externalId = "gmail:${msgRef.id}"
                        if (dao.findByExternalId(externalId) != null) {
                            skipped++
                            continue
                        }

                        val msg = gmail.users().messages().get("me", msgRef.id)
                            .setFormat("full")
                            .execute()

                        val parsed = parseMessage(msg)
                        if (parsed == null) {
                            skipped++
                            continue
                        }

                        val note = parsed.bodyNote
                        val reminderId = dao.insertReminder(
                            ReminderEntity(
                                title = parsed.title,
                                note = note,
                                dueDate = parsed.dueDate,
                                dueTime = parsed.dueTime,
                                category = "HOMEWORK",
                                externalId = externalId,
                                syncSource = "gmail",
                                sourceName = parsed.courseName?.ifBlank { null } ?: "unknown"
                            )
                        )
                        scheduleDefaultNotifications(context, reminderId, parsed.dueDate, parsed.dueTime, notificationDao)
                        imported++
                    }
                }

                ApiLogger.log("Gmail(同步)", "sync", "匯入$imported 略過$skipped", 0, true)
                if (imported > 0) com.rendy.classnote.widget.ClassNoteWidget.refreshAll(context)
                SyncResult.Success(imported, skipped)
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                ApiLogger.log("Gmail(同步)", "sync", e.message ?: "同步失敗", 0, false)
                SyncResult.Error(e.message ?: "同步失敗")
            }
        }

    private fun parseMessage(msg: com.google.api.services.gmail.model.Message): ParsedAssignment? {
        val headers = msg.payload?.headers ?: return null
        val subject = headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: return null

        // Gmail 查詢已用 from:no-reply@classroom.google.com 過濾，不需再用主旨偵測
        val bodyText = getPlainTextBody(msg.payload)
            ?.replace("\r\n", "\n")?.replace("\r", "\n") ?: return null

        // 優先從內文抓完整標題，主旨可能被 Gmail 截斷
        val title = BODY_TITLE_BLOCK.find(bodyText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: BODY_TITLE_EN_BLOCK.find(bodyText)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: SUBJECT_TITLE_ZH.find(subject)?.groupValues?.get(1)
            ?: SUBJECT_TITLE_EN.find(subject)?.groupValues?.get(1)
            ?: return null

        val (dueDate, dueTime) = parseDueDateTime(bodyText)
        val courseName = COURSE_IN_BODY.find(bodyText)?.groupValues?.get(1)?.trim()
            ?: COURSE_AT_TOP.find(bodyText)?.groupValues?.get(1)?.trim()
        val bodyNote = extractBodyNote(bodyText, title.trim(), courseName)

        return ParsedAssignment(title.trim(), courseName, dueDate, dueTime, bodyNote)
    }

    /** 遞迴取出 multipart 郵件中的 text/plain 部分。 */
    private fun getPlainTextBody(payload: MessagePart?): String? {
        if (payload == null) return null
        if (payload.mimeType == "text/plain" && payload.body?.data != null) {
            return String(Base64.decode(payload.body.data, Base64.URL_SAFE), Charsets.UTF_8)
        }
        payload.parts?.forEach { part ->
            val result = getPlainTextBody(part)
            if (result != null) return result
        }
        return null
    }

    /**
     * 從郵件內文解析截止日期與時間。
     * 格式：「截止日期：M月D日 [上午|下午] H:MM」（時間可選）
     * 若日期早於今天 60 天以上，自動推為明年。
     * 回傳 Pair(dueDate, dueTime)，dueTime 可為 null。
     */
    private fun parseDueDateTime(body: String): Pair<String?, String?> {
        val match = DUE_DATE_BLOCK.find(body) ?: DUE_DATE_INLINE.find(body) ?: return Pair(null, null)
        val month = match.groupValues[1].toIntOrNull() ?: return Pair(null, null)
        val day = match.groupValues[2].toIntOrNull() ?: return Pair(null, null)
        val now = LocalDate.now()
        val candidate = runCatching { LocalDate.of(now.year, month, day) }.getOrNull() ?: return Pair(null, null)
        val year = if (candidate.isBefore(now.minusDays(60))) now.year + 1 else now.year
        val dueDate = "%04d-%02d-%02d".format(year, month, day)

        val period = match.groupValues[3]  // "上午" / "下午" / ""
        val hourRaw = match.groupValues[4].toIntOrNull()
        val minute = match.groupValues[5].toIntOrNull()
        val dueTime = if (hourRaw != null && minute != null) {
            val hour = when {
                period == "下午" && hourRaw < 12 -> hourRaw + 12
                period == "上午" && hourRaw == 12 -> 0
                else -> hourRaw
            }
            "%02d:%02d".format(hour, minute)
        } else null

        return Pair(dueDate, dueTime)
    }

    /** 從郵件內文萃取有意義的備註內容，移除連結、導覽列、頁尾、標題行、課程名、截止日期行等雜訊。 */
    private fun extractBodyNote(body: String, title: String, courseName: String?): String =
        body.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank()
                    && !NOISE_LINE.containsMatchIn(line)
                    && !line.contains(title, ignoreCase = true)
                    && (courseName.isNullOrBlank() || !line.contains(courseName, ignoreCase = true))
                    && !DUE_DATE_BLOCK.containsMatchIn(line)
                    && !DUE_DATE_INLINE.containsMatchIn(line)
            }
            .joinToString("\n")
            .trim()

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
            val base = java.time.LocalDateTime.of(
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

    private data class ParsedAssignment(
        val title: String,
        val courseName: String?,
        val dueDate: String?,
        val dueTime: String?,
        val bodyNote: String
    )
}
