package com.jayesh.satnav.data.local.maps

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.jayesh.satnav.core.constants.AppConstants
import com.jayesh.satnav.core.utils.AppDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class MbtilesFileDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val appDispatchers: AppDispatchers,
) {
    private val migrationMutex = Mutex()

    suspend fun resolveExistingMapFile(): File? = withContext(appDispatchers.io) {
        migrationMutex.withLock {
            val internalFile = defaultMapFile()
        val externalFile = externalMapFile()

        // 1. One-time migration: If we have an external file but no internal file, move it.
        if (externalFile.exists() && (!internalFile.exists() || internalFile.length() == 0L)) {
            Log.i(TAG, "Migrating map from external to internal storage: ${externalFile.name}")
            try {
                externalFile.renameTo(internalFile)
                // If rename fails (different filesystems), try copy + delete
                if (!internalFile.exists()) {
                    externalFile.inputStream().use { input ->
                        internalFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    externalFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Migration failed", e)
            }
        }

        // 2. Check internal storage (primary)
        if (internalFile.exists() && internalFile.isFile && internalFile.canRead()) {
            return@withContext internalFile
        }

        // 3. Extract bundled map.mbtiles from assets on first run to internal storage
        try {
            context.assets.open("maps/map.mbtiles").use { inputStream ->
                internalFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (internalFile.exists() && internalFile.isFile && internalFile.canRead()) {
                return@withContext internalFile
            }
        } catch (ignored: Exception) {
            // No bundled assets available, proceed normally
        }

        mapsDirectory()
            .listFiles()
            ?.firstOrNull { it.isFile && it.extension.equals("mbtiles", ignoreCase = true) }
        }
    }

    suspend fun importFromUri(uriString: String): Result<File> = withContext(appDispatchers.io) {
        runCatching {
            val uri = uriString.toUri()
            val targetFile = defaultMapFile()
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: error("Unable to open selected MBTiles document.")

            if (targetFile.exists()) {
                targetFile.delete()
            }
            check(tempFile.renameTo(targetFile)) {
                "Failed to move imported MBTiles into app storage."
            }

            Log.i(TAG, "Imported MBTiles to ${targetFile.absolutePath}")
            targetFile
        }
    }

    fun mapsDirectoryPath(): String = mapsDirectory().absolutePath

    private fun defaultMapFile(): File = File(mapsDirectory(), AppConstants.OfflineMapsFileName)

    private fun externalMapFile(): File {
        val dir = context.getExternalFilesDir(AppConstants.OfflineMapsDirectory) ?: return File("")
        return File(dir, AppConstants.OfflineMapsFileName)
    }

    private fun mapsDirectory(): File {
        // ALWAYS use internal storage for production reliability on newer Android versions (SELinux restrictions)
        val baseDirectory = File(context.filesDir, AppConstants.OfflineMapsDirectory)
        if (!baseDirectory.exists()) {
            baseDirectory.mkdirs()
        }
        return baseDirectory
    }

    private companion object {
        const val TAG = "MbtilesFileDataSource"
    }
}
