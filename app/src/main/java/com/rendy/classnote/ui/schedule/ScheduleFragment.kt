package com.rendy.classnote.ui.schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentScheduleBinding
import kotlinx.coroutines.launch

/**
 * 課表主畫面：包含「週檢視」與「日曆檢視」兩個 Tab
 */
class ScheduleFragment : Fragment() {

    private var _binding: FragmentScheduleBinding? = null
    private val binding get() = _binding!!

    val viewModel: ScheduleViewModel by viewModels {
        val repo = (requireActivity().application as ClassNoteApplication).courseRepository
        ScheduleViewModel.Factory(repo)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pagerAdapter = SchedulePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_week_view)
                1 -> getString(R.string.tab_calendar_view)
                else -> ""
            }
        }.attach()

        setupSemesterSelector()

        binding.btnAddSemester.setOnClickListener { showAddSemesterDialog() }
    }

    private fun setupSemesterSelector() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.semesterIds.collect { semesters ->
                    val current = viewModel.currentSemesterId.value
                    val list = (semesters + current).distinct()
                    val adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_dropdown_item_1line,
                        list
                    )
                    binding.spinnerSemester.setAdapter(adapter)
                    // 顯示目前學期
                    if (binding.spinnerSemester.text.toString() != current) {
                        binding.spinnerSemester.setText(current, false)
                    }
                }
            }
        }

        binding.spinnerSemester.setOnItemClickListener { _, _, position, _ ->
            val adapter = binding.spinnerSemester.adapter ?: return@setOnItemClickListener
            viewModel.setSemester(adapter.getItem(position) as String)
        }
    }

    private fun showAddSemesterDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = "例如 2025-1"
            setSingleLine()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("新增學期")
            .setView(input)
            .setPositiveButton("確認") { _, _ ->
                val newId = input.text.toString().trim()
                if (newId.isNotEmpty()) {
                    viewModel.setSemester(newId)
                    binding.spinnerSemester.setText(newId, false)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
