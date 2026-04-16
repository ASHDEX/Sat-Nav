package com.jayesh.satnav.domain.routing

import com.jayesh.satnav.core.utils.LatLng
import com.jayesh.satnav.domain.model.RouteOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RoutingException(message: String) : Exception(message)
class NoRouteException(message: String) : Exception(message)

class RoutePlanner @Inject constructor() {
    suspend fun plan(
        start: LatLng,
        destination: LatLng,
        profile: String = "car",
    ): Result<List<RouteOption>> = withContext(Dispatchers.Default) {
        runCatching {
            if (start.latitude == destination.latitude && start.longitude == destination.longitude) {
                return@runCatching emptyList()
            }
            return@runCatching emptyList()
        }
    }
}
