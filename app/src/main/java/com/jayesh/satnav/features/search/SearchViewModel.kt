package com.jayesh.satnav.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.data.local.search.PoiLocalDataSource
import com.jayesh.satnav.features.location.GpsLocationManager
import com.jayesh.satnav.domain.model.PointOfInterest
import com.jayesh.satnav.domain.model.PoiCategory
import com.jayesh.satnav.domain.model.PoiSearchQuery
import com.jayesh.satnav.domain.model.RecentSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<PointOfInterest> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val autocompleteSuggestions: List<String> = emptyList(),
    val selectedPoi: PointOfInterest? = null,
    val showFavorites: Boolean = false,
    val selectedCategory: PoiCategory? = null,
    val useLocationSearch: Boolean = false,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val hasMoreResults: Boolean = false,
    val currentPage: Int = 0,
    val currentLocation: Pair<Double, Double>? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val poiLocalDataSource: PoiLocalDataSource,
    private val gpsLocationManager: GpsLocationManager,
    private val appDispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        _uiState
            .map { it.query }
            .distinctUntilChanged()
            .debounce(250)
            .onEach { query ->
                val suggestions = if (query.length >= 2) {
                    runCatching { poiLocalDataSource.getAutocompleteSuggestions(query) }
                        .getOrDefault(emptyList())
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(autocompleteSuggestions = suggestions) }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch(appDispatchers.io) {
            poiLocalDataSource.initialize()
            refresh()
        }

        // Collect GPS location so nearby search and distance display work
        if (gpsLocationManager.isPermissionGranted()) {
            viewModelScope.launch {
                gpsLocationManager.locationUpdates.collect { location ->
                    updateCurrentLocation(location.latitude, location.longitude)
                }
            }
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                query = "",
                searchResults = emptyList(),
                autocompleteSuggestions = emptyList(),
                selectedPoi = null,
                errorMessage = null,
                currentPage = 0,
                hasMoreResults = false,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                selectedPoi = null,
                errorMessage = null,
            )
        }
    }

    fun performSearch(resetPagination: Boolean = false) {
        val state = _uiState.value
        if (state.query.isBlank() && state.selectedCategory == null) return

        viewModelScope.launch(appDispatchers.io) {
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }

            try {
                val query = PoiSearchQuery(
                    query = state.query,
                    category = state.selectedCategory,
                    centerLatitude = state.currentLocation?.first,
                    centerLongitude = state.currentLocation?.second,
                    radiusMeters = if (state.useLocationSearch) 5000.0 else null,
                    limit = 50,
                    offset = if (resetPagination) 0 else state.currentPage * 50,
                    sortBy = if (state.currentLocation != null) {
                        PoiSearchQuery.SortBy.DISTANCE
                    } else {
                        PoiSearchQuery.SortBy.RELEVANCE
                    },
                )

                val result = poiLocalDataSource.searchPois(query)

                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = if (resetPagination) {
                            result.pois
                        } else {
                            it.searchResults + result.pois
                        },
                        hasMoreResults = result.hasMore,
                        currentPage = if (resetPagination) 1 else it.currentPage + 1,
                    )
                }

                if (state.query.isNotBlank()) {
                    poiLocalDataSource.addRecentSearch(
                        query = state.query,
                        latitude = state.currentLocation?.first,
                        longitude = state.currentLocation?.second,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = e.message ?: "Search failed.",
                    )
                }
            }
        }
    }

    fun selectRecentSearch(recentSearch: RecentSearch) {
        _uiState.update {
            it.copy(
                query = recentSearch.query,
                errorMessage = null,
            )
        }
        performSearch(resetPagination = true)
    }

    fun clearRecentSearches() {
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    fun selectAutocompleteSuggestion(suggestion: String) {
        _uiState.update {
            it.copy(
                query = suggestion,
                autocompleteSuggestions = emptyList(),
                errorMessage = null,
            )
        }
        performSearch(resetPagination = true)
    }

    fun selectPoi(poi: PointOfInterest) {
        _uiState.update { it.copy(selectedPoi = poi) }
    }

    fun toggleFavorite(poi: PointOfInterest) {
        viewModelScope.launch(appDispatchers.io) {
            if (poi.isFavorite) {
                poiLocalDataSource.removeFromFavorites(poi.id)
            } else {
                poiLocalDataSource.addToFavorites(poi.id)
            }
        }
    }

    fun loadMoreResults() {
        val state = _uiState.value
        if (state.hasMoreResults && !state.isSearching) {
            performSearch(resetPagination = false)
        }
    }

    fun selectCategory(category: PoiCategory?) {
        _uiState.update {
            it.copy(
                selectedCategory = category,
                currentPage = 0,
            )
        }
        if (category != null) {
            performSearch(resetPagination = true)
        }
    }

    fun toggleLocationSearch() {
        _uiState.update { it.copy(useLocationSearch = !it.useLocationSearch) }
    }

    fun closePoiDetails() {
        _uiState.update { it.copy(selectedPoi = null) }
    }

    fun toggleFavorites() {
        _uiState.update { it.copy(showFavorites = !it.showFavorites) }
    }

    fun importPoisFromFile(filePath: String) {
        // Reserved for future POI import support.
    }

    fun refresh() {
        viewModelScope.launch(appDispatchers.io) {
            val recent = runCatching { poiLocalDataSource.getRecentSearches() }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(recentSearches = recent) }
        }
    }

    fun updateCurrentLocation(latitude: Double, longitude: Double) {
        _uiState.update { it.copy(currentLocation = latitude to longitude) }
    }
}
