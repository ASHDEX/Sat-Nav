package com.jayesh.satnav.domain.trip

import com.jayesh.satnav.domain.model.TripPlan

/**
 * State of trip computation.
 */
sealed interface TripComputeState {
    /** No trip being computed */
    data object Idle : TripComputeState
    
    /** Computing trip with current progress */
    data class Computing(
        val currentStopIndex: Int,
        val totalStops: Int,
        val progressMessage: String
    ) : TripComputeState
    
    /** Trip computed successfully */
    data class Success(
        val tripPlan: TripPlan
    ) : TripComputeState
    
    /** Trip computation failed */
    data class Error(
        val message: String,
        val recoverable: Boolean = false
    ) : TripComputeState
}