package com.rendy.classnote.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rendy.classnote.data.local.entity.CourseEntity
import com.rendy.classnote.data.local.entity.CourseOverrideEntity
import com.rendy.classnote.data.local.entity.PeriodTimeEntity
import com.rendy.classnote.data.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleViewModel(private val repository: CourseRepository) : ViewModel() {

    // 目前學期（格式 "YYYY-S"）：學期約在 2 月底、8 月底開始，以 20 日為切換點
    val currentSemesterId = MutableStateFlow(run {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val (semYear, semester) = when {
            month > 8 || (month == 8 && day >= 20) -> year to 2
            month > 2 || (month == 2 && day >= 20) -> year to 1
            else -> (year - 1) to 2  // 1月~2月中旬：上一年第二學期
        }
        "$semYear-$semester"
    })

    val courses: StateFlow<List<CourseEntity>> = currentSemesterId
        .flatMapLatest { repository.getCoursesBySemester(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val semesterIds: StateFlow<List<String>> = repository.getAllSemesterIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val periodTimes: StateFlow<List<PeriodTimeEntity>> = repository.getAllPeriodTimes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSemester(semesterId: String) {
        currentSemesterId.value = semesterId
    }

    suspend fun addCourse(course: CourseEntity) {
        repository.insertCourse(course)
    }

    suspend fun updateCourse(course: CourseEntity) {
        repository.updateCourse(course)
    }

    fun deleteCourse(course: CourseEntity) = viewModelScope.launch {
        repository.deleteCourse(course)
    }

    /** 臨時修改（僅影響特定日期） */
    fun addOverride(override: CourseOverrideEntity) = viewModelScope.launch {
        repository.upsertOverride(override)
    }

    fun removeOverride(courseId: Long, date: String) = viewModelScope.launch {
        repository.removeOverride(courseId, date)
    }

    /** 取得特定日期的臨時修改（供週檢視使用） */
    fun getOverridesByDate(date: String) = repository.getOverridesByDate(date)

    class Factory(private val repository: CourseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleViewModel(repository) as T
    }
}
