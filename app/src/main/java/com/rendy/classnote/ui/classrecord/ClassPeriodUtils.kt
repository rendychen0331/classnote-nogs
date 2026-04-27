package com.rendy.classnote.ui.classrecord

object ClassPeriodUtils {

    // 每節 start/end（分鐘，從 00:00 起算），label
    // 預設台灣學校常見時間，使用者可在設定頁調整（未來）
    private val periods = listOf(
        Triple(490,  540,  "第1節"),   // 08:10-09:00
        Triple(550,  600,  "第2節"),   // 09:10-10:00
        Triple(610,  660,  "第3節"),   // 10:10-11:00
        Triple(670,  720,  "第4節"),   // 11:10-12:00
        Triple(775,  830,  "第5節"),   // 12:55-13:50
        Triple(840,  890,  "第6節"),   // 14:00-14:50
        Triple(910,  960,  "第7節"),   // 15:10-16:00
    )

    /**
     * 根據時間（小時 + 分鐘）判斷是第幾節，
     * 若在節次範圍內回傳「第X節」，否則回傳 null。
     */
    fun detect(hour: Int, minute: Int): String? {
        val totalMin = hour * 60 + minute
        return periods.firstOrNull { (start, end, _) -> totalMin in start..end }?.third
    }
}
