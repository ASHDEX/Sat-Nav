package com.jayesh.satnav.di

import android.content.Context
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.core.utils.DefaultAppDispatchers
import com.jayesh.satnav.data.repository.OfflineMapRepositoryImpl
import com.jayesh.satnav.data.repository.OfflineAssetsRepositoryImpl
import com.jayesh.satnav.data.repository.RoutingRepositoryImpl
import com.jayesh.satnav.data.repository.SavedPlacesRepositoryImpl
import com.jayesh.satnav.data.repository.TrafficPatternRepositoryImpl
import com.jayesh.satnav.domain.repository.OfflineMapRepository
import com.jayesh.satnav.domain.repository.OfflineAssetsRepository
import com.jayesh.satnav.domain.repository.RoutingRepository
import com.jayesh.satnav.domain.repository.SavedPlacesRepository
import com.jayesh.satnav.domain.repository.TrafficPatternRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingModule {

    @Binds
    @Singleton
    abstract fun bindAppDispatchers(impl: DefaultAppDispatchers): AppDispatchers

    @Binds
    @Singleton
    abstract fun bindOfflineAssetsRepository(
        impl: OfflineAssetsRepositoryImpl,
    ): OfflineAssetsRepository

    @Binds
    @Singleton
    abstract fun bindOfflineMapRepository(
        impl: OfflineMapRepositoryImpl,
    ): OfflineMapRepository

    @Binds
    @Singleton
    abstract fun bindRoutingRepository(
        impl: RoutingRepositoryImpl,
    ): RoutingRepository

    @Binds
    @Singleton
    abstract fun bindTrafficPatternRepository(
        impl: TrafficPatternRepositoryImpl,
    ): TrafficPatternRepository

    @Binds
    @Singleton
    abstract fun bindSavedPlacesRepository(
        impl: SavedPlacesRepositoryImpl,
    ): SavedPlacesRepository
}
