package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.repository.OfflineMapRepository
import javax.inject.Inject

class ImportOfflineMapUseCase @Inject constructor(
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke(uriString: String): Result<Unit> {
        return offlineMapRepository.importFromUri(uriString)
    }
}
