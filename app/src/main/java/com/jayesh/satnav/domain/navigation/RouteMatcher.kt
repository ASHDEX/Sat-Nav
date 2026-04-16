package com.jayesh.satnav.domain.navigation

import android.location.Location
import com.jayesh.satnav.domain.model.RouteOption

data class MatchResult(
    val snappedLat: Double,
    val snappedLon: Double,
    val bearing: Float,
    val distanceAlongRouteM: Double,
    val distanceToNextManeuverM: Double,
    val currentInstructionIndex: Int,
    val isOffRoute: Boolean,
    val offRouteDistanceM: Double,
)

class RouteMatcher(private val route: RouteOption) {
    fun matchLocation(location: Location): MatchResult {
        return MatchResult(
            snappedLat = location.latitude,
            snappedLon = location.longitude,
            bearing = location.bearing,
            distanceAlongRouteM = 0.0,
            distanceToNextManeuverM = route.distanceMeters / 2,
            currentInstructionIndex = 0,
            isOffRoute = false,
            offRouteDistanceM = 0.0
        )
    }

    fun getRouteProgress(matchResult: MatchResult): Double {
        return if (route.distanceMeters > 0) {
            matchResult.distanceAlongRouteM / route.distanceMeters
        } else {
            0.0
        }
    }
}
