package com.jayesh.satnav.domain.model

import kotlinx.datetime.Instant

/**
 * Domain models for multi-modal transportation support.
 * Phase 14: Multi-modal Transportation Support
 */

/**
 * Transportation modes supported by the navigation system.
 */
enum class TransportationMode(
    val displayName: String,
    val iconName: String,
    val averageSpeedKmh: Double,
    val routingProfile: RoutingProfile,
    val colorHex: String
) {
    CAR(
        displayName = "Car",
        iconName = "car",
        averageSpeedKmh = 50.0,
        routingProfile = RoutingProfile.CAR,
        colorHex = "#3B82F6" // Blue
    ),
    BICYCLE(
        displayName = "Bicycle",
        iconName = "bicycle",
        averageSpeedKmh = 15.0,
        routingProfile = RoutingProfile.BIKE,
        colorHex = "#10B981" // Green
    ),
    WALKING(
        displayName = "Walking",
        iconName = "walking",
        averageSpeedKmh = 5.0,
        routingProfile = RoutingProfile.FOOT,
        colorHex = "#8B5CF6" // Purple
    ),
    PUBLIC_TRANSIT(
        displayName = "Public Transit",
        iconName = "bus",
        averageSpeedKmh = 25.0,
        routingProfile = RoutingProfile.PUBLIC_TRANSIT,
        colorHex = "#F59E0B" // Orange
    ),
    MIXED(
        displayName = "Mixed Mode",
        iconName = "mixed",
        averageSpeedKmh = 20.0,
        routingProfile = RoutingProfile.MIXED,
        colorHex = "#EC4899" // Pink
    );

    companion object {
        fun fromString(value: String): TransportationMode? {
            return values().find { it.name.equals(value, ignoreCase = true) }
        }
    }
}

/**
 * Transportation mode preference configuration.
 * @param primaryMode Primary transportation mode
 * @param secondaryModes List of alternative modes
 * @param avoidTolls Whether to avoid toll roads (car mode)
 * @param avoidHighways Whether to avoid highways (car/bike mode)
 * @param preferBikeLanes Whether to prefer bike lanes (bike mode)
 * @param preferSidewalks Whether to prefer sidewalks (walking mode)
 * @param maxWalkingDistanceMeters Maximum walking distance for mixed trips
 * @param transitPreferences Public transit preferences
 */
data class TransportationPreferences(
    val primaryMode: TransportationMode = TransportationMode.CAR,
    val secondaryModes: List<TransportationMode> = emptyList(),
    val avoidTolls: Boolean = false,
    val avoidHighways: Boolean = false,
    val preferBikeLanes: Boolean = true,
    val preferSidewalks: Boolean = true,
    val maxWalkingDistanceMeters: Double = 1000.0,
    val transitPreferences: TransitPreferences = TransitPreferences()
)

/**
 * Public transit preferences.
 * @param preferredAgencies Preferred transit agencies
 * @param avoidTransfers Whether to avoid transfers
 * @param maxTransfers Maximum number of transfers allowed
 * @param preferredRoutes Preferred route numbers/names
 * @param accessibilityRequirements Accessibility requirements
 */
data class TransitPreferences(
    val preferredAgencies: List<String> = emptyList(),
    val avoidTransfers: Boolean = false,
    val maxTransfers: Int = 3,
    val preferredRoutes: List<String> = emptyList(),
    val accessibilityRequirements: AccessibilityRequirements = AccessibilityRequirements.NONE
)

/**
 * Accessibility requirements for transit.
 */
enum class AccessibilityRequirements {
    NONE,
    WHEELCHAIR_ACCESSIBLE,
    ELEVATOR_REQUIRED,
    ESCALATOR_AVOIDANCE
}

/**
 * Multi-modal route segment.
 * @param mode Transportation mode for this segment
 * @param startCoordinate Start coordinate of segment
 * @param endCoordinate End coordinate of segment
 * @param distanceMeters Distance in meters
 * @param durationSeconds Estimated duration in seconds
 * @param instructions Navigation instructions for this segment
 * @param transitDetails Transit details (if mode is PUBLIC_TRANSIT)
 * @param elevationGainMeters Elevation gain (for bike/walking)
 * @param caloriesBurned Estimated calories burned (for bike/walking)
 */
data class MultiModalSegment(
    val mode: TransportationMode,
    val startCoordinate: RouteCoordinate,
    val endCoordinate: RouteCoordinate,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val instructions: List<NavInstruction>,
    val transitDetails: TransitDetails? = null,
    val elevationGainMeters: Double = 0.0,
    val caloriesBurned: Double? = null
)

/**
 * Transit details for public transit segments.
 * @param agencyName Transit agency name
 * @param routeName Route name/number
 * @param routeType Type of transit (bus, train, subway, etc.)
 * @param departureTime Scheduled departure time
 * @param arrivalTime Scheduled arrival time
 * @fromStopName Departure stop name
 * @toStopName Arrival stop name
 * @param tripId Trip identifier
 * @param headsign Destination headsign
 * @param wheelchairAccessible Whether vehicle is wheelchair accessible
 */
data class TransitDetails(
    val agencyName: String,
    val routeName: String,
    val routeType: TransitType,
    val departureTime: Instant,
    val arrivalTime: Instant,
    val fromStopName: String,
    val toStopName: String,
    val tripId: String,
    val headsign: String,
    val wheelchairAccessible: Boolean
)

/**
 * Types of public transit.
 */
enum class TransitType(val displayName: String) {
    BUS("Bus"),
    TRAIN("Train"),
    SUBWAY("Subway"),
    TRAM("Tram"),
    FERRY("Ferry"),
    CABLE_CAR("Cable Car"),
    GONDOLA("Gondola"),
    FUNICULAR("Funicular");

    companion object {
        fun fromGtfsRouteType(routeType: Int): TransitType {
            return when (routeType) {
                0 -> TRAM
                1 -> SUBWAY
                2 -> TRAIN
                3 -> BUS
                4 -> FERRY
                5 -> CABLE_CAR
                6 -> GONDOLA
                7 -> FUNICULAR
                else -> BUS
            }
        }
    }
}

/**
 * Multi-modal route combining multiple segments.
 * @param segments List of route segments with different modes
 * @param totalDistanceMeters Total distance in meters
 * @param totalDurationSeconds Total estimated duration in seconds
 * @param modeChanges Number of mode changes
 * @param totalCaloriesBurned Total calories burned (if applicable)
 * @param totalElevationGainMeters Total elevation gain
 * @param accessibilityScore Accessibility score (0-100)
 */
data class MultiModalRoute(
    val segments: List<MultiModalSegment>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val modeChanges: Int,
    val totalCaloriesBurned: Double? = null,
    val totalElevationGainMeters: Double = 0.0,
    val accessibilityScore: Int = 100
) {
    val primaryMode: TransportationMode = segments.firstOrNull()?.mode ?: TransportationMode.CAR
    
    fun getSegmentByMode(mode: TransportationMode): List<MultiModalSegment> {
        return segments.filter { it.mode == mode }
    }
    
    fun getTransitSegments(): List<MultiModalSegment> {
        return segments.filter { it.mode == TransportationMode.PUBLIC_TRANSIT }
    }
    
    fun getWalkingSegments(): List<MultiModalSegment> {
        return segments.filter { it.mode == TransportationMode.WALKING }
    }
}

/**
 * Transportation mode statistics.
 * @param mode Transportation mode
 * @param totalDistanceMeters Total distance traveled
 * @param totalDurationSeconds Total time spent
 * @param totalCaloriesBurned Total calories burned
 * @param averageSpeedKmh Average speed
 * @param tripCount Number of trips
 */
data class TransportationStats(
    val mode: TransportationMode,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val totalCaloriesBurned: Double? = null,
    val averageSpeedKmh: Double,
    val tripCount: Int
)

/**
 * Mode switching suggestion.
 * @param fromMode Current transportation mode
 * @param toMode Suggested transportation mode
 * @param reason Reason for suggestion
 * @param estimatedTimeSavingsSeconds Estimated time savings
 * @param estimatedCostSavings Estimated cost savings (if applicable)
 * @param confidence Confidence score (0.0-1.0)
 */
data class ModeSuggestion(
    val fromMode: TransportationMode,
    val toMode: TransportationMode,
    val reason: SuggestionReason,
    val estimatedTimeSavingsSeconds: Double,
    val estimatedCostSavings: Double? = null,
    val confidence: Double
)

/**
 * Reasons for mode switching suggestions.
 */
enum class SuggestionReason(val displayName: String) {
    TRAFFIC_CONGESTION("Traffic congestion ahead"),
    PARKING_DIFFICULTY("Parking may be difficult"),
    BETTER_ALTERNATIVE("Better alternative available"),
    WEATHER_CONDITIONS("Weather conditions favorable"),
    HEALTH_BENEFITS("Health benefits"),
    COST_SAVINGS("Cost savings"),
    ENVIRONMENTAL_IMPACT("Reduced environmental impact"),
    TIME_EFFICIENCY("More time efficient")
}

/**
 * Transportation mode availability for current location.
 * @param mode Transportation mode
 * @param isAvailable Whether mode is available
 * @param availabilityReason Reason for availability status
 * @param estimatedWaitTime Estimated wait time (for transit)
 * @param costPerKm Estimated cost per kilometer
 */
data class ModeAvailability(
    val mode: TransportationMode,
    val isAvailable: Boolean,
    val availabilityReason: AvailabilityReason,
    val estimatedWaitTime: Double? = null,
    val costPerKm: Double? = null
)

/**
 * Reasons for mode availability.
 */
enum class AvailabilityReason(val displayName: String) {
    ALWAYS_AVAILABLE("Always available"),
    TRANSIT_SCHEDULE("Available per transit schedule"),
    BIKE_SHARE_NEARBY("Bike share nearby"),
    CAR_SHARE_NEARBY("Car share nearby"),
    NO_TRANSIT_COVERAGE("No transit coverage"),
    OUT_OF_SERVICE_HOURS("Out of service hours"),
    WEATHER_RESTRICTION("Weather restriction"),
    ACCESSIBILITY_LIMITATION("Accessibility limitation")
}

/**
 * Transportation mode comparison for route planning.
 * @param mode Transportation mode
 * @param estimatedDurationSeconds Estimated duration
 * @param estimatedCost Estimated cost
 * @param caloriesBurned Calories burned
 * @param carbonEmissionsKg CO2 emissions in kg
 * @param healthBenefits Health benefits description
 */
data class ModeComparison(
    val mode: TransportationMode,
    val estimatedDurationSeconds: Double,
    val estimatedCost: Double,
    val caloriesBurned: Double? = null,
    val carbonEmissionsKg: Double,
    val healthBenefits: String? = null
)
