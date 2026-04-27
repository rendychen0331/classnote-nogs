package com.rendy.classnote.ui.classrecord

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.data.local.entity.ClassRecordEntity
import com.rendy.classnote.data.local.entity.ClassRecordMediaEntity
import com.rendy.classnote.databinding.FragmentAudioRecordBinding
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class AudioRecordFragment : Fragment() {

    private var _binding: FragmentAudioRecordBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private var selectedDate: String = LocalDate.now().format(dateFormatter)

    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null
    private var recordingStartMs: Long = 0L
    private var savedAudioPath: String? = null
    private var savedDurationMs: Long = 0L

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsed = (System.currentTimeMillis() - recordingStartMs) / 1000
            val mins = elapsed / 60
            val secs = elapsed % 60
            binding.tvTimer.text = "%02d:%02d".format(mins, secs)
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val requestAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording()
        else Toast.makeText(requireContext(), "需要錄音權限", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAudioRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvAudioDate.text = selectedDate
        binding.tvAudioDate.setOnClickListener { pickDate() }

        binding.btnRecord.setOnClickListener {
            if (isRecording) stopRecording() else checkPermissionAndRecord()
        }
        binding.btnAudioCancel.setOnClickListener {
            if (isRecording) stopRecording()
            findNavController().popBackStack()
        }
        binding.btnAudioSave.setOnClickListener { saveRecord() }
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day).format(dateFormatter)
            binding.tvAudioDate.text = selectedDate
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun checkPermissionAndRecord() {
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
        binding.tvRecordingStatus.text = "錄音中..."
        binding.btnRecord.setIconResource(android.R.drawable.ic_media_pause)
        timerHandler.post(timerRunnable)
    }

    private fun stopRecording() {
        timerHandler.removeCallbacks(timerRunnable)
        savedDurationMs = System.currentTimeMillis() - recordingStartMs
        try { mediaRecorder?.stop() } catch (_: Exception) {}
        mediaRecorder?.release()
        mediaRecorder = null
        isRecording = false
        savedAudioPath = currentAudioFile?.absolutePath
        currentAudioFile = null
        binding.tvRecordingStatus.text = "錄音完成，點擊儲存"
        binding.btnRecord.setIconResource(android.R.drawable.ic_btn_speak_now)
        binding.btnAudioSave.isEnabled = true
    }

    private fun saveRecord() {
        val path = savedAudioPath ?: return
        val record = ClassRecordEntity(
            date = selectedDate,
            timeLabel = binding.etAudioTimeLabel.text.toString().trim()
        )
        val media = listOf(
            ClassRecordMediaEntity(recordId = 0, type = "audio", filePath = path, durationMs = savedDurationMs)
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.save(record, media)
            if (isAdded) findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerRunnable)
        if (isRecording) {
            try { mediaRecorder?.stop() } catch (_: Exception) {}
            mediaRecorder?.release()
        }
        _binding = null
    }
}
