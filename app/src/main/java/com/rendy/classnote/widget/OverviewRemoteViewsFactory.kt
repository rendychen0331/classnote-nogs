package com.rendy.classnote.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.rendy.classnote.R
import com.rendy.classnote.data.local.ClassNoteDatabase
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class OverviewRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {

    private val db: ClassNoteDatabase? by lazy {
        try { ClassNoteDatabase.getDatabase(context) } catch (e: Exception) {
            Log.e("OverviewFactory", "DB init failed", e); null
        }
    }
    private var items: List<ScheduleItem> = emptyList()

    data class ScheduleItem(
        val reminderId: Long,
        val title: String,
        val category: String?,
        val timeLabel: String,
        val isNotification: Boolean  // true = bell icon, false = circle
    )

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = try {
            val database = db ?: return
            runBlocking {
                withTimeout(5000L) {
                    val today = LocalDate.now()
                    val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val tomorrowStr = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

                    val reminders = database.reminderDao().getActiveRemindersOnce()
                    val notifications = database.reminderNotificationDao().getAllPendingNotifications()
                    val notifMap = notifications.groupBy { it.reminderId }

                    buildList {
                        for (r in reminders) {
                            val timeLabel = buildTimeLabel(r, notifMap[r.id]?.minByOrNull { it.triggerAt }?.triggerAt, todayStr, tomorrowStr)
                            add(ScheduleItem(
                                reminderId = r.id,
                                title = r.title,
                                category = r.category,
                                timeLabel = timeLabel,
                                isNotification = r.category == ReminderCategory.REMINDER.name
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OverviewFactory", "onDataSetChanged failed", e)
            emptyList()
        }
    }

    private fun buildTimeLabel(
        r: ReminderEntity,
        nextTrigger: Long?,
        todayStr: String,
        tomorrowStr: String
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
        return try {
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

            // 勾選完成按鈕：fill-in intent 攜帶 reminder ID
            val fillIntent = Intent().apply {
                putExtra(ClassNoteWidget.EXTRA_REMINDER_ID, item.reminderId)
            }
            views.setOnClickFillInIntent(R.id.btnComplete, fillIntent)

            views
        } catch (e: Exception) {
            Log.e("OverviewFactory", "getViewAt($position) failed", e)
            RemoteViews(context.packageName, R.layout.widget_item_schedule)
        }
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
