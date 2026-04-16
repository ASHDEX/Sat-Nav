package com.jayesh.satnav.data.local.graphhopper

import android.content.Context
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.domain.model.OfflineDataset
import com.jayesh.satnav.domain.model.OfflineDatasetType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class GraphHopperLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    suspend fun getDatasets(): List<OfflineDataset> = listOf(
        OfflineDataset(
            id = AppConstants.OfflineGraphHopperDirectory,
            type = OfflineDatasetType.GraphHopperGraph,
            isAvailable = resolveExistingGraphDirectory() != null,
            description = "Prebuilt routing graph-cache for offline path calculation.",
        ),
    )

    fun resolveExistingGraphDirectory(): File? {
        return candidateGraphDirectories()
            .firstOrNull(::isUsableGraphDirectory)
    }

    fun recommendedGraphDirectoryPath(): String = appGraphCacheDirectory().absolutePath

    fun candidateGraphDirectoryPaths(): List<String> {
        return candidateGraphDirectories().map { file -> file.absolutePath }
    }

    private fun candidateGraphDirectories(): List<File> {
        return listOf(
            appGraphCacheDirectory(),
            appGraphRootDirectory(),
            File(AppConstants.SharedGraphCacheAbsolutePath),
        ).distinctBy { file -> file.absolutePath }
    }

    private fun appGraphCacheDirectory(): File {
        val graphRoot = appGraphRootDirectory()
        if (!graphRoot.exists()) {
            graphRoot.mkdirs()
        }
        val graphCache = File(graphRoot, AppConstants.OfflineGraphCacheDirectory)
        if (!graphCache.exists()) {
            graphCache.mkdirs()
        }
        return graphCache
    }

    private fun appGraphRootDirectory(): File {
        return context.getExternalFilesDir(AppConstants.OfflineGraphHopperDirectory)
            ?: File(context.filesDir, AppConstants.OfflineGraphHopperDirectory)
    }

    private fun isUsableGraphDirectory(directory: File): Boolean {
        if (!directory.exists() || !directory.isDirectory || !directory.canRead()) {
            return false
        }

        val children = directory.listFiles().orEmpty()
        if (children.isEmpty()) {
            return false
        }

        return children.any { child ->
            child.isFile && (
                child.name == "properties" ||
                    child.name == "properties.txt" ||
                child.name.endsWith(".properties", ignoreCase = true) ||
                    child.name.endsWith(".gh", ignoreCase = true) ||
                    child.name == "edges" ||
                    child.name == "geometry" ||
                    child.name == "location_index"
                )
        }
    }
}
