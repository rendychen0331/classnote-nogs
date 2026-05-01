package com.rendy.classnote.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetAiSettingsBinding
import com.rendy.classnote.ui.BiometricHelper

class AiSettingsSheet : Fragment() {

    private var _binding: SheetAiSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetAiSettingsBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupAiSection()
    }

    private fun setupAiSection() {
        binding.switchAiEnabled.isChecked = prefs.aiEnabled
        updateAiKeysVisibility()

        binding.switchAiEnabled.setOnCheckedChangeListener { _, checked ->
            prefs.aiEnabled = checked
            updateAiKeysVisibility()
        }

        fun setupKeyField(
            til: com.google.android.material.textfield.TextInputLayout,
            et: com.google.android.material.textfield.TextInputEditText,
            btn: com.google.android.material.button.MaterialButton,
            getKey: () -> String,
            saveKey: (String) -> Unit,
            savedMsg: String
        ) {
            val saved = getKey()
            if (saved.isNotBlank()) et.setText(saved)
            var visible = false
            til.setEndIconOnClickListener {
                if (visible) {
                    visible = false
                    et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                    til.setEndIconDrawable(R.drawable.ic_visibility_off)
                    et.setSelection(et.text?.length ?: 0)
                } else {
                    BiometricHelper.authenticate(
                        fragment = this,
                        title = getString(R.string.biometric_title_monitor),
                        subtitle = getString(R.string.biometric_subtitle_apikey),
                        onSuccess = {
                            visible = true
                            et.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            til.setEndIconDrawable(R.drawable.ic_visibility)
                            et.setSelection(et.text?.length ?: 0)
                        }
                    )
                }
            }
            btn.setOnClickListener {
                BiometricHelper.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_title_monitor),
                    subtitle = getString(R.string.biometric_subtitle_apikey),
                    onSuccess = {
                        saveKey(et.text?.toString()?.trim() ?: "")
                        Toast.makeText(requireContext(), savedMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        fun setupModelSwitch(
            switch: com.google.android.material.materialswitch.MaterialSwitch,
            keyLayout: android.view.View,
            getEnabled: () -> Boolean,
            saveEnabled: (Boolean) -> Unit,
            til: com.google.android.material.textfield.TextInputLayout,
            et: com.google.android.material.textfield.TextInputEditText,
            btn: com.google.android.material.button.MaterialButton,
            getKey: () -> String,
            saveKey: (String) -> Unit,
            savedMsg: String
        ) {
            switch.isChecked = getEnabled()
            keyLayout.visibility = if (getEnabled()) View.VISIBLE else View.GONE
            switch.setOnCheckedChangeListener { _, checked ->
                saveEnabled(checked)
                keyLayout.visibility = if (checked) View.VISIBLE else View.GONE
            }
            setupKeyField(til, et, btn, getKey, saveKey, savedMsg)
        }

        setupModelSwitch(
            switch = binding.switchGeminiEnabled,
            keyLayout = binding.layoutGeminiKey,
            getEnabled = { prefs.geminiEnabled },
            saveEnabled = { prefs.geminiEnabled = it },
            til = binding.tilGeminiApiKey,
            et = binding.etGeminiApiKey,
            btn = binding.btnSaveGeminiKey,
            getKey = { prefs.geminiApiKey },
            saveKey = { prefs.geminiApiKey = it },
            savedMsg = getString(R.string.settings_ai_gemini_key_saved)
        )
        setupModelSwitch(
            switch = binding.switchMimoEnabled,
            keyLayout = binding.layoutMimoKey,
            getEnabled = { prefs.mimoEnabled },
            saveEnabled = { prefs.mimoEnabled = it },
            til = binding.tilMimoApiKey,
            et = binding.etMimoApiKey,
            btn = binding.btnSaveMimoKey,
            getKey = { prefs.mimoApiKey },
            saveKey = { prefs.mimoApiKey = it },
            savedMsg = "MiMo API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchClaudeEnabled,
            keyLayout = binding.layoutClaudeKey,
            getEnabled = { prefs.claudeEnabled },
            saveEnabled = { prefs.claudeEnabled = it },
            til = binding.tilClaudeApiKey,
            et = binding.etClaudeApiKey,
            btn = binding.btnSaveClaudeKey,
            getKey = { prefs.claudeApiKey },
            saveKey = { prefs.claudeApiKey = it },
            savedMsg = "Claude API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchOpenaiEnabled,
            keyLayout = binding.layoutOpenaiKey,
            getEnabled = { prefs.openaiEnabled },
            saveEnabled = { prefs.openaiEnabled = it },
            til = binding.tilOpenaiApiKey,
            et = binding.etOpenaiApiKey,
            btn = binding.btnSaveOpenaiKey,
            getKey = { prefs.openaiApiKey },
            saveKey = { prefs.openaiApiKey = it },
            savedMsg = "OpenAI API Key 已儲存"
        )
        setupModelSwitch(
            switch = binding.switchGroqEnabled,
            keyLayout = binding.layoutGroqKey,
            getEnabled = { prefs.groqEnabled },
            saveEnabled = { prefs.groqEnabled = it },
            til = binding.tilGroqApiKey,
            et = binding.etGroqApiKey,
            btn = binding.btnSaveGroqKey,
            getKey = { prefs.groqApiKey },
            saveKey = { prefs.groqApiKey = it },
            savedMsg = "Groq API Key 已儲存"
        )

        setupChatProviderChips()
        setupNotifProviderChips()
    }

    private fun setupChatProviderChips() {
        val chipMap = mapOf(
            binding.chipChatGemini to ("gemini" to (prefs.geminiApiKey to prefs.geminiEnabled)),
            binding.chipChatMimo   to ("mimo"   to (prefs.mimoApiKey   to prefs.mimoEnabled)),
            binding.chipChatClaude to ("claude" to (prefs.claudeApiKey to prefs.claudeEnabled)),
            binding.chipChatOpenai to ("openai" to (prefs.openaiApiKey to prefs.openaiEnabled)),
            binding.chipChatGroq   to ("groq"   to (prefs.groqApiKey   to prefs.groqEnabled))
        )
        chipMap.forEach { (chip, pair) ->
            val (_, keyAndEnabled) = pair
            val active = keyAndEnabled.first.isNotBlank() && keyAndEnabled.second
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }
        val preferred = prefs.preferredChatProvider
        val preferredChip = chipMap.entries.firstOrNull { it.value.first == preferred }?.key
        val firstAvailable = chipMap.entries.firstOrNull { it.key.isEnabled }?.key
        (preferredChip?.takeIf { it.isEnabled } ?: firstAvailable)?.isChecked = true

        binding.chipGroupChatProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first
                ?: return@setOnCheckedStateChangeListener
            prefs.preferredChatProvider = provider
        }
    }

    private fun setupNotifProviderChips() {
        val chipMap = mapOf(
            binding.chipNotifGemini to ("gemini" to (prefs.geminiApiKey to prefs.geminiEnabled)),
            binding.chipNotifMimo   to ("mimo"   to (prefs.mimoApiKey   to prefs.mimoEnabled)),
            binding.chipNotifClaude to ("claude" to (prefs.claudeApiKey to prefs.claudeEnabled)),
            binding.chipNotifOpenai to ("openai" to (prefs.openaiApiKey to prefs.openaiEnabled)),
            binding.chipNotifGroq   to ("groq"   to (prefs.groqApiKey   to prefs.groqEnabled))
        )
        chipMap.forEach { (chip, pair) ->
            val (_, keyAndEnabled) = pair
            val active = keyAndEnabled.first.isNotBlank() && keyAndEnabled.second
            chip.isEnabled = active
            chip.alpha = if (active) 1f else 0.4f
        }
        val preferred = prefs.preferredNotifProvider
        val preferredChip = chipMap.entries.firstOrNull { it.value.first == preferred }?.key
        val firstAvailable = chipMap.entries.firstOrNull { it.key.isEnabled }?.key
        (preferredChip?.takeIf { it.isEnabled } ?: firstAvailable)?.isChecked = true

        binding.chipGroupNotifProvider.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val provider = chipMap.entries.firstOrNull { it.key.id == id }?.value?.first
                ?: return@setOnCheckedStateChangeListener
            prefs.preferredNotifProvider = provider
        }
    }

    private fun updateAiKeysVisibility() {
        val v = if (prefs.aiEnabled) View.VISIBLE else View.GONE
        binding.cardGemini.visibility = v
        binding.cardMimo.visibility = v
        binding.cardClaude.visibility = v
        binding.cardOpenai.visibility = v
        binding.cardGroq.visibility = v
        binding.cardChatProvider.visibility = v
        binding.cardNotifProvider.visibility = v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AiSettingsSheet"
    }
}
