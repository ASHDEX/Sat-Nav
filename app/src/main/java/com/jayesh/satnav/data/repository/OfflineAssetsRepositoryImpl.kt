package com.jayesh.satnav.data.repository

import com.jayesh.satnav.data.local.graphhopper.GraphHopperLocalDataSource
import com.jayesh.satnav.data.local.maps.MapsLocalDataSource
import com.jayesh.satnav.data.local.terrain.TerrainLocalDataSource
import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.repository.OfflineAssetsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineAssetsRepositoryImpl @Inject constructor(
    private val mapsLocalDataSource: MapsLocalDataSource,
    private val terrainLocalDataSource: TerrainLocalDataSource,
    private val graphHopperLocalDataSource: GraphHopperLocalDataSource,
) : OfflineAssetsRepository {

    override suspend fun getOfflineDatasets(): List<OfflineDataset> {
        return buildList {
            addAll(mapsLocalDataSource.getDatasets())
            addAll(graphHopperLocalDataSource.getDatasets())
            addAll(terrainLocalDataSource.getDatasets())
        }
    }
}

