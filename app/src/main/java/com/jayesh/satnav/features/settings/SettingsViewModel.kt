package com.jayesh.satnav.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the SettingsScreen.
 * Uses in-memory storage for now; should be replaced with DataStore in production.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun toggleFakeLocation(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(useFakeLocation = enabled)
        // TODO: If FakeLocationRepository is re-enabled, toggle it here
    }

    fun toggleDarkMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(darkMode = enabled)
        // TODO: Apply theme change
    }

    fun toggleVoiceGuidance(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(voiceGuidance = enabled)
        // TODO: Update voice guidance setting
    }

    fun toggleTrafficUpdates(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(trafficUpdates = enabled)
        // TODO: Update traffic updates setting
    }

    fun toggleSaveTripHistory(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(saveTripHistory = enabled)
        // TODO: Update trip history setting
    }

    fun saveSettings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // TODO: Persist settings to DataStore
                // Simulate network/disk operation
                kotlinx.coroutines.delay(500)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to save settings: ${e.message}"
                )
            }
        }
    }
}