package com.jayesh.satnav.data.local.maps

/**
 * Interface for a tile server that can be notified to rescan its sources
 * when offline map files change (e.g., after deletion).
 */
interface TileServer {
    /**
     * Notifies the tile server that the underlying map files may have changed.
     * The server should rescan its sources and update accordingly.
     */
    fun reload()
}