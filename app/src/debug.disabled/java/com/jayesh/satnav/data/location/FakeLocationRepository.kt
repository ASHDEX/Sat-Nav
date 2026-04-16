package com.jayesh.satnav.data.location

import android.content.Context
import android.location.Location
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake location repository that replays GPX traces for debugging.
 * Only available in debug builds.
 */
@Singleton
class FakeLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers,
) : LocationRepository {

    private val _currentLocation = MutableStateFlow<Location?>(null)
    private val currentLocationFlow = MutableStateFlow(createDefaultLocation())

    private var replayJob: Job? = null
    private var currentGpxPoints: List<GpxPoint> = emptyList()
    private var speedMultiplier: Float = 1.0f
    private var currentIndex: Int = 0

    override val currentLocation: Flow<Location>
        get() = currentLocationFlow

    override suspend fun lastKnown(): Location? = withContext(appDispatchers.io) {
        currentLocationFlow.value
    }

    /**
     * Load a GPX file from assets/gpx/ directory.
     */
    suspend fun loadGpxFile(filename: String) = withContext(appDispatchers.io) {
        stopReplay()
        currentGpxPoints = parseGpxFile(filename)
        currentIndex = 0
        if (currentGpxPoints.isNotEmpty()) {
            // Jump to first point
            val firstPoint = currentGpxPoints.first()
            updateLocation(firstPoint.toLocation())
        }
    }

    /**
     * Start replaying the loaded GPX trace.
     */
    fun startReplay(speedMultiplier: Float = 1.0f) {
        stopReplay()
        this.speedMultiplier = speedMultiplier
        
        if (currentGpxPoints.isEmpty()) return

        replayJob = CoroutineScope(appDispatchers.io).launch {
            var previousTime = currentGpxPoints.first().timestamp
            updateLocation(currentGpxPoints.first().toLocation())

            for (i in 1 until currentGpxPoints.size) {
                if (!isActive) break
                
                val currentPoint = currentGpxPoints[i]
                val timeDiff = currentPoint.timestamp.toEpochMilli() - previousTime.toEpochMilli()
                val adjustedDelay = (timeDiff / speedMultiplier).toLong()
                
                if (adjustedDelay > 0) {
                    delay(adjustedDelay)
                }
                
                updateLocation(currentPoint.toLocation())
                previousTime = currentPoint.timestamp
                currentIndex = i
            }
        }
    }

    /**
     * Stop the current replay.
     */
    fun stopReplay() {
        replayJob?.cancel()
        replayJob = null
    }

    /**
     * Jump to a specific point in the GPX trace.
     */
    fun jumpToPoint(index: Int) {
        if (index in currentGpxPoints.indices) {
            currentIndex = index
            updateLocation(currentGpxPoints[index].toLocation())
        }
    }

    /**
     * Jump to the start of the route.
     */
    fun jumpToStart() {
        jumpToPoint(0)
    }

    /**
     * Get available GPX files from assets/gpx/.
     */
    fun getAvailableGpxFiles(): List<String> {
        return try {
            context.assets.list("gpx")?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateLocation(location: Location) {
        _currentLocation.value = location
        currentLocationFlow.value = location
    }

    private suspend fun parseGpxFile(filename: String): List<GpxPoint> = withContext(appDispatchers.io) {
        val points = mutableListOf<GpxPoint>()
        
        try {
            val inputStream: InputStream = context.assets.open("gpx/$filename")
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, null)
            
            var eventType = parser.eventType
            var currentLat: Double? = null
            var currentLon: Double? = null
            var currentEle: Double? = null
            var currentTime: String? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trkpt" -> {
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                            }
                            "ele" -> {
                                parser.next()
                                currentEle = parser.text.toDoubleOrNull()
                            }
                            "time" -> {
                                parser.next()
                                currentTime = parser.text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "trkpt" && currentLat != null && currentLon != null) {
                            val timestamp = parseIsoTime(currentTime)
                            points.add(
                                GpxPoint(
                                    latitude = currentLat!!,
                                    longitude = currentLon!!,
                                    elevation = currentEle ?: 0.0,
                                    timestamp = timestamp
                                )
                            )
                            currentLat = null
                            currentLon = null
                            currentEle = null
                            currentTime = null
                        }
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        points
    }

    private fun parseIsoTime(timeString: String?): Instant {
        if (timeString == null) return Instant.now()
        
        return try {
            val formatter = DateTimeFormatter.ISO_DATE_TIME
            ZonedDateTime.parse(timeString, formatter).toInstant()
        } catch (e: Exception) {
            Instant.now()
        }
    }

    private fun createDefaultLocation(): Location {
        return Location("fake").apply {
            latitude = 28.4595
            longitude = 77.0266
            accuracy = 5.0f
            speed = 0f
            bearing = 0f
            time = System.currentTimeMillis()
        }
    }

    data class GpxPoint(
        val latitude: Double,
        val longitude: Double,
        val elevation: Double,
        val timestamp: Instant
    ) {
        fun toLocation(): Location {
            return Location("fake").apply {
                latitude = this@GpxPoint.latitude
                longitude = this@GpxPoint.longitude
                altitude = elevation
                accuracy = 5.0f
                speed = 10.0f // Default speed
                bearing = 0f
                time = timestamp.toEpochMilli()
            }
        }
    }
}