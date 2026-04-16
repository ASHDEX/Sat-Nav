package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.model.OfflineDatasetType
import com.jayesh.satnav.domain.repository.OfflineAssetsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GetOfflineReadinessUseCaseTest {

    @Test
    fun `returns missing datasets when assets are unavailable`() = runTest {
        val repository = object : OfflineAssetsRepository {
            override suspend fun getOfflineDatasets(): List<OfflineDataset> = listOf(
                OfflineDataset(
                    id = "maps",
                    type = OfflineDatasetType.MapTiles,
                    isAvailable = true,
                    description = "Maps",
                ),
                OfflineDataset(
                    id = "graphhopper",
                    type = OfflineDatasetType.GraphHopperGraph,
                    isAvailable = false,
                    description = "Graph",
                ),
            )
        }

        val result = GetOfflineReadinessUseCase(repository)()

        assertEquals(2, result.totalDatasets)
        assertEquals(1, result.availableDatasets)
        assertEquals(1, result.missingDatasets.size)
        assertFalse(result.isReady)
    }
}
