package com.rendy.classnote.ui.classrecord

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ui.SwipeActionsCallback
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.databinding.FragmentClassRecordListBinding
import kotlinx.coroutines.launch

class ClassRecordListFragment : Fragment() {

    private var _binding: FragmentClassRecordListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private lateinit var adapter: ClassRecordAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassRecordListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ClassRecordAdapter(
            onClick = { record ->
                findNavController().navigate(
                    ClassRecordListFragmentDirections
                        .actionClassRecordListFragmentToClassRecordDetailFragment(record.id)
                )
            }
        )

        binding.rvClassRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClassRecords.adapter = adapter

        ItemTouchHelper(SwipeActionsCallback(
            context = requireContext(),
            onDelete = { position ->
                val item = adapter.currentList.getOrNull(position) as? ClassRecordListItem.Record
                    ?: return@SwipeActionsCallback
                viewModel.deleteRecord(item.entity.id)
            },
            onEdit = { position ->
                val item = adapter.currentList.getOrNull(position) as? ClassRecordListItem.Record
                    ?: return@SwipeActionsCallback
                findNavController().navigate(
                    ClassRecordListFragmentDirections
                        .actionClassRecordListFragmentToClassRecordEditFragment(item.entity.id)
                )
            },
            isSwipeable = { viewType -> viewType == ClassRecordAdapter.VIEW_TYPE_RECORD }
        )).attachToRecyclerView(binding.rvClassRecords)

        binding.fabAddRecord.setOnClickListener {
            val options = arrayOf("📝  文字筆記", "✏️  手寫筆記", "📷  拍照筆記", "🖼  相簿匯入", "🎙  錄音筆記")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("新增上課紀錄")
                .setItems(options) { _, idx ->
                    when (idx) {
                        0 -> findNavController().navigate(
                            ClassRecordListFragmentDirections
                                .actionClassRecordListFragmentToClassRecordEditFragment(-1L, "text")
                        )
                        1 -> findNavController().navigate(
                            ClassRecordListFragmentDirections
                                .actionClassRecordListToDrawing(createRecord = true)
                        )
                        2 -> findNavController().navigate(
                            ClassRecordListFragmentDirections
                                .actionClassRecordListFragmentToClassRecordEditFragment(-1L, "photo")
                        )
                        3 -> findNavController().navigate(
                            ClassRecordListFragmentDirections
                                .actionClassRecordListFragmentToClassRecordEditFragment(-1L, "gallery")
                        )
                        4 -> findNavController().navigate(
                            ClassRecordListFragmentDirections
                                .actionClassRecordListToAudioRecord()
                        )
                    }
                }
                .show()
        }

        binding.fabAiSummary.setOnClickListener { showSessionPicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.records.collect { records ->
                val items = buildListItems(records)
                adapter.submitList(items)
                binding.tvNoRecords.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun buildListItems(records: List<ClassRecordEntity>): List<ClassRecordListItem> {
        val grouped = records.groupBy { "${it.date}||${it.timeLabel}" }
        return buildList {
            for ((key, group) in grouped) {
                val (date, timeLabel) = key.split("||", limit = 2)
                add(ClassRecordListItem.Header(date, timeLabel, group.size))
                group.forEach { add(ClassRecordListItem.Record(it)) }
            }
        }
    }

    private fun showSessionPicker() {
        val records = viewModel.records.value
        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "目前沒有上課紀錄", Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("AI 總結")
            .setItems(arrayOf("📅  當天總結", "📚  單一節次總結")) { _, idx ->
                when (idx) {
                    0 -> showDaySummaryPicker(records)
                    1 -> showSingleSessionPicker(records)
                }
            }
            .show()
    }

    private fun showDaySummaryPicker(records: List<ClassRecordEntity>) {
        val days = records.groupBy { it.date }.keys.toList().sorted().reversed()
        if (days.size == 1) {
            val dayRecords = records.filter { it.date == days[0] }
            runAiSessionSummary(days[0], dayRecords)
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇日期")
            .setItems(days.toTypedArray()) { _, idx ->
                val date = days[idx]
                val dayRecords = records.filter { it.date == date }
                runAiSessionSummary(date, dayRecords)
            }
            .show()
    }

    private fun showSingleSessionPicker(records: List<ClassRecordEntity>) {
        val sessions = records
            .groupBy { "${it.date}||${it.timeLabel}" }
            .keys.toList()
        val sessionLabels = sessions.map { key ->
            val (date, timeLabel) = key.split("||", limit = 2)
            if (timeLabel.isBlank()) date else "$date  $timeLabel"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇節次")
            .setItems(sessionLabels) { _, idx ->
                val key = sessions[idx]
                val (date, timeLabel) = key.split("||", limit = 2)
                val group = records.filter { it.date == date && it.timeLabel == timeLabel }
                runAiSessionSummary(sessionLabels[idx], group)
            }
            .show()
    }

    private fun runAiSessionSummary(sessionLabel: String, records: List<ClassRecordEntity>) {
        val app = requireActivity().application as ClassNoteApplication
        val apiKey = AppPreferences(requireContext()).geminiApiKey
        if (apiKey.isBlank()) {
            Toast.makeText(requireContext(), "請先在設定頁輸入 Gemini API Key", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage("AI 總結中...")
            .setCancelable(false)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val contentParts = mutableListOf<String>()
            for (record in records) {
                if (record.textNote.isNotBlank()) {
                    contentParts.add("【文字筆記】\n${record.textNote}")
                }
                val mediaList = viewModel.getMediaOnce(record.id)
                val audioMedia = mediaList.filter { it.type == "audio" }
                for (audio in audioMedia) {
                    if (record.aiSummary.isNotBlank()) {
                        contentParts.add("【錄音摘要】\n${record.aiSummary}")
                    } else {
                        val audioSummary = GeminiApi.summarizeAudio(apiKey, audio.filePath)
                        if (!audioSummary.isNullOrBlank()) {
                            contentParts.add("【錄音摘要】\n$audioSummary")
                        }
                    }
                }
                val photoMedia = mediaList.filter { it.type == "photo" || it.type == "drawing" }
                for (photo in photoMedia) {
                    val label = if (photo.type == "drawing") "手繪內容" else "照片內容"
                    if (photo.aiSummary.isNotBlank()) {
                        contentParts.add("【$label】\n${photo.aiSummary}")
                    } else {
                        val photoSummary = GeminiApi.summarizePhoto(apiKey, photo.filePath)
                        if (!photoSummary.isNullOrBlank()) {
                            viewModel.updateMediaAiSummary(photo.id, photoSummary)
                            contentParts.add("【$label】\n$photoSummary")
                        }
                    }
                }
            }
            loadingDialog.dismiss()

            if (contentParts.isEmpty()) {
                Toast.makeText(requireContext(), "這堂課沒有可總結的內容", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val combined = contentParts.joinToString("\n\n")
            val loadingDialog2 = MaterialAlertDialogBuilder(requireContext())
                .setMessage("整合總結中...")
                .setCancelable(false)
                .show()
            val summary = GeminiApi.summarizeSession(apiKey, combined)
            loadingDialog2.dismiss()

            if (summary.isNullOrBlank()) {
                Toast.makeText(requireContext(), "總結失敗，請稍後再試", Toast.LENGTH_SHORT).show()
                return@launch
            }

            findNavController().navigate(
                ClassRecordListFragmentDirections
                    .actionClassRecordListFragmentToClassRecordSummaryFragment(sessionLabel, summary)
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
