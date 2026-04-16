package com.jayesh.satnav.features.routing

import com.jayesh.satnav.domain.model.RouteOption
import com.jayesh.satnav.domain.model.TripLeg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates trip data between screens.
 * Supports both single-destination routing (existing flow) and multi-leg trips (new feature).
 *
 * Single-destination flow:
 * - RoutePreviewScreen calls `store(route)` and gets a routeId
 * - NavigationScreen retrieves the route via `take(routeId)`
 *
 * Multi-leg flow:
 * - TripCreatorScreen calls `setMultiLegTrip(legs)`
 * - NavigationScreen uses multi-leg methods to navigate through legs
 */
@Singleton
class TripCoordinator @Inject constructor() {
    
    // Single-route storage (existing flow)
    private val storedRoutes = mutableMapOf<Int, RouteOption>()
    private var nextRouteId = 1
    
    // Multi-leg trip state
    private val _legs = MutableStateFlow<List<TripLeg>?>(null)
    private val _currentLegIndex = MutableStateFlow<Int?>(null)
    
    /**
     * Store a single route for later retrieval (existing flow).
     * @return routeId that can be passed to NavigationScreen
     */
    fun store(route: RouteOption): Int {
        val routeId = nextRouteId++
        storedRoutes[routeId] = route
        return routeId
    }
    
    /**
     * Take a stored route by ID (existing flow).
     * Removes the route from storage after retrieval.
     */
    fun take(routeId: Int): RouteOption? {
        return storedRoutes.remove(routeId)
    }
    
    /**
     * Check if a route with the given ID exists.
     */
    fun hasRoute(routeId: Int): Boolean = storedRoutes.containsKey(routeId)
    
    /**
     * Clear all stored single routes.
     */
    fun clearStoredRoutes() {
        storedRoutes.clear()
    }
    
    // Multi-leg trip methods
    
    /**
     * Set a multi-leg trip for navigation.
     * Resets current leg index to 0 (first leg).
     */
    fun setMultiLegTrip(legs: List<TripLeg>) {
        require(legs.isNotEmpty()) { "Multi-leg trip must have at least one leg" }
        _legs.value = legs
        _currentLegIndex.value = 0
    }
    
    /**
     * Get the current leg being navigated, or null if not in multi-leg mode.
     */
    fun currentLeg(): TripLeg? {
        val legs = _legs.value
        val index = _currentLegIndex.value
        return if (legs != null && index != null && index in legs.indices) {
            legs[index]
        } else {
            null
        }
    }
    
    /**
     * Advance to the next leg in the trip.
     * @return The new current leg, or null if already on the last leg
     */
    fun advanceToNextLeg(): TripLeg? {
        val legs = _legs.value ?: return null
        val currentIndex = _currentLegIndex.value ?: return null
        
        if (currentIndex >= legs.lastIndex) {
            return null // Already on last leg
        }
        
        val nextIndex = currentIndex + 1
        _currentLegIndex.value = nextIndex
        return legs[nextIndex]
    }
    
    /**
     * Check if the current leg is the last leg in the trip.
     */
    fun isLastLeg(): Boolean {
        val legs = _legs.value ?: return true // If no legs, treat as "last"
        val index = _currentLegIndex.value ?: return true
        return index >= legs.lastIndex
    }
    
    /**
     * Check if we're in multi-leg mode.
     */
    fun isMultiLeg(): Boolean = _legs.value != null
    
    /**
     * Get the current leg index (0-based), or null if not in multi-leg mode.
     */
    fun currentLegIndex(): Int? = _currentLegIndex.value
    
    /**
     * Get the total number of legs in the trip, or 0 if not in multi-leg mode.
     */
    fun totalLegs(): Int = _legs.value?.size ?: 0
    
    /**
     * Get all legs in the trip, or empty list if not in multi-leg mode.
     */
    fun allLegs(): List<TripLeg> = _legs.value ?: emptyList()
    
    /**
     * Get the leg at the specified index.
     */
    fun getLeg(index: Int): TripLeg? = _legs.value?.getOrNull(index)
    
    /**
     * Jump to a specific leg index.
     * @return true if the index is valid and jump was successful
     */
    fun jumpToLeg(index: Int): Boolean {
        val legs = _legs.value ?: return false
        if (index in legs.indices) {
            _currentLegIndex.value = index
            return true
        }
        return false
    }
    
    /**
     * Clear the current multi-leg trip.
     * Also clears any stored single routes.
     */
    fun clearTrip() {
        _legs.value = null
        _currentLegIndex.value = null
        clearStoredRoutes()
    }
    
    /**
     * Get the current leg's route (for compatibility with single-route navigation).
     * In multi-leg mode, returns the selected route of the current leg.
     * In single-route mode, returns null (use take() instead).
     */
    fun currentRoute(): RouteOption? = currentLeg()?.selectedRoute
    
    /**
     * Get progress information for UI display.
     * @return Pair of (currentLegIndex + 1, totalLegs) or null if not in multi-leg mode
     */
    fun progress(): Pair<Int, Int>? {
        val legs = _legs.value ?: return null
        val index = _currentLegIndex.value ?: return null
        return Pair(index + 1, legs.size)
    }
    
    /**
     * Get the stop names for the current leg.
     * @return Pair of (fromStopName, toStopName) or null if not available
     */
    fun currentLegStopNames(): Pair<String, String>? {
        val leg = currentLeg() ?: return null
        // Note: This would need access to the full TripPlan to get stop names.
        // For now, return placeholder names.
        return Pair("Stop ${currentLegIndex() ?: 0}", "Stop ${(currentLegIndex() ?: 0) + 1}")
    }
}