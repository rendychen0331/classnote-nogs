package com.rendy.classnote.ui.settings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rendy.classnote.data.local.entity.ApiLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ApiLogAdapter(private val items: List<ApiLogEntity>) :
    RecyclerView.Adapter<ApiLogAdapter.VH>() {

    private val fmt = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvHeader: TextView = view.findViewById(android.R.id.text1)
        val tvDetail: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val log = items[position]
        val status = if (log.isSuccess) "✓" else "✗"
        val time = fmt.format(Date(log.timestamp))
        holder.tvHeader.text = "$status  [${log.model}]  ${log.durationMs}ms  $time"
        holder.tvHeader.setTextColor(if (log.isSuccess) Color.parseColor("#2e7d32") else Color.parseColor("#c62828"))
        val detail = buildString {
            if (log.requestPreview.isNotBlank()) append("▶ ${log.requestPreview}\n")
            if (log.responsePreview.isNotBlank()) append("◀ ${log.responsePreview}")
        }.trim()
        holder.tvDetail.text = detail
        holder.tvDetail.visibility = if (detail.isNotBlank()) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = items.size
}
