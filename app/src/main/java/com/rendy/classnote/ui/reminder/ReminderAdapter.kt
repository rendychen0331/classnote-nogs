package com.rendy.classnote.ui.reminder

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ReminderEntity
import com.rendy.classnote.data.model.ReminderCategory
import com.rendy.classnote.databinding.ItemReminderBinding

class ReminderAdapter(
    private val onComplete: (ReminderEntity) -> Unit,
    private val onEdit: (ReminderEntity) -> Unit,
    private val onItemClick: (ReminderEntity) -> Unit
) : ListAdapter<ReminderEntity, ReminderAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemReminderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReminderEntity) {
            binding.tvTitle.text = item.title

            if (item.note.isBlank()) {
                binding.tvNote.visibility = View.GONE
            } else {
                binding.tvNote.visibility = View.VISIBLE
                binding.tvNote.text = item.note
                binding.tvNote.isSelected = true  // 觸發 marquee 動畫
            }

            val due = item.dueDate
            if (due.isNullOrBlank()) {
                binding.tvDueDate.visibility = View.GONE
            } else {
                binding.tvDueDate.visibility = View.VISIBLE
                binding.tvDueDate.text = if (!item.dueTime.isNullOrBlank()) "$due ${item.dueTime}" else due
            }

            val sourceIcon = when (item.syncSource) {
                "gmail" -> com.rendy.classnote.R.drawable.ic_gmail
                "classroom" -> com.rendy.classnote.R.drawable.ic_classroom_logo
                "notify" -> com.rendy.classnote.R.drawable.ic_notify_source
                else -> null
            }
            if (sourceIcon != null) {
                binding.layoutSource.visibility = View.VISIBLE
                binding.ivSource.setImageResource(sourceIcon)
                if (!item.sourceName.isNullOrBlank()) {
                    binding.tvSourceName.visibility = View.VISIBLE
                    binding.tvSourceName.text = item.sourceName
                } else {
                    binding.tvSourceName.visibility = View.GONE
                }
            } else {
                binding.layoutSource.visibility = View.GONE
            }

            // Accent bar + category chip
            val cat = ReminderCategory.fromString(item.category)
            val accentColor = Color.parseColor(ReminderCategory.colorFor(item.category))
            binding.viewAccentBar.setBackgroundColor(accentColor)

            if (cat != null) {
                binding.tvCategory.visibility = View.VISIBLE
                binding.tvCategory.text = cat.label
                // Tint the rounded-rect chip background with the category color
                val bg = binding.tvCategory.background.mutate() as? GradientDrawable
                bg?.setColor(accentColor)
            } else {
                binding.tvCategory.visibility = View.GONE
            }

            binding.checkboxDone.isChecked = item.isCompleted
            binding.checkboxDone.setOnClickListener { onComplete(item) }
            binding.btnEdit.setOnClickListener { onEdit(item) }
            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReminderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<ReminderEntity>() {
        override fun areItemsTheSame(old: ReminderEntity, new: ReminderEntity) =
            old.id == new.id

        override fun areContentsTheSame(old: ReminderEntity, new: ReminderEntity) =
            old == new
    }
}
