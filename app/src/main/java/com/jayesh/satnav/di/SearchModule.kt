package com.jayesh.satnav.di

import com.jayesh.satnav.data.repository.LocationRepositoryImpl
import com.jayesh.satnav.data.search.OfflineGeocoder
import com.jayesh.satnav.data.search.OfflineGeocoderImpl
import com.jayesh.satnav.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SearchModule {

    @Binds
    @Singleton
    abstract fun bindOfflineGeocoder(
        impl: OfflineGeocoderImpl,
    ): OfflineGeocoder

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl,
    ): LocationRepository
}