package com.rendy.classnote.ui.reminder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.chip.Chip
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.FragmentReminderEditBinding
import kotlinx.coroutines.Job
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

    // 編輯時保留原始欄位，避免儲存時遺失
    private var originalCourseId: Long? = null
    private var originalCreatedAt: Long? = null
    private var selectedCategory: String? = null
    private var fullScreenAlarm: Boolean = true
    // 追蹤非同步載入 job，儲存前先等待完成
    private var loadJob: Job? = null

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

        // 全螢幕提醒開關
        binding.switchFullScreenAlarm.isChecked = fullScreenAlarm
        binding.switchFullScreenAlarm.setOnCheckedChangeListener { _, checked ->
            fullScreenAlarm = checked
        }

        // Category chip selection
        binding.chipGroupCategory.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when {
                checkedIds.contains(binding.chipWork.id) -> ReminderCategory.WORK.name
                checkedIds.contains(binding.chipHomework.id) -> ReminderCategory.HOMEWORK.name
                checkedIds.contains(binding.chipExam.id) -> ReminderCategory.EXAM.name
                checkedIds.contains(binding.chipReminder.id) -> ReminderCategory.REMINDER.name
                else -> null
            }
        }
    }

    private fun loadReminder(reminderId: Long) {
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ClassNoteApplication
            val reminder = app.reminderRepository.getReminderById(reminderId) ?: return@launch
            originalCourseId = reminder.courseId
            originalCreatedAt = reminder.createdAt
            binding.etTitle.setText(reminder.title)
            binding.etNote.setText(reminder.note)
            binding.tvDueDate.text = reminder.dueDate ?: ""
            selectedCategory = reminder.category
            fullScreenAlarm = reminder.fullScreenAlarm
            binding.switchFullScreenAlarm.isChecked = fullScreenAlarm
            when (reminder.category) {
                ReminderCategory.WORK.name -> binding.chipWork.isChecked = true
                ReminderCategory.HOMEWORK.name -> binding.chipHomework.isChecked = true
                ReminderCategory.EXAM.name -> binding.chipExam.isChecked = true
                ReminderCategory.REMINDER.name -> binding.chipReminder.isChecked = true
            }
            val existing = app.reminderRepository.getNotificationsOnce(reminderId)
            // 保留使用者在非同步載入完成前已加入的時間
            val userAdded = notificationTimes.toList()
            notificationTimes.clear()
            notificationTimes.addAll(existing.filter { !it.isFired }.map { it.triggerAt })
            for (t in userAdded) {
                if (t !in notificationTimes) notificationTimes.add(t)
            }
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
                        when {
                            millis < System.currentTimeMillis() ->
                                Toast.makeText(requireContext(), "請選擇未來的時間", Toast.LENGTH_SHORT).show()
                            millis in notificationTimes ->
                                Toast.makeText(requireContext(), "此時間已加入", Toast.LENGTH_SHORT).show()
                            else -> {
                                notificationTimes.add(millis)
                                refreshNotificationList()
                            }
                        }
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
        binding.chipGroupNotifications.removeAllViews()
        if (notificationTimes.isEmpty()) {
            binding.tvNoNotifications.visibility = View.VISIBLE
            return
        }
        binding.tvNoNotifications.visibility = View.GONE
        notificationTimes.toList().forEach { millis ->
            val dt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()
            )
            val chip = Chip(requireContext()).apply {
                text = dt.format(timeFormatter)
                isCloseIconVisible = true
                isClickable = false
                setOnCloseIconClickListener {
                    notificationTimes.remove(millis)
                    refreshNotificationList()
                }
            }
            binding.chipGroupNotifications.addView(chip)
        }
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
            dueDate = binding.tvDueDate.text.toString().ifEmpty { null },
            courseId = originalCourseId,
            createdAt = originalCreatedAt ?: System.currentTimeMillis(),
            category = selectedCategory,
            fullScreenAlarm = fullScreenAlarm
        )

        // NonCancellable 確保 DB 寫入不因 lifecycle 取消而中斷
        viewLifecycleOwner.lifecycleScope.launch {
            // 等待資料載入完成，避免競態條件覆蓋使用者加入的通知時間
            loadJob?.join()
            // 載入完成後再複製，確保包含 DB 已有的通知時間
            val times = notificationTimes.toList()
            if (args.reminderId > 0) {
                viewModel.updateReminderAndWait(reminder, times)
            } else {
                viewModel.addReminderAndWait(reminder, times)
            }
            if (isAdded) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
