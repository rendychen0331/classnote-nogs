package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.CourseOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseOverrideDao {

    @Query("SELECT * FROM course_overrides WHERE date = :date")
    fun getOverridesByDate(date: String): Flow<List<CourseOverrideEntity>>

    @Query("SELECT * FROM course_overrides WHERE courseId = :courseId")
    fun getOverridesByCourse(courseId: Long): Flow<List<CourseOverrideEntity>>

    @Query("SELECT * FROM course_overrides WHERE courseId = :courseId AND date = :date LIMIT 1")
    suspend fun getOverride(courseId: Long, date: String): CourseOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOverride(override: CourseOverrideEntity): Long

    @Delete
    suspend fun deleteOverride(override: CourseOverrideEntity)

    @Query("DELETE FROM course_overrides WHERE courseId = :courseId AND date = :date")
    suspend fun deleteOverrideByDateAndCourse(courseId: Long, date: String)

    @Query("SELECT * FROM course_overrides WHERE date = :date")
    suspend fun getOverridesByDateOnce(date: String): List<CourseOverrideEntity>
}
