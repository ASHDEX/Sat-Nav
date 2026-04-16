package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.model.OfflineDatasetType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapsLocalDataSource @Inject constructor(
    private val mbtilesFileDataSource: MbtilesFileDataSource,
) {

    suspend fun getDatasets(): List<OfflineDataset> = listOf(
        OfflineDataset(
            id = AppConstants.OfflineMapsDirectory,
            type = OfflineDatasetType.MapTiles,
            isAvailable = mbtilesFileDataSource.resolveExistingMapFile() != null,
            description = "MBTiles packages for regional basemaps stored in app-specific files.",
        ),
    )
}

