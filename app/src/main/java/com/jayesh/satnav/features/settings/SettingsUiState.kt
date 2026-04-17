package com.jayesh.satnav.features.settings

/**
 * UI state for the SettingsScreen.
 */
data class SettingsUiState(
    val useFakeLocation: Boolean = false,
    val darkMode: Boolean = false,
    val voiceGuidance: Boolean = true,
    val trafficUpdates: Boolean = true,
    val saveTripHistory: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)