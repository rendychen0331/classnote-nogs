package com.rendy.classnote.ui.classrecord

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.databinding.ItemClassRecordBinding
import com.rendy.classnote.databinding.ItemClassRecordHeaderBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

sealed class ClassRecordListItem {
    data class Header(val date: String, val timeLabel: String, val count: Int) : ClassRecordListItem()
    data class Record(val entity: ClassRecordEntity) : ClassRecordListItem()
}

class ClassRecordAdapter(
    private val onClick: (ClassRecordEntity) -> Unit,
    private val onDelete: (ClassRecordEntity) -> Unit
) : ListAdapter<ClassRecordListItem, RecyclerView.ViewHolder>(DiffCallback) {

    inner class HeaderViewHolder(private val binding: ItemClassRecordHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassRecordListItem.Header) {
            val dowStr = runCatching {
                val d = LocalDate.parse(item.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "（${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)}）"
            }.getOrDefault("")
            val timeStr = if (item.timeLabel.isNotBlank()) "  ${item.timeLabel}" else ""
            binding.tvSessionDate.text = "${item.date}$dowStr$timeStr"
            binding.tvSessionCount.text = "${item.count} 筆"
        }
    }

    inner class RecordViewHolder(private val binding: ItemClassRecordBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ClassRecordEntity) {
            binding.tvRecordDate.visibility = View.GONE

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

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ClassRecordListItem.Header -> VIEW_TYPE_HEADER
        is ClassRecordListItem.Record -> VIEW_TYPE_RECORD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(ItemClassRecordHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            RecordViewHolder(ItemClassRecordBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ClassRecordListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ClassRecordListItem.Record -> (holder as RecordViewHolder).bind(item.entity)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RECORD = 1

        val DiffCallback = object : DiffUtil.ItemCallback<ClassRecordListItem>() {
            override fun areItemsTheSame(old: ClassRecordListItem, new: ClassRecordListItem): Boolean =
                when {
                    old is ClassRecordListItem.Header && new is ClassRecordListItem.Header ->
                        old.date == new.date && old.timeLabel == new.timeLabel
                    old is ClassRecordListItem.Record && new is ClassRecordListItem.Record ->
                        old.entity.id == new.entity.id
                    else -> false
                }

            override fun areContentsTheSame(old: ClassRecordListItem, new: ClassRecordListItem) = old == new
        }
    }
}
