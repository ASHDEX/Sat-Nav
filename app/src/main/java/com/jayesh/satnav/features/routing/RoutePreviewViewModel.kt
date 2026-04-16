package com.jayesh.satnav.features.routing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.routing.NoRouteException
import com.jayesh.satnav.domain.routing.RoutePlanner
import com.jayesh.satnav.domain.routing.RoutingException
import com.jayesh.satnav.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the route preview screen.
 */
sealed interface RoutePreviewUiState {
    data object Loading : RoutePreviewUiState
    data class Ready(
        val routes: List<RouteOption>,
        val destinationName: String,
    ) : RoutePreviewUiState
    data class Error(val message: String) : RoutePreviewUiState
}

/**
 * ViewModel for the Google‑Maps‑style trip planning flow.
 *
 * Takes a destination from navigation arguments, uses current GPS location as start,
 * plans 2‑3 genuinely different routes, and lets the user pick one.
 */
@HiltViewModel
class RoutePreviewViewModel @Inject constructor(
    private val planner: RoutePlanner,
    private val locationRepo: LocationRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_ROUTE_ID = "selected_route_id"
        private const val KEY_DESTINATION_LAT = "destination_lat"
        private const val KEY_DESTINATION_LNG = "destination_lng"
        private const val KEY_DESTINATION_NAME = "destination_name"
    }

    private val destination: LatLng = LatLng(
        latitude = savedStateHandle.get<Double>(KEY_DESTINATION_LAT) ?: 0.0,
        longitude = savedStateHandle.get<Double>(KEY_DESTINATION_LNG) ?: 0.0,
    )
    private val destinationName: String = savedStateHandle.get<String>(KEY_DESTINATION_NAME) ?: "Destination"

    private val _selectedRouteId = MutableStateFlow(
        savedStateHandle.get<Int>(KEY_SELECTED_ROUTE_ID) ?: 0
    )
    val selectedRouteId: StateFlow<Int> = _selectedRouteId.asStateFlow()

    val uiState: StateFlow<RoutePreviewUiState> = flow<RoutePreviewUiState> {
        emit(RoutePreviewUiState.Loading)

        val location = locationRepo.lastKnown()
        if (location == null) {
            emit(RoutePreviewUiState.Error("No GPS fix available"))
            return@flow
        }

        val start = LatLng(location.latitude, location.longitude)

        val result = planner.plan(start, destination)
        if (result.isSuccess) {
            val routes = result.getOrNull() ?: emptyList()
            if (routes.isEmpty()) {
                emit(RoutePreviewUiState.Error("No route found"))
            } else {
                emit(RoutePreviewUiState.Ready(routes, destinationName))
            }
        } else {
            val error = result.exceptionOrNull()
            val message = when (error) {
                is NoRouteException -> "No route found between the points"
                is RoutingException -> "Routing failed: ${error.message}"
                else -> error?.message ?: "Unknown error"
            }
            emit(RoutePreviewUiState.Error(message))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RoutePreviewUiState.Loading,
    )

    /**
     * Select a route by its ID.
     *
     * The selection is persisted in SavedStateHandle to survive orientation changes.
     */
    fun selectRoute(id: Int) {
        _selectedRouteId.value = id
        savedStateHandle[KEY_SELECTED_ROUTE_ID] = id
    }

    /**
     * Returns the currently selected route, if any.
     */
    fun getSelectedRoute(routes: List<RouteOption>): RouteOption? {
        val id = _selectedRouteId.value
        return routes.firstOrNull { it.id == id }
    }
}