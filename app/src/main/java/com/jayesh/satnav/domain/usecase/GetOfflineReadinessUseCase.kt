package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.model.OfflineReadiness
import com.jayesh.satnav.domain.repository.OfflineAssetsRepository
import javax.inject.Inject

class GetOfflineReadinessUseCase @Inject constructor(
    private val offlineAssetsRepository: OfflineAssetsRepository,
) {

    suspend operator fun invoke(): OfflineReadiness {
        val datasets = offlineAssetsRepository.getOfflineDatasets()
        val missing = datasets.filterNot { it.isAvailable }
        return OfflineReadiness(
            totalDatasets = datasets.size,
            availableDatasets = datasets.size - missing.size,
            missingDatasets = missing,
        )
    }
}

