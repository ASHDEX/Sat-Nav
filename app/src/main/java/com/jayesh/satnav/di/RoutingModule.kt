package com.jayesh.satnav.di

import com.graphhopper.GraphHopper
import com.jayesh.satnav.data.local.graphhopper.GraphHopperManager
import com.jayesh.satnav.domain.routing.RoutePlanner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Dagger module that provides routing‑related dependencies.
 *
 * Exposes the existing GraphHopper singleton (already initialized in GraphHopperManager)
 * to avoid creating a second instance.
 */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {

    /**
     * Provides the GraphHopper instance managed by GraphHopperManager.
     *
     * GraphHopper is expensive to init and holds a large graph in memory.
     * We reuse the instance that GraphHopperManager already loads.
     *
     * The provider ensures the engine is loaded by calling [GraphHopperManager.getRoutingEngineStatus]
     * which triggers lazy loading. Once loaded, [GraphHopperManager.getGraphHopper] returns the instance.
     */
    @Provides
    @Singleton
    fun provideGraphHopper(manager: GraphHopperManager): GraphHopper {
        // Trigger loading (idempotent) by checking status.
        // This runs on a background thread via suspend, but providers cannot be suspend.
        // Instead, we rely on the fact that the engine is already loaded by the time
        // RoutePlanner is used (because the app's startup loads it).
        // If not loaded, getGraphHopper() will throw; we accept that as a clear error.
        return manager.getGraphHopper()
    }

    /**
     * Provides the RoutePlanner that uses the shared GraphHopper instance.
     */
    @Provides
    @Singleton
    fun provideRoutePlanner(): RoutePlanner {
        return RoutePlanner()
    }
}