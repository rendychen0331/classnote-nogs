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

    companion object {
        private const val PREFS_NAME = "classnote_prefs"
        private const val KEY_FULL_SCREEN_ALARM = "full_screen_alarm_enabled"
        private const val KEY_SNOOZE_MINUTES = "snooze_minutes"
    }
}
