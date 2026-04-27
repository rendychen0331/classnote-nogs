package com.rendy.classnote.ui.classrecord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.databinding.FragmentDrawingBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class DrawingFragment : Fragment() {

    companion object {
        const val RESULT_KEY = "drawing_result"
        const val EXTRA_PATH = "path"
    }

    private var _binding: FragmentDrawingBinding? = null
    private val binding get() = _binding!!
    private val args: DrawingFragmentArgs by navArgs()

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val penColors = listOf(
        Color.parseColor("#212121"),
        Color.parseColor("#E53935"),
        Color.parseColor("#1E88E5"),
        Color.parseColor("#43A047")
    )
    private var currentColor = penColors[0]

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDrawingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupColorButtons()
        setupToolButtons()
        setupActionButtons()
        loadExistingDrawing()
    }

    private fun setupColorButtons() {
        val colorViews = listOf(
            binding.btnColorBlack,
            binding.btnColorRed,
            binding.btnColorBlue,
            binding.btnColorGreen
        )
        colorViews.forEachIndexed { i, v ->
            v.background = ovalDrawable(penColors[i])
            v.setOnClickListener {
                currentColor = penColors[i]
                binding.drawingView.strokeColor = currentColor
                binding.drawingView.isEraser = false
                refreshColorSelection(colorViews, i)
                updateEraserVisual(false)
            }
        }
        refreshColorSelection(colorViews, 0)
    }

    private fun refreshColorSelection(views: List<View>, selectedIdx: Int) {
        views.forEachIndexed { i, v ->
            val d = v.background as? GradientDrawable ?: return@forEachIndexed
            d.setStroke(if (i == selectedIdx) dpToPx(3) else 0, Color.WHITE)
        }
    }

    private fun setupToolButtons() {
        binding.btnPenSmall.setOnClickListener {
            binding.drawingView.strokeWidth = 4f
            binding.drawingView.isEraser = false
            updateEraserVisual(false)
        }
        binding.btnPenLarge.setOnClickListener {
            binding.drawingView.strokeWidth = 10f
            binding.drawingView.isEraser = false
            updateEraserVisual(false)
        }
        binding.btnEraser.setOnClickListener {
            val nowEraser = !binding.drawingView.isEraser
            binding.drawingView.isEraser = nowEraser
            updateEraserVisual(nowEraser)
        }
        binding.btnUndo.setOnClickListener { binding.drawingView.undo() }
        binding.btnRedo.setOnClickListener { binding.drawingView.redo() }
        binding.btnClearAll.setOnClickListener { binding.drawingView.clearAll() }
    }

    private fun setupActionButtons() {
        binding.btnDrawCancel.setOnClickListener { findNavController().popBackStack() }
        binding.btnDrawSave.setOnClickListener { saveDrawing() }
    }

    private fun loadExistingDrawing() {
        val path = args.existingPath
        if (path.isNotEmpty()) {
            BitmapFactory.decodeFile(path)?.let { binding.drawingView.loadBitmap(it) }
        }
    }

    private fun saveDrawing() {
        val bmp = binding.drawingView.toBitmap()
        val dir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ClassNote")
        dir.mkdirs()
        val filename = args.existingPath.let { p ->
            if (p.isNotEmpty()) File(p).name else "drawing_${System.currentTimeMillis()}.png"
        }
        val file = File(dir, filename)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        if (args.createRecord) {
            val record = ClassRecordEntity(
                date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            )
            val media = listOf(
                ClassRecordMediaEntity(recordId = 0, type = "drawing", filePath = file.absolutePath)
            )
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.save(record, media)
                if (isAdded) findNavController().popBackStack()
            }
        } else {
            setFragmentResult(RESULT_KEY, bundleOf(EXTRA_PATH to file.absolutePath))
            findNavController().popBackStack()
        }
    }

    private fun updateEraserVisual(active: Boolean) {
        binding.btnEraser.alpha = if (active) 1f else 0.5f
    }

    private fun ovalDrawable(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
