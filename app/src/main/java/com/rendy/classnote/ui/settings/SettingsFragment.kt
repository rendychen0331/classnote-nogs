package com.rendy.classnote.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: com.rendy.classnote.data.AppPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupAlarmSection()
        setupPermissionsSection()
    }

    override fun onResume() {
        super.onResume()
        // 重新進頁面時更新權限狀態（使用者可能剛從設定頁回來）
        updatePermissionStatuses()
    }

    // ── 鬧鐘提醒 ─────────────────────────────────────────────────────────────

    private fun setupAlarmSection() {
        // 全頁提醒 toggle
        binding.switchFullScreenAlarm.isChecked = prefs.fullScreenAlarmEnabled
        binding.switchFullScreenAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.fullScreenAlarmEnabled = checked
            binding.cardSnooze.alpha = if (checked) 1f else 0.4f
        }
        binding.cardSnooze.alpha = if (prefs.fullScreenAlarmEnabled) 1f else 0.4f

        // Snooze 時間
        updateSnoozeSummary()
        binding.cardSnooze.setOnClickListener {
            if (!prefs.fullScreenAlarmEnabled) return@setOnClickListener
            showSnoozeDialog()
        }
    }

    private fun updateSnoozeSummary() {
        val label = when (prefs.snoozeDurationMinutes) {
            5 -> getString(R.string.snooze_5min)
            15 -> getString(R.string.snooze_15min)
            30 -> getString(R.string.snooze_30min)
            else -> getString(R.string.snooze_10min)
        }
        binding.tvSnoozeValue.text = label
    }

    private fun showSnoozeDialog() {
        val options = arrayOf(
            getString(R.string.snooze_5min),
            getString(R.string.snooze_10min),
            getString(R.string.snooze_15min),
            getString(R.string.snooze_30min)
        )
        val minutes = intArrayOf(5, 10, 15, 30)
        val current = minutes.indexOfFirst { it == prefs.snoozeDurationMinutes }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_snooze_title))
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.snoozeDurationMinutes = minutes[which]
                updateSnoozeSummary()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── 權限狀態 ──────────────────────────────────────────────────────────────

    private fun setupPermissionsSection() {
        // 全螢幕通知（Android 14+ 才顯示）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            binding.cardPermFullScreen.visibility = View.VISIBLE
            binding.cardPermFullScreen.setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                )
            }
        } else {
            binding.cardPermFullScreen.visibility = View.GONE
        }

        // 顯示在其他 App 上方
        binding.cardPermOverlay.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
            )
        }

        // 小米鎖屏顯示
        if (isXiaomiDevice()) {
            binding.cardPermXiaomi.visibility = View.VISIBLE
            binding.btnXiaomiPermSettings.setOnClickListener {
                openXiaomiAppPermissions()
            }
        } else {
            binding.cardPermXiaomi.visibility = View.GONE
        }

        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        val granted = getString(R.string.settings_perm_granted)
        val notGranted = getString(R.string.settings_perm_not_granted)
        val grantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_exam_color)

        // 全螢幕通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val ok = NotificationManagerCompat.from(requireContext()).canUseFullScreenIntent()
            binding.tvPermFullScreenStatus.text = if (ok) granted else notGranted
            binding.tvPermFullScreenStatus.setTextColor(if (ok) grantedColor else notGrantedColor)
        }

        // Overlay
        val overlayOk = Settings.canDrawOverlays(requireContext())
        binding.tvPermOverlayStatus.text = if (overlayOk) granted else notGranted
        binding.tvPermOverlayStatus.setTextColor(if (overlayOk) grantedColor else notGrantedColor)
    }

    // ── Xiaomi 偵測（與 MainActivity 相同邏輯）────────────────────────────────

    private fun isXiaomiDevice(): Boolean =
        android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        android.os.Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
        !getSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()

    private fun getSystemProperty(key: String): String? = try {
        @Suppress("PrivateApi")
        val method = Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
        method.invoke(null, key) as? String
    } catch (_: Exception) { null }

    private fun openXiaomiAppPermissions() {
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", requireContext().packageName)
        }
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        try {
            startActivity(miuiIntent)
        } catch (_: Exception) {
            startActivity(fallback)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
