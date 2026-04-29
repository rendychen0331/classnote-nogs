package com.rendy.classnote.ui.classrecord

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentClassRecordDetailBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class ClassRecordDetailFragment : Fragment() {

    private var _binding: FragmentClassRecordDetailBinding? = null
    private val binding get() = _binding!!

    private val args: ClassRecordDetailFragmentArgs by navArgs()
    private val viewModel: ClassRecordViewModel by viewModels {
        val app = requireActivity().application as ClassNoteApplication
        ClassRecordViewModel.Factory(app.classRecordRepository)
    }

    private val mediaPlayers = mutableListOf<MediaPlayer>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentClassRecordDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabEdit.setOnClickListener {
            findNavController().navigate(
                ClassRecordDetailFragmentDirections
                    .actionClassRecordDetailFragmentToClassRecordEditFragment(args.recordId)
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as ClassNoteApplication
            val record = viewModel.getById(args.recordId) ?: return@launch

            // Date / time label header
            val dow = runCatching {
                val d = LocalDate.parse(record.date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                "（${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.TRADITIONAL_CHINESE)}）"
            }.getOrDefault("")
            val timeStr = if (record.timeLabel.isNotBlank()) "  ${record.timeLabel}" else ""
            binding.tvDetailDateTime.text = "${record.date}$dow$timeStr"

            // Title
            if (record.title.isNotBlank()) {
                binding.tvDetailTitle.text = record.title
                binding.tvDetailTitle.visibility = View.VISIBLE
            }

            // Text note
            if (record.textNote.isNotBlank()) {
                val stripped = Html.fromHtml(record.textNote, Html.FROM_HTML_MODE_COMPACT).toString().trim()
                if (stripped.isNotBlank()) {
                    binding.tvDetailNote.text = Html.fromHtml(record.textNote, Html.FROM_HTML_MODE_COMPACT)
                    binding.tvDetailNote.visibility = View.VISIBLE
                }
            }

            // Media
            val mediaItems = app.classRecordRepository.getMediaForRecordOnce(record.id)

            val photos = mediaItems.filter { it.type == "photo" || it.type == "drawing" }
            if (photos.isNotEmpty()) {
                binding.scrollPhotos.visibility = View.VISIBLE
                val sizePx = (120 * resources.displayMetrics.density).toInt()
                photos.forEach { media ->
                    val iv = ImageView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                            marginEnd = (8 * resources.displayMetrics.density).toInt()
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        BitmapFactory.decodeFile(media.filePath)?.let { setImageBitmap(it) }
                    }
                    binding.layoutPhotos.addView(iv)
                }
            }

            val audios = mediaItems.filter { it.type == "audio" }
            if (audios.isNotEmpty()) {
                binding.layoutAudio.visibility = View.VISIBLE
                audios.forEach { media ->
                    addAudioRow(media.filePath, media.durationMs)
                }
            }
        }
    }

    private fun addAudioRow(filePath: String, durationMs: Long) {
        val rowView = LayoutInflater.from(requireContext())
            .inflate(android.R.layout.simple_list_item_2, binding.layoutAudio, false)

        val card = com.google.android.material.card.MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * resources.displayMetrics.density).toInt() }
            radius = (8 * resources.displayMetrics.density)
            strokeWidth = 0
            setCardBackgroundColor(requireContext().getColor(android.R.color.transparent))
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (8 * resources.displayMetrics.density).toInt()
            )
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val density = resources.displayMetrics.density
        val playBtn = ImageButton(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * density).toInt(), (40 * density).toInt()
            )
            setImageResource(android.R.drawable.ic_media_play)
            background = null
        }

        val seekBar = SeekBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (8 * density).toInt()
                marginEnd = (8 * density).toInt()
            }
            max = if (durationMs > 0) durationMs.toInt() else 100
        }

        val tvDuration = TextView(requireContext()).apply {
            val totalSec = (durationMs / 1000).toInt()
            text = "%d:%02d".format(totalSec / 60, totalSec % 60)
            textSize = 12f
        }

        container.addView(playBtn)
        container.addView(seekBar)
        container.addView(tvDuration)
        card.addView(container)
        binding.layoutAudio.addView(card)

        val player = MediaPlayer()
        mediaPlayers.add(player)
        var isPlaying = false

        playBtn.setOnClickListener {
            if (isPlaying) {
                player.pause()
                isPlaying = false
                playBtn.setImageResource(android.R.drawable.ic_media_play)
            } else {
                if (!player.isPlaying) {
                    try {
                        if (player.currentPosition == 0) {
                            player.setDataSource(filePath)
                            player.prepare()
                        }
                        player.start()
                        isPlaying = true
                        playBtn.setImageResource(android.R.drawable.ic_media_pause)
                        seekBar.max = player.duration
                        updateSeekBar(player, seekBar, tvDuration, playBtn) { isPlaying = false }
                    } catch (_: Exception) {}
                }
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) player.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        player.setOnCompletionListener {
            isPlaying = false
            seekBar.progress = 0
            playBtn.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun updateSeekBar(
        player: MediaPlayer,
        seekBar: SeekBar,
        tvDuration: TextView,
        playBtn: ImageButton,
        onDone: () -> Unit
    ) {
        seekBar.post(object : Runnable {
            override fun run() {
                if (_binding == null) return
                if (player.isPlaying) {
                    seekBar.progress = player.currentPosition
                    val remaining = (player.duration - player.currentPosition) / 1000
                    tvDuration.text = "%d:%02d".format(remaining / 60, remaining % 60)
                    seekBar.postDelayed(this, 200)
                } else {
                    onDone()
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayers.forEach { it.release() }
        mediaPlayers.clear()
        _binding = null
    }
}
