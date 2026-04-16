package com.jayesh.satnav.domain.model

enum class OfflineDatasetType {
    MapTiles,
    GraphHopperGraph,
    TerrainDem,
    TerrainHillshade,
}

data class OfflineDataset(
    val id: String,
    val type: OfflineDatasetType,
    val isAvailable: Boolean,
    val description: String,
)

data class OfflineReadiness(
    val totalDatasets: Int,
    val availableDatasets: Int,
    val missingDatasets: List<OfflineDataset>,
) {
    val isReady: Boolean = missingDatasets.isEmpty()
}

