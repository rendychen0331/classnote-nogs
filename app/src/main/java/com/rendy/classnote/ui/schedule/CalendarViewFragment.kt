package com.rendy.classnote.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.FragmentCalendarViewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日曆月視圖，點擊日期後顯示當天課程與提醒事項
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
            loadDayContent(selectedDate)
        }

        loadDayContent(selectedDate)
    }

    private fun loadDayContent(date: LocalDate) {
        dayCoursesJob?.cancel()
        val dayOfWeek = date.dayOfWeek.value  // 1=Mon, 7=Sun
        val dateStr = date.format(dateFormatter)

        val app = requireActivity().application as ClassNoteApplication
        val reminderDao = app.database.reminderDao()

        dayCoursesJob = viewLifecycleOwner.lifecycleScope.launch {
            combine(
                scheduleViewModel.courses,
                scheduleViewModel.getOverridesByDate(dateStr),
                reminderDao.getActiveReminders()
            ) { courses, overrides, reminders ->
                val lines = mutableListOf<String>()

                // 課程
                if (dayOfWeek <= 5) {
                    val dayCourses = courses.filter { it.dayOfWeek == dayOfWeek }
                        .sortedBy { it.period }
                    val overrideMap = overrides.associateBy { it.courseId }
                    for (course in dayCourses) {
                        val override = overrideMap[course.id]
                        lines += when {
                            override?.overrideType == "cancel" ->
                                "📚 第 ${course.period} 節：${course.name}（取消）"
                            override?.overrideType == "replace" ->
                                "📚 第 ${course.period} 節：${override.name}（臨時）"
                            else ->
                                "📚 第 ${course.period} 節：${course.name}"
                        }
                    }
                }

                // 提醒事項（該日期在 startDate~dueDate 範圍內）
                val dayReminders = reminders.filter { r -> reminderOnDate(r, date) }
                    .sortedBy { it.category }
                for (r in dayReminders) {
                    val cat = ReminderCategory.fromString(r.category)
                    val catLabel = cat?.label ?: "提醒"
                    val prefix = when (cat) {
                        ReminderCategory.EXAM -> "📝"
                        ReminderCategory.HOMEWORK -> "📄"
                        ReminderCategory.WORK -> "💼"
                        else -> "🔔"
                    }
                    val dueInfo = if (r.dueDate == dateStr) "（截止）" else ""
                    lines += "$prefix $catLabel：${r.title}$dueInfo"
                }

                if (lines.isEmpty()) {
                    if (dayOfWeek > 5) "週末無課程或提醒" else "今日無課程或提醒"
                } else {
                    lines.joinToString("\n")
                }
            }.collectLatest { displayText -> binding.tvDayCourses.text = displayText }
        }
    }

    private fun reminderOnDate(r: ReminderEntity, date: LocalDate): Boolean {
        try {
            val due = r.dueDate?.let { LocalDate.parse(it, dateFormatter) }
            val start = r.startDate?.let { LocalDate.parse(it, dateFormatter) } ?: due
            if (start == null && due == null) return false
            val effectiveStart = start ?: due!!
            val effectiveEnd = due ?: start!!
            return !date.isBefore(effectiveStart) && !date.isAfter(effectiveEnd)
        } catch (_: Exception) {
            return false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
