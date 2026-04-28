package com.rendy.classnote.ui.reminder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.FragmentReminderDetailBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderDetailFragment : Fragment() {

    private var _binding: FragmentReminderDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ReminderDetailFragmentArgs by navArgs()

    private val viewModel: ReminderViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ReminderViewModel.Factory(app.reminderRepository, app.applicationContext)
    }

    private val dtFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReminderDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as ClassNoteApplication
        val repository = app.reminderRepository

        viewLifecycleOwner.lifecycleScope.launch {
            val reminder = repository.getReminderById(args.reminderId) ?: run {
                findNavController().popBackStack()
                return@launch
            }
            val notifications = repository.getNotificationsOnce(reminder.id)

            // ── 標題 ─────────────────────────────────────────────────────────
            binding.tvDetailTitle.text = reminder.title

            // ── Accent bar + 分類 chip ───────────────────────────────────────
            val cat = ReminderCategory.fromString(reminder.category)
            if (cat != null) {
                val accentColor = Color.parseColor(ReminderCategory.colorFor(reminder.category))
                binding.viewDetailAccentBar.setBackgroundColor(accentColor)
                binding.tvDetailCategory.visibility = View.VISIBLE
                binding.tvDetailCategory.text = cat.label
                (binding.tvDetailCategory.background.mutate() as? GradientDrawable)?.setColor(accentColor)
            }

            // ── 來源 ─────────────────────────────────────────────────────────
            val sourceIcon = when (reminder.syncSource) {
                "gmail" -> R.drawable.ic_gmail
                "classroom" -> R.drawable.ic_classroom_logo
                "notify" -> R.drawable.ic_notify_source
                else -> null
            }
            if (sourceIcon != null) {
                binding.ivDetailSource.visibility = View.VISIBLE
                binding.ivDetailSource.setImageResource(sourceIcon)
            }
            if (!reminder.sourceName.isNullOrBlank()) {
                binding.tvDetailSourceName.visibility = View.VISIBLE
                binding.tvDetailSourceName.text = reminder.sourceName
            }

            // ── 截止日期 ─────────────────────────────────────────────────────
            if (!reminder.dueDate.isNullOrBlank()) {
                binding.sectionDueDate.visibility = View.VISIBLE
                binding.tvDetailDueDate.text = if (!reminder.dueTime.isNullOrBlank()) {
                    "${reminder.dueDate} ${reminder.dueTime}"
                } else {
                    reminder.dueDate
                }
            }

            // ── 開始日期 ─────────────────────────────────────────────────────
            if (!reminder.startDate.isNullOrBlank()) {
                binding.sectionStartDate.visibility = View.VISIBLE
                binding.tvDetailStartDate.text = reminder.startDate
            }

            // ── 備註 ─────────────────────────────────────────────────────────
            if (reminder.note.isNotBlank()) {
                binding.sectionNote.visibility = View.VISIBLE
                binding.tvDetailNote.text = reminder.note
            }

            // ── 重複 ─────────────────────────────────────────────────────────
            val repeatLabel = when (reminder.repeatType) {
                "DAILY" -> getString(R.string.repeat_daily)
                "WEEKLY" -> getString(R.string.repeat_weekly)
                "MONTHLY" -> getString(R.string.repeat_monthly)
                else -> null
            }
            if (repeatLabel != null) {
                binding.sectionRepeat.visibility = View.VISIBLE
                binding.tvDetailRepeat.text = repeatLabel
            }

            // ── 通知時間 ─────────────────────────────────────────────────────
            val now = System.currentTimeMillis()
            val pending = notifications.filter { !it.isFired && it.triggerAt > now }
            binding.tvDetailNotifications.text = if (pending.isEmpty()) {
                getString(R.string.detail_no_notification)
            } else {
                pending.sortedBy { it.triggerAt }
                    .joinToString("\n") { dtFormatter.format(Date(it.triggerAt)) }
            }

            // ── 全螢幕提醒 ───────────────────────────────────────────────────
            binding.tvDetailFullScreen.text = if (reminder.fullScreenAlarm) {
                getString(R.string.detail_fullscreen_on)
            } else {
                getString(R.string.detail_fullscreen_off)
            }

            // ── 編輯按鈕 ─────────────────────────────────────────────────────
            binding.btnDetailEdit.setOnClickListener {
                findNavController().navigate(
                    ReminderDetailFragmentDirections
                        .actionReminderDetailFragmentToReminderEditFragment(reminder.id)
                )
            }

            // ── 刪除按鈕 ─────────────────────────────────────────────────────
            binding.btnDetailDelete.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.confirm_delete_reminder_title))
                    .setMessage(getString(R.string.confirm_delete_reminder_msg))
                    .setPositiveButton(getString(R.string.delete)) { _, _ ->
                        viewModel.deleteReminder(reminder)
                        findNavController().popBackStack()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
