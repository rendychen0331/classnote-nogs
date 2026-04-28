package com.rendy.classnote.ui.settings

import android.app.Activity
import android.content.Intent
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.app.TimePickerDialog
import com.rendy.classnote.ui.BiometricHelper
import com.rendy.classnote.ui.settings.ApiLogAdapter
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.data.ClassroomSyncManager
import com.rendy.classnote.data.DriveBackupManager
import com.rendy.classnote.data.GmailSyncManager
import com.rendy.classnote.data.DriveBackupWorker
import com.rendy.classnote.data.GmailSyncWorker
import com.rendy.classnote.data.ClassroomSyncWorker
import com.rendy.classnote.data.GoogleAuthManager
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.databinding.FragmentSettingsBinding
import com.rendy.classnote.notification.WeatherNotificationScheduler
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!


    private lateinit var prefs: com.rendy.classnote.data.AppPreferences
    private lateinit var weatherPrefs: WeatherPreferences

    private val reAuthLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(requireContext(), getString(R.string.settings_google_reauth_ok), Toast.LENGTH_SHORT).show()
        }
        updateDriveSection()
    }

    private val gmailSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GoogleAuthManager.handleSignInResult(result.data)
        }
        updateGmailSection()
    }

    // 專門用於補請 Gmail scope（requestPermissions 回傳，不影響 Drive 授權）
    private val gmailPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        updateGmailSection()
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(requireContext(), getString(R.string.settings_gmail_permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private val classroomSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleAuthManager.handleSignInResult(result.data)
            if (account?.email != null) {
                GoogleAuthManager.setClassroomAccountEmail(requireContext(), account.email)
            } else {
                Toast.makeText(requireContext(), getString(R.string.settings_classroom_permission_denied), Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.settings_classroom_permission_denied), Toast.LENGTH_SHORT).show()
        }
        updateClassroomSection()
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleAuthManager.handleSignInResult(result.data)
            if (account != null) {
                loadLastBackupTime(account)
            } else {
                Toast.makeText(requireContext(), "登入失敗", Toast.LENGTH_SHORT).show()
            }
        }
        updateDriveSection()
    }

    private val exportSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = GoogleAuthManager.handleSignInResult(result.data)
            if (account != null) {
                doExport(account)
            } else {
                Toast.makeText(requireContext(), "授權失敗", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
        weatherPrefs = WeatherPreferences(requireContext())
        setupAlarmSection()
        setupPermissionsSection()
        setupDndSection()
        setupGoogleSection()
        setupGmailSection()
        setupClassroomSection()
        setupWeatherNotificationSection()
        setupNotificationListenerSection()
        setupApiLogSection()
    }

    override fun onResume() {
        super.onResume()
        // 重新進頁面時更新權限狀態（使用者可能剛從設定頁回來）
        updatePermissionStatuses()
        updateDndStatus()
        updateNotifListenerStatus()
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

        setupQuietHoursSection()
    }

    private fun setupQuietHoursSection() {
        updateQuietHoursTimes()

        // 開始時間
        binding.tvQuietStart.setOnClickListener {
            val h = prefs.quietHoursStart
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursStart = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        // 結束時間
        binding.tvQuietEnd.setOnClickListener {
            val h = prefs.quietHoursEnd
            android.app.TimePickerDialog(requireContext(), { _, hour, _ ->
                prefs.quietHoursEnd = hour
                updateQuietHoursTimes()
            }, h, 0, true).show()
        }

        // 提前 / 延後 RadioGroup
        binding.rgQuietPolicy.check(
            if (prefs.quietHoursPolicyBefore) R.id.rbQuietBefore else R.id.rbQuietAfter
        )
        binding.rgQuietPolicy.setOnCheckedChangeListener { _, checkedId ->
            prefs.quietHoursPolicyBefore = (checkedId == R.id.rbQuietBefore)
        }
    }

    private fun updateQuietHoursTimes() {
        binding.tvQuietStart.text = "%02d:00".format(prefs.quietHoursStart)
        binding.tvQuietEnd.text = "%02d:00".format(prefs.quietHoursEnd)
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

        // 小米鎖屏（MIUI AppOps OP_SHOW_WHEN_LOCKED = 10020）
        if (isXiaomiDevice()) {
            when (isXiaomiLockScreenGranted(requireContext())) {
                true -> {
                    binding.tvPermXiaomiStatus.text = granted
                    binding.tvPermXiaomiStatus.setTextColor(grantedColor)
                }
                false -> {
                    binding.tvPermXiaomiStatus.text = notGranted
                    binding.tvPermXiaomiStatus.setTextColor(notGrantedColor)
                }
                null -> {
                    binding.tvPermXiaomiStatus.text = getString(R.string.settings_perm_status_unknown)
                    binding.tvPermXiaomiStatus.setTextColor(requireContext().getColor(android.R.color.darker_gray))
                }
            }
        }
    }

    // ── Xiaomi 偵測與權限查詢 ─────────────────────────────────────────────────

    /**
     * 透過反射查詢 MIUI 私有 AppOps OP_SHOW_WHEN_LOCKED (10020)。
     * 無法取得時回傳 false（安全降級，不影響非小米裝置）。
     */
    /** 回傳 true=已授權、false=未授權、null=無法查詢（反射失敗） */
    private fun isXiaomiLockScreenGranted(context: Context): Boolean? = try {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val method = android.app.AppOpsManager::class.java.getDeclaredMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val result = method.invoke(
            manager,
            10020,
            android.os.Binder.getCallingUid(),
            context.packageName
        ) as Int
        result == android.app.AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) { null }

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

    // ── 勿擾模式穿透 ─────────────────────────────────────────────────────────────

    private fun setupDndSection() {
        binding.switchBypassDnd.isChecked = prefs.bypassDndEnabled
        updateDndStatus()

        binding.switchBypassDnd.setOnCheckedChangeListener { _, checked ->
            prefs.bypassDndEnabled = checked
            if (checked) {
                val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
                if (!nm.isNotificationPolicyAccessGranted) {
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
            applyDndChannel()
            updateDndStatus()
        }

        binding.tvPermDndStatus.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun updateDndStatus() {
        val enabled = prefs.bypassDndEnabled
        binding.tvPermDndStatus.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) return

        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val granted = nm.isNotificationPolicyAccessGranted
        val grantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_exam_color)
        binding.tvPermDndStatus.text = if (granted)
            getString(R.string.settings_perm_dnd_access_granted)
        else
            getString(R.string.settings_perm_dnd_access_denied)
        binding.tvPermDndStatus.setTextColor(if (granted) grantedColor else notGrantedColor)

        if (granted) applyDndChannel()
    }

    private fun applyDndChannel() {
        val nm = requireContext().getSystemService(android.app.NotificationManager::class.java)
        val bypassDnd = prefs.bypassDndEnabled && nm.isNotificationPolicyAccessGranted
        com.rendy.classnote.notification.NotificationHelper.createNotificationChannel(requireContext(), bypassDnd)
    }

    // ── Google 帳號備份 ──────────────────────────────────────────────────────────

    private fun setupGoogleSection() {
        binding.switchDriveBackup.isChecked = prefs.driveBackupEnabled
        updateDriveSection()

        binding.switchDriveBackup.setOnCheckedChangeListener { _, checked ->
            prefs.driveBackupEnabled = checked
            updateDriveSection()
        }

        updateBackupNetworkSummary()
        binding.cardBackupNetwork.setOnClickListener { showBackupNetworkDialog() }
        setupAutoBackup()

        binding.btnGoogleSignIn.setOnClickListener {
            val currentAccount = GoogleAuthManager.getAccount(requireContext())
            if (currentAccount != null) {
                GoogleAuthManager.signOut(requireContext()) {
                    requireActivity().runOnUiThread { updateDriveSection() }
                }
            } else {
                signInLauncher.launch(GoogleAuthManager.getSignInIntent(requireContext()))
            }
        }

        binding.btnUseGmailAccount.setOnClickListener {
            signInLauncher.launch(GoogleAuthManager.getSignInIntentWithGmail(requireContext()))
        }

        binding.btnGoogleBackup.setOnClickListener {
            val acc = GoogleAuthManager.getAccount(requireContext()) ?: return@setOnClickListener
            binding.btnGoogleBackup.isEnabled = false
            Toast.makeText(requireContext(), getString(R.string.settings_google_backup_in_progress), Toast.LENGTH_SHORT).show()
            viewLifecycleOwner.lifecycleScope.launch {
                val result = DriveBackupManager.backup(requireContext(), acc, prefs.backupNetworkType)
                binding.btnGoogleBackup.isEnabled = true
                when (result) {
                    is DriveBackupManager.Result.Success -> {
                        Toast.makeText(requireContext(), getString(R.string.settings_google_backup_success), Toast.LENGTH_SHORT).show()
                        loadLastBackupTime(acc)
                    }
                    is DriveBackupManager.Result.AuthRequired ->
                        reAuthLauncher.launch(result.intent)
                    is DriveBackupManager.Result.Error ->
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnGoogleRestore.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_google_restore_confirm_title))
                .setMessage(getString(R.string.settings_google_restore_confirm_msg))
                .setPositiveButton("還原") { _, _ ->
                    val acc = GoogleAuthManager.getAccount(requireContext()) ?: return@setPositiveButton
                    binding.btnGoogleRestore.isEnabled = false
                    viewLifecycleOwner.lifecycleScope.launch {
                        val result = DriveBackupManager.restore(requireContext(), acc, prefs.backupNetworkType)
                        binding.btnGoogleRestore.isEnabled = true
                        when (result) {
                            is DriveBackupManager.Result.Success ->
                                Toast.makeText(requireContext(), getString(R.string.settings_google_restore_success), Toast.LENGTH_LONG).show()
                            is DriveBackupManager.Result.AuthRequired ->
                                reAuthLauncher.launch(result.intent)
                            is DriveBackupManager.Result.Error ->
                                Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }

        binding.btnSyncNotes.setOnClickListener {
            if (!GoogleAuthManager.hasDriveFileScope(requireContext())) {
                exportSignInLauncher.launch(GoogleAuthManager.getSignInIntentForExport(requireContext()))
            } else {
                val acc = GoogleAuthManager.getAccount(requireContext()) ?: return@setOnClickListener
                doSyncNotes(acc)
            }
        }

        binding.btnGoogleExport.setOnClickListener {
            if (!GoogleAuthManager.hasDriveFileScope(requireContext())) {
                exportSignInLauncher.launch(GoogleAuthManager.getSignInIntentForExport(requireContext()))
            } else {
                val acc = GoogleAuthManager.getAccount(requireContext()) ?: return@setOnClickListener
                doExport(acc)
            }
        }
    }

    private fun doExport(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        binding.btnGoogleExport.isEnabled = false
        Toast.makeText(requireContext(), getString(R.string.settings_google_export_in_progress), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = DriveBackupManager.exportToVisibleDrive(requireContext(), account)
            binding.btnGoogleExport.isEnabled = true
            when (result) {
                is DriveBackupManager.Result.Success ->
                    Toast.makeText(requireContext(), getString(R.string.settings_google_export_success), Toast.LENGTH_LONG).show()
                is DriveBackupManager.Result.AuthRequired ->
                    reAuthLauncher.launch(result.intent)
                is DriveBackupManager.Result.Error ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun doSyncNotes(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        binding.btnSyncNotes.isEnabled = false
        Toast.makeText(requireContext(), "同步中...", Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = DriveBackupManager.syncNotesToDrive(requireContext(), account)
            binding.btnSyncNotes.isEnabled = true
            when (result) {
                is DriveBackupManager.Result.Success ->
                    Toast.makeText(requireContext(), "同步完成", Toast.LENGTH_LONG).show()
                is DriveBackupManager.Result.AuthRequired ->
                    reAuthLauncher.launch(result.intent)
                is DriveBackupManager.Result.Error ->
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAutoBackup() {
        val intervalLabels = mapOf(
            6 to getString(R.string.settings_auto_backup_interval_6h),
            12 to getString(R.string.settings_auto_backup_interval_12h),
            24 to getString(R.string.settings_auto_backup_interval_24h),
            72 to getString(R.string.settings_auto_backup_interval_72h)
        )
        fun updateIntervalLabel() {
            binding.tvAutoBackupInterval.text = intervalLabels[prefs.autoBackupIntervalHours]
                ?: getString(R.string.settings_auto_backup_interval_24h)
        }

        binding.switchAutoBackup.isChecked = prefs.autoBackupEnabled
        binding.rowAutoBackupInterval.visibility =
            if (prefs.autoBackupEnabled) View.VISIBLE else View.GONE
        updateIntervalLabel()

        binding.switchAutoBackup.setOnCheckedChangeListener { _, checked ->
            prefs.autoBackupEnabled = checked
            binding.rowAutoBackupInterval.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked) scheduleAutoBackup() else cancelAutoBackup()
        }

        binding.rowAutoBackupInterval.setOnClickListener {
            val keys = listOf(6, 12, 24, 72)
            val labels = keys.map { intervalLabels[it] ?: "" }.toTypedArray()
            val current = keys.indexOf(prefs.autoBackupIntervalHours).coerceAtLeast(0)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_auto_backup_interval))
                .setSingleChoiceItems(labels, current) { dialog, which ->
                    prefs.autoBackupIntervalHours = keys[which]
                    updateIntervalLabel()
                    if (prefs.autoBackupEnabled) scheduleAutoBackup()
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun scheduleAutoBackup() {
        val networkType = if (prefs.backupNetworkType == AppPreferences.NETWORK_WIFI)
            NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
        val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(
            prefs.autoBackupIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(constraints).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "drive_auto_backup",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelAutoBackup() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("drive_auto_backup")
    }

    private fun updateBackupNetworkSummary() {
        val label = when (prefs.backupNetworkType) {
            AppPreferences.NETWORK_WIFI -> getString(R.string.backup_network_wifi)
            AppPreferences.NETWORK_MOBILE -> getString(R.string.backup_network_mobile)
            else -> getString(R.string.backup_network_any)
        }
        binding.tvBackupNetworkValue.text = label
    }

    private fun showBackupNetworkDialog() {
        val options = arrayOf(
            getString(R.string.backup_network_wifi),
            getString(R.string.backup_network_mobile),
            getString(R.string.backup_network_any)
        )
        val keys = arrayOf(AppPreferences.NETWORK_WIFI, AppPreferences.NETWORK_MOBILE, AppPreferences.NETWORK_ANY)
        val current = keys.indexOfFirst { it == prefs.backupNetworkType }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_network_title))
            .setSingleChoiceItems(options, current) { dialog, which ->
                prefs.backupNetworkType = keys[which]
                updateBackupNetworkSummary()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateDriveSection() {
        val enabled = prefs.driveBackupEnabled
        binding.cardGoogleAccount.visibility = if (enabled) View.VISIBLE else View.GONE

        val account = GoogleAuthManager.getAccount(requireContext())
        val signedIn = account != null

        binding.cardGoogleActions.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE
        binding.cardBackupNetwork.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE
        binding.cardAutoBackup.visibility = if (enabled && signedIn) View.VISIBLE else View.GONE

        if (!enabled) return

        // 帳號資訊
        binding.tvGoogleAccountEmail.text = account?.email
            ?: getString(R.string.settings_google_not_signed_in)
        binding.btnGoogleSignIn.text = if (signedIn)
            getString(R.string.settings_google_sign_out)
        else
            getString(R.string.settings_google_sign_in)
        binding.tvGoogleLastBackup.visibility = if (signedIn) View.VISIBLE else View.GONE

        // Gmail 帳號捷徑：未登入 + Gmail 同步已啟用時顯示
        binding.layoutUseGmailAccount.visibility =
            if (!signedIn && prefs.gmailSyncEnabled) View.VISIBLE else View.GONE
    }

    private fun loadLastBackupTime(account: GoogleSignInAccount) {
        viewLifecycleOwner.lifecycleScope.launch {
            val time = DriveBackupManager.getLastBackupTime(requireContext(), account)
            binding.tvGoogleLastBackup.text = if (time != null)
                getString(R.string.settings_google_last_backup, time)
            else
                getString(R.string.settings_google_no_backup)
        }
    }

    // ── Gmail 作業同步 ──────────────────────────────────────────────────────────

    private fun setupGmailSection() {
        binding.switchGmailSync.isChecked = prefs.gmailSyncEnabled
        updateGmailSection()

        binding.switchGmailSync.setOnCheckedChangeListener { _, checked ->
            prefs.gmailSyncEnabled = checked
            updateGmailSection()
            updateDriveSection()
        }

        binding.btnGmailSignIn.setOnClickListener {
            val account = GoogleAuthManager.getAccount(requireContext())
            val hasPermission = account != null && GmailSyncManager.hasGmailPermission(requireContext(), account)
            if (hasPermission) {
                // 已授權：登出
                GoogleAuthManager.signOut(requireContext()) { updateGmailSection() }
            } else {
                // 尚未登入或未授權：走完整登入流程（含 Gmail scope）
                gmailSignInLauncher.launch(GoogleAuthManager.getSignInIntentWithGmail(requireContext()))
            }
        }

        binding.btnUseDriveAccount.setOnClickListener {
            gmailPermissionLauncher.launch(GoogleAuthManager.getSignInIntentWithGmail(requireContext()))
        }

        binding.switchGmailForward.isChecked = prefs.gmailClassroomForwardEnabled
        binding.switchGmailForward.setOnCheckedChangeListener { _, checked ->
            prefs.gmailClassroomForwardEnabled = checked
        }

        setupGmailAutoSync()

        binding.btnGmailSyncNow.setOnClickListener {
            val account = GoogleAuthManager.getAccount(requireContext()) ?: return@setOnClickListener
            val db = (requireActivity().application as ClassNoteApplication).database
            val dao = db.reminderDao()
            val notificationDao = db.reminderNotificationDao()
            binding.btnGmailSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = GmailSyncManager.sync(requireContext(), account, dao, notificationDao, prefs.gmailClassroomForwardEnabled)
                binding.btnGmailSyncNow.isEnabled = true
                val summary = when (result) {
                    is GmailSyncManager.SyncResult.Success ->
                        getString(R.string.settings_gmail_sync_result, result.imported, result.skipped)
                    is GmailSyncManager.SyncResult.Error -> result.message
                    GmailSyncManager.SyncResult.NoPermission ->
                        getString(R.string.settings_gmail_no_permission)
                }
                prefs.lastGmailSyncSummary = summary
                binding.tvGmailSyncStatus.text = summary
            }
        }
    }

    private fun setupGmailAutoSync() {
        val intervalHours = prefs.autoGmailSyncIntervalHours
        binding.switchGmailAutoSync.isChecked = prefs.autoGmailSyncEnabled
        binding.tvAutoGmailSyncInterval.text =
            getString(R.string.settings_auto_gmail_sync_interval, intervalHours)

        binding.switchGmailAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoGmailSyncEnabled = checked
            if (checked) scheduleGmailSync() else cancelGmailSync()
        }
    }

    private fun scheduleGmailSync() {
        val request = androidx.work.PeriodicWorkRequestBuilder<GmailSyncWorker>(
            prefs.autoGmailSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "gmail_auto_sync",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelGmailSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("gmail_auto_sync")
    }

    private fun updateGmailSection() {
        val enabled = prefs.gmailSyncEnabled
        binding.cardGmailAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardGmailSync.visibility = View.GONE
        if (!enabled) return

        val account = GoogleAuthManager.getAccount(requireContext())
        val hasPermission = account != null && GmailSyncManager.hasGmailPermission(requireContext(), account)

        binding.tvGmailAccountEmail.text = account?.email ?: getString(R.string.settings_gmail_not_signed_in)
        binding.btnGmailSignIn.text = if (hasPermission)
            getString(R.string.settings_gmail_sign_out)
        else
            getString(R.string.settings_gmail_sign_in)

        // 使用 Drive 帳號捷徑：Drive 已登入但 Gmail 尚未授權時顯示
        val driveAccount = GoogleAuthManager.getAccount(requireContext())
        val showUseDrive = driveAccount != null && !hasPermission
        binding.layoutUseDriveAccount.visibility = if (showUseDrive) View.VISIBLE else View.GONE
        if (showUseDrive) {
            binding.tvDriveAccountEmail.text = driveAccount!!.email ?: driveAccount.displayName
        }

        if (hasPermission) {
            binding.cardGmailSync.visibility = View.VISIBLE
            binding.tvGmailSyncStatus.text = prefs.lastGmailSyncSummary.ifEmpty {
                getString(R.string.settings_gmail_no_sync)
            }
        }
    }

    // ── Classroom 作業同步 ────────────────────────────────────────────────────

    private fun setupClassroomSection() {
        binding.switchClassroomSync.isChecked = prefs.classroomSyncEnabled
        updateClassroomSection()

        binding.switchClassroomSync.setOnCheckedChangeListener { _, checked ->
            prefs.classroomSyncEnabled = checked
            updateClassroomSection()
        }

        binding.btnClassroomAuth.setOnClickListener {
            val email = GoogleAuthManager.getClassroomAccountEmail(requireContext())
            if (email != null) {
                // 已授權：清除本機記錄並登出 Google SignIn client，讓下次授權重新顯示帳號選擇器
                GoogleAuthManager.clearClassroomAccount(requireContext())
                GoogleAuthManager.signOutClassroom(requireContext()) {
                    requireActivity().runOnUiThread { updateClassroomSection() }
                }
            } else {
                // 尚未授權：先登出以清除快取，強制顯示帳號選擇器，再啟動 Classroom 登入流程
                GoogleAuthManager.signOutClassroom(requireContext()) {
                    requireActivity().runOnUiThread {
                        classroomSignInLauncher.launch(
                            GoogleAuthManager.getSignInIntentForClassroom(requireContext())
                        )
                    }
                }
            }
        }

        setupClassroomAutoSync()

        binding.btnClassroomSyncNow.setOnClickListener {
            val email = GoogleAuthManager.getClassroomAccountEmail(requireContext()) ?: return@setOnClickListener
            val db = (requireActivity().application as com.rendy.classnote.ClassNoteApplication).database
            val dao = db.reminderDao()
            val notificationDao = db.reminderNotificationDao()
            binding.btnClassroomSyncNow.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = ClassroomSyncManager.sync(requireContext(), email, dao, notificationDao)
                binding.btnClassroomSyncNow.isEnabled = true
                val summary = when (result) {
                    is ClassroomSyncManager.SyncResult.Success ->
                        getString(R.string.settings_classroom_sync_result, result.imported, result.skipped)
                    is ClassroomSyncManager.SyncResult.Error -> result.message
                    ClassroomSyncManager.SyncResult.NoPermission ->
                        getString(R.string.settings_classroom_permission_denied)
                }
                prefs.lastClassroomSyncSummary = summary
                binding.tvClassroomSyncStatus.text = summary
            }
        }
    }

    private fun setupClassroomAutoSync() {
        val intervalHours = prefs.autoClassroomSyncIntervalHours
        binding.switchClassroomAutoSync.isChecked = prefs.autoClassroomSyncEnabled
        binding.tvAutoClassroomSyncInterval.text =
            getString(R.string.settings_auto_classroom_sync_interval, intervalHours)

        binding.switchClassroomAutoSync.setOnCheckedChangeListener { _, checked ->
            prefs.autoClassroomSyncEnabled = checked
            if (checked) scheduleClassroomSync() else cancelClassroomSync()
        }
    }

    private fun scheduleClassroomSync() {
        val request = androidx.work.PeriodicWorkRequestBuilder<ClassroomSyncWorker>(
            prefs.autoClassroomSyncIntervalHours.toLong(), TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        ).build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "classroom_auto_sync",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    private fun cancelClassroomSync() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork("classroom_auto_sync")
    }

    private fun updateClassroomSection() {
        val enabled = prefs.classroomSyncEnabled
        binding.cardClassroomAccount.visibility = if (enabled) View.VISIBLE else View.GONE
        binding.cardClassroomSync.visibility = View.GONE
        if (!enabled) return

        val email = GoogleAuthManager.getClassroomAccountEmail(requireContext())
        val hasAccount = email != null

        binding.tvClassroomAccountEmail.text = email
            ?: getString(R.string.settings_classroom_not_authorized)
        binding.btnClassroomAuth.text = if (hasAccount)
            getString(R.string.settings_classroom_sign_out)
        else
            getString(R.string.settings_classroom_authorize)

        if (hasAccount) {
            binding.cardClassroomSync.visibility = View.VISIBLE
            binding.tvClassroomSyncStatus.text = prefs.lastClassroomSyncSummary.ifEmpty {
                getString(R.string.settings_classroom_no_sync)
            }
        }
    }

    // ── 天氣通知 ─────────────────────────────────────────────────────────────

    private fun setupWeatherNotificationSection() {
        binding.switchWeatherNotif.isChecked = weatherPrefs.weatherNotifEnabled
        updateWeatherNotifSettings()

        binding.switchWeatherNotif.setOnCheckedChangeListener { _, checked ->
            weatherPrefs.weatherNotifEnabled = checked
            updateWeatherNotifSettings()
            WeatherNotificationScheduler.schedule(requireContext())
        }

        binding.rowWeatherNotifTime.setOnClickListener {
            showWeatherTimePicker()
        }

        binding.rowWeatherNotifLocation.setOnClickListener {
            showWeatherLocationPicker()
        }
    }

    private fun updateWeatherNotifSettings() {
        val enabled = weatherPrefs.weatherNotifEnabled
        binding.cardWeatherNotifSettings.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) return

        val h = weatherPrefs.weatherNotifHour
        val m = weatherPrefs.weatherNotifMinute
        binding.tvWeatherNotifTime.text = String.format("%02d:%02d", h, m)

        val loc = weatherPrefs.weatherNotifLocation
        binding.tvWeatherNotifLocation.text = loc.ifEmpty { getString(R.string.settings_weather_no_location) }
    }

    private fun showWeatherTimePicker() {
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                weatherPrefs.weatherNotifHour = hour
                weatherPrefs.weatherNotifMinute = minute
                updateWeatherNotifSettings()
                WeatherNotificationScheduler.schedule(requireContext())
            },
            weatherPrefs.weatherNotifHour,
            weatherPrefs.weatherNotifMinute,
            true
        ).show()
    }

    private fun showWeatherLocationPicker() {
        val saved = weatherPrefs.savedLocations
        if (saved.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.weather_no_locations), Toast.LENGTH_SHORT).show()
            return
        }
        val locations = saved.toTypedArray()
        val current = locations.indexOfFirst { it == weatherPrefs.weatherNotifLocation }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_weather_notif_location))
            .setSingleChoiceItems(locations, current) { dialog, which ->
                weatherPrefs.weatherNotifLocation = locations[which]
                updateWeatherNotifSettings()
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    // ── AI 通知解析 ─────────────────────────────────────────────────────────────

    // 防止 switch listener 遞迴觸發
    private var skipNotifSwitchListener = false

    private fun setupNotificationListenerSection() {
        binding.switchNotifListener.isChecked = prefs.notificationListenerAutoAdd
        updateAiNotifySettingsVisibility()

        binding.switchNotifListener.setOnCheckedChangeListener { _, checked ->
            if (skipNotifSwitchListener) return@setOnCheckedChangeListener
            // 立即還原 switch，等驗證成功再真正套用
            skipNotifSwitchListener = true
            binding.switchNotifListener.isChecked = !checked
            skipNotifSwitchListener = false

            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = {
                    skipNotifSwitchListener = true
                    binding.switchNotifListener.isChecked = checked
                    skipNotifSwitchListener = false
                    prefs.notificationListenerAutoAdd = checked
                    updateAiNotifySettingsVisibility()
                }
            )
        }

        binding.btnNotifListenerSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // 載入已存 key（預設遮蔽，需生物辨識才能顯示）
        val savedKey = prefs.geminiApiKey
        if (savedKey.isNotBlank()) {
            binding.etGeminiApiKey.setText(savedKey)
        }

        // 顯示/隱藏 API key 也需要生物辨識
        var apiKeyVisible = false
        binding.tilGeminiApiKey.setEndIconOnClickListener {
            if (apiKeyVisible) {
                // 隱藏不需要驗證
                apiKeyVisible = false
                binding.etGeminiApiKey.inputType =
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.tilGeminiApiKey.setEndIconDrawable(R.drawable.ic_visibility_off)
                binding.etGeminiApiKey.setSelection(binding.etGeminiApiKey.text?.length ?: 0)
            } else {
                BiometricHelper.authenticate(
                    fragment = this,
                    title = getString(R.string.biometric_title_monitor),
                    subtitle = getString(R.string.biometric_subtitle_apikey),
                    onSuccess = {
                        apiKeyVisible = true
                        binding.etGeminiApiKey.inputType =
                            android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        binding.tilGeminiApiKey.setEndIconDrawable(R.drawable.ic_visibility)
                        binding.etGeminiApiKey.setSelection(binding.etGeminiApiKey.text?.length ?: 0)
                    }
                )
            }
        }

        binding.btnSaveGeminiKey.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_apikey),
                onSuccess = {
                    val key = binding.etGeminiApiKey.text?.toString()?.trim() ?: ""
                    prefs.geminiApiKey = key
                    Toast.makeText(requireContext(), getString(R.string.settings_ai_gemini_key_saved), Toast.LENGTH_SHORT).show()
                }
            )
        }

        // CWA API Key
        val savedCwaKey = prefs.cwaApiKey
        if (savedCwaKey.isNotBlank()) binding.etCwaApiKey.setText(savedCwaKey)
        binding.btnSaveCwaApiKey.setOnClickListener {
            val key = binding.etCwaApiKey.text?.toString()?.trim() ?: ""
            prefs.cwaApiKey = key
            Toast.makeText(requireContext(), getString(R.string.settings_weather_cwa_key_saved), Toast.LENGTH_SHORT).show()
        }

        binding.btnPickMonitoredApps.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showAppPickerDialog() }
            )
        }
        updateMonitoredAppsSummary()

        binding.btnChannelFilter.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showChannelAppPickerDialog() }
            )
        }
        updateChannelFilterSummary()

        // 無時間事件的預設提醒時間
        updateDefaultRemindTimeSummary()
        binding.tvDefaultRemindTime.setOnClickListener {
            TimePickerDialog(
                requireContext(),
                { _, hour, minute ->
                    prefs.defaultRemindHour = hour
                    prefs.defaultRemindMinute = minute
                    updateDefaultRemindTimeSummary()
                },
                prefs.defaultRemindHour,
                prefs.defaultRemindMinute,
                true
            ).show()
        }
    }

    private fun updateDefaultRemindTimeSummary() {
        binding.tvDefaultRemindTime.text =
            "%02d:%02d".format(prefs.defaultRemindHour, prefs.defaultRemindMinute)
    }

    private fun updateMonitoredAppsSummary() {
        val selected = prefs.monitoredPackages
        binding.tvMonitoredApps.text = if (selected.isEmpty())
            getString(R.string.settings_ai_monitored_apps_all)
        else
            getString(R.string.settings_ai_monitored_apps_count, selected.size)
    }

    private fun showAppPickerDialog() {
        // 先顯示載入中，避免卡頓感
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…")
            .setCancelable(true)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val pm = requireContext().packageManager
            val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

            // 在 IO 執行緒做耗時的 PackageManager 查詢
            val allItems = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                pm.queryIntentActivities(launchIntent, 0)
                    .map { it.activityInfo.packageName }
                    .filter { it != requireContext().packageName }
                    .distinct()
                    .map { pkg ->
                        val label = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg }
                        label to pkg
                    }
                    .sortedBy { it.first }
            }

            if (!isAdded) return@launch
            loadingDialog.dismiss()

            val checkedPkgs = prefs.monitoredPackages.toMutableSet()
            val displayedItems = allItems.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<ListView>(R.id.listViewApps)

            val adapter = object : BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? CheckedTextView)
                        ?: (layoutInflater.inflate(android.R.layout.simple_list_item_multiple_choice, parent, false) as CheckedTextView)
                    val (label, pkg) = displayedItems[pos]
                    view.text = label
                    view.isChecked = checkedPkgs.contains(pkg)
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, pos, _ ->
                val pkg = displayedItems[pos].second
                if (!checkedPkgs.remove(pkg)) checkedPkgs.add(pkg)
                adapter.notifyDataSetChanged()
            }

            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(
                    if (query.isBlank()) allItems
                    else allItems.filter { it.first.lowercase().contains(query) }
                )
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_monitored_apps_dialog_title))
                .setView(dialogView)
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    prefs.monitoredPackages = checkedPkgs
                    updateMonitoredAppsSummary()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun updateChannelFilterSummary() {
        val configured = prefs.getMonitoredChannels().count { it.value.isNotEmpty() }
        binding.tvChannelFilter.text = if (configured == 0)
            getString(R.string.settings_ai_channel_filter_all)
        else
            getString(R.string.settings_ai_channel_filter_count, configured)
    }

    private fun showChannelAppPickerDialog() {
        val seen = prefs.getSeenChannels()
        if (seen.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.settings_ai_channel_no_seen), Toast.LENGTH_SHORT).show()
            return
        }
        val pm = requireContext().packageManager
        val monitored = prefs.getMonitoredChannels()
        val pkgs = seen.keys.sortedBy { pkg ->
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        }
        val labels = pkgs.map { pkg ->
            val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
            val count = monitored[pkg]?.size ?: 0
            if (count == 0) appLabel else "$appLabel（$count 個頻道）"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.settings_ai_channel_select_app))
            .setItems(labels) { _, which ->
                showChannelWhitelistDialog(pkgs[which], seen[pkgs[which]] ?: emptySet())
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showChannelWhitelistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentWhitelist = prefs.getMonitoredChannels()[pkg] ?: emptySet()
        val channels = seenChannels.sorted()
        val checkedItems = BooleanArray(channels.size) { currentWhitelist.contains(channels[it]) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(appLabel)
            .setMessage(getString(R.string.settings_ai_channel_filter_desc))
            .setMultiChoiceItems(channels.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val selected = channels.indices.filter { checkedItems[it] }.map { channels[it] }.toSet()
                val map = prefs.getMonitoredChannels().toMutableMap()
                if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                prefs.setMonitoredChannels(map)
                updateChannelFilterSummary()
            }
            .setNeutralButton(getString(R.string.settings_ai_channel_clear_btn)) { _, _ ->
                val map = prefs.getMonitoredChannels().toMutableMap()
                map.remove(pkg)
                prefs.setMonitoredChannels(map)
                updateChannelFilterSummary()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateAiNotifySettingsVisibility() {
        binding.cardAiNotifySettings.visibility =
            if (prefs.notificationListenerAutoAdd) View.VISIBLE else View.GONE
    }

    private fun updateNotifListenerStatus() {
        if (!prefs.notificationListenerAutoAdd) return
        val granted = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
        val grantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(com.rendy.classnote.R.color.chip_exam_color)
        binding.tvNotifListenerStatus.text = if (granted)
            getString(R.string.settings_ai_notify_access_granted)
        else
            getString(R.string.settings_ai_notify_access_denied)
        binding.tvNotifListenerStatus.setTextColor(if (granted) grantedColor else notGrantedColor)
    }

    // ── API 呼叫記錄 ──────────────────────────────────────────────────────────

    private fun setupApiLogSection() {
        binding.btnViewApiLogs.setOnClickListener { showApiLogs() }
    }

    private fun showApiLogs() {
        val app = requireActivity().application as ClassNoteApplication
        viewLifecycleOwner.lifecycleScope.launch {
            val logs = app.database.apiLogDao().getRecentLogs()
            val rv = androidx.recyclerview.widget.RecyclerView(requireContext()).apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                adapter = ApiLogAdapter(logs)
                setPadding(0, 8, 0, 8)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("API 呼叫記錄（最近 ${logs.size} 筆）")
                .setView(rv)
                .setNeutralButton("清除全部") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        app.database.apiLogDao().clearAll()
                        Toast.makeText(requireContext(), "已清除", Toast.LENGTH_SHORT).show()
                    }
                }
                .setPositiveButton("關閉", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
