package com.rendy.classnote.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.rendy.classnote.R
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CalendarWidgetHelper {

    private val CELL_IDS = arrayOf(
        intArrayOf(R.id.calCell00, R.id.calCell01, R.id.calCell02, R.id.calCell03, R.id.calCell04, R.id.calCell05, R.id.calCell06),
        intArrayOf(R.id.calCell07, R.id.calCell08, R.id.calCell09, R.id.calCell10, R.id.calCell11, R.id.calCell12, R.id.calCell13),
        intArrayOf(R.id.calCell14, R.id.calCell15, R.id.calCell16, R.id.calCell17, R.id.calCell18, R.id.calCell19, R.id.calCell20),
        intArrayOf(R.id.calCell21, R.id.calCell22, R.id.calCell23, R.id.calCell24, R.id.calCell25, R.id.calCell26, R.id.calCell27),
        intArrayOf(R.id.calCell28, R.id.calCell29, R.id.calCell30, R.id.calCell31, R.id.calCell32, R.id.calCell33, R.id.calCell34),
        intArrayOf(R.id.calCell35, R.id.calCell36, R.id.calCell37, R.id.calCell38, R.id.calCell39, R.id.calCell40, R.id.calCell41)
    )

    private val WEEK_IDS = intArrayOf(
        R.id.calWeek0, R.id.calWeek1, R.id.calWeek2,
        R.id.calWeek3, R.id.calWeek4, R.id.calWeek5
    )

    private val EVENT_IDS = intArrayOf(
        R.id.calEvents0, R.id.calEvents1, R.id.calEvents2,
        R.id.calEvents3, R.id.calEvents4, R.id.calEvents5
    )

    private val EV_COL_IDS = intArrayOf(
        R.id.evCol0, R.id.evCol1, R.id.evCol2, R.id.evCol3,
        R.id.evCol4, R.id.evCol5, R.id.evCol6
    )

    private data class CalEvent(
        val title: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val color: Int
    )

    fun populate(context: Context, views: RemoteViews, yearMonth: YearMonth, reminders: List<ReminderEntity>) {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val today = LocalDate.now()
        val isCurrentMonth = yearMonth == YearMonth.from(today)

        views.setTextViewText(R.id.tvCalMonthYear, "${yearMonth.year}年 ${yearMonth.monthValue}月")

        // Parse events from reminders
        val events = reminders.mapNotNull { r ->
            try {
                val start = r.startDate?.let { LocalDate.parse(it, fmt) }
                    ?: r.dueDate?.let { LocalDate.parse(it, fmt) }
                    ?: return@mapNotNull null
                val end = r.dueDate?.let { LocalDate.parse(it, fmt) } ?: start
                val color = try {
                    Color.parseColor(ReminderCategory.colorFor(r.category))
                } catch (_: Exception) { Color.GRAY }
                CalEvent(r.title, start, if (end.isBefore(start)) start else end, color)
            } catch (_: Exception) { null }
        }.sortedWith(
            compareBy<CalEvent> { it.startDate }
                .thenByDescending { ChronoUnit.DAYS.between(it.startDate, it.endDate) }
        )

        // Calendar grid
        val firstDay = yearMonth.atDay(1)
        val offset = firstDay.dayOfWeek.value % 7 // Sunday=0
        val daysInMonth = yearMonth.lengthOfMonth()

        // Find which weeks have dates in this month
        val weeksWithDates = (0..5).filter { week ->
            (0..6).any { col ->
                val dayNum = week * 7 + col - offset + 1
                dayNum in 1..daysInMonth
            }
        }

        // Show only 3 weeks: current month → start from week containing today; other months → first 3
        val showWeeks: List<Int> = if (isCurrentMonth && weeksWithDates.isNotEmpty()) {
            val todayWeek = (today.dayOfMonth + offset - 1) / 7
            val startIdx = weeksWithDates.indexOfFirst { it >= todayWeek }.coerceAtLeast(0)
            val adjustedStart = if (weeksWithDates.size - startIdx < 3) {
                maxOf(0, weeksWithDates.size - 3)
            } else startIdx
            weeksWithDates.drop(adjustedStart).take(3)
        } else {
            weeksWithDates.take(3)
        }

        for (week in 0..5) {
            if (week !in showWeeks) {
                views.setViewVisibility(WEEK_IDS[week], View.GONE)
                views.removeAllViews(EVENT_IDS[week])
                continue
            }

            views.setViewVisibility(WEEK_IDS[week], View.VISIBLE)
            views.removeAllViews(EVENT_IDS[week])

            // Fill date cells
            for (col in 0..6) {
                val dayNum = week * 7 + col - offset + 1
                val cellId = CELL_IDS[week][col]

                if (dayNum < 1 || dayNum > daysInMonth) {
                    views.setTextViewText(cellId, "")
                    views.setInt(cellId, "setBackgroundResource", 0)
                } else {
                    val date = yearMonth.atDay(dayNum)

                    if (isCurrentMonth && date.isBefore(today)) {
                        // Past date: hide number
                        views.setTextViewText(cellId, "")
                        views.setInt(cellId, "setBackgroundResource", 0)
                    } else if (date == today) {
                        views.setTextViewText(cellId, dayNum.toString())
                        views.setInt(cellId, "setBackgroundResource", R.drawable.widget_today_circle)
                        views.setTextColor(cellId, Color.WHITE)
                    } else {
                        views.setTextViewText(cellId, dayNum.toString())
                        views.setInt(cellId, "setBackgroundResource", 0)
                        views.setTextColor(cellId, if (col == 0) Color.parseColor("#EF5350") else Color.parseColor("#D1D5DB"))
                    }
                }
            }

            // Week date range (within this month)
            val weekStartDay = maxOf(1, week * 7 - offset + 1)
            val weekEndDay = minOf(daysInMonth, (week + 1) * 7 - offset)
            if (weekStartDay > daysInMonth || weekEndDay < 1) continue
            val weekStart = yearMonth.atDay(weekStartDay)
            val weekEnd = yearMonth.atDay(weekEndDay)

            // Events overlapping this week (max 3)
            val weekEvents = events.filter {
                !it.endDate.isBefore(weekStart) && !it.startDate.isAfter(weekEnd)
            }.take(3)

            for (ev in weekEvents) {
                val bar = RemoteViews(context.packageName, R.layout.widget_cal_event_bar)

                for (col in 0..6) {
                    val dayNum = week * 7 + col - offset + 1
                    val colId = EV_COL_IDS[col]
                    val date = if (dayNum in 1..daysInMonth) yearMonth.atDay(dayNum) else null
                    val inRange = date != null &&
                        !date.isBefore(ev.startDate) && !date.isAfter(ev.endDate)

                    // Hide event bars for past dates
                    val isPast = isCurrentMonth && date != null && date.isBefore(today)

                    if (!inRange || isPast) {
                        bar.setViewVisibility(colId, View.INVISIBLE)
                    } else {
                        bar.setViewVisibility(colId, View.VISIBLE)

                        // Treat today as visual start if actual start is in the past
                        val isVisualStart = date == ev.startDate || col == 0 || dayNum == 1 ||
                            (isCurrentMonth && date == today && ev.startDate.isBefore(today))
                        val isLast = date == ev.endDate || col == 6 || dayNum == daysInMonth

                        val bgRes = when {
                            isVisualStart && isLast -> R.drawable.widget_event_single
                            isVisualStart -> R.drawable.widget_event_start
                            isLast -> R.drawable.widget_event_end
                            else -> R.drawable.widget_event_mid
                        }

                        bar.setInt(colId, "setBackgroundResource", bgRes)
                        bar.setColorStateList(
                            colId, "setBackgroundTintList",
                            ColorStateList.valueOf(ev.color)
                        )
                        bar.setTextViewText(colId, if (isVisualStart) ev.title else "")
                    }
                }

                views.addView(EVENT_IDS[week], bar)
            }
        }
    }
}
