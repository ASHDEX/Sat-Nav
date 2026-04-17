package com.jayesh.satnav.data.repository

import com.jayesh.satnav.domain.model.Place
import com.jayesh.satnav.domain.repository.SavedPlacesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stub implementation of SavedPlacesRepository.
 * In a real app, this would be backed by Room or DataStore.
 */
@Singleton
class SavedPlacesRepositoryImpl @Inject constructor() : SavedPlacesRepository {
    private val _places = MutableStateFlow<List<Place>>(emptyList())

    override fun getAll(): Flow<List<Place>> = _places

    override suspend fun save(place: Place) {
        val current = _places.value.toMutableList()
        // Replace if exists, otherwise add
        val index = current.indexOfFirst { it.id == place.id }
        if (index >= 0) {
            current[index] = place
        } else {
            current.add(place)
        }
        _places.value = current
    }

    override suspend fun delete(id: String) {
        val current = _places.value.toMutableList()
        current.removeIf { it.id == id }
        _places.value = current
    }

    override suspend fun isSaved(id: String): Boolean {
        return _places.value.any { it.id == id }
    }
}