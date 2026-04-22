package com.rendy.classnote.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.ActivityReminderAlarmBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 全螢幕鬧鐘介面。
 * 透過 NotificationHelper.setFullScreenIntent 觸發，
 * 或在鎖屏狀態下由通知直接喚醒螢幕顯示。
 */
class ReminderAlarmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITLE = "alarm_title"
        const val EXTRA_NOTE = "alarm_note"
        const val EXTRA_CATEGORY = "alarm_category"
        const val EXTRA_NOTIFICATION_ID = "alarm_notification_id"
        const val EXTRA_FULL_SCREEN_ALARM = "alarm_full_screen_alarm"

        // 通知列動作按鈕（延後/完成）觸發後廣播此 action 讓 Activity 自動關閉
        const val ACTION_DISMISS = "com.rendy.classnote.ALARM_DISMISS"
        const val EXTRA_DISMISS_NOTIFICATION_ID = "dismiss_notification_id"

        const val SNOOZE_MINUTES = 10L

        // 震動模式：on 800ms → off 400ms → on 800ms → off 400ms → ... 循環
        private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400, 800, 400, 800, 2000)
    }

    private lateinit var binding: ActivityReminderAlarmBinding
    private var vibrator: Vibrator? = null
    private var notificationId: Int = -1

    // 監聽通知列按鈕（延後/完成）觸發的 dismiss 廣播
    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(EXTRA_DISMISS_NOTIFICATION_ID, -1)
            if (id == notificationId || id == -1) {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 讓 Activity 顯示在鎖屏上方並喚醒螢幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        binding = ActivityReminderAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: return finish()
        val note = intent.getStringExtra(EXTRA_NOTE) ?: ""
        val category = intent.getStringExtra(EXTRA_CATEGORY)
        notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val fullScreenAlarm = intent.getBooleanExtra(EXTRA_FULL_SCREEN_ALARM, true)

        // 註冊 dismiss receiver（通知列延後/完成 → 自動關閉 Activity）
        ContextCompat.registerReceiver(
            this, dismissReceiver,
            IntentFilter(ACTION_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 顯示當前時間
        binding.tvAlarmTime.text =
            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        // 分類色條 + chip
        val cat = ReminderCategory.fromString(category)
        val accentColor = Color.parseColor(ReminderCategory.colorFor(category))
        binding.viewAccentBar.setBackgroundColor(accentColor)

        if (cat != null) {
            binding.tvCategory.visibility = View.VISIBLE
            binding.tvCategory.text = cat.label
            val bg = binding.tvCategory.background.mutate() as? GradientDrawable
            bg?.setColor(accentColor)
        }

        // 標題 + 備註
        binding.tvTitle.text = title
        if (note.isNotBlank()) {
            binding.tvNote.visibility = View.VISIBLE
            binding.tvNote.text = note
        }

        // 震動（全螢幕提醒開啟時才震）
        if (fullScreenAlarm) startVibration()

        // 關閉按鈕
        binding.btnDismiss.setOnClickListener {
            finish()
        }

        // 延後（Snooze）
        binding.btnSnooze.setOnClickListener {
            scheduleSnooze(notificationId, title, note, category, fullScreenAlarm)
            finish()
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VibratorManager::class.java))?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 播完 PATTERN 後從 index 0 重複
            vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(VIBRATION_PATTERN, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vibrator?.cancel()
        try { unregisterReceiver(dismissReceiver) } catch (_: Exception) {}
    }

    private fun scheduleSnooze(
        notificationId: Int,
        title: String,
        note: String,
        category: String?,
        fullScreenAlarm: Boolean
    ) {
        if (notificationId < 0) return
        val app = applicationContext as? com.rendy.classnote.ClassNoteApplication
        val snoozeMinutes = app?.appPreferences?.snoozeDurationMinutes?.toLong() ?: SNOOZE_MINUTES
        val triggerAt = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val alarmManager =
            getSystemService(android.app.AlarmManager::class.java) ?: return
        val intent = android.content.Intent(this,
            com.rendy.classnote.notification.ReminderReceiver::class.java).apply {
            action = "com.rendy.classnote.REMINDER_ALARM"
            putExtra(com.rendy.classnote.notification.ReminderReceiver.EXTRA_NOTIFICATION_ID,
                notificationId.toLong())
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_NOTE, note)
            putExtra(EXTRA_CATEGORY, category)
            putExtra("is_snooze", true)
            putExtra("full_screen_alarm", fullScreenAlarm)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this,
            notificationId + 100_000,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
        )
        try {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } catch (_: SecurityException) {}
    }
}
