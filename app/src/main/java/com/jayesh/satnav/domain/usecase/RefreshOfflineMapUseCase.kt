package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.repository.OfflineMapRepository
import javax.inject.Inject

class RefreshOfflineMapUseCase @Inject constructor(
    private val offlineMapRepository: OfflineMapRepository,
) {
    suspend operator fun invoke() {
        offlineMapRepository.refresh()
    }
}
