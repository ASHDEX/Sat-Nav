package com.jayesh.satnav.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.jayesh.satnav.data.local.persistence.NavigationStatePersistence
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext

@Module
@InstallIn(ViewModelComponent::class)
object PersistenceModule {

    @Provides
    fun provideNavigationStatePersistence(
        savedStateHandle: SavedStateHandle,
    ): NavigationStatePersistence {
        return NavigationStatePersistence(savedStateHandle)
    }
}