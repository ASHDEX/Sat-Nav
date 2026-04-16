package com.jayesh.satnav.domain.repository

import com.jayesh.satnav.domain.model.OfflineMapState
import kotlinx.coroutines.flow.StateFlow

interface OfflineMapRepository {
    val state: StateFlow<OfflineMapState>

    suspend fun refresh()

    suspend fun importFromUri(uriString: String): Result<Unit>
}
