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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                        .actionClassRecordListFragmentToClassRecordEditFragment(record.id)
                )
            },
            onDelete = { record ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("刪除紀錄")
                    .setMessage("確定要刪除這筆上課紀錄？")
                    .setPositiveButton("刪除") { _, _ -> viewModel.deleteRecord(record.id) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )

        binding.rvClassRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvClassRecords.adapter = adapter

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
                adapter.submitList(records)
                binding.tvNoRecords.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showSessionPicker() {
        val records = viewModel.records.value
        if (records.isEmpty()) {
            Toast.makeText(requireContext(), "目前沒有上課紀錄", Toast.LENGTH_SHORT).show()
            return
        }
        val sessions = records
            .groupBy { "${it.date}  ${it.timeLabel}".trim() }
            .keys.toList()
        val sessionLabels = sessions.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("選擇要總結的課堂")
            .setItems(sessionLabels) { _, idx ->
                val key = sessions[idx]
                val group = records.filter { "${it.date}  ${it.timeLabel}".trim() == key }
                runAiSessionSummary(key, group)
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

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(sessionLabel)
                .setMessage(summary)
                .setPositiveButton("關閉", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
