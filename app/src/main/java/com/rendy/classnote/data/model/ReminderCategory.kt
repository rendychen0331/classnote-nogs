package com.rendy.classnote.data.model

enum class ReminderCategory(val label: String, val colorHex: String) {
    WORK("工作", "#FFA726"),
    HOMEWORK("作業", "#66BB6A"),
    EXAM("考試", "#EF5350"),
    REMINDER("提醒", "#4ECCA3");

    companion object {
        fun fromString(value: String?): ReminderCategory? =
            entries.find { it.name == value }

        fun colorFor(category: String?): String =
            fromString(category)?.colorHex ?: "#78909C"
    }
}
