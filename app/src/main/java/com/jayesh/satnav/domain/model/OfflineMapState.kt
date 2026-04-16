package com.jayesh.satnav.domain.model

enum class OfflineMapLoadStatus {
    Missing,
    Importing,
    Ready,
    Error,
    Unsupported,
}

enum class OfflineTileFormat {
    RasterPng,
    RasterJpeg,
    RasterWebp,
    VectorPbf,
    Unknown,
}

enum class OfflineTileScheme {
    XYZ,
    TMS,
}

data class OfflineMapPackage(
    val fileName: String,
    val absolutePath: String,
    val format: OfflineTileFormat,
    val scheme: OfflineTileScheme,
    val minZoom: Int,
    val maxZoom: Int,
    val bounds: List<Double> = emptyList(),
    val center: List<Double> = emptyList(),
    val tileSize: Int = 256,
    val attribution: String? = null,
    val description: String? = null,
)

data class OfflineMapState(
    val loadStatus: OfflineMapLoadStatus = OfflineMapLoadStatus.Missing,
    val mapPackage: OfflineMapPackage? = null,
    val styleUrl: String? = null,
    val tilesJsonUrl: String? = null,
    val debugMessage: String = "No offline MBTiles package loaded.",
    val errorMessage: String? = null,
    val importInProgress: Boolean = false,
)
