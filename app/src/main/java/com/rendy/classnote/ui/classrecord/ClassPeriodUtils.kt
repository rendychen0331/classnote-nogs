package com.rendy.classnote.ui.classrecord

object ClassPeriodUtils {

    data class PeriodInfo(val startMinute: Int, val endMinute: Int, val label: String) {
        val startTimeStr: String get() = "%02d:%02d".format(startMinute / 60, startMinute % 60)
        val endTimeStr: String get() = "%02d:%02d".format(endMinute / 60, endMinute % 60)
        val displayName: String get() = "$label  $startTimeStr–$endTimeStr"
    }

    private val periods = listOf(
        Triple(490,  540,  "第1節"),   // 08:10-09:00
        Triple(550,  600,  "第2節"),   // 09:10-10:00
        Triple(610,  660,  "第3節"),   // 10:10-11:00
        Triple(670,  720,  "第4節"),   // 11:10-12:00
        Triple(775,  830,  "第5節"),   // 12:55-13:50
        Triple(840,  890,  "第6節"),   // 14:00-14:50
        Triple(910,  960,  "第7節"),   // 15:10-16:00
    )

    fun getAllPeriods(): List<PeriodInfo> =
        periods.map { (start, end, label) -> PeriodInfo(start, end, label) }

    fun detect(hour: Int, minute: Int): String? {
        val totalMin = hour * 60 + minute
        return periods.firstOrNull { (start, end, _) -> totalMin in start..end }?.third
    }
}
