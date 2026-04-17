package com.jayesh.satnav.features.offlinemaps

import com.jayesh.satnav.domain.model.OfflineMapPackage
import com.jayesh.satnav.domain.model.OfflineMapState

/**
 * UI state for the OfflineMapsScreen.
 */
sealed interface OfflineMapsUiState {
    data object Loading : OfflineMapsUiState
    data class Success(val mapState: OfflineMapState) : OfflineMapsUiState
    data object Empty : OfflineMapsUiState
    data class Error(val message: String) : OfflineMapsUiState
}