package com.jayesh.satnav.features.offlinemaps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.model.OfflineMapLoadStatus
import com.jayesh.satnav.domain.repository.OfflineMapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the OfflineMapsScreen.
 */
@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    private val repository: OfflineMapRepository
) : ViewModel() {

    val uiState: StateFlow<OfflineMapsUiState> = repository.state
        .map { mapState ->
            when (mapState.loadStatus) {
                OfflineMapLoadStatus.Missing -> OfflineMapsUiState.Empty
                OfflineMapLoadStatus.Importing -> OfflineMapsUiState.Loading
                OfflineMapLoadStatus.Ready -> OfflineMapsUiState.Success(mapState)
                OfflineMapLoadStatus.Error -> OfflineMapsUiState.Error(
                    mapState.errorMessage ?: "Unknown error"
                )
                OfflineMapLoadStatus.Unsupported -> OfflineMapsUiState.Error(
                    "Unsupported map format"
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = OfflineMapsUiState.Loading
        )

    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
        }
    }

    fun deleteMap() {
        // TODO: Implement map deletion
        // This would require adding a delete method to the repository
    }
}