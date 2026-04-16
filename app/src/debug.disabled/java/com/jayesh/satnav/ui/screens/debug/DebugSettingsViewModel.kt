package com.jayesh.satnav.ui.screens.debug

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.data.location.FakeLocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

private val USE_GPX_REPLAY_KEY = booleanPreferencesKey("debug_use_gpx_replay")
private val SELECTED_GPX_FILE_KEY = stringPreferencesKey("debug_selected_gpx_file")
private val SPEED_MULTIPLIER_KEY = floatPreferencesKey("debug_speed_multiplier")

@HiltViewModel
class DebugSettingsViewModel @Inject constructor(
    private val fakeLocationRepository: FakeLocationRepository,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugSettingsUiState())
    val uiState: StateFlow<DebugSettingsUiState> = _uiState.asStateFlow()

    init {
        observeLocationUpdates()
    }

    fun loadAvailableGpxFiles() {
        viewModelScope.launch {
            val files = fakeLocationRepository.getAvailableGpxFiles()
            _uiState.value = _uiState.value.copy(availableGpxFiles = files)
        }
    }

    fun loadDebugSettings() {
        viewModelScope.launch {
            val useGpxReplay = dataStore.data.first()[USE_GPX_REPLAY_KEY] ?: false
            val selectedFile = dataStore.data.first()[SELECTED_GPX_FILE_KEY] ?: ""
            val speedMultiplier = dataStore.data.first()[SPEED_MULTIPLIER_KEY] ?: 1.0f
            
            _uiState.value = _uiState.value.copy(
                useGpxReplay = useGpxReplay,
                selectedGpxFile = selectedFile,
                speedMultiplier = speedMultiplier
            )
        }
    }

    fun setUseGpxReplay(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[USE_GPX_REPLAY_KEY] = enabled
            }
            _uiState.value = _uiState.value.copy(useGpxReplay = enabled)
        }
    }

    fun loadGpxFile(filename: String) {
        viewModelScope.launch {
            fakeLocationRepository.loadGpxFile(filename)
            dataStore.edit { preferences ->
                preferences[SELECTED_GPX_FILE_KEY] = filename
            }
            _uiState.value = _uiState.value.copy(selectedGpxFile = filename)
        }
    }

    fun setSpeedMultiplier(multiplier: Float) {
        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[SPEED_MULTIPLIER_KEY] = multiplier
            }
            _uiState.value = _uiState.value.copy(speedMultiplier = multiplier)
        }
    }

    fun startReplay(speedMultiplier: Float) {
        viewModelScope.launch {
            fakeLocationRepository.startReplay(speedMultiplier)
            _uiState.value = _uiState.value.copy(isReplaying = true)
        }
    }

    fun stopReplay() {
        viewModelScope.launch {
            fakeLocationRepository.stopReplay()
            _uiState.value = _uiState.value.copy(isReplaying = false)
        }
    }

    fun jumpToStart() {
        viewModelScope.launch {
            fakeLocationRepository.jumpToStart()
        }
    }

    private fun observeLocationUpdates() {
        viewModelScope.launch {
            fakeLocationRepository.currentLocation.collect { location ->
                _uiState.value = _uiState.value.copy(currentLocation = location)
            }
        }
    }
}

data class DebugSettingsUiState(
    val useGpxReplay: Boolean = false,
    val availableGpxFiles: List<String> = emptyList(),
    val selectedGpxFile: String = "",
    val speedMultiplier: Float = 1.0f,
    val isReplaying: Boolean = false,
    val currentLocation: Location? = null
)