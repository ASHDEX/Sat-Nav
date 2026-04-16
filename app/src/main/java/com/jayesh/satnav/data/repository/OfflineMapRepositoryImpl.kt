package com.jayesh.satnav.data.repository

import android.content.Context
import android.util.Log
import com.jayesh.satnav.core.utils.AppDispatchers
import com.jayesh.satnav.data.local.maps.MbtilesArchive
import com.jayesh.satnav.data.local.maps.MbtilesFileDataSource
import com.jayesh.satnav.data.local.maps.MbtilesLocalHttpServer
import com.jayesh.satnav.domain.model.OfflineMapLoadStatus
import com.jayesh.satnav.domain.model.OfflineMapState
import com.jayesh.satnav.domain.model.OfflineTileFormat
import com.jayesh.satnav.domain.repository.OfflineMapRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

@Singleton
class OfflineMapRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers,
    private val mbtilesFileDataSource: MbtilesFileDataSource,
) : OfflineMapRepository {

    private val mutableState = MutableStateFlow(
        OfflineMapState(
            debugMessage = "Import an MBTiles package or copy map.mbtiles to ${mbtilesFileDataSource.mapsDirectoryPath()}",
        ),
    )
    override val state: StateFlow<OfflineMapState> = mutableState.asStateFlow()

    private var archive: MbtilesArchive? = null
    private var localHttpServer: MbtilesLocalHttpServer? = null

    override suspend fun refresh() {
        withContext(appDispatchers.io) {
            loadCurrentMapPackage()
        }
    }

    override suspend fun importFromUri(uriString: String): Result<Unit> {
        return withContext(appDispatchers.io) {
            mutableState.update {
                it.copy(
                    loadStatus = OfflineMapLoadStatus.Importing,
                    importInProgress = true,
                    errorMessage = null,
                    debugMessage = "Importing MBTiles into app storage…",
                )
            }

            mbtilesFileDataSource.importFromUri(uriString).mapCatching {
                loadCurrentMapPackage()
            }.onFailure { error ->
                Log.e(TAG, "MBTiles import failed", error)
                mutableState.update {
                    it.copy(
                        loadStatus = OfflineMapLoadStatus.Error,
                        importInProgress = false,
                        errorMessage = error.message,
                        debugMessage = "Import failed. Pick a valid .mbtiles file.",
                    )
                }
            }
        }
    }

    private suspend fun loadCurrentMapPackage() {
        val file = mbtilesFileDataSource.resolveExistingMapFile()
        if (file == null) {
            archive?.close()
            archive = null
            stopServer()
            mutableState.value = OfflineMapState(
                loadStatus = OfflineMapLoadStatus.Missing,
                debugMessage = "No MBTiles file found. Import one or copy map.mbtiles to ${mbtilesFileDataSource.mapsDirectoryPath()}",
            )
            return
        }

        val loadedArchive = MbtilesArchive.open(file).getOrElse { error ->
            Log.e(TAG, "Failed to open MBTiles archive: ${error.message}", error)
            
            // If the error indicates corruption or malformed data, and it's in our internal directory, 
            // we try to delete it so resolveExistingMapFile() can re-extract from assets.
            if (error.message?.contains("malformed", ignoreCase = true) == true || 
                error.message?.contains("corruption", ignoreCase = true) == true) {
                if (file.absolutePath.contains(context.filesDir.absolutePath)) {
                    Log.w(TAG, "Corrupt internal MBTiles detected. Deleting for re-extraction: ${file.name}")
                    file.delete()
                    // Retry once to re-extract from assets
                    val retriedFile = mbtilesFileDataSource.resolveExistingMapFile()
                    if (retriedFile != null) {
                        MbtilesArchive.open(retriedFile).getOrNull()?.let { retriedArchive ->
                            archive?.close()
                            archive = retriedArchive
                            restartServer(retriedArchive)
                            return
                        }
                    }
                }
            }

            stopServer()
            mutableState.value = OfflineMapState(
                loadStatus = OfflineMapLoadStatus.Error,
                errorMessage = error.message,
                debugMessage = "Failed to open MBTiles archive. It may be corrupt.",
            )
            return
        }

        // Temporarily allow vector tiles for testing
        // if (loadedArchive.mapPackage.format == OfflineTileFormat.VectorPbf ||
        //     loadedArchive.mapPackage.format == OfflineTileFormat.Unknown
        // ) {
        //     archive?.close()
        //     archive = loadedArchive
        //     stopServer()
        //     mutableState.value = OfflineMapState(
        //         loadStatus = OfflineMapLoadStatus.Unsupported,
        //         mapPackage = loadedArchive.mapPackage,
        //         errorMessage = "This Phase 3 implementation supports raster MBTiles. Vector or unknown tile encodings need an accompanying offline style/glyph pipeline.",
        //         debugMessage = "Unsupported MBTiles format: ${loadedArchive.mapPackage.format.name}.",
        //     )
        //     return
        // }

        archive?.close()
        archive = loadedArchive
        restartServer(loadedArchive)
    }

    private fun restartServer(loadedArchive: MbtilesArchive) {
        stopServer()
        val server = MbtilesLocalHttpServer(context, loadedArchive)
        server.startServer()
        localHttpServer = server
        val formatLabel = when (loadedArchive.mapPackage.format) {
            OfflineTileFormat.VectorPbf -> "vector"
            OfflineTileFormat.RasterPng,
            OfflineTileFormat.RasterJpeg,
            OfflineTileFormat.RasterWebp -> "raster"
            OfflineTileFormat.Unknown -> "offline"
        }
        mutableState.value = OfflineMapState(
            loadStatus = OfflineMapLoadStatus.Ready,
            mapPackage = loadedArchive.mapPackage,
            styleUrl = server.styleUrl(),
            tilesJsonUrl = server.tileJsonUrl(),
            debugMessage = "Offline $formatLabel map ready from ${loadedArchive.mapPackage.fileName}",
            importInProgress = false,
        )
    }

    private fun stopServer() {
        localHttpServer?.stop()
        localHttpServer = null
    }

    private companion object {
        const val TAG = "OfflineMapRepository"
    }
}
