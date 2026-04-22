package com.rendy.classnote.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.view.View
import android.widget.RemoteViews
// Use context.getSharedPreferences directly (no preference-ktx dependency needed)
import android.content.ComponentName
import com.rendy.classnote.R
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.notification.ReminderScheduler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar

class ClassNoteWidget : AppWidgetProvider() {

    companion object {
        const val ACTION_SWITCH_TAB = "com.rendy.classnote.SWITCH_TAB"
        const val ACTION_PREV_MONTH = "com.rendy.classnote.PREV_MONTH"
        const val ACTION_NEXT_MONTH = "com.rendy.classnote.NEXT_MONTH"
        const val ACTION_COMPLETE_REMINDER = "com.rendy.classnote.COMPLETE_REMINDER"
        const val EXTRA_WIDGET_ID = "widget_id"
        const val EXTRA_SHOW_CALENDAR = "show_calendar"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val PREF_TAB = "widget_tab_"
        const val PREF_MONTH = "widget_month_"

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            try {
                updateWidgetInternal(context, manager, widgetId)
            } catch (e: Exception) {
                // 任何未預期的例外：顯示空白 widget 避免「載入小工具時發生問題」
                Log.e("ClassNoteWidget", "updateWidget failed for id=$widgetId", e)
                try {
                    val views = RemoteViews(context.packageName, R.layout.widget_main)
                    manager.updateAppWidget(widgetId, views)
                } catch (e2: Exception) {
                    Log.e("ClassNoteWidget", "fallback updateAppWidget also failed", e2)
                }
            }
        }

        private fun updateWidgetInternal(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val prefs = context.getSharedPreferences("classnote_widget", Context.MODE_PRIVATE)
            val showCalendar = prefs.getBoolean("$PREF_TAB$widgetId", false)
            val yearMonthStr = prefs.getString("$PREF_MONTH$widgetId", null)
            val yearMonth = yearMonthStr?.let {
                runCatching { YearMonth.parse(it) }.getOrNull()
            } ?: YearMonth.now()

            val views = RemoteViews(context.packageName, R.layout.widget_main)

            // ── Tab click actions ──────────────────────────────────────────
            val overviewIntent = Intent(context, ClassNoteWidget::class.java).apply {
                action = ACTION_SWITCH_TAB
                putExtra(EXTRA_WIDGET_ID, widgetId)
                putExtra(EXTRA_SHOW_CALENDAR, false)
            }
            val calendarIntent = Intent(context, ClassNoteWidget::class.java).apply {
                action = ACTION_SWITCH_TAB
                putExtra(EXTRA_WIDGET_ID, widgetId)
                putExtra(EXTRA_SHOW_CALENDAR, true)
            }
            val prevIntent = Intent(context, ClassNoteWidget::class.java).apply {
                action = ACTION_PREV_MONTH
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            val nextIntent = Intent(context, ClassNoteWidget::class.java).apply {
                action = ACTION_NEXT_MONTH
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

            views.setOnClickPendingIntent(R.id.tabOverview,
                android.app.PendingIntent.getBroadcast(context, widgetId * 10, overviewIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.tabCalendar,
                android.app.PendingIntent.getBroadcast(context, widgetId * 10 + 1, calendarIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btnCalPrev,
                android.app.PendingIntent.getBroadcast(context, widgetId * 10 + 2, prevIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            views.setOnClickPendingIntent(R.id.btnCalNext,
                android.app.PendingIntent.getBroadcast(context, widgetId * 10 + 3, nextIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            if (openAppIntent != null) {
                views.setOnClickPendingIntent(R.id.btnAdd,
                    android.app.PendingIntent.getActivity(context, widgetId * 10 + 4, openAppIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
            }

            // ── Tab styling ────────────────────────────────────────────────
            if (showCalendar) {
                views.setInt(R.id.tabCalendar, "setBackgroundResource", R.drawable.widget_tab_selected)
                views.setTextColor(R.id.tabCalendar, Color.parseColor("#1A1040"))
                views.setInt(R.id.tabOverview, "setBackgroundResource", 0)
                views.setTextColor(R.id.tabOverview, Color.parseColor("#AAAACC"))
                views.setViewVisibility(R.id.containerOverview, View.GONE)
                views.setViewVisibility(R.id.containerCalendar, View.VISIBLE)
            } else {
                views.setInt(R.id.tabOverview, "setBackgroundResource", R.drawable.widget_tab_selected)
                views.setTextColor(R.id.tabOverview, Color.parseColor("#1A1040"))
                views.setInt(R.id.tabCalendar, "setBackgroundResource", 0)
                views.setTextColor(R.id.tabCalendar, Color.parseColor("#AAAACC"))
                views.setViewVisibility(R.id.containerOverview, View.VISIBLE)
                views.setViewVisibility(R.id.containerCalendar, View.GONE)
            }

            // ── Load data ──────────────────────────────────────────────────
            try {
                runBlocking {
                    // 限時 5 秒，避免 DB 查詢過慢導致 ANR
                    withTimeout(5000L) {
                        val db = ClassNoteDatabase.getDatabase(context)

                        if (!showCalendar) {
                            populateOverview(context, views, db, widgetId)
                        } else {
                            val reminders = db.reminderDao().getActiveRemindersOnce()
                            CalendarWidgetHelper.populate(context, views, yearMonth, reminders)
                        }
                    }
                }
            } catch (e: Exception) {
                // 資料載入失敗或逾時：仍更新 widget（顯示空白狀態）
                Log.e("ClassNoteWidget", "loadData failed or timed out", e)
            }

            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.listSchedule)
        }

        private suspend fun populateOverview(
            context: Context,
            views: RemoteViews,
            db: ClassNoteDatabase,
            widgetId: Int
        ) {
            // ── Determine current semester ID ──────────────────────────────
            // 學期約在 2 月底、8 月底開始，以 20 日為切換點
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH) + 1
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val (semYear, semester) = when {
                month > 8 || (month == 8 && day >= 20) -> year to 2
                month > 2 || (month == 2 && day >= 20) -> year to 1
                else -> (year - 1) to 2  // 1月~2月中旬：上一年第二學期
            }
            val semesterId = "$semYear-$semester"

            // ── Find next course ───────────────────────────────────────────
            val today = LocalDate.now()
            val dayOfWeek = today.dayOfWeek.value  // 1=Mon, 7=Sun; our data uses 1=Mon...5=Fri
            val periodTimes = db.periodTimeDao().getAllPeriodTimesOnce()
            val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute
            val todayCourses = if (dayOfWeek <= 5) {
                db.courseDao().getCoursesByDayOnce(semesterId, dayOfWeek)
            } else emptyList()

            val nextCourse = todayCourses.firstOrNull { course ->
                val pt = periodTimes.find { it.period == course.period }
                pt != null && pt.startMinute > nowMinute
            }

            // NEXT label
            if (nextCourse != null) {
                val pt = periodTimes.find { it.period == nextCourse.period }
                val timeStr = if (pt != null) {
                    val h = pt.startMinute / 60
                    val m = pt.startMinute % 60
                    "%02d:%02d".format(h, m)
                } else ""
                views.setTextViewText(R.id.tvNextLabel, "NEXT: ${nextCourse.name}  $timeStr")
            } else {
                views.setTextViewText(R.id.tvNextLabel, "今日無課")
            }

            // ── Linked reminders for next course ──────────────────────────
            val linked = if (nextCourse != null) {
                db.reminderDao().getActiveRemindersOnce()
                    .filter { it.courseId == nextCourse.id }
                    .take(2)
            } else emptyList()

            fun bindLinked(reminderId: Int, catId: Int, titleId: Int, reminder: com.rendy.classnote.data.local.entity.ReminderEntity?) {
                if (reminder == null) {
                    views.setViewVisibility(reminderId, View.GONE)
                } else {
                    views.setViewVisibility(reminderId, View.VISIBLE)
                    val cat = ReminderCategory.fromString(reminder.category)
                    if (cat != null) {
                        views.setTextViewText(catId, cat.label)
                        val (drawableRes, textColor) = when (cat) {
                            ReminderCategory.WORK -> R.drawable.widget_chip_work to "#54C7FC"
                            ReminderCategory.HOMEWORK -> R.drawable.widget_chip_homework to "#66BB6A"
                            ReminderCategory.EXAM -> R.drawable.widget_chip_exam to "#FF6B6B"
                            ReminderCategory.REMINDER -> R.drawable.widget_chip_reminder to "#FFB74D"
                        }
                        views.setInt(catId, "setBackgroundResource", drawableRes)
                        views.setTextColor(catId, Color.parseColor(textColor))
                    }
                    views.setTextViewText(titleId, reminder.title)
                }
            }
            bindLinked(R.id.linkedReminder1, R.id.tvLinkedCat1, R.id.tvLinkedTitle1, linked.getOrNull(0))
            bindLinked(R.id.linkedReminder2, R.id.tvLinkedCat2, R.id.tvLinkedTitle2, linked.getOrNull(1))

            // ── Right panel: soonest upcoming reminder with notification ───
            val allReminders = db.reminderDao().getActiveRemindersOnce()
            val notifications = db.reminderNotificationDao().getAllPendingNotifications()
                .sortedBy { it.triggerAt }
            val soonestNotif = notifications.firstOrNull()
            val soonestReminder = soonestNotif?.let { n -> allReminders.find { it.id == n.reminderId } }

            if (soonestReminder != null && soonestNotif != null) {
                views.setViewVisibility(R.id.containerRightReminder, View.VISIBLE)
                views.setTextViewText(R.id.tvRightReminderTitle, soonestReminder.title)
                val dt = java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(soonestNotif.triggerAt),
                    java.time.ZoneId.systemDefault()
                )
                val pattern = if (dt.hour < 12) "上午 hh:mm" else "下午 hh:mm"
                val timeLabel = dt.format(DateTimeFormatter.ofPattern(pattern))
                views.setTextViewText(R.id.tvRightReminderTime, timeLabel)
            } else {
                views.setViewVisibility(R.id.containerRightReminder, View.GONE)
            }

            // ── Schedule ListView ──────────────────────────────────────────
            val serviceIntent = Intent(context, OverviewRemoteViewsService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = android.net.Uri.fromParts("content", widgetId.toString(), null)
            }
            views.setRemoteAdapter(R.id.listSchedule, serviceIntent)

            // 設定 ListView 項目點擊模板（完成提醒事項）
            val completeTemplate = Intent(context, ClassNoteWidget::class.java).apply {
                action = ACTION_COMPLETE_REMINDER
                putExtra(EXTRA_WIDGET_ID, widgetId)
            }
            views.setPendingIntentTemplate(R.id.listSchedule,
                android.app.PendingIntent.getBroadcast(context, widgetId * 10 + 5, completeTemplate,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE))
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        for (id in widgetIds) updateWidget(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.getSharedPreferences("classnote_widget", Context.MODE_PRIVATE)
        val manager = AppWidgetManager.getInstance(context)

        when (intent.action) {
            ACTION_SWITCH_TAB -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                val showCal = intent.getBooleanExtra(EXTRA_SHOW_CALENDAR, false)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    prefs.edit().putBoolean("$PREF_TAB$id", showCal).apply()
                    updateWidget(context, manager, id)
                }
            }
            ACTION_PREV_MONTH -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getString("$PREF_MONTH$id", null)
                        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now()
                    prefs.edit().putString("$PREF_MONTH$id", cur.minusMonths(1).toString()).apply()
                    updateWidget(context, manager, id)
                }
            }
            ACTION_NEXT_MONTH -> {
                val id = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getString("$PREF_MONTH$id", null)
                        ?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.now()
                    prefs.edit().putString("$PREF_MONTH$id", cur.plusMonths(1).toString()).apply()
                    updateWidget(context, manager, id)
                }
            }
            ACTION_COMPLETE_REMINDER -> {
                val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
                if (reminderId > 0) {
                    try {
                        runBlocking {
                            withTimeout(3000L) {
                                val db = ClassNoteDatabase.getDatabase(context)
                                // 取消未觸發的通知鬧鐘
                                val pending = db.reminderNotificationDao()
                                    .getNotificationsOnce(reminderId)
                                    .filter { !it.isFired }
                                pending.forEach { ReminderScheduler.cancelNotification(context, it.id) }
                                db.reminderNotificationDao().deleteNotificationsForReminder(reminderId)
                                // 標記完成
                                db.reminderDao().markCompleted(reminderId)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ClassNoteWidget", "completeReminder id=$reminderId failed", e)
                    }
                    // 刷新所有 widget
                    val ids = manager.getAppWidgetIds(ComponentName(context, ClassNoteWidget::class.java))
                    for (id in ids) updateWidget(context, manager, id)
                }
            }
        }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        val prefs = context.getSharedPreferences("classnote_widget", Context.MODE_PRIVATE)
        for (id in widgetIds) {
            prefs.edit()
                .remove("$PREF_TAB$id")
                .remove("$PREF_MONTH$id")
                .apply()
        }
    }
}
