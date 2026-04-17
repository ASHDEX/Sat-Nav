package com.jayesh.satnav.features.savedplaces

import com.jayesh.satnav.domain.model.Place

/**
 * UI state for the SavedPlacesScreen.
 */
sealed interface SavedPlacesUiState {
    data object Loading : SavedPlacesUiState
    data class Success(val places: List<Place>) : SavedPlacesUiState
    data object Empty : SavedPlacesUiState
    data class Error(val message: String) : SavedPlacesUiState
}