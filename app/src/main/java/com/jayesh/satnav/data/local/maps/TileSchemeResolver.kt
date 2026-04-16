package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.domain.model.OfflineTileScheme

object TileSchemeResolver {

    fun candidateRows(z: Int, y: Int, declaredScheme: OfflineTileScheme): List<Int> {
        val flipped = flipY(z, y)
        return when (declaredScheme) {
            OfflineTileScheme.XYZ -> listOf(y, flipped).distinct()
            OfflineTileScheme.TMS -> listOf(flipped, y).distinct()
        }
    }

    private fun flipY(z: Int, y: Int): Int {
        return (1 shl z) - 1 - y
    }
}

