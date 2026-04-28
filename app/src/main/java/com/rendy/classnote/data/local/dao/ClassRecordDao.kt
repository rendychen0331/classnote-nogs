package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClassRecordDao {
    @Query("SELECT * FROM class_records ORDER BY date DESC, createdAt DESC")
    fun getAllRecords(): Flow<List<ClassRecordEntity>>

    @Query("SELECT * FROM class_records ORDER BY date DESC, createdAt DESC")
    suspend fun getAllRecordsOnce(): List<ClassRecordEntity>

    @Query("SELECT * FROM class_records WHERE id = :id")
    suspend fun getById(id: Long): ClassRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ClassRecordEntity): Long

    @Update
    suspend fun update(record: ClassRecordEntity)

    @Query("DELETE FROM class_records WHERE id = :id")
    suspend fun deleteById(id: Long)
}
