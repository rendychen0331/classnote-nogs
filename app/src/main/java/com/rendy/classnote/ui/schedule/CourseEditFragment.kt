package com.rendy.classnote.ui.schedule

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.CourseEntity
import com.rendy.classnote.databinding.FragmentCourseEditBinding
import kotlinx.coroutines.launch

/**
 * 新增 / 編輯課程
 * navArgs: courseId（-1L = 新增）、dayOfWeek、period
 */
class CourseEditFragment : Fragment() {

    private var _binding: FragmentCourseEditBinding? = null
    private val binding get() = _binding!!

    private val args: CourseEditFragmentArgs by navArgs()

    // Navigation destination，直接持有自己的 ViewModel
    private val viewModel: ScheduleViewModel by viewModels {
        val repo = (requireActivity().application as ClassNoteApplication).courseRepository
        ScheduleViewModel.Factory(repo)
    }

    private val days = listOf("週一", "週二", "週三", "週四", "週五")
    private val periods = (1..7).map { "第 $it 節" }

    private val presetColors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#3F51B5",
        "#2196F3", "#00BCD4", "#4CAF50", "#8BC34A",
        "#FFEB3B", "#FF9800", "#795548", "#607D8B"
    )
    private var selectedColorHex: String = "#4CAF50"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCourseEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()

        if (args.courseId > 0) {
            loadCourse(args.courseId)
        } else {
            setDropdown(binding.spinnerDay, days, args.dayOfWeek - 1)
            setDropdown(binding.spinnerPeriod, periods, args.period - 1)
        }

        updateColorPreview()
        binding.btnPickColor.setOnClickListener { showColorPickerDialog() }
        binding.btnSave.setOnClickListener { saveCourse() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun setupDropdowns() {
        binding.spinnerDay.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, days)
        )
        binding.spinnerPeriod.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, periods)
        )
    }

    private fun setDropdown(view: android.widget.AutoCompleteTextView, items: List<String>, index: Int) {
        view.setText(items.getOrNull(index) ?: items[0], false)
    }

    private fun selectedIndex(view: android.widget.AutoCompleteTextView, items: List<String>): Int {
        val text = view.text.toString()
        return items.indexOf(text).coerceAtLeast(0)
    }

    private fun loadCourse(courseId: Long) {
        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = (requireActivity().application as ClassNoteApplication).courseRepository
            val course = repo.getCourseById(courseId) ?: run {
                Toast.makeText(requireContext(), "找不到此課程", Toast.LENGTH_SHORT).show()
                binding.btnSave.isEnabled = true
                return@launch
            }
            binding.etName.setText(course.name)
            binding.etTeacher.setText(course.teacher)
            binding.etRoom.setText(course.room)
            setDropdown(binding.spinnerDay, days, course.dayOfWeek - 1)
            setDropdown(binding.spinnerPeriod, periods, course.period - 1)
            selectedColorHex = course.colorHex
            updateColorPreview()
            binding.btnSave.isEnabled = true
        }
    }

    private fun updateColorPreview() {
        binding.colorPreview.setBackgroundColor(Color.parseColor(selectedColorHex))
    }

    private fun showColorPickerDialog() {
        val dp = resources.displayMetrics.density
        val cellSize = (44 * dp).toInt()
        val padding = (8 * dp).toInt()

        val grid = GridLayout(requireContext()).apply {
            columnCount = 4
            setPadding(padding, padding, padding, padding)
        }

        presetColors.forEach { hex ->
            val swatch = View(requireContext()).apply {
                setBackgroundColor(Color.parseColor(hex))
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cellSize
                    height = cellSize
                    setMargins(padding / 2, padding / 2, padding / 2, padding / 2)
                }
            }
            grid.addView(swatch)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇顏色")
            .setView(grid)
            .setNegativeButton("取消", null)
            .create()

        presetColors.forEachIndexed { i, hex ->
            grid.getChildAt(i).setOnClickListener {
                selectedColorHex = hex
                updateColorPreview()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveCourse() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etName.error = "請輸入課程名稱"
            return
        }

        val course = CourseEntity(
            id = if (args.courseId > 0) args.courseId else 0,
            semesterId = args.semesterId.ifEmpty { viewModel.currentSemesterId.value },
            dayOfWeek = selectedIndex(binding.spinnerDay, days) + 1,
            period = selectedIndex(binding.spinnerPeriod, periods) + 1,
            name = name,
            teacher = binding.etTeacher.text.toString().trim(),
            room = binding.etRoom.text.toString().trim(),
            colorHex = selectedColorHex
        )

        binding.btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            if (args.courseId > 0) viewModel.updateCourse(course)
            else viewModel.addCourse(course)
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
