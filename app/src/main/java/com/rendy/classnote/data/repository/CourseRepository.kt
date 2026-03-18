package com.rendy.classnote.data.repository

import com.rendy.classnote.data.local.dao.CourseDao
import com.rendy.classnote.data.local.dao.CourseOverrideDao
import com.rendy.classnote.data.local.dao.PeriodTimeDao
import com.rendy.classnote.data.local.entity.CourseEntity
import com.rendy.classnote.data.local.entity.CourseOverrideEntity
import com.rendy.classnote.data.local.entity.PeriodTimeEntity
import kotlinx.coroutines.flow.Flow

class CourseRepository(
    private val courseDao: CourseDao,
    private val overrideDao: CourseOverrideDao,
    private val periodTimeDao: PeriodTimeDao
) {
    // ── 課程 ─────────────────────────────────────────────────────────────

    fun getCoursesBySemester(semesterId: String): Flow<List<CourseEntity>> =
        courseDao.getCoursesBySemester(semesterId)

    fun getCoursesByDay(semesterId: String, dayOfWeek: Int): Flow<List<CourseEntity>> =
        courseDao.getCoursesByDay(semesterId, dayOfWeek)

    suspend fun getCourseById(id: Long): CourseEntity? =
        courseDao.getCourseById(id)

    suspend fun insertCourse(course: CourseEntity): Long =
        courseDao.insertCourse(course)

    suspend fun updateCourse(course: CourseEntity) =
        courseDao.updateCourse(course)

    suspend fun deleteCourse(course: CourseEntity) =
        courseDao.deleteCourse(course)

    fun getAllSemesterIds(): Flow<List<String>> =
        courseDao.getAllSemesterIds()

    /** 學期切換：刪除舊學期資料後重建（覆蓋語意） */
    suspend fun switchSemester(oldSemesterId: String, newSemesterId: String) {
        courseDao.deleteSemester(oldSemesterId)
        // 呼叫端負責插入新學期課程
    }

    // ── 臨時修改 ──────────────────────────────────────────────────────────

    fun getOverridesByDate(date: String): Flow<List<CourseOverrideEntity>> =
        overrideDao.getOverridesByDate(date)

    fun getOverridesByCourse(courseId: Long): Flow<List<CourseOverrideEntity>> =
        overrideDao.getOverridesByCourse(courseId)

    suspend fun getOverride(courseId: Long, date: String): CourseOverrideEntity? =
        overrideDao.getOverride(courseId, date)

    suspend fun upsertOverride(override: CourseOverrideEntity): Long =
        overrideDao.insertOverride(override)

    suspend fun removeOverride(courseId: Long, date: String) =
        overrideDao.deleteOverrideByDateAndCourse(courseId, date)

    // ── 節次時間 ──────────────────────────────────────────────────────────

    fun getAllPeriodTimes(): Flow<List<PeriodTimeEntity>> =
        periodTimeDao.getAllPeriodTimes()

    suspend fun updatePeriodTime(periodTime: PeriodTimeEntity) =
        periodTimeDao.insertOrUpdate(periodTime)

    // ── Widget 用（suspend，非 Flow） ─────────────────────────────────────

    suspend fun getCoursesForDayOnce(semesterId: String, dayOfWeek: Int): List<CourseEntity> =
        courseDao.getCoursesByDayOnce(semesterId, dayOfWeek)

    suspend fun getPeriodTimesOnce(): List<PeriodTimeEntity> =
        periodTimeDao.getAllPeriodTimesOnce()

    suspend fun getOverridesForDateOnce(date: String): List<CourseOverrideEntity> =
        overrideDao.getOverridesByDateOnce(date)
}
