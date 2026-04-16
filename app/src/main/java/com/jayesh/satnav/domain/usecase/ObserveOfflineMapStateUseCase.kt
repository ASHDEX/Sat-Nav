package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.model.OfflineMapState
import com.jayesh.satnav.domain.repository.OfflineMapRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

class ObserveOfflineMapStateUseCase @Inject constructor(
    private val offlineMapRepository: OfflineMapRepository,
) {
    operator fun invoke(): StateFlow<OfflineMapState> = offlineMapRepository.state
}
