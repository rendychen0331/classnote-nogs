package com.rendy.classnote.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_logs")
data class ApiLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val model: String,
    val requestPreview: String = "",
    val responsePreview: String = "",
    val durationMs: Long = 0,
    val isSuccess: Boolean = true
)
