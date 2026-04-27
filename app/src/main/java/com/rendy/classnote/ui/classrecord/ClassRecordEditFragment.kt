package com.rendy.classnote.ui.classrecord

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.remote.GeminiApi
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.databinding.FragmentClassRecordEditBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class ClassRecordEditFragment : Fragment() {

    private var _binding: FragmentClassRecordEditBinding? = null
    private val binding get() = _binding!!

    private val args: ClassRecordEditFragmentArgs by navArgs()
    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: String = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private var currentRecordId: Long = -1L

    private val existingMediaItems = mutableListOf<ClassRecordMediaEntity>()
    private val newPhotoPaths = mutableListOf<String>()
    private val newAudioItems = mutableListOf<Pair<String, Long>>()
    private val newDrawingPaths = mutableListOf<String>()

    // WebView note editor bridge
    inner class NoteEditorBridge {
        @Volatile var content: String = ""
        @JavascriptInterface
        fun onChanged(html: String) { content = html }
    }
    private lateinit var noteEditorBridge: NoteEditorBridge
    private var notePageLoaded = false
    private var pendingNoteHtml = ""

    // Audio recording
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null
    private var recordingStartMs: Long = 0L

    // Camera
    private var pendingPhotoPath: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoPath?.let { newPhotoPaths.add(it); addPhotoThumbnail(it) }
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { importImageFromUri(it) }
    }

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(requireContext(), "需要相機權限", Toast.LENGTH_SHORT).show()
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(requireContext(), "需要錄音權限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener(DrawingFragment.RESULT_KEY) { _, bundle ->
            bundle.getString(DrawingFragment.EXTRA_PATH)?.let { path ->
                newDrawingPaths.add(path)
                addDrawingThumbnail(path)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassRecordEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvRecordDate.text = selectedDate
        binding.tvRecordDate.setOnClickListener { pickDate() }
        binding.tilTimeLabel.setEndIconOnClickListener { pickTime() }
        binding.btnSaveRecord.setOnClickListener { saveRecord() }

        val isTextType = args.noteType == "text" || args.noteType.isBlank()
        val isPhotoType = args.noteType == "photo" || args.noteType == "gallery"
        if (isPhotoType) {
            binding.cardNoteEditor.visibility = View.GONE
        }
        if (isTextType) {
            binding.btnGenerateTitle.visibility = View.VISIBLE
        }

        setupNoteEditor()

        if (args.recordId > 0) {
            currentRecordId = args.recordId
            loadRecord(args.recordId)
        } else {
            val t = java.time.LocalTime.now()
            val label = ClassPeriodUtils.detect(t.hour, t.minute)
                ?: t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            binding.etTimeLabel.setText(label)
            when (args.noteType) {
                "photo" -> checkCameraAndLaunch()
                "gallery" -> launchGallery()
                "audio" -> checkAudioAndRecord()
                "drawing" -> openDrawingFragment()
                else -> Unit
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupNoteEditor() {
        noteEditorBridge = NoteEditorBridge()
        binding.webViewNote.setBackgroundColor(Color.TRANSPARENT)
        binding.webViewNote.settings.javaScriptEnabled = true
        binding.webViewNote.addJavascriptInterface(noteEditorBridge, "AndroidBridge")
        binding.webViewNote.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                notePageLoaded = true
                if (pendingNoteHtml.isNotEmpty()) {
                    view?.evaluateJavascript("setContent('${escapeForJs(pendingNoteHtml)}')", null)
                    pendingNoteHtml = ""
                }
            }
        }
        binding.webViewNote.loadUrl("file:///android_asset/note_editor/editor.html")

        binding.btnGenerateTitle.setOnClickListener { generateTitle() }

        binding.btnFormatBold.setOnClickListener { execNoteCmd("bold") }
        binding.btnFormatItalic.setOnClickListener { execNoteCmd("italic") }
        binding.btnFormatUnderline.setOnClickListener { execNoteCmd("underline") }
        binding.btnFormatBullet.setOnClickListener { execNoteCmd("insertUnorderedList") }
        binding.btnFormatOrdered.setOnClickListener { execNoteCmd("insertOrderedList") }
        binding.btnFormatTable.setOnClickListener {
            binding.webViewNote.evaluateJavascript("insertTable(3,3)", null)
        }
        binding.btnFormatUndo.setOnClickListener { execNoteCmd("undo") }
        binding.btnFormatRedo.setOnClickListener { execNoteCmd("redo") }
    }

    private fun execNoteCmd(cmd: String) {
        binding.webViewNote.evaluateJavascript("exec('$cmd')", null)
    }

    private fun escapeForJs(s: String) =
        s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")

    private fun generateTitle() {
        val content = noteEditorBridge.content.replace(Regex("<[^>]+>"), "").trim()
        if (content.isBlank()) { Toast.makeText(requireContext(), "請先輸入筆記內容", Toast.LENGTH_SHORT).show(); return }
        val apiKey = AppPreferences(requireContext()).geminiApiKey
        if (apiKey.isBlank()) { Toast.makeText(requireContext(), "請先設定 Gemini API Key", Toast.LENGTH_SHORT).show(); return }
        binding.btnGenerateTitle.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val title = GeminiApi.generateTitle(apiKey, content)
            binding.btnGenerateTitle.isEnabled = true
            if (title != null) binding.etTitle.setText(title)
            else Toast.makeText(requireContext(), "生成失敗，請稍後再試", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDrawingFragment() {
        val action = ClassRecordEditFragmentDirections.actionClassRecordEditToDrawing()
        findNavController().navigate(action)
    }

    private fun loadRecord(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ClassNoteApplication
            val record = viewModel.getById(id) ?: return@launch
            selectedDate = record.date
            binding.tvRecordDate.text = record.date
            binding.etTimeLabel.setText(record.timeLabel)
            binding.etTitle.setText(record.title)

            noteEditorBridge.content = record.textNote
            if (notePageLoaded) {
                binding.webViewNote.evaluateJavascript("setContent('${escapeForJs(record.textNote)}')", null)
            } else {
                pendingNoteHtml = record.textNote
            }

            val mediaItems = app.classRecordRepository.getMediaForRecordOnce(id)
            existingMediaItems.addAll(mediaItems)
            mediaItems.filter { it.type == "photo" }.forEach { addPhotoThumbnail(it.filePath) }
            mediaItems.filter { it.type == "audio" }.forEach { addAudioRow(it.filePath, it.durationMs) }
            mediaItems.filter { it.type == "drawing" }.forEach { addDrawingThumbnail(it.filePath) }
        }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day).format(dateFormatter)
            binding.tvRecordDate.text = selectedDate
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun pickTime() {
        val cal = Calendar.getInstance()
        android.app.TimePickerDialog(requireContext(), { _, hour, minute ->
            val timeStr = "%02d:%02d".format(hour, minute)
            binding.etTimeLabel.setText(timeStr)
            binding.etTimeLabel.setSelection(timeStr.length)
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun checkCameraAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val photoDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ClassNote")
        photoDir.mkdirs()
        val photoFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")
        pendingPhotoPath = photoFile.absolutePath
        val uri: Uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile
        )
        takePictureLauncher.launch(uri)
    }

    private fun launchGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun importImageFromUri(uri: Uri) {
        val photoDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ClassNote")
        photoDir.mkdirs()
        val destFile = File(photoDir, "import_${System.currentTimeMillis()}.jpg")
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            newPhotoPaths.add(destFile.absolutePath)
            addPhotoThumbnail(destFile.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "匯入失敗：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addPhotoThumbnail(path: String) {
        val sizePx = (80 * resources.displayMetrics.density).toInt()
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        BitmapFactory.decodeFile(path)?.let { iv.setImageBitmap(it) }
        binding.layoutPhotos.addView(iv)
        binding.scrollPhotos.visibility = View.VISIBLE
    }

    private fun addDrawingThumbnail(path: String) {
        val sizePx = (80 * resources.displayMetrics.density).toInt()
        val iv = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setOnClickListener { openDrawingFragmentEdit(path) }
        }
        BitmapFactory.decodeFile(path)?.let { iv.setImageBitmap(it) }
        binding.layoutDrawings.addView(iv)
        binding.scrollDrawings.visibility = View.VISIBLE
    }

    private fun openDrawingFragmentEdit(existingPath: String) {
        val action = ClassRecordEditFragmentDirections.actionClassRecordEditToDrawing(existingPath)
        findNavController().navigate(action)
    }

    private fun toggleRecording() {
        if (isRecording) stopRecording() else checkAudioAndRecord()
    }

    private fun checkAudioAndRecord() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val audioDir = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "ClassNote")
        audioDir.mkdirs()
        currentAudioFile = File(audioDir, "audio_${System.currentTimeMillis()}.m4a")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(requireContext())
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentAudioFile!!.absolutePath)
            prepare()
            start()
        }
        isRecording = true
        recordingStartMs = System.currentTimeMillis()
    }

    private fun stopRecording() {
        val durationMs = System.currentTimeMillis() - recordingStartMs
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        currentAudioFile?.let { file ->
            newAudioItems.add(Pair(file.absolutePath, durationMs))
            addAudioRow(file.absolutePath, durationMs)
        }
        currentAudioFile = null
    }

    private fun addAudioRow(path: String, durationMs: Long) {
        val mins = durationMs / 60000
        val secs = (durationMs % 60000) / 1000
        val tv = TextView(requireContext()).apply {
            text = "🎙  ${File(path).name}  (${mins}:${"%02d".format(secs)})"
            textSize = 13f
            setTextColor(resources.getColor(android.R.color.tab_indicator_text, requireContext().theme))
            val padV = (4 * resources.displayMetrics.density).toInt()
            setPadding(0, padV, 0, padV)
        }
        binding.layoutRecordings.addView(tv)
    }

    private fun saveRecord() {
        val record = ClassRecordEntity(
            id = if (currentRecordId > 0) currentRecordId else 0,
            date = selectedDate,
            timeLabel = binding.etTimeLabel.text.toString().trim(),
            title = binding.etTitle.text?.toString()?.trim().orEmpty(),
            textNote = noteEditorBridge.content,
            aiSummary = ""
        )
        val newMediaItems =
            newPhotoPaths.map { ClassRecordMediaEntity(recordId = 0, type = "photo", filePath = it) } +
            newAudioItems.map { (path, dur) -> ClassRecordMediaEntity(recordId = 0, type = "audio", filePath = path, durationMs = dur) } +
            newDrawingPaths.map { ClassRecordMediaEntity(recordId = 0, type = "drawing", filePath = it) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.save(record, newMediaItems)
            if (isAdded) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRecording) stopRecording()
        binding.webViewNote.destroy()
        _binding = null
    }
}
