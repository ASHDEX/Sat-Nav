package com.jayesh.satnav.data.local.maps

import com.jayesh.satnav.domain.model.OfflineMapPackage

interface OfflineStyleBuilder {
    fun build(tilesUrl: String, mapPackage: OfflineMapPackage): String
    fun buildTileJson(tilesUrl: String, mapPackage: OfflineMapPackage): String
}