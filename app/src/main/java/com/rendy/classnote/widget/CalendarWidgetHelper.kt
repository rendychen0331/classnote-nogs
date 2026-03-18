package com.rendy.classnote.widget

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.rendy.classnote.R
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

object CalendarWidgetHelper {

    private val CELL_IDS = intArrayOf(
        R.id.calCell00, R.id.calCell01, R.id.calCell02, R.id.calCell03, R.id.calCell04, R.id.calCell05, R.id.calCell06,
        R.id.calCell07, R.id.calCell08, R.id.calCell09, R.id.calCell10, R.id.calCell11, R.id.calCell12, R.id.calCell13,
        R.id.calCell14, R.id.calCell15, R.id.calCell16, R.id.calCell17, R.id.calCell18, R.id.calCell19, R.id.calCell20,
        R.id.calCell21, R.id.calCell22, R.id.calCell23, R.id.calCell24, R.id.calCell25, R.id.calCell26, R.id.calCell27,
        R.id.calCell28, R.id.calCell29, R.id.calCell30, R.id.calCell31, R.id.calCell32, R.id.calCell33, R.id.calCell34,
        R.id.calCell35, R.id.calCell36, R.id.calCell37, R.id.calCell38, R.id.calCell39, R.id.calCell40, R.id.calCell41
    )

    fun populate(views: RemoteViews, yearMonth: YearMonth, reminders: List<ReminderEntity>) {
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE
        val today = LocalDate.now()

        // Build event map: date string → list of colors
        val eventColorMap = mutableMapOf<String, MutableList<String>>()
        for (r in reminders) {
            val start = r.startDate?.let { LocalDate.parse(it, fmt) } ?: r.dueDate?.let { LocalDate.parse(it, fmt) } ?: continue
            val end = r.dueDate?.let { LocalDate.parse(it, fmt) } ?: start
            val color = ReminderCategory.colorFor(r.category)
            var cur = start
            while (!cur.isAfter(end)) {
                if (cur.year == yearMonth.year && cur.monthValue == yearMonth.monthValue) {
                    eventColorMap.getOrPut(cur.format(fmt)) { mutableListOf() }.add(color)
                }
                cur = cur.plusDays(1)
            }
        }

        // Update month/year label
        views.setTextViewText(R.id.tvCalMonthYear, "${yearMonth.year}年 ${yearMonth.monthValue}月")

        // First day of month: dayOfWeek (1=Mon … 7=Sun in ISO, need 0=Sun offset)
        val firstDay = yearMonth.atDay(1)
        // ISO dayOfWeek: 1=Mon, 7=Sun. We want Sunday=0 column
        val offset = (firstDay.dayOfWeek.value % 7) // Mon=1→1, Sun=7→0

        val daysInMonth = yearMonth.lengthOfMonth()

        for (i in CELL_IDS.indices) {
            val dayNum = i - offset + 1
            val cellId = CELL_IDS[i]

            if (dayNum < 1 || dayNum > daysInMonth) {
                views.setTextViewText(cellId, "")
                views.setInt(cellId, "setBackgroundColor", Color.TRANSPARENT)
            } else {
                val date = yearMonth.atDay(dayNum)
                val dateStr = date.format(fmt)
                val colors = eventColorMap[dateStr]

                views.setTextViewText(cellId, dayNum.toString())

                // Text color: today = blue, Sunday col = red, else white
                val col = (i % 7)
                val textColor = when {
                    date == today -> Color.parseColor("#4FC3F7")
                    col == 0 -> Color.parseColor("#EF9A9A")
                    else -> Color.WHITE
                }
                views.setTextColor(cellId, textColor)

                // Background: first event color if any
                if (!colors.isNullOrEmpty()) {
                    views.setInt(cellId, "setBackgroundColor", Color.parseColor(colors[0] + "99"))
                } else {
                    views.setInt(cellId, "setBackgroundColor", Color.TRANSPARENT)
                }
            }
        }
    }
}
