package com.rendy.classnote.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.ClassNoteApplication
import com.rendy.classnote.R
import com.rendy.classnote.data.AppPreferences
import com.rendy.classnote.databinding.SheetNotifListenerBinding
import com.rendy.classnote.ui.BiometricHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotifListenerSheet : Fragment() {

    private var _binding: SheetNotifListenerBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private var skipNotifSwitchListener = false
    private var cachedAppList: List<Pair<String, String>>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = SheetNotifListenerBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = (requireActivity().application as ClassNoteApplication).appPreferences
        setupNotificationListenerSection()
    }

    override fun onResume() {
        super.onResume()
        updateNotifListenerStatus()
    }

    private fun setupNotificationListenerSection() {
        binding.switchNotifListener.isChecked = prefs.notificationListenerAutoAdd
        updateAiNotifySettingsVisibility()

        binding.switchNotifListener.setOnCheckedChangeListener { _, checked ->
            if (skipNotifSwitchListener) return@setOnCheckedChangeListener
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

        binding.btnChannelBlacklist.setOnClickListener {
            BiometricHelper.authenticate(
                fragment = this,
                title = getString(R.string.biometric_title_monitor),
                subtitle = getString(R.string.biometric_subtitle_monitor),
                onSuccess = { showChannelBlacklistAppPickerDialog() }
            )
        }
        updateChannelBlacklistSummary()

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

    private fun updateAiNotifySettingsVisibility() {
        binding.cardAiNotifySettings.visibility =
            if (prefs.notificationListenerAutoAdd) View.VISIBLE else View.GONE
    }

    private fun updateNotifListenerStatus() {
        if (!prefs.notificationListenerAutoAdd) return
        val granted = NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
        val grantedColor = requireContext().getColor(R.color.chip_reminder_color)
        val notGrantedColor = requireContext().getColor(R.color.chip_exam_color)
        binding.tvNotifListenerStatus.text = if (granted)
            getString(R.string.settings_ai_notify_access_granted)
        else
            getString(R.string.settings_ai_notify_access_denied)
        binding.tvNotifListenerStatus.setTextColor(if (granted) grantedColor else notGrantedColor)
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

    private suspend fun loadAppList(): List<Pair<String, String>> {
        cachedAppList?.let { return it }
        val pm = requireContext().packageManager
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return withContext(Dispatchers.IO) {
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
        }.also { cachedAppList = it }
    }

    private fun showAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…")
            .setCancelable(true)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()

            if (!isAdded) return@launch
            loadingDialog?.dismiss()

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

    private fun updateChannelBlacklistSummary() {
        val configured = prefs.getBlacklistedChannels().count { it.value.isNotEmpty() }
        binding.tvChannelBlacklist.text = if (configured == 0)
            getString(R.string.settings_ai_channel_blacklist_none)
        else
            getString(R.string.settings_ai_channel_blacklist_count, configured)
    }

    private fun showChannelBlacklistAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…")
            .setCancelable(true)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()
            if (!isAdded) return@launch
            loadingDialog?.dismiss()

            val blacklisted = prefs.getBlacklistedChannels()
            val seen = prefs.getSeenChannels()

            val allLabeled = allItems.map { (label, pkg) ->
                val blacklistCount = blacklisted[pkg]?.size ?: 0
                val seenCount = seen[pkg]?.size ?: 0
                val suffix = when {
                    blacklistCount > 0 -> "（已封鎖 $blacklistCount 個頻道）"
                    seenCount > 0      -> "（$seenCount 個頻道可選）"
                    else               -> ""
                }
                "$label$suffix" to pkg
            }.toMutableList()

            val displayedItems = allLabeled.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<android.widget.ListView>(R.id.listViewApps)

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? android.widget.TextView)
                        ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
                    view.text = displayedItems[pos].first
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, pos, _ ->
                val pkg = displayedItems[pos].second
                showChannelBlacklistDialog(pkg, seen[pkg] ?: emptySet())
            }

            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(
                    if (query.isBlank()) allLabeled
                    else allLabeled.filter { it.first.lowercase().contains(query) }
                )
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_channel_blacklist_select_app))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChannelBlacklistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentBlacklist = prefs.getBlacklistedChannels()[pkg] ?: emptySet()
        val channels = (seenChannels + currentBlacklist).sorted().toMutableList()
        val checkedItems = channels.map { currentBlacklist.contains(it) }.toBooleanArray()

        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle("封鎖頻道 - $appLabel")
        if (channels.isEmpty()) {
            builder.setMessage("尚無頻道紀錄，先讓 App 發送通知後再設定")
            builder.setNegativeButton(getString(R.string.cancel), null).show()
        } else {
            builder.setMultiChoiceItems(channels.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            builder
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    val selected = channels.indices.filter { checkedItems[it] }.map { channels[it] }.toSet()
                    val map = prefs.getBlacklistedChannels().toMutableMap()
                    if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                    prefs.setBlacklistedChannels(map)
                    updateChannelBlacklistSummary()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChannelAppPickerDialog() {
        val loadingDialog = cachedAppList?.let { null } ?: MaterialAlertDialogBuilder(requireContext())
            .setMessage("載入 App 清單…")
            .setCancelable(true)
            .show()

        viewLifecycleOwner.lifecycleScope.launch {
            val allItems = loadAppList()

            if (!isAdded) return@launch
            loadingDialog?.dismiss()

            val seen = prefs.getSeenChannels()
            val monitored = prefs.getMonitoredChannels()

            val allLabeled = allItems.map { (label, pkg) ->
                val channelCount = monitored[pkg]?.size ?: 0
                val seenCount = seen[pkg]?.size ?: 0
                val suffix = when {
                    channelCount > 0 -> "（已篩選 $channelCount 個頻道）"
                    seenCount > 0    -> "（$seenCount 個頻道可選）"
                    else             -> ""
                }
                "$label$suffix" to pkg
            }.toMutableList()

            val displayedItems = allLabeled.toMutableList()

            val dialogView = layoutInflater.inflate(R.layout.dialog_app_picker, null)
            val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etAppSearch)
            val listView = dialogView.findViewById<ListView>(R.id.listViewApps)

            val adapter = object : BaseAdapter() {
                override fun getCount() = displayedItems.size
                override fun getItem(pos: Int) = displayedItems[pos]
                override fun getItemId(pos: Int) = pos.toLong()
                override fun getView(pos: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val view = (convertView as? android.widget.TextView)
                        ?: layoutInflater.inflate(android.R.layout.simple_list_item_1, parent, false) as android.widget.TextView
                    view.text = displayedItems[pos].first
                    return view
                }
            }
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, pos, _ ->
                val pkg = displayedItems[pos].second
                showChannelWhitelistDialog(pkg, seen[pkg] ?: emptySet())
            }

            etSearch.doAfterTextChanged { text ->
                val query = text?.toString()?.trim()?.lowercase() ?: ""
                displayedItems.clear()
                displayedItems.addAll(
                    if (query.isBlank()) allLabeled
                    else allLabeled.filter { it.first.lowercase().contains(query) }
                )
                adapter.notifyDataSetChanged()
            }

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.settings_ai_channel_select_app))
                .setView(dialogView)
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun showChannelWhitelistDialog(pkg: String, seenChannels: Set<String>) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        val currentWhitelist = prefs.getMonitoredChannels()[pkg] ?: emptySet()
        // 已在白名單的頻道也要顯示，避免無從編輯
        val channels = (seenChannels + currentWhitelist).sorted().toMutableList()
        val checkedItems = channels.map { currentWhitelist.contains(it) }.toBooleanArray()

        fun buildAndShow(channelList: MutableList<String>, checked: BooleanArray) {
            val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(appLabel)
            if (channelList.isEmpty()) {
                // 無記錄時只顯示訊息，不設 list（兩者衝突）
                builder.setMessage("尚無頻道紀錄，可點「手動新增」加入頻道名稱")
                builder
                    .setPositiveButton(getString(R.string.save), null)
                    .setNeutralButton("手動新增") { _, _ ->
                        showManualAddChannelDialog(pkg, channelList, checked)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                builder.setMultiChoiceItems(channelList.toTypedArray(), checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                builder
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        val selected = channelList.indices.filter { checked[it] }.map { channelList[it] }.toSet()
                        val map = prefs.getMonitoredChannels().toMutableMap()
                        if (selected.isEmpty()) map.remove(pkg) else map[pkg] = selected
                        prefs.setMonitoredChannels(map)
                        updateChannelFilterSummary()
                    }
                    .setNeutralButton("手動新增") { _, _ ->
                        showManualAddChannelDialog(pkg, channelList, checked)
                    }
                    .setNegativeButton("清除紀錄") { _, _ ->
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("清除頻道紀錄")
                            .setMessage("清除「$appLabel」的所有已記錄頻道？\n已加入白名單的頻道不受影響。")
                            .setPositiveButton("清除") { _, _ ->
                                prefs.clearSeenChannels(pkg)
                                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: emptySet())
                            }
                            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channelList.toSet())
                            }
                            .show()
                    }
                    .show()
            }
        }

        buildAndShow(channels, checkedItems)
    }

    private fun showManualAddChannelDialog(pkg: String, channels: MutableList<String>, checked: BooleanArray) {
        val pm = requireContext().packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }

        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = "頻道名稱（如：群組名稱、通知標題）"
            setPadding(48, 24, 48, 8)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("手動新增頻道 - $appLabel")
            .setView(input)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                if (name.isBlank()) return@setPositiveButton
                if (!channels.contains(name)) {
                    prefs.addSeenChannel(pkg, name)
                    val map = prefs.getMonitoredChannels().toMutableMap()
                    map[pkg] = (map[pkg] ?: emptySet()) + name
                    prefs.setMonitoredChannels(map)
                    updateChannelFilterSummary()
                    showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: setOf(name))
                } else {
                    Toast.makeText(requireContext(), "頻道已存在", Toast.LENGTH_SHORT).show()
                    showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channels.toSet())
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                showChannelWhitelistDialog(pkg, prefs.getSeenChannels()[pkg] ?: channels.toSet())
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "NotifListenerSheet"
    }
}
