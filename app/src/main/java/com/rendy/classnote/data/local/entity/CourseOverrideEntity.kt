package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 特定日期的臨時修改（取消/換課）
 * overrideType:
 *   "cancel"  - 取消該節課
 *   "replace" - 換成其他課程內容
 */
@Entity(
    tableName = "course_overrides",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("courseId"),
        Index(value = ["courseId", "date"], unique = true)
    ]
)
data class CourseOverrideEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val courseId: Long,
    val date: String,           // "yyyy-MM-dd"
    val overrideType: String,   // "cancel" | "replace"
    val name: String = "",
    val teacher: String = "",
    val room: String = ""
)
