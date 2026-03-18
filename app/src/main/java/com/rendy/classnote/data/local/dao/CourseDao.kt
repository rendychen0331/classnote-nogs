package com.rendy.classnote.data.local.dao

import androidx.room.*
import com.rendy.classnote.data.local.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId ORDER BY dayOfWeek, period")
    fun getCoursesBySemester(semesterId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND dayOfWeek = :dayOfWeek ORDER BY period")
    fun getCoursesByDay(semesterId: String, dayOfWeek: Int): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteSemester(semesterId: String)

    @Query("SELECT DISTINCT semesterId FROM courses ORDER BY semesterId DESC")
    fun getAllSemesterIds(): Flow<List<String>>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId AND dayOfWeek = :dayOfWeek ORDER BY period")
    suspend fun getCoursesByDayOnce(semesterId: String, dayOfWeek: Int): List<CourseEntity>
}
