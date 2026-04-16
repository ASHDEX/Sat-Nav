package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.repository.RoutingRepository
import javax.inject.Inject

class GetRoutingEngineStatusUseCase @Inject constructor(
    private val routingRepository: RoutingRepository,
) {
    suspend operator fun invoke() = routingRepository.getRoutingEngineStatus()
}
