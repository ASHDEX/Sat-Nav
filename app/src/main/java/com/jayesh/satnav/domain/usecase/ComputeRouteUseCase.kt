package com.jayesh.satnav.domain.usecase

import com.jayesh.satnav.domain.model.RouteCoordinate
import com.jayesh.satnav.domain.model.RoutingProfile
import com.jayesh.satnav.domain.repository.RoutingRepository
import javax.inject.Inject

class ComputeRouteUseCase @Inject constructor(
    private val routingRepository: RoutingRepository,
) {
    suspend operator fun invoke(
        points: List<RouteCoordinate>,
        profile: RoutingProfile = RoutingProfile.CAR,
    ) = routingRepository.computeRoute(points, profile)
}
