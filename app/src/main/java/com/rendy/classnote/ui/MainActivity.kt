package com.rendy.classnote.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.R
import com.rendy.classnote.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 停用 edge-to-edge，讓系統 bar 正常佔用空間，不讓內容畫到 status bar 後面
        WindowCompat.setDecorFitsSystemWindows(window, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // 僅首次建立時請求權限（避免旋轉螢幕重複跳轉設定頁）
        if (savedInstanceState == null) {
            requestRequiredPermissions()
            handleNavigateIntent(intent)
        }
    }

    // app 已在後台時，通知點擊會觸發 onNewIntent 而非 onCreate
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNavigateIntent(intent)
    }

    private fun handleNavigateIntent(intent: Intent?) {
        if (intent?.getStringExtra("navigate_to") == "reminders") {
            // 透過 BottomNavigation 切換，而非直接 navController.navigate()，
            // 確保 NavigationUI 使用正確的 NavOptions (popUpTo / saveState / restoreState)，
            // 避免 top-level destination 被 push 到一般 back stack 而無法切換回課表頁
            binding.bottomNavigation.selectedItemId = R.id.reminderListFragment
        }
    }

    private fun requestRequiredPermissions() {
        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // SCHEDULE_EXACT_ALARM (Android 12+) — 需引導使用者至系統設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
        }

        // USE_FULL_SCREEN_INTENT (Android 14+) — 顯示全螢幕鬧鐘介面所需
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = NotificationManagerCompat.from(this)
            if (!nm.canUseFullScreenIntent()) {
                showFullScreenIntentDialog()
            }
        }

        // SYSTEM_ALERT_WINDOW — 顯示在其他 App 上方（鬧鐘效果增強）
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog()
        }

        // Xiaomi HyperOS / MIUI — 鎖屏顯示需額外手動開啟
        requestXiaomiLockScreenPermission()
    }

    private fun showFullScreenIntentDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("開啟全螢幕提醒")
            .setMessage("允許 ClassNote 在鎖屏或使用其他 App 時，以全螢幕方式顯示提醒（類似鬧鐘）。")
            .setPositiveButton("前往設定") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.fromParts("package", packageName, null)
                        }
                    )
                }
            }
            .setNegativeButton("略過", null)
            .show()
    }

    private fun showOverlayPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("顯示在其他 App 上方")
            .setMessage("允許 ClassNote 在使用其他 App 時也能顯示提醒視窗（類似鬧鐘）。\n\n前往設定 → 允許「顯示在其他應用程式上方」。")
            .setPositiveButton("前往設定") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            .setNegativeButton("略過", null)
            .show()
    }

    // ── Xiaomi HyperOS / MIUI 額外鎖屏權限引導 ──────────────────────────────

    private fun requestXiaomiLockScreenPermission() {
        if (!isXiaomiDevice()) return
        val prefs = getSharedPreferences("classnote_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("xiaomi_lockscreen_prompted", false)) return
        MaterialAlertDialogBuilder(this)
            .setTitle("小米 / HyperOS：開啟鎖屏顯示")
            .setMessage(
                "在小米 HyperOS / MIUI 上，需額外手動開啟「鎖定螢幕顯示」，" +
                "否則提醒視窗在鎖屏時無法彈出。\n\n" +
                "路徑：設定 → 應用程式 → 管理應用程式 → ClassNote\n" +
                "→「其他權限」→ 開啟「在鎖定螢幕上顯示」"
            )
            .setPositiveButton("前往 App 設定") { _, _ ->
                prefs.edit().putBoolean("xiaomi_lockscreen_prompted", true).apply()
                openXiaomiAppPermissions()
            }
            .setNegativeButton("略過") { _, _ ->
                prefs.edit().putBoolean("xiaomi_lockscreen_prompted", true).apply()
            }
            .show()
    }

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
        // 嘗試直接開啟 MIUI 的 App 權限編輯頁
        val miuiIntent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            setClassName(
                "com.miui.securitycenter",
                "com.miui.permcenter.permissions.PermissionsEditorActivity"
            )
            putExtra("extra_pkgname", packageName)
        }
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        try {
            startActivity(miuiIntent)
        } catch (_: Exception) {
            startActivity(fallbackIntent)
        }
    }
}
