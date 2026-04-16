package com.jayesh.satnav.di

import android.content.Context
import com.jayesh.satnav.data.search.OfflineGeocoder
import com.jayesh.satnav.data.trip.SavedTripsRepository
import com.jayesh.satnav.domain.repository.LocationRepository
import com.jayesh.satnav.domain.routing.RoutePlanner
import com.jayesh.satnav.domain.trip.TripPlannerService
import com.jayesh.satnav.features.routing.TripCoordinator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger module that provides trip planning and multi-leg navigation dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object TripModule {

    /**
     * Provides the TripPlannerService for computing multi-leg trips.
     * Reuses the existing RoutePlanner instance to avoid creating a second GraphHopper.
     */
    @Provides
    @Singleton
    fun provideTripPlannerService(
        geocoder: OfflineGeocoder,
        routePlanner: RoutePlanner,
        locationRepository: LocationRepository,
    ): TripPlannerService {
        return TripPlannerService(geocoder, routePlanner, locationRepository)
    }

    /**
     * Provides the SavedTripsRepository for persisting and loading trip plans.
     */
    @Provides
    @Singleton
    fun provideSavedTripsRepository(
        @ApplicationContext context: Context,
    ): SavedTripsRepository {
        return SavedTripsRepository(context)
    }

    /**
     * Provides the TripCoordinator singleton that manages trip data between screens.
     * Supports both single-route (existing flow) and multi-leg trips (new feature).
     */
    @Provides
    @Singleton
    fun provideTripCoordinator(): TripCoordinator {
        return TripCoordinator()
    }
}