package com.rendy.classnote.widget

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rendy.classnote.R
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OverviewRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val db = ClassNoteDatabase.getDatabase(context)
    private var items: List<ScheduleItem> = emptyList()

    data class ScheduleItem(
        val title: String,
        val category: String?,
        val timeLabel: String,
        val isNotification: Boolean  // true = bell icon, false = circle
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = runBlocking {
            val today = LocalDate.now()
            val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            val tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            val nowMs = System.currentTimeMillis()

            val reminders = db.reminderDao().getActiveRemindersOnce()
            val notifications = db.reminderNotificationDao().getAllPendingNotifications()
            val notifMap = notifications.groupBy { it.reminderId }

            buildList {
                for (r in reminders) {
                    val timeLabel = buildTimeLabel(r, notifMap[r.id]?.minByOrNull { it.triggerAt }?.triggerAt, todayStr, tomorrowStr, nowMs)
                    add(ScheduleItem(
                        title = r.title,
                        category = r.category,
                        timeLabel = timeLabel,
                        isNotification = r.category == ReminderCategory.REMINDER.name
                    ))
                }
            }
        }
    }

    private fun buildTimeLabel(
        r: ReminderEntity,
        nextTrigger: Long?,
        todayStr: String,
        tomorrowStr: String,
        nowMs: Long
    ): String {
        if (nextTrigger != null) {
            val dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(nextTrigger), ZoneId.systemDefault()
            )
            val dayPrefix = when (dt.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)) {
                todayStr -> "今天"
                tomorrowStr -> "明天"
                else -> dt.toLocalDate().format(DateTimeFormatter.ofPattern("M/d"))
            }
            val timeStr = dt.format(DateTimeFormatter.ofPattern("HH:mm"))
            return "$dayPrefix $timeStr"
        }
        if (r.dueDate != null) {
            val suffix = when (r.dueDate) {
                todayStr -> "今天 截止"
                tomorrowStr -> "明天 截止"
                else -> "${r.dueDate} 截止"
            }
            return suffix
        }
        return "待辦"
    }

    override fun onDestroy() {}

    override fun getCount() = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_item_schedule)

        val views = RemoteViews(context.packageName, R.layout.widget_item_schedule)

        views.setTextViewText(R.id.tvTitle, item.title)
        views.setTextViewText(R.id.tvTime, item.timeLabel)

        if (item.isNotification) {
            views.setTextViewText(R.id.tvIcon, "🔔")
            views.setTextColor(R.id.tvIcon, Color.parseColor("#FFA726"))
            views.setViewVisibility(R.id.tvCategory, View.GONE)
        } else {
            views.setTextViewText(R.id.tvIcon, "○")
            views.setTextColor(R.id.tvIcon, Color.parseColor("#AAAACC"))

            val cat = ReminderCategory.fromString(item.category)
            if (cat != null) {
                views.setViewVisibility(R.id.tvCategory, View.VISIBLE)
                views.setTextViewText(R.id.tvCategory, cat.label)
                views.setInt(R.id.tvCategory, "setBackgroundResource", chipDrawableRes(cat))
            } else {
                views.setViewVisibility(R.id.tvCategory, View.GONE)
            }
        }

        return views
    }

    private fun chipDrawableRes(cat: ReminderCategory) = when (cat) {
        ReminderCategory.WORK -> R.drawable.widget_chip_work
        ReminderCategory.HOMEWORK -> R.drawable.widget_chip_homework
        ReminderCategory.EXAM -> R.drawable.widget_chip_exam
        ReminderCategory.REMINDER -> R.drawable.widget_chip_reminder
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false
}
