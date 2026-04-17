package com.jayesh.satnav.features.savedplaces

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.domain.repository.SavedPlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the SavedPlacesScreen.
 */
@HiltViewModel
class SavedPlacesViewModel @Inject constructor(
    private val repository: SavedPlacesRepository
) : ViewModel() {

    val uiState: StateFlow<SavedPlacesUiState> = repository.getAll()
        .map { places ->
            when {
                places.isEmpty() -> SavedPlacesUiState.Empty
                else -> SavedPlacesUiState.Success(places)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SavedPlacesUiState.Loading
        )

    fun deletePlace(id: String) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }
}