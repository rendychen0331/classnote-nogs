package com.rendy.classnote.ui.classrecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.databinding.ItemClassRecordBinding

class ClassRecordAdapter(
    private val onClick: (ClassRecordEntity) -> Unit,
    private val onDelete: (ClassRecordEntity) -> Unit
) : ListAdapter<ClassRecordEntity, ClassRecordAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemClassRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassRecordEntity) {
            val timeStr = if (item.timeLabel.isNotBlank()) "  ${item.timeLabel}" else ""
            binding.tvRecordDate.text = "${item.date}$timeStr"

            binding.tvRecordTitle.text = item.title
            binding.tvRecordTitle.visibility = if (item.title.isNotBlank()) View.VISIBLE else View.GONE

            val preview = when {
                item.aiSummary.isNotBlank() -> item.aiSummary.take(80)
                item.textNote.isNotBlank() -> android.text.Html.fromHtml(item.textNote, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim().take(80)
                else -> ""
            }
            binding.tvRecordNote.text = preview
            binding.tvRecordNote.visibility = if (preview.isNotEmpty()) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(item) }
            binding.btnDeleteRecord.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClassRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    companion object DiffCallback : DiffUtil.ItemCallback<ClassRecordEntity>() {
        override fun areItemsTheSame(old: ClassRecordEntity, new: ClassRecordEntity) = old.id == new.id
        override fun areContentsTheSame(old: ClassRecordEntity, new: ClassRecordEntity) = old == new
    }
}
