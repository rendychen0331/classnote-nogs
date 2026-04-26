package com.rendy.classnote.ui.weather

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rendy.classnote.R
import com.rendy.classnote.data.WeatherPreferences
import com.rendy.classnote.data.remote.ForecastItem
import com.rendy.classnote.data.remote.WeatherApi
import com.rendy.classnote.databinding.FragmentWeatherBinding
import kotlinx.coroutines.launch

class WeatherFragment : Fragment() {

    private var _binding: FragmentWeatherBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WeatherViewModel by viewModels()
    private val adapter = ForecastAdapter()
    private lateinit var weatherPrefs: WeatherPreferences

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWeatherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        weatherPrefs = WeatherPreferences(requireContext())

        setupRecyclerView()
        observeUiState()

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadWeather()
        }

        binding.btnAddLocation.setOnClickListener {
            showAddLocationDialog()
        }

        rebuildChips()
        loadCurrentLocation()
    }

    // ── Chip 管理 ────────────────────────────────────────────────────────────

    private fun rebuildChips() {
        binding.chipGroupLocations.removeAllViews()
        val saved = weatherPrefs.savedLocations
        saved.forEachIndexed { index, name ->
            addChip(name, isChecked = index == 0)
        }
    }

    private fun addChip(name: String, isChecked: Boolean = false) {
        val chip = Chip(requireContext()).apply {
            text = name
            isCheckable = true
            this.isChecked = isChecked
            isCloseIconVisible = true
            setOnClickListener {
                if (this.isChecked) viewModel.loadWeather(name)
            }
            setOnCloseIconClickListener {
                removeLocation(name)
            }
        }
        binding.chipGroupLocations.addView(chip)
    }

    private fun removeLocation(name: String) {
        val updated = weatherPrefs.savedLocations.toMutableList()
        updated.remove(name)
        weatherPrefs.savedLocations = updated

        rebuildChips()
        loadCurrentLocation()
    }

    private fun loadCurrentLocation() {
        val saved = weatherPrefs.savedLocations
        if (saved.isEmpty()) {
            showNoLocations()
        } else {
            // 選中第一個 chip
            (binding.chipGroupLocations.getChildAt(0) as? Chip)?.isChecked = true
            viewModel.loadWeather(saved.first())
        }
    }

    // ── 新增地區對話框 ────────────────────────────────────────────────────────

    private fun showAddLocationDialog() {
        val savedLocs = weatherPrefs.savedLocations
        val allNames = WeatherApi.LOCATIONS.map { it.displayName }
        val available = allNames.toSet()

        // 第一層：選縣市
        val counties = WeatherApi.COUNTY_NAMES.toMutableList()
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_location_picker, null)
        val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLocationSearch)
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.listViewLocations)

        val filtered = counties.toMutableList()
        val listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, filtered)
        listView.adapter = listAdapter

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.weather_add_location))
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .show()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                filtered.clear()
                filtered.addAll(if (q.isEmpty()) counties else counties.filter { it.contains(q) })
                listAdapter.notifyDataSetChanged()
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            showDistrictDialog(filtered[position], savedLocs)
        }
    }

    private fun showDistrictDialog(county: String, savedLocs: List<String>) {
        val districts = WeatherApi.districtsOf(county).map { it.displayName }
        // 「整個 XXX」選項放最前面
        val options = mutableListOf(county) + districts
        val available = options.filter { it !in savedLocs }

        if (available.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.weather_all_added), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_location_picker, null)
        val etSearch = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etLocationSearch)
        val listView = dialogView.findViewById<android.widget.ListView>(R.id.listViewLocations)

        val filtered = available.toMutableList()
        val listAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, filtered)
        listView.adapter = listAdapter

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(county)
            .setView(dialogView)
            .setNegativeButton(getString(R.string.cancel), null)
            .setNeutralButton(getString(R.string.back)) { _, _ -> showAddLocationDialog() }
            .show()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim() ?: ""
                filtered.clear()
                filtered.addAll(if (q.isEmpty()) available else available.filter { it.contains(q) })
                listAdapter.notifyDataSetChanged()
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val selected = filtered[position]
            val updated = weatherPrefs.savedLocations.toMutableList()
            updated.add(selected)
            weatherPrefs.savedLocations = updated

            val isFirst = binding.chipGroupLocations.childCount == 0
            addChip(selected, isChecked = isFirst)

            if (isFirst) {
                binding.tvNoLocations.visibility = View.GONE
                viewModel.loadWeather(selected)
            }

            dialog.dismiss()
        }
    }

    // ── RecyclerView ─────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        binding.recyclerForecast.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerForecast.adapter = adapter
    }

    // ── 狀態觀察 ─────────────────────────────────────────────────────────────

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.swipeRefreshLayout.isRefreshing = false
                    when (state) {
                        is WeatherUiState.Loading -> {
                            if (weatherPrefs.savedLocations.isEmpty()) showNoLocations()
                            else showLoading()
                        }
                        is WeatherUiState.Success -> showSuccess(state.forecasts)
                        is WeatherUiState.Error -> showError(state.message)
                    }
                }
            }
        }
    }

    private fun showNoLocations() {
        binding.progressBar.visibility = View.GONE
        binding.cardCurrent.visibility = View.GONE
        binding.tvForecastTitle.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvNoLocations.visibility = View.VISIBLE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.cardCurrent.visibility = View.GONE
        binding.tvForecastTitle.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvNoLocations.visibility = View.GONE
    }

    private fun showSuccess(forecasts: List<ForecastItem>) {
        binding.progressBar.visibility = View.GONE
        binding.tvError.visibility = View.GONE
        binding.tvNoLocations.visibility = View.GONE

        if (forecasts.isEmpty()) {
            binding.cardCurrent.visibility = View.GONE
            binding.tvForecastTitle.visibility = View.GONE
            binding.tvError.text = "無天氣資料"
            binding.tvError.visibility = View.VISIBLE
            return
        }

        val first = forecasts.first()
        binding.cardCurrent.visibility = View.VISIBLE
        binding.tvLocationName.text = getSelectedLocationName()
        binding.tvTemperature.text = "${first.tempMin}°C"
        binding.tvWeatherDesc.text = first.description
        binding.tvHumidity.text = ""
        binding.tvRainProb.text = "${first.rainProb}%"

        binding.tvForecastTitle.visibility = View.VISIBLE
        adapter.submitList(forecasts.drop(1))
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.cardCurrent.visibility = View.GONE
        binding.tvForecastTitle.visibility = View.GONE
        binding.tvNoLocations.visibility = View.GONE
        binding.tvError.text = "載入失敗：$message"
        binding.tvError.visibility = View.VISIBLE
    }

    private fun getSelectedLocationName(): String {
        for (i in 0 until binding.chipGroupLocations.childCount) {
            val chip = binding.chipGroupLocations.getChildAt(i) as? Chip
            if (chip?.isChecked == true) return chip.text.toString()
        }
        return weatherPrefs.savedLocations.firstOrNull() ?: ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
