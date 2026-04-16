package com.jayesh.satnav.data.local.search

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

/**
 * Minimal SQLiteOpenHelper for POI database.
 * This is a stub implementation to allow compilation.
 */
internal class PoiDatabaseHelper(
    context: Context,
    private val databasePath: String,
) : SQLiteOpenHelper(
    context,
    databasePath,
    null, // cursor factory
    1,
) {
    
    override fun onCreate(db: SQLiteDatabase) {
        // Create tables - stub implementation
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS pois (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                category TEXT NOT NULL,
                address TEXT,
                phone TEXT,
                website TEXT,
                opening_hours TEXT,
                rating REAL,
                tags TEXT,
                is_favorite INTEGER DEFAULT 0,
                last_visited INTEGER
            )
        """.trimIndent())
        
        // FTS5 is not available on all Android SQLite builds; use a plain index instead.
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pois_name ON pois(name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pois_category ON pois(category)")
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS favorites (
                poi_id TEXT PRIMARY KEY,
                added_timestamp INTEGER NOT NULL,
                custom_name TEXT,
                notes TEXT,
                tags TEXT
            )
        """.trimIndent())
        
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS recent_searches (
                id TEXT PRIMARY KEY,
                query TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                result_count INTEGER,
                location_latitude REAL,
                location_longitude REAL
            )
        """.trimIndent())
    }
    
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For now, just drop and recreate
        db.execSQL("DROP TABLE IF EXISTS pois")
        db.execSQL("DROP TABLE IF EXISTS favorites")
        db.execSQL("DROP TABLE IF EXISTS recent_searches")
        onCreate(db)
    }
    
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.execSQL("PRAGMA foreign_keys = ON")
        // WAL mode enabled via enableWriteAheadLogging() on the connection, not PRAGMA
    }
}

/**
 * Returns the POI database file inside the app's internal files directory.
 * Uses [Context.getFilesDir] which is always writable on Android.
 */
internal fun getDatabaseFile(context: Context): File {
    val poiDir = File(context.filesDir, "poi")
    if (!poiDir.exists()) {
        poiDir.mkdirs()
    }
    return File(poiDir, "poi.db")
}