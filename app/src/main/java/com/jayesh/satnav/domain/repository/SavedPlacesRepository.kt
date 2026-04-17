package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.Place
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing saved places (favorites, recent destinations, etc.)
 */
interface SavedPlacesRepository {
    /**
     * Get all saved places.
     */
    fun getAll(): Flow<List<Place>>

    /**
     * Save a new place.
     */
    suspend fun save(place: Place)

    /**
     * Delete a saved place by ID.
     */
    suspend fun delete(id: String)

    /**
     * Check if a place is saved.
     */
    suspend fun isSaved(id: String): Boolean
}