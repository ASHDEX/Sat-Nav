package com.jayesh.satnav.data.local.terrain

import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.model.OfflineDatasetType
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class TerrainLocalDataSource @Inject constructor(
    private val appDispatchers: AppDispatchers,
) {

    suspend fun getDatasets(): List<OfflineDataset> = withContext(appDispatchers.io) {
        val terrainDirectory = File(AppConstants.OfflineTerrainDirectory)
        val demFile = File(terrainDirectory, "dem.mbtiles")
        val hillshadeFile = File(terrainDirectory, "hillshade.mbtiles")

        listOf(
            OfflineDataset(
                id = "${AppConstants.OfflineTerrainDirectory}_dem",
                type = OfflineDatasetType.TerrainDem,
                isAvailable = demFile.exists() && demFile.length() > 0,
                description = "Digital elevation model tiles for terrain shading.",
            ),
            OfflineDataset(
                id = "${AppConstants.OfflineTerrainDirectory}_hillshade",
                type = OfflineDatasetType.TerrainHillshade,
                isAvailable = hillshadeFile.exists() && hillshadeFile.length() > 0,
                description = "Precomputed hillshade raster overlays.",
            ),
        )
    }

    suspend fun resolveDemFile(): File? = withContext(appDispatchers.io) {
        val terrainDirectory = File(AppConstants.OfflineTerrainDirectory)
        val demFile = File(terrainDirectory, "dem.mbtiles")
        if (demFile.exists() && demFile.length() > 0) demFile else null
    }

    suspend fun resolveHillshadeFile(): File? = withContext(appDispatchers.io) {
        val terrainDirectory = File(AppConstants.OfflineTerrainDirectory)
        val hillshadeFile = File(terrainDirectory, "hillshade.mbtiles")
        if (hillshadeFile.exists() && hillshadeFile.length() > 0) hillshadeFile else null
    }

    companion object {
        private const val TAG = "TerrainLocalDataSource"
    }
}
