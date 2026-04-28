package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassRecordMediaDao {
    @Query("SELECT * FROM class_record_media WHERE recordId = :recordId ORDER BY id ASC")
    fun getMediaForRecord(recordId: Long): Flow<List<ClassRecordMediaEntity>>

    @Query("SELECT * FROM class_record_media WHERE recordId = :recordId ORDER BY id ASC")
    suspend fun getMediaForRecordOnce(recordId: Long): List<ClassRecordMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: ClassRecordMediaEntity): Long

    @Delete
    suspend fun delete(media: ClassRecordMediaEntity)

    @Query("DELETE FROM class_record_media WHERE recordId = :recordId")
    suspend fun deleteAllForRecord(recordId: Long)

    @Query("UPDATE class_record_media SET aiSummary = :summary WHERE id = :id")
    suspend fun updateAiSummary(id: Long, summary: String)
}
