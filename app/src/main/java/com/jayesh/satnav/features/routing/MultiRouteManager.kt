package com.jayesh.satnav.features.routing

import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.NavInstruction
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.domain.model.Waypoint
import com.jayesh.satnav.domain.usecase.ComputeRouteUseCase
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale
import kotlinx.coroutines.withContext

@Singleton
class MultiRouteManager @Inject constructor(
    private val computeRouteUseCase: ComputeRouteUseCase,
    private val appDispatchers: AppDispatchers,
) {

    suspend fun computeRoute(
        waypoints: List<Waypoint>,
        profile: RoutingProfile,
    ): Result<OfflineRoute> = withContext(appDispatchers.default) {
        runCatching {
            require(waypoints.size >= 2) {
                "Add at least two waypoints to build a route."
            }

            val sanitizedWaypoints = waypoints.mapIndexed { index, waypoint ->
                require(waypoint.lat in -90.0..90.0) {
                    "Waypoint ${index + 1} latitude is out of range."
                }
                require(waypoint.lon in -180.0..180.0) {
                    "Waypoint ${index + 1} longitude is out of range."
                }
                waypoint
            }

            val segments = buildList {
                for (index in 0 until sanitizedWaypoints.lastIndex) {
                    val start = sanitizedWaypoints[index]
                    val end = sanitizedWaypoints[index + 1]
                    val segment = computeRouteUseCase(
                        points = listOf(
                            RouteCoordinate(latitude = start.lat, longitude = start.lon),
                            RouteCoordinate(latitude = end.lat, longitude = end.lon),
                        ),
                        profile = profile,
                    ).getOrElse { error ->
                        throw IllegalStateException(
                            "Segment ${index + 1} failed between ${start.lat.format()}," +
                                " ${start.lon.format()} and ${end.lat.format()}, ${end.lon.format()}: " +
                                (error.message ?: "unknown routing error"),
                            error,
                        )
                    }
                    add(segment)
                }
            }

            RouteMerger.merge(
                segments = segments,
                profile = profile,
                waypointCount = sanitizedWaypoints.size,
            )
        }
    }
}

internal object RouteMerger {
    fun merge(
        segments: List<OfflineRoute>,
        profile: RoutingProfile,
        waypointCount: Int,
    ): OfflineRoute {
        require(segments.isNotEmpty()) {
            "No route segments were produced."
        }

        val mergedPoints = buildList {
            segments.forEachIndexed { index, segment ->
                require(segment.points.isNotEmpty()) {
                    "Route segment ${index + 1} is empty."
                }

                val segmentPoints = if (index == 0) {
                    segment.points
                } else {
                    segment.points.dropWhileSharedBoundary(lastOrNull())
                }
                addAll(segmentPoints)
            }
        }

        require(mergedPoints.size >= 2) {
            "Merged multi-point route is invalid."
        }

        val mergedInstructions = buildList {
            segments.forEachIndexed { index, segment ->
                val instrs = if (index < segments.lastIndex) {
                    // Drop the final "Arrive" instruction from all but the last segment
                    segment.instructions.dropLastWhile { it.sign == NavInstruction.SIGN_FINISH }
                } else {
                    segment.instructions
                }
                addAll(instrs)
            }
        }

        return OfflineRoute(
            profile = profile,
            points = mergedPoints,
            distanceMeters = segments.sumOf { it.distanceMeters },
            durationMillis = segments.sumOf { it.durationMillis },
            computationTimeMillis = segments.sumOf { it.computationTimeMillis },
            segmentCount = segments.size,
            waypointCount = waypointCount,
            instructions = mergedInstructions,
        )
    }

    private fun List<RouteCoordinate>.dropWhileSharedBoundary(
        previousLastPoint: RouteCoordinate?,
    ): List<RouteCoordinate> {
        if (previousLastPoint == null || isEmpty()) {
            return this
        }

        return if (first().isSameCoordinate(previousLastPoint)) drop(1) else this
    }

    private fun RouteCoordinate.isSameCoordinate(other: RouteCoordinate): Boolean {
        return kotlin.math.abs(latitude - other.latitude) < 1e-6 &&
            kotlin.math.abs(longitude - other.longitude) < 1e-6
    }
}

private fun Double.format(): String = String.format(Locale.US, "%.5f", this)
