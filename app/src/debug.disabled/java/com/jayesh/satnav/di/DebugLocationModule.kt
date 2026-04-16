package com.jayesh.satnav.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jayesh.satnav.data.location.FakeLocationRepository
import com.jayesh.satnav.data.location.LocationRepositoryImpl
import com.jayesh.satnav.domain.repository.LocationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "debug_settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugLocationModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        fakeRepository: FakeLocationRepository
    ): LocationRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DebugConfigModule {

    private val USE_GPX_REPLAY_KEY = booleanPreferencesKey("debug_use_gpx_replay")

    @Provides
    @Singleton
    fun provideLocationRepository(
        @ApplicationContext context: Context,
        fakeRepository: FakeLocationRepository,
        realRepository: LocationRepositoryImpl
    ): LocationRepository {
        return runBlocking {
            val useGpxReplay = context.dataStore.data.first()[USE_GPX_REPLAY_KEY] ?: false
            if (useGpxReplay) {
                fakeRepository
            } else {
                realRepository
            }
        }
    }

    @Provides
    @Singleton
    fun provideDebugDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}