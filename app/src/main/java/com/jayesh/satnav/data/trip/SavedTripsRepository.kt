package com.jayesh.satnav.data.trip

import android.content.Context
import com.jayesh.satnav.domain.model.TripPlan
import com.jayesh.satnav.domain.model.TripPlanSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for saving and loading trip plans to/from local storage.
 * Storage: JSON files under context.filesDir/trips/, one file per trip,
 * plus a trips_index.json manifest for the list view.
 */
@Singleton
class SavedTripsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val tripsDir: File by lazy {
        File(context.filesDir, "trips").apply {
            if (!exists()) mkdirs()
        }
    }
    
    private val indexFile: File by lazy {
        File(tripsDir, "trips_index.json")
    }
    
    private val _tripsFlow = MutableSharedFlow<List<TripPlanSummary>>(replay = 1)
    val tripsFlow: Flow<List<TripPlanSummary>> = _tripsFlow.asSharedFlow()
    
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    init {
        // Load initial index and emit
        ioScope.launch {
            val summaries = loadIndex()
            _tripsFlow.emit(summaries)
        }
    }
    
    /**
     * Save a trip plan to storage.
     */
    suspend fun save(trip: TripPlan): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Save trip file
            val tripFile = File(tripsDir, "${trip.id}.json")
            val jsonString = json.encodeToString(trip)
            tripFile.writeText(jsonString)
            
            // Update index
            val summaries = loadIndex().toMutableList()
            val existingIndex = summaries.indexOfFirst { it.id == trip.id }
            val summary = TripPlanSummary.fromTripPlan(trip)
            
            if (existingIndex >= 0) {
                summaries[existingIndex] = summary
            } else {
                summaries.add(summary)
            }
            
            saveIndex(summaries)
            _tripsFlow.emit(summaries)
        }
    }
    
    /**
     * Delete a trip plan from storage.
     */
    suspend fun delete(tripId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Delete trip file
            val tripFile = File(tripsDir, "$tripId.json")
            if (tripFile.exists()) {
                tripFile.delete()
            }
            
            // Update index
            val summaries = loadIndex().filter { it.id != tripId }
            saveIndex(summaries)
            _tripsFlow.emit(summaries)
        }
    }
    
    /**
     * Get all saved trip summaries.
     */
    suspend fun getAll(): List<TripPlanSummary> = withContext(Dispatchers.IO) {
        loadIndex()
    }
    
    /**
     * Get a specific trip plan by ID.
     */
    suspend fun getById(tripId: String): TripPlan? = withContext(Dispatchers.IO) {
        runCatching {
            val tripFile = File(tripsDir, "$tripId.json")
            if (!tripFile.exists()) return@withContext null
            
            val jsonString = tripFile.readText()
            json.decodeFromString<TripPlan>(jsonString)
        }.getOrNull()
    }
    
    /**
     * Check if a trip with the given ID exists.
     */
    suspend fun exists(tripId: String): Boolean = withContext(Dispatchers.IO) {
        File(tripsDir, "$tripId.json").exists()
    }
    
    /**
     * Update only the name of a trip.
     */
    suspend fun updateName(tripId: String, newName: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val trip = getById(tripId) ?: return@runCatching
            val updatedTrip = trip.copy(name = newName)
            save(updatedTrip)
        }
    }
    
    private fun loadIndex(): List<TripPlanSummary> {
        return try {
            if (!indexFile.exists()) return emptyList()
            
            val jsonString = indexFile.readText()
            json.decodeFromString<List<TripPlanSummary>>(jsonString)
        } catch (e: Exception) {
            // Corrupted index - try to rebuild from trip files
            rebuildIndex()
        }
    }
    
    private fun saveIndex(summaries: List<TripPlanSummary>) {
        try {
            val jsonString = json.encodeToString(summaries)
            indexFile.writeText(jsonString)
        } catch (e: IOException) {
            // Log error but don't crash
            e.printStackTrace()
        }
    }
    
    private fun rebuildIndex(): List<TripPlanSummary> {
        val summaries = mutableListOf<TripPlanSummary>()
        
        try {
            tripsDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".json") && file.name != "trips_index.json") {
                    try {
                        val jsonString = file.readText()
                        val trip = json.decodeFromString<TripPlan>(jsonString)
                        summaries.add(TripPlanSummary.fromTripPlan(trip))
                    } catch (e: Exception) {
                        // Skip corrupted file
                    }
                }
            }
            
            // Sort by creation date (newest first)
            summaries.sortByDescending { it.createdAt }
            saveIndex(summaries)
        } catch (e: Exception) {
            // Couldn't rebuild index
        }
        
        return summaries
    }
    
    companion object {
        /**
         * Clear all saved trips (for testing or reset).
         */
        suspend fun clearAll(context: Context) {
            withContext(Dispatchers.IO) {
                val tripsDir = File(context.filesDir, "trips")
                if (tripsDir.exists()) {
                    tripsDir.deleteRecursively()
                }
            }
        }
    }
}