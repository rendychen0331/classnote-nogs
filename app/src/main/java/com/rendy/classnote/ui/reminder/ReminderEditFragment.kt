package com.rendy.classnote.ui.reminder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.databinding.FragmentReminderEditBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * 新增 / 編輯提醒事項
 * 支援多個通知時間設定
 */
class ReminderEditFragment : Fragment() {

    private var _binding: FragmentReminderEditBinding? = null
    private val binding get() = _binding!!

    private val args: ReminderEditFragmentArgs by navArgs()

    private val viewModel: ReminderViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ReminderViewModel.Factory(app.reminderRepository, app.applicationContext)
    }

    private val notificationTimes = mutableListOf<Long>()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (args.reminderId > 0) {
            loadReminder(args.reminderId)
        }

        binding.btnPickDueDate.setOnClickListener { pickDate() }
        binding.btnAddNotification.setOnClickListener { pickNotificationTime() }
        binding.btnSave.setOnClickListener { saveReminder() }
        binding.btnCancel.setOnClickListener { findNavController().popBackStack() }
    }

    private fun loadReminder(reminderId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ClassNoteApplication
            val reminder = app.reminderRepository.getReminderById(reminderId) ?: return@launch
            binding.etTitle.setText(reminder.title)
            binding.etNote.setText(reminder.note)
            binding.tvDueDate.text = reminder.dueDate ?: ""
            val existing = app.reminderRepository.getNotificationsOnce(reminderId)
            notificationTimes.clear()
            notificationTimes.addAll(existing.map { it.triggerAt })
            refreshNotificationList()
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = LocalDate.of(year, month + 1, day).format(dateFormatter)
                binding.tvDueDate.text = date
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickNotificationTime() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute ->
                        val dt = LocalDateTime.of(year, month + 1, day, hour, minute)
                        val millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        notificationTimes.add(millis)
                        refreshNotificationList()
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun refreshNotificationList() {
        val lines = notificationTimes.mapIndexed { i, t ->
            val dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(t), ZoneId.systemDefault()
            )
            "${i + 1}. ${dt.format(timeFormatter)}"
        }.joinToString("\n")
        binding.tvNotificationList.text = lines.ifEmpty { "尚未設定通知時間" }
    }

    private fun saveReminder() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etTitle.error = "請輸入提醒標題"
            return
        }

        val reminder = ReminderEntity(
            id = if (args.reminderId > 0) args.reminderId else 0,
            title = title,
            note = binding.etNote.text.toString().trim(),
            dueDate = binding.tvDueDate.text.toString().ifEmpty { null }
        )

        if (args.reminderId > 0) {
            viewModel.updateReminder(reminder, notificationTimes)
        } else {
            viewModel.addReminder(reminder, notificationTimes)
        }

        findNavController().popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
