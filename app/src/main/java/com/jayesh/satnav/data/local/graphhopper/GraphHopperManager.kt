package com.jayesh.satnav.data.local.graphhopper

import android.os.SystemClock
import android.util.Log
import com.graphhopper.GHRequest
import com.graphhopper.GraphHopper
import com.graphhopper.config.Profile
import com.graphhopper.util.shapes.GHPoint
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.NavInstruction
import com.jayesh.satnav.domain.model.OfflineRoute
import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.model.RoutingEngineStatus
import com.jayesh.satnav.domain.model.RoutingProfile
import java.io.File
import java.util.Locale
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class GraphHopperManager @Inject constructor(
    private val graphHopperLocalDataSource: GraphHopperLocalDataSource,
    private val appDispatchers: AppDispatchers,
) {

    private val engineLock = Mutex()
    private var graphHopper: GraphHopper? = null
    private var loadedGraphDirectory: String? = null
    private var loadedProfiles: List<String> = emptyList()

    /**
     * Exposes the loaded GraphHopper instance for dependency injection.
     * The instance is guaranteed to be loaded after a successful call to [loadEngine].
     *
     * @throws IllegalStateException if the engine hasn't been loaded yet.
     */
    fun getGraphHopper(): GraphHopper {
        return graphHopper ?: throw IllegalStateException(
            "GraphHopper engine not loaded. Call loadEngine() first."
        )
    }

    suspend fun getRoutingEngineStatus(): RoutingEngineStatus = withContext(appDispatchers.io) {
        val graphDirectory = graphHopperLocalDataSource.resolveExistingGraphDirectory()
        Log.d(TAG, "Resolving existing graph directory...")
        
        if (graphDirectory == null) {
            val missingPaths = graphHopperLocalDataSource.candidateGraphDirectoryPaths().joinToString("\n")
            Log.w(TAG, "No usable GraphHopper graph-cache found in candidate paths:\n$missingPaths")
            return@withContext RoutingEngineStatus(
                graphDirectory = null,
                debugMessage = "No graph-cache found. Searched in:\n$missingPaths",
            )
        }

        Log.i(TAG, "Found potential graph directory: ${graphDirectory.absolutePath}")
        loadEngine(graphDirectory).fold(
            onSuccess = {
                Log.i(TAG, "GraphHopper engine successfully initialized.")
                RoutingEngineStatus(
                    isGraphPresent = true,
                    isLoaded = true,
                    graphDirectory = loadedGraphDirectory,
                    availableProfiles = loadedProfiles,
                    debugMessage = "GraphHopper loaded from ${graphDirectory.absolutePath}",
                )
            },
            onFailure = { error ->
                Log.e(TAG, "GraphHopper engine failed to initialize: ${error.message}", error)
                RoutingEngineStatus(
                    isGraphPresent = true,
                    isLoaded = false,
                    graphDirectory = graphDirectory.absolutePath,
                    availableProfiles = loadedProfiles,
                    debugMessage = "GraphHopper failed to load.",
                    errorMessage = error.message,
                )
            },
        )
    }

    suspend fun computeRoute(
        points: List<RouteCoordinate>,
        profile: RoutingProfile,
    ): Result<OfflineRoute> = withContext(appDispatchers.io) {
        runCatching {
            require(points.size >= 2) {
                "Provide at least a start point and an end point."
            }

            val graphDirectory = graphHopperLocalDataSource.resolveExistingGraphDirectory()
                ?: error(
                    "No graph-cache found. Copy it to ${graphHopperLocalDataSource.recommendedGraphDirectoryPath()} or ${graphHopperLocalDataSource.candidateGraphDirectoryPaths().last()}",
                )
            val hopper = loadEngine(graphDirectory).getOrThrow()

            if (loadedProfiles.isNotEmpty() && profile.profileName !in loadedProfiles) {
                error("Profile ${profile.profileName} not available. Available: ${loadedProfiles.joinToString()}")
            }
            val request = GHRequest().setProfile(profile.profileName).setLocale(Locale.US)
            points.forEach { request.addPoint(GHPoint(it.latitude, it.longitude)) }

            val startTime = SystemClock.elapsedRealtime()
            val response = hopper.route(request)
            val elapsed = SystemClock.elapsedRealtime() - startTime

            if (response.hasErrors()) {
                val message = response.getErrors().joinToString(separator = " | ") { error ->
                    error.message ?: error.toString()
                }
                error("Route calculation failed: $message")
            }

            val bestRoute = response.getBest()
            val routePoints = bestRoute.getPoints().toRouteCoordinates()
            require(routePoints.size >= 2) {
                "GraphHopper returned an empty or invalid route."
            }

            val navInstructions = bestRoute.getInstructions().map { instr ->
                NavInstruction(
                    sign = instr.getSign(),
                    streetName = instr.getName().orEmpty(),
                    distanceMeters = instr.getDistance(),
                    durationMillis = instr.getTime(),
                )
            }

            OfflineRoute(
                profile = profile,
                points = routePoints,
                distanceMeters = bestRoute.getDistance(),
                durationMillis = bestRoute.getTime(),
                computationTimeMillis = elapsed,
                instructions = navInstructions,
            )
        }.onFailure { error ->
            Log.e(TAG, "Routing request failed for profile=${profile.profileName}", error)
        }
    }

    private suspend fun loadEngine(graphDirectory: File): Result<GraphHopper> {
        return engineLock.withLock {
            val existing = graphHopper
            if (existing != null && loadedGraphDirectory == graphDirectory.absolutePath) {
                return@withLock Result.success(existing)
            }

            runCatching<GraphHopper> {
                graphHopper?.close()
                GraphHopper()
                    .forMobile()
                    .setAllowWrites(false)
                    .also { hopper ->
                        val encoders = readGraphEncoders(graphDirectory)
                        hopper.setProfiles(
                            encoders.map { enc ->
                                Profile(enc).setVehicle(enc).setWeighting("fastest")
                            },
                        )
                        check(hopper.load(graphDirectory.absolutePath)) {
                            "GraphHopper could not open ${graphDirectory.absolutePath}"
                        }
                        loadedGraphDirectory = graphDirectory.absolutePath
                        val namedProfiles = hopper.getProfiles().map { it.getName() }
                        loadedProfiles = namedProfiles.ifEmpty {
                            hopper.getEncodingManager().fetchEdgeEncoders()
                                .map { it.toString() }
                        }
                        graphHopper = hopper
                        Log.i(TAG, "Loaded GraphHopper graph from ${graphDirectory.absolutePath}")
                    }
            }
        }
    }

    private fun readGraphEncoders(graphDirectory: File): List<String> {
        return try {
            val propertiesFile = sequenceOf(
                File(graphDirectory, "properties"),
                File(graphDirectory, "properties.txt"),
            ).firstOrNull(File::exists) ?: return listOf("car")

            val properties = Properties()
            propertiesFile.inputStream().use { input ->
                properties.load(input)
            }

            properties.getProperty("graph.flag_encoders")
                ?.split(',')
                ?.mapNotNull { encoder ->
                    encoder.substringBefore('|').trim().takeIf(String::isNotEmpty)
                }
                ?.distinct()
                ?.ifEmpty { null }
                ?: listOf("car")
        } catch (e: Exception) {
            Log.w(TAG, "Could not read graph encoders, defaulting to [car]", e)
            listOf("car")
        }
    }

    private fun com.graphhopper.util.PointList.toRouteCoordinates(): List<RouteCoordinate> {
        return buildList(size()) {
            for (index in 0 until size()) {
                add(
                    RouteCoordinate(
                        latitude = getLat(index),
                        longitude = getLon(index),
                    ),
                )
            }
        }
    }

    private companion object {
        const val TAG = "GraphHopperManager"
    }
}
