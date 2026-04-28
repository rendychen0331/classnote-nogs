package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_record_media")
data class ClassRecordMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val type: String,           // "photo" | "audio"
    val filePath: String,
    val isUploaded: Boolean = false,
    val durationMs: Long = 0,
    val aiSummary: String = ""
)
