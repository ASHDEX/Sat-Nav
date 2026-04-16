package com.jayesh.satnav.domain.model

import com.graphhopper.util.shapes.GHPoint
import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.core.utils.LatLngBounds
import kotlinx.serialization.Serializable

/**
 * Represents a route alternative in the trip planning flow.
 *
 * @property id Unique identifier for this route option (0‑based index).
 * @property label Human‑readable label, e.g., "Fastest", "Alternative 1".
 * @property points Full geometry as a list of LatLng coordinates.
 * @property distanceMeters Total route distance in meters.
 * @property durationMillis Estimated travel time in milliseconds.
 * @property viaRoads Top 2‑3 notable street names along the route.
 * @property instructions Turn‑by‑turn instructions for navigation.
 * @property bounds Pre‑computed bounding box for camera fitting.
 */
@Serializable
data class RouteOption(
    val id: Int,
    val label: String,
    val points: List<LatLng>,
    val distanceMeters: Double,
    val durationMillis: Long,
    val viaRoads: List<String>,
    val instructions: List<TurnInstruction>,
    val bounds: LatLngBounds,
) {
    companion object {
        fun fromGraphHopperPath(
            path: com.graphhopper.ResponsePath,
            index: Int,
            streetNameIntervals: List<Triple<Int, Int, String>>?,
        ): RouteOption {
            val points = path.points.toLatLngList()
            val label = when (index) {
                0 -> "Fastest"
                else -> "Alternative $index"
            }
            val viaRoads = extractViaRoads(streetNameIntervals, path.time)
            val instructions = path.instructions?.mapIndexed { idx, instr ->
                TurnInstruction.fromGraphHopperInstruction(instr, idx)
            } ?: emptyList()
            val bounds = computeBounds(points)

            return RouteOption(
                id = index,
                label = label,
                points = points,
                distanceMeters = path.distance,
                durationMillis = path.time,
                viaRoads = viaRoads,
                instructions = instructions,
                bounds = bounds,
            )
        }

        private fun extractViaRoads(
            intervals: List<Triple<Int, Int, String>>?,
            totalTime: Long,
        ): List<String> {
            if (intervals.isNullOrEmpty()) return emptyList()

            // Sort by duration (approximated by interval length)
            val sorted = intervals.sortedByDescending { (start, end, _) -> end - start }
            return sorted.take(3).map { (_, _, name) -> name }.filterNot { it.isBlank() }
        }

        private fun computeBounds(points: List<LatLng>): LatLngBounds {
            if (points.isEmpty()) return LatLngBounds(
                southwest = LatLng(0.0, 0.0),
                northeast = LatLng(0.0, 0.0),
            )

            var minLat = points.first().latitude
            var maxLat = minLat
            var minLng = points.first().longitude
            var maxLng = minLng

            for (point in points) {
                if (point.latitude < minLat) minLat = point.latitude
                if (point.latitude > maxLat) maxLat = point.latitude
                if (point.longitude < minLng) minLng = point.longitude
                if (point.longitude > maxLng) maxLng = point.longitude
            }

            return LatLngBounds(
                southwest = LatLng(minLat, minLng),
                northeast = LatLng(maxLat, maxLng),
            )
        }
    }
}

private fun com.graphhopper.util.PointList.toLatLngList(): List<LatLng> {
    return buildList(size()) {
        for (i in 0 until size()) {
            add(LatLng(getLat(i), getLon(i)))
        }
    }
}

private fun GHPoint.toLatLng(): LatLng = LatLng(lat, lon)