package com.rendy.classnote.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.databinding.FragmentCalendarViewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日曆月視圖，點擊日期後顯示當天課程
 */
class CalendarViewFragment : Fragment() {

    private var _binding: FragmentCalendarViewBinding? = null
    private val binding get() = _binding!!

    private val scheduleViewModel: ScheduleViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    ) {
        val repo = (requireActivity().application as ClassNoteApplication).courseRepository
        ScheduleViewModel.Factory(repo)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: LocalDate = LocalDate.now()
    private var dayCoursesJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            loadDayCourses(selectedDate)
        }

        loadDayCourses(selectedDate)
    }

    private fun loadDayCourses(date: LocalDate) {
        dayCoursesJob?.cancel()
        val dayOfWeek = date.dayOfWeek.value  // 1=Mon, 7=Sun
        if (dayOfWeek > 5) {
            binding.tvDayCourses.text = "週末無課"
            return
        }
        val dateStr = date.format(dateFormatter)
        dayCoursesJob = viewLifecycleOwner.lifecycleScope.launch {
            scheduleViewModel.courses
                .combine(scheduleViewModel.getOverridesByDate(dateStr)) { courses, overrides ->
                    val dayCourses = courses.filter { it.dayOfWeek == dayOfWeek }
                        .sortedBy { it.period }
                    val overrideMap = overrides.associateBy { it.courseId }
                    dayCourses.joinToString("\n") { course ->
                        val override = overrideMap[course.id]
                        when {
                            override?.overrideType == "cancel" ->
                                "第 ${course.period} 節：${course.name}（取消）"
                            override?.overrideType == "replace" ->
                                "第 ${course.period} 節：${override.name}（臨時）"
                            else ->
                                "第 ${course.period} 節：${course.name}"
                        }
                    }.ifEmpty { "今日無課" }
                }
                .collectLatest { displayText -> binding.tvDayCourses.text = displayText }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
