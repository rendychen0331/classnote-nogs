package com.rendy.classnote.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * App 全域設定，存於 SharedPreferences。
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 全頁提醒：提醒觸發時以全螢幕介面顯示（類似鬧鐘）。預設開啟。 */
    var fullScreenAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_FULL_SCREEN_ALARM, true)
        set(value) = prefs.edit { putBoolean(KEY_FULL_SCREEN_ALARM, value) }

    /** Snooze 分鐘數。預設 5 分鐘。 */
    var snoozeDurationMinutes: Int
        get() = prefs.getInt(KEY_SNOOZE_MINUTES, 5)
        set(value) = prefs.edit { putInt(KEY_SNOOZE_MINUTES, value) }

    /** Drive 備份開關。預設開啟。 */
    var driveBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_DRIVE_BACKUP_ENABLED, true)
        set(value) = prefs.edit { putBoolean(KEY_DRIVE_BACKUP_ENABLED, value) }

    /** Gmail 作業同步開關。預設關閉。 */
    var gmailSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_GMAIL_SYNC_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_GMAIL_SYNC_ENABLED, value) }

    /** （測試）同步轉寄的 Classroom 信件。預設關閉。 */
    var gmailClassroomForwardEnabled: Boolean
        get() = prefs.getBoolean(KEY_GMAIL_CLASSROOM_FORWARD, false)
        set(value) = prefs.edit { putBoolean(KEY_GMAIL_CLASSROOM_FORWARD, value) }

    /** 上次 Gmail 同步的結果摘要，供 UI 顯示。 */
    var lastGmailSyncSummary: String
        get() = prefs.getString(KEY_GMAIL_SYNC_SUMMARY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GMAIL_SYNC_SUMMARY, value) }

    /** Classroom 作業同步開關。預設關閉。 */
    var classroomSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLASSROOM_SYNC_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_CLASSROOM_SYNC_ENABLED, value) }

    /** 上次 Classroom 同步的結果摘要，供 UI 顯示。 */
    var lastClassroomSyncSummary: String
        get() = prefs.getString(KEY_CLASSROOM_SYNC_SUMMARY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CLASSROOM_SYNC_SUMMARY, value) }

    /**
     * 備份允許的網路類型。
     * "wifi" = 僅 WiFi，"mobile" = 僅行動數據，"any" = 任何網路（預設）
     */
    var backupNetworkType: String
        get() = prefs.getString(KEY_BACKUP_NETWORK, NETWORK_ANY) ?: NETWORK_ANY
        set(value) = prefs.edit { putString(KEY_BACKUP_NETWORK, value) }

    /** 自動備份開關。預設關閉。 */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_BACKUP_ENABLED, value) }

    /** 自動備份間隔（小時）。預設 24 小時。 */
    var autoBackupIntervalHours: Int
        get() = prefs.getInt(KEY_AUTO_BACKUP_INTERVAL_HOURS, 24)
        set(value) = prefs.edit { putInt(KEY_AUTO_BACKUP_INTERVAL_HOURS, value) }

    /** 自動 Gmail 同步開關。預設關閉。 */
    var autoGmailSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_GMAIL_SYNC_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_GMAIL_SYNC_ENABLED, value) }

    /** 自動 Gmail 同步間隔（小時）。預設 6 小時。 */
    var autoGmailSyncIntervalHours: Int
        get() = prefs.getInt(KEY_AUTO_GMAIL_SYNC_INTERVAL_HOURS, 6)
        set(value) = prefs.edit { putInt(KEY_AUTO_GMAIL_SYNC_INTERVAL_HOURS, value) }

    /** 自動 Classroom 同步開關。預設關閉。 */
    var autoClassroomSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CLASSROOM_SYNC_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CLASSROOM_SYNC_ENABLED, value) }

    /** 自動 Classroom 同步間隔（小時）。預設 6 小時。 */
    var autoClassroomSyncIntervalHours: Int
        get() = prefs.getInt(KEY_AUTO_CLASSROOM_SYNC_INTERVAL_HOURS, 6)
        set(value) = prefs.edit { putInt(KEY_AUTO_CLASSROOM_SYNC_INTERVAL_HOURS, value) }

    /** Gemini API Key，用於 AI 通知解析。 */
    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GEMINI_API_KEY, value) }

    /** CWA API Key，用於天氣預報查詢。 */
    var cwaApiKey: String
        get() = prefs.getString(KEY_CWA_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CWA_API_KEY, value) }

    /** Xiaomi MiMo API Key，用於課堂筆記 AI 對話。 */
    var mimoApiKey: String
        get() = prefs.getString(KEY_MIMO_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_MIMO_API_KEY, value) }

    /** Anthropic Claude API Key。 */
    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CLAUDE_API_KEY, value) }

    /** OpenAI API Key。 */
    var openaiApiKey: String
        get() = prefs.getString(KEY_OPENAI_API_KEY, "") ?: ""
        set(value) = prefs.edit { putString(KEY_OPENAI_API_KEY, value) }

    /** 課堂筆記 AI 對話優先使用的 provider："gemini" | "mimo" | "claude" | "openai"。 */
    var preferredChatProvider: String
        get() = prefs.getString(KEY_PREFERRED_CHAT_PROVIDER, "gemini") ?: "gemini"
        set(value) = prefs.edit { putString(KEY_PREFERRED_CHAT_PROVIDER, value) }

    /** 勿擾模式穿透：提醒通知在勿擾模式下仍顯示。預設關閉。需搭配 ACCESS_NOTIFICATION_POLICY 授權。 */
    var bypassDndEnabled: Boolean
        get() = prefs.getBoolean(KEY_BYPASS_DND, false)
        set(value) = prefs.edit { putBoolean(KEY_BYPASS_DND, value) }

    /** 安靜時段開始（整點，24小時制）。預設 23。 */
    var quietHoursStart: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_START, 23)
        set(value) = prefs.edit { putInt(KEY_QUIET_HOURS_START, value) }

    /** 安靜時段結束（整點，24小時制）。預設 6。 */
    var quietHoursEnd: Int
        get() = prefs.getInt(KEY_QUIET_HOURS_END, 6)
        set(value) = prefs.edit { putInt(KEY_QUIET_HOURS_END, value) }

    /**
     * 安靜時段衝突的通知排程策略。
     * true = 提前（推到安靜時段開始前 1 分鐘）；false = 延後（推到安靜時段結束後）。預設延後。
     */
    var quietHoursPolicyBefore: Boolean
        get() = prefs.getBoolean(KEY_QUIET_HOURS_POLICY_BEFORE, false)
        set(value) = prefs.edit { putBoolean(KEY_QUIET_HOURS_POLICY_BEFORE, value) }

    /** 無時間提醒的預設通知小時（24小時制）。預設 8。 */
    var defaultRemindHour: Int
        get() = prefs.getInt(KEY_DEFAULT_REMIND_HOUR, 8)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_REMIND_HOUR, value) }

    /** 無時間提醒的預設通知分鐘。預設 0。 */
    var defaultRemindMinute: Int
        get() = prefs.getInt(KEY_DEFAULT_REMIND_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_DEFAULT_REMIND_MINUTE, value) }

    /** AI 通知解析開關：自動偵測通知並加入提醒。預設關閉。 */
    var notificationListenerAutoAdd: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_LISTENER_AUTO_ADD, false)
        set(value) = prefs.edit { putBoolean(KEY_NOTIF_LISTENER_AUTO_ADD, value) }

    /**
     * 要監控的 App package name 清單。
     * 空集合 = 監控所有 App；非空 = 只監控清單內的 App。
     */
    var monitoredPackages: Set<String>
        get() = prefs.getStringSet(KEY_MONITORED_PACKAGES, emptySet()) ?: emptySet()
        set(value) = prefs.edit { putStringSet(KEY_MONITORED_PACKAGES, value) }

    /**
     * 各 App 的頻道白名單（通知 title）。
     * 空集合 = 監控該 App 所有頻道；非空 = 只監控清單內的頻道。
     */
    fun getMonitoredChannels(): Map<String, Set<String>> =
        parseChannelJson(prefs.getString(KEY_MONITORED_CHANNELS, null))

    fun setMonitoredChannels(map: Map<String, Set<String>>) {
        prefs.edit { putString(KEY_MONITORED_CHANNELS, toChannelJson(map)) }
    }

    /**
     * 自動記錄看過哪些頻道名稱（通知 title），供 UI 選擇用。
     * 每個 App 最多保留 80 個。
     */
    fun getSeenChannels(): Map<String, Set<String>> =
        parseChannelJson(prefs.getString(KEY_SEEN_CHANNELS, null))

    fun addSeenChannel(pkg: String, channelTitle: String) {
        val map = getSeenChannels().toMutableMap()
        val channels = (map[pkg] ?: emptySet()).toMutableSet()
        if (channels.add(channelTitle)) {
            map[pkg] = if (channels.size > 80) channels.take(80).toSet() else channels
            prefs.edit { putString(KEY_SEEN_CHANNELS, toChannelJson(map)) }
        }
    }

    private fun parseChannelJson(json: String?): Map<String, Set<String>> {
        json ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { pkg ->
                val arr = obj.getJSONArray(pkg)
                (0 until arr.length()).map { arr.getString(it) }.toSet()
            }
        } catch (_: Exception) { emptyMap() }
    }

    private fun toChannelJson(map: Map<String, Set<String>>): String {
        val obj = JSONObject()
        map.forEach { (pkg, channels) -> obj.put(pkg, JSONArray(channels.toList())) }
        return obj.toString()
    }

    companion object {
        private const val PREFS_NAME = "classnote_prefs"
        private const val KEY_FULL_SCREEN_ALARM = "full_screen_alarm_enabled"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_DRIVE_BACKUP_ENABLED = "drive_backup_enabled"
        private const val KEY_GMAIL_SYNC_ENABLED = "gmail_sync_enabled"
        private const val KEY_GMAIL_SYNC_SUMMARY = "gmail_sync_summary"
        private const val KEY_GMAIL_CLASSROOM_FORWARD = "gmail_classroom_forward_enabled"
        private const val KEY_CLASSROOM_SYNC_ENABLED = "classroom_sync_enabled"
        private const val KEY_CLASSROOM_SYNC_SUMMARY = "classroom_sync_summary"
        private const val KEY_BACKUP_NETWORK = "backup_network_type"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_BACKUP_INTERVAL_HOURS = "auto_backup_interval_hours"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_CWA_API_KEY = "cwa_api_key"
        private const val KEY_MIMO_API_KEY = "mimo_api_key"
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_PREFERRED_CHAT_PROVIDER = "preferred_chat_provider"
        private const val KEY_NOTIF_LISTENER_AUTO_ADD = "notif_listener_auto_add"
        private const val KEY_BYPASS_DND = "bypass_dnd_enabled"
        private const val KEY_MONITORED_PACKAGES = "notif_monitored_packages"
        private const val KEY_MONITORED_CHANNELS = "notif_monitored_channels"
        private const val KEY_SEEN_CHANNELS = "notif_seen_channels"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_QUIET_HOURS_POLICY_BEFORE = "quiet_hours_policy_before"
        private const val KEY_AUTO_GMAIL_SYNC_ENABLED = "auto_gmail_sync_enabled"
        private const val KEY_AUTO_GMAIL_SYNC_INTERVAL_HOURS = "auto_gmail_sync_interval_hours"
        private const val KEY_AUTO_CLASSROOM_SYNC_ENABLED = "auto_classroom_sync_enabled"
        private const val KEY_AUTO_CLASSROOM_SYNC_INTERVAL_HOURS = "auto_classroom_sync_interval_hours"
        private const val KEY_DEFAULT_REMIND_HOUR = "default_remind_hour"
        private const val KEY_DEFAULT_REMIND_MINUTE = "default_remind_minute"

        const val NETWORK_WIFI = "wifi"
        const val NETWORK_MOBILE = "mobile"
        const val NETWORK_ANY = "any"
    }
}
