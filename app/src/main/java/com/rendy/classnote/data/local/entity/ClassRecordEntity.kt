package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "class_records")
data class ClassRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timeLabel: String = "",
    val title: String = "",
    val textNote: String = "",
    val aiSummary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
