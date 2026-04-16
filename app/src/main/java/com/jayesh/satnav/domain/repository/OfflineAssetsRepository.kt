package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.OfflineDataset

interface OfflineAssetsRepository {
    suspend fun getOfflineDatasets(): List<OfflineDataset>
}

