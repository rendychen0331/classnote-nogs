package com.rendy.classnote.data

import android.content.Context
import androidx.core.content.edit

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

    /** 小米鎖屏顯示權限：使用者手動確認過是否已授予（無公開 API 可查詢）。預設 false。 */
    var xiaomiLockScreenGranted: Boolean
        get() = prefs.getBoolean(KEY_XIAOMI_LOCK_SCREEN, false)
        set(value) = prefs.edit { putBoolean(KEY_XIAOMI_LOCK_SCREEN, value) }

    /**
     * 備份允許的網路類型。
     * "wifi" = 僅 WiFi，"mobile" = 僅行動數據，"any" = 任何網路（預設）
     */
    var backupNetworkType: String
        get() = prefs.getString(KEY_BACKUP_NETWORK, NETWORK_ANY) ?: NETWORK_ANY
        set(value) = prefs.edit { putString(KEY_BACKUP_NETWORK, value) }

    companion object {
        private const val PREFS_NAME = "classnote_prefs"
        private const val KEY_FULL_SCREEN_ALARM = "full_screen_alarm_enabled"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
        private const val KEY_XIAOMI_LOCK_SCREEN = "xiaomi_lock_screen_granted"
        private const val KEY_BACKUP_NETWORK = "backup_network_type"

        const val NETWORK_WIFI = "wifi"
        const val NETWORK_MOBILE = "mobile"
        const val NETWORK_ANY = "any"
    }
}
