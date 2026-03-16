package com.rendy.classnote.ui.schedule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.local.entity.CourseEntity
import com.rendy.classnote.databinding.FragmentWeekViewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class WeekViewFragment : Fragment() {

    private var _binding: FragmentWeekViewBinding? = null
    private val binding get() = _binding!!

    private val scheduleViewModel: ScheduleViewModel by viewModels(
        ownerProducer = { requireParentFragment() }
    ) {
        val repo = (requireActivity().application as ClassNoteApplication).courseRepository
        ScheduleViewModel.Factory(repo)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeekViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            scheduleViewModel.courses
                .combine(scheduleViewModel.periodTimes) { courses, periodTimes ->
                    Pair(courses, periodTimes)
                }
                .collectLatest { (courses, periodTimes) ->
                    renderWeekGrid(courses, periodTimes)
                }
        }

        binding.fabAddCourse.setOnClickListener {
            findNavController().navigate(
                ScheduleFragmentDirections.actionWeekViewFragmentToCourseEditFragment()
            )
        }
    }

    private fun renderWeekGrid(courses: List<CourseEntity>, periodTimes: List<com.rendy.classnote.data.local.entity.PeriodTimeEntity>) {
        val container = binding.gridContainer
        container.removeAllViews()

        val bySlot = courses.associateBy { Pair(it.dayOfWeek, it.period) }
        val dp = resources.displayMetrics.density

        for (period in 1..7) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (72 * dp).toInt()
                )
            }

            // 節次時間標籤（左欄）
            val pt = periodTimes.getOrNull(period - 1)
            val timeLabel = if (pt != null) {
                "%02d:%02d".format(pt.startMinute / 60, pt.startMinute % 60)
            } else "第${period}節"

            val periodLabel = TextView(requireContext()).apply {
                text = "第${period}節\n${timeLabel}"
                textSize = 9f
                gravity = android.view.Gravity.CENTER
                setTextColor(requireContext().getColor(android.R.color.darker_gray))
                layoutParams = LinearLayout.LayoutParams(
                    (56 * dp).toInt(),
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            row.addView(periodLabel)

            // 每天的課程格子
            for (day in 1..5) {
                val course = bySlot[Pair(day, period)]
                val cell = buildCourseCell(course, dp)
                cell.setOnClickListener {
                    if (course != null) {
                        findNavController().navigate(
                            ScheduleFragmentDirections
                                .actionWeekViewFragmentToCourseEditFragment(
                                    courseId = course.id,
                                    dayOfWeek = day,
                                    period = period,
                                    semesterId = scheduleViewModel.currentSemesterId.value
                                )
                        )
                    } else {
                        findNavController().navigate(
                            ScheduleFragmentDirections
                                .actionWeekViewFragmentToCourseEditFragment(
                                    courseId = -1L,
                                    dayOfWeek = day,
                                    period = period,
                                    semesterId = scheduleViewModel.currentSemesterId.value
                                )
                        )
                    }
                }
                row.addView(cell)
            }

            container.addView(row)
        }
    }

    private fun buildCourseCell(course: CourseEntity?, dp: Float): View {
        val cellWidth = (64 * dp).toInt()
        val cellHeight = ViewGroup.LayoutParams.MATCH_PARENT
        val margin = (1 * dp).toInt()

        val params = LinearLayout.LayoutParams(cellWidth, cellHeight).apply {
            setMargins(margin, margin, margin, margin)
        }

        return TextView(requireContext()).apply {
            layoutParams = params
            gravity = android.view.Gravity.CENTER
            textSize = 10f
            setPadding(
                (4 * dp).toInt(), (4 * dp).toInt(),
                (4 * dp).toInt(), (4 * dp).toInt()
            )
            if (course != null) {
                text = course.name
                setTextColor(Color.WHITE)
                try {
                    setBackgroundColor(Color.parseColor(course.colorHex))
                } catch (_: IllegalArgumentException) {
                    setBackgroundColor(requireContext().getColor(R.color.course_color_1))
                }
            } else {
                text = ""
                setBackgroundColor(
                    requireContext().getColor(android.R.color.transparent)
                )
                background = requireContext().getDrawable(R.drawable.bg_empty_cell)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
