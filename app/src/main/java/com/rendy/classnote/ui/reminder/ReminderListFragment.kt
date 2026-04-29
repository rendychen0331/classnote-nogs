package com.rendy.classnote.ui.reminder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.rendy.classnote.ui.SwipeActionsCallback
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.FragmentReminderListBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReminderListFragment : Fragment() {

    private var _binding: FragmentReminderListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ReminderViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ReminderViewModel.Factory(app.reminderRepository, app.applicationContext)
    }

    private lateinit var adapter: ReminderAdapter
    private var selectedCategory: ReminderCategory? = null
    private var allReminders: List<ReminderEntity> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ReminderAdapter(
            onComplete = { reminder -> viewModel.completeReminder(reminder.id) },
            onEdit = { reminder ->
                findNavController().navigate(
                    ReminderListFragmentDirections
                        .actionReminderListFragmentToReminderEditFragment(reminder.id)
                )
            },
            onItemClick = { reminder ->
                findNavController().navigate(
                    ReminderListFragmentDirections
                        .actionReminderListFragmentToReminderDetailFragment(reminder.id)
                )
            }
        )

        binding.recyclerReminders.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ReminderListFragment.adapter
        }

        ItemTouchHelper(SwipeActionsCallback(
            context = requireContext(),
            onDelete = { position ->
                val reminder = adapter.currentList.getOrNull(position) ?: return@SwipeActionsCallback
                viewModel.deleteReminder(reminder)
            },
            onEdit = { position ->
                val reminder = adapter.currentList.getOrNull(position) ?: return@SwipeActionsCallback
                findNavController().navigate(
                    ReminderListFragmentDirections
                        .actionReminderListFragmentToReminderEditFragment(reminder.id)
                )
            }
        )).attachToRecyclerView(binding.recyclerReminders)

        setupFilterChips()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.activeReminders.collectLatest { reminders ->
                allReminders = reminders
                applyFilter()
            }
        }

        binding.fabAddReminder.setOnClickListener {
            findNavController().navigate(
                ReminderListFragmentDirections
                    .actionReminderListFragmentToReminderEditFragment(-1L)
            )
        }
    }

    private fun setupFilterChips() {
        binding.chipAll.setOnCheckedChangeListener { _, checked ->
            if (checked) { selectedCategory = null; applyFilter() }
        }
        binding.chipFilterWork.setOnCheckedChangeListener { _, checked ->
            if (checked) { selectedCategory = ReminderCategory.WORK; applyFilter() }
        }
        binding.chipFilterHomework.setOnCheckedChangeListener { _, checked ->
            if (checked) { selectedCategory = ReminderCategory.HOMEWORK; applyFilter() }
        }
        binding.chipFilterExam.setOnCheckedChangeListener { _, checked ->
            if (checked) { selectedCategory = ReminderCategory.EXAM; applyFilter() }
        }
        binding.chipFilterReminder.setOnCheckedChangeListener { _, checked ->
            if (checked) { selectedCategory = ReminderCategory.REMINDER; applyFilter() }
        }
    }

    private fun applyFilter() {
        val filtered = if (selectedCategory == null) {
            allReminders
        } else {
            allReminders.filter { it.category == selectedCategory!!.name }
        }
        adapter.submitList(filtered)
        binding.tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
