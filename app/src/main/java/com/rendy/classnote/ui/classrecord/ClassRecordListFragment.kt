package com.rendy.classnote.ui.classrecord

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
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

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.records.collect { records ->
                adapter.submitList(records)
                binding.tvNoRecords.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
