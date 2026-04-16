# Satnav Architecture Documentation

## 1. Overview

Satnav is an offline GPS navigation Android application similar to Garmin, built with Jetpack Compose, MVVM, Hilt, and Kotlin Coroutines. The app currently provides basic offline map rendering using MapLibre with MBTiles served via a local NanoHTTPD server, GraphHopper routing engine integration for car routing, terrain visualization with DEM tiles, and a multi-screen navigation UI. Core functionality works: maps load offline tiles, routing computes paths, navigation provides turn-by-turn guidance. However, several subsystems are incomplete: POI search database lacks data import, AI route suggestions are stubbed, traffic patterns are unimplemented, and the navigation engine lacks proper map-matching and deviation detection.

## 2. Module & package structure

```
app/src/main/java/com/jayesh/satnav/
├── core/
│   ├── constants/AppConstants.kt              # App-wide constants (ports, directories)
│   └── utils/
│       ├── AppDispatchers.kt                  # Coroutine dispatchers wrapper
│       └── NavLog.kt                          # Logging utility
├── data/
│   ├── local/
│   │   ├── graphhopper/
│   │   │   ├── GraphHopperLocalDataSource.kt  # GraphHopper file system operations
│   │   │   └── GraphHopperManager.kt          # GraphHopper engine lifecycle & routing
│   │   ├── maps/
│   │   │   ├── MapsLocalDataSource.kt         # MBTiles file discovery
│   │   │   ├── MbtilesArchive.kt              # MBTiles SQLite access
│   │   │   ├── MbtilesFileDataSource.kt       # MBTiles file operations
│   │   │   ├── MbtilesLocalHttpServer.kt      # NanoHTTPD server for tile serving
│   │   │   ├── OfflineRasterStyleBuilder.kt   # Raster style JSON generation
│   │   │   ├── OfflineStyleBuilder.kt         # Base style builder interface
│   │   │   ├── OfflineVectorStyleBuilder.kt   # Vector style JSON generation
│   │   │   └── TileSchemeResolver.kt          # Tile URL scheme resolution
│   │   ├── persistence/
│   │   │   └── NavigationStatePersistence.kt  # SavedStateHandle wrapper for nav state
│   │   ├── search/
│   │   │   ├── PoiDatabaseHelper.kt           # SQLite POI database helper
│   │   │   └── PoiLocalDataSource.kt          # POI database operations
│   │   └── terrain/
│   │       ├── TerrainLocalDataSource.kt      # DEM tile file operations
│   │       ├── TerrainLocalHttpServer.kt      # NanoHTTPD server for terrain tiles
│   │       └── TerrainStyleBuilder.kt         # Terrain style JSON generation
│   ├── repository/
│   │   ├── OfflineAssetsRepositoryImpl.kt     # Asset file operations
│   │   ├── OfflineMapRepositoryImpl.kt        # Map tile serving orchestration
│   │   ├── RoutingRepositoryImpl.kt           # GraphHopper routing facade
│   │   ├── SearchRepositoryImpl.kt            # POI search implementation
│   │   └── TrafficPatternRepositoryImpl.kt    # Traffic patterns (stubbed)
│   └── domain/
│       ├── model/                             # Data classes for all domain models
│       ├── repository/                        # Repository interfaces
│       └── usecase/                           # Business logic use cases
├── di/
│   ├── AppModule.kt                           # Singleton bindings
│   └── PersistenceModule.kt                   # ViewModel-scoped persistence
├── features/
│   ├── ai/                                    # AI route suggestions (stubbed)
│   ├── demo/                                  # Trip demonstration screen
│   ├── location/                              # GPS location management
│   ├── map/                                   # Main map screen
│   ├── navigation/                            # Turn-by-turn navigation
│   ├── routing/                               # Route planning screen
│   ├── search/                                # POI search screen
│   ├── traffic/                               # Traffic patterns (stubbed)
│   └── transport/                             # Multi-modal routing (stubbed)
├── ui/
│   ├── components/FeaturePlaceholderCard.kt   # UI component for placeholder features
│   ├── screens/SatnavNavHost.kt               # Navigation host with bottom bar
│   └── theme/                                 # Compose theming
├── MainActivity.kt                            # Single Activity entry point
└── SatnavApp.kt                               # Application class with MapLibre init
```

## 3. Dependency inventory

| Name | Version | Purpose | Initialization / Primary Use |
|------|---------|---------|-----------------------------|
| MapLibre Android SDK | 12.3.1 | Offline map rendering | `SatnavApp.onCreate()` |
| GraphHopper Core | 1.0 | Offline routing engine | `GraphHopperManager.loadEngine()` |
| NanoHTTPD | 2.3.1 | Local HTTP server for MBTiles/DEM tiles | `MbtilesLocalHttpServer.startServer()` |
| Hilt | 2.57 | Dependency injection | `@HiltAndroidApp`, `@AndroidEntryPoint` |
| Jetpack Compose | BOM 2025.10.00 | Declarative UI toolkit | All `@Composable` screens |
| Navigation Compose | 2.9.5 | Screen navigation | `SatnavNavHost` |
| Coroutines Android | 1.10.2 | Asynchronous programming | All ViewModels, repositories |
| Play Services Location | 21.3.0 | FusedLocationProvider | `GpsLocationManager` |
| Kotlinx Serialization | 1.10.0 | JSON parsing | Style building |
| Kotlinx DateTime | 0.6.0 | Date/time handling | Navigation timing |

## 4. Entry points

- **Application class**: `SatnavApp` – Hilt-enabled, initializes MapLibre instance.
- **MainActivity**: `MainActivity` – Single Activity hosting `SatnavNavHost` Compose content with edge-to-edge UI.
- **Services**: None currently; all background work via coroutines.
- **BroadcastReceivers/WorkManager**: None implemented.
- **Foreground service**: Not yet implemented for ongoing navigation.

## 5. Screens

| Screen | File | ViewModel | Purpose | State source | Known issues |
|--------|------|-----------|---------|--------------|--------------|
| Map | `MapScreen.kt` | `MapViewModel` | Primary map view, offline tile loading | `OfflineMapRepository`, `GpsLocationManager` | Camera controls could be smoother |
| Routing | `RoutingScreen.kt` | `RoutingViewModel` | Route planning between points | `RoutingRepository` | UI lacks waypoint editing |
| Navigation | `NavigationScreen.kt` | `NavigationViewModel` | Turn-by-turn navigation | `NavigationEngine`, `GpsLocationManager` | No map-matching, deviation detection basic |
| Location | `LocationScreen.kt` | `LocationViewModel` | GPS status and raw coordinates | `GpsLocationManager` | Minimal UI |
| Search | `SearchScreen.kt` | `SearchViewModel` | POI search and selection | `SearchRepository` | Database empty, import not implemented |
| Trip Demonstration | `TripDemonstrationScreen.kt` | `TripDemonstrationViewModel` | Demo route playback | Hardcoded route | Feedback button non-functional (TODO) |
| AI Route Suggestions | `AiRouteSuggestionsScreen.kt` | `AiRouteSuggestionsViewModel` | AI‑based route alternatives | Stubbed data | Entire feature is placeholder |

## 6. ViewModels

### MapViewModel (`features/map/MapViewModel.kt`)
- **Injected dependencies**: `GetOfflineReadinessUseCase`, `ObserveOfflineMapStateUseCase`, `ImportOfflineMapUseCase`, `RefreshOfflineMapUseCase`, `GpsLocationManager`, `NavigationCameraController`
- **Exposed StateFlow**: `MapUiState` – contains camera state, offline status, user position, search pin
- **Public intents**: `onMapClick`, `moveCamera`, `importMap`, `refreshMap`, `onSearchConsumed`
- **SavedStateHandle**: No

### RoutingViewModel (`features/routing/RoutingViewModel.kt`)
- **Injected dependencies**: `ComputeRouteUseCase`, `GetRoutingEngineStatusUseCase`
- **Exposed StateFlow**: `RoutingUiState` – route result, waypoints, profile, loading state
- **Public intents**: `addWaypoint`, `removeWaypoint`, `clearWaypoints`, `computeRoute`, `setProfile`
- **SavedStateHandle**: No

### NavigationViewModel (`features/navigation/NavigationViewModel.kt`)
- **Injected dependencies**: `NavigationEngine`, `NavigationCameraController`, `GpsLocationManager`
- **Exposed StateFlow**: `NavigationUiState` – current instruction, progress, route, state
- **Public intents**: `startNavigation`, `stopNavigation`, `skipInstruction`
- **SavedStateHandle**: No

### SearchViewModel (`features/search/SearchViewModel.kt`)
- **Injected dependencies**: `SearchRepository`
- **Exposed StateFlow**: `SearchUiState` – query, results, categories, loading
- **Public intents**: `updateQuery`, `search`, `selectCategory`, `toggleFavorite`
- **SavedStateHandle**: No

### LocationViewModel (`features/location/LocationViewModel.kt`)
- **Injected dependencies**: `GpsLocationManager`
- **Exposed StateFlow**: `LocationUiState` – position, accuracy, bearing, GPS status
- **Public intents**: `requestLocationUpdates`, `stopLocationUpdates`
- **SavedStateHandle**: No

### TripDemonstrationViewModel (`features/demo/TripDemonstrationViewModel.kt`)
- **Injected dependencies**: None
- **Exposed StateFlow**: `TripDemonstrationUiState` – playback state, progress, route
- **Public intents**: `startPlayback`, `pausePlayback`, `seekTo`
- **SavedStateHandle**: No

### AiRouteSuggestionsViewModel (`features/ai/AiRouteSuggestionsViewModel.kt`)
- **Injected dependencies**: None (stubbed)
- **Exposed StateFlow**: `AiRouteSuggestionsUiState` – placeholder suggestions
- **Public intents**: `loadSuggestions`, `selectSuggestion`
- **SavedStateHandle**: No

## 7. Data layer

### Repositories
- **OfflineMapRepository** (`domain/repository/OfflineMapRepository.kt`) – Interface for map tile availability and serving.
  - Implementation: `OfflineMapRepositoryImpl` – Orchestrates `MapsLocalDataSource` and `MbtilesLocalHttpServer`.
- **RoutingRepository** (`domain/repository/RoutingRepository.kt`) – Interface for route computation.
  - Implementation: `RoutingRepositoryImpl` – Delegates to `GraphHopperManager`.
- **SearchRepository** (`domain/repository/SearchRepository.kt`) – Interface for POI search, favorites, recent searches.
  - Implementation: `SearchRepositoryImpl` – Uses `PoiLocalDataSource` (SQLite).
- **OfflineAssetsRepository** (`domain/repository/OfflineAssetsRepository.kt`) – Interface for asset file operations.
  - Implementation: `OfflineAssetsRepositoryImpl` – Copies assets to external storage.
- **TrafficPatternRepository** (`domain/repository/TrafficPatternRepository.kt`) – Interface for traffic data (stubbed).
  - Implementation: `TrafficPatternRepositoryImpl` – Returns empty data.

### Local persistence
- **SQLite POI database**: `poi.db` in `satnav/poi/` directory. Tables: `pois`, `favorites`, `recent_searches`. Managed by `PoiDatabaseHelper`.
- **Navigation state**: Via `NavigationStatePersistence` using `SavedStateHandle` (ViewModel-scoped).
- **Raw files**: MBTiles (`.mbtiles`), DEM tiles (`.png`), GraphHopper graph‑cache files in external storage.

### Search index
- **Location**: SQLite database with spatial indexing via `R-Tree` virtual table (`pois_rtree`).
- **Build**: Currently empty; import functions are TODO (GeoJSON/OSM PBF parsing not implemented).
- **Query**: `PoiLocalDataSource` executes SQL queries with bounding box filters and full‑text search on `name` and `categories`.

## 8. Maps subsystem

- **MapLibre initialization**: `SatnavApp.onCreate()` calls `MapLibre.getInstance(this)`.
- **Style JSONs**: Three styles in `app/src/main/assets/styles/`:
  - `offline_vector_style.json` – Vector tile styling.
  - `offline_raster_style.json` – Raster tile styling.
  - `offline_terrain_style.json` – Hillshade/DEM styling.
- **MapView in Compose**: `MapLibreMapView.kt` wraps `org.maplibre.android.maps.MapView` in `AndroidView`. Handles lifecycle callbacks.
- **Camera handling**: `NavigationCameraController` provides smooth camera transitions for navigation.
- **Custom layers/sources**: None beyond standard vector/raster/terrain sources.

## 9. Tile serving

- **NanoHTTPD start/stop**: `MbtilesLocalHttpServer.startServer()` called by `OfflineMapRepositoryImpl` when map package is loaded. Server runs until app process dies.
- **Port**: `38765` (defined in `AppConstants.OfflineTileServerPort`).
- **MBTiles discovery**: `MapsLocalDataSource` searches external storage `satnav/maps/` and assets `maps/` for `map.mbtiles`.
- **URL template**: `http://127.0.0.1:38765/tiles/{z}/{x}/{y}.pbf` (vector) or `.png` (raster).
- **Lifecycle**: Server starts when map screen loads a valid MBTiles file; stops when app is killed (no explicit stop). Should be tied to `Service` or `ForegroundService` for navigation but currently isn't.

## 10. Routing subsystem

- **GraphHopper initialization**: `GraphHopperManager.loadEngine()` loads graph‑cache from directory.
- **OSM PBF file**: Not loaded on device; graph‑cache must be pre‑built on desktop and placed in `satnav/graph‑cache/`.
- **Graph cache location**: External storage `satnav/graph‑cache/` (fallback to internal `graphhopper/`).
- **Profiles**: Only `car` profile is configured (`Profile("car")`).
- **RoutePlanner**: `GraphHopperManager.computeRoute()` creates `GHRequest`, uses `hopper.route()`.
- **Alternatives**: Not requested (`ghRequest.setAlgorithm("dijkstra")`).

## 11. Location & sensors

- **GPS provider**: `GpsLocationManager` uses FusedLocationProviderClient (`PlayServicesLocation`).
- **Permissions**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` declared in `AndroidManifest.xml`.
- **Bearing/heading**: Provided via `Location.bearing`; used in navigation for camera rotation.
- **Update intervals**: `LocationRequest.PRIORITY_HIGH_ACCURACY` with 1‑second interval.

## 12. Navigation graph

- **NavHost location**: `ui/screens/SatnavNavHost.kt`.
- **Routes**:
  - `map` – Main map screen (start destination).
  - `routing` – Route planning screen.
  - `navigation` – Turn‑by‑turn navigation screen.
  - `location` – GPS status screen.
  - `search` – POI search screen (modal).
  - `demonstration` – Trip demonstration screen (modal).
- **Arguments**: None; `selected_poi_lat`/`selected_poi_lon` passed via `SavedStateHandle` from search to map.
- **Deep links**: None.

## 13. Threading model

- **Dispatchers**: `AppDispatchers` provides `Main`, `IO`, `Default`. Used consistently in repositories and use cases.
- **Main thread violations**: None observed; all suspend functions specify `withContext(appDispatchers.io)` for IO work.
- **Blocking work without dispatcher**: GraphHopper route computation runs on calling thread (should be `Default`). `GraphHopperManager` uses `appDispatchers.io` for `loadEngine` but `computeRoute` does not switch dispatchers (potential UI freeze).
- **Coroutine scopes**: ViewModels use `viewModelScope`; repositories inject dispatchers.

## 14. DI graph

- **AppModule** (`di/AppModule.kt`) – Singleton bindings for repositories and `AppDispatchers`.
- **PersistenceModule** (`di/PersistenceModule.kt`) – ViewModel‑scoped `NavigationStatePersistence`.
- **Missing bindings**: `SearchRepository` not bound in any module (compile‑time error). `PoiLocalDataSource`, `GraphHopperLocalDataSource`, `MapsLocalDataSource`, `TerrainLocalDataSource` not provided via DI (currently instantiated directly).
- **Scoping**: All repositories are `@Singleton`. ViewModels are unscoped (created per navigation).

## 15. Known issues (CRITICAL SECTION)

1. **[ARCH] SearchRepository not bound in DI** (`SearchRepositoryImpl` is not provided via Hilt).
   File: `di/AppModule.kt` (missing `@Binds` for `SearchRepository`).
   Impact: `SearchViewModel` will fail to inject.

2. **[LOGIC] POI database empty – import not implemented**
   File: `data/repository/SearchRepositoryImpl.kt:239,251` – TODO comments for GeoJSON/OSM PBF parsing.
   Impact: Search returns zero results.

3. **[ARCH] Local data sources not managed by DI** (`PoiLocalDataSource`, `GraphHopperLocalDataSource`, etc.) are instantiated directly inside repositories.
   File: `data/repository/SearchRepositoryImpl.kt:24` – `PoiLocalDataSource` constructed with context.
   Impact: Hard to test, violates DI principles.

4. **[PERF] GraphHopper route computation blocks calling thread**
   File: `data/local/graphhopper/GraphHopperManager.kt` – `computeRoute()` does not switch dispatchers.
   Impact: Potential UI freeze during route calculation.

5. **[BUG] Navigation engine lacks map‑matching**
   File: `features/navigation/NavigationEngine.kt` – GPS points are snapped to nearest route segment but algorithm is naive.
   Impact: Position may jump incorrectly on complex roads.

6. **[UI] Trip demonstration feedback button non‑functional**
   File: `features/demo/TripDemonstrationScreen.kt:922` – TODO comment.
   Impact: User cannot submit feedback.

7. **[ARCH] Tile server lifecycle unmanaged** – `MbtilesLocalHttpServer` starts but never stops explicitly.
   File: `data/local/maps/MbtilesLocalHttpServer.kt` – No `stopServer()` call in app lifecycle.
   Impact: Port may remain bound after app death.

8. **[LOGIC] Only car profile supported** – GraphHopper configured with single profile.
   File: `data/local/graphhopper/GraphHopperManager.kt` – `Profile("car")` only.
   Impact: No bike/pedestrian routing.

9. **[PERF] MBTiles entire style loaded into memory** – `OfflineStyleBuilder` reads entire JSON asset as string.
   File: `data/local/maps/OfflineStyleBuilder.kt` – `build()` loads full style.
   Impact: Memory overhead for large styles.

10. **[ARCH] Missing foreground service for navigation** – No `ForegroundService` to keep GPS active during navigation.
    File: None – feature not implemented.
    Impact: Navigation may be killed in background.

## 16. Glossary of names

| Name | Kind | File |
|------|------|------|
| `SatnavApp` | class | `SatnavApp.kt` |
| `MainActivity` | class | `MainActivity.kt` |
| `SatnavNavHost` | composable | `ui/screens/SatnavNavHost.kt` |
| `MapScreen` | composable | `features/map/MapScreen.kt` |
| `MapViewModel` | ViewModel | `features/map/MapViewModel.kt` |
| `MapLibreMapView` | AndroidView wrapper | `features/map/MapLibreMapView.kt` |
| `RoutingScreen` | composable | `features/routing/RoutingScreen.kt` |
| `RoutingViewModel` | ViewModel | `features/routing/RoutingViewModel.kt` |
| `NavigationScreen` | composable | `features/navigation/NavigationScreen.kt` |
| `NavigationViewModel` | ViewModel | `features/navigation/NavigationViewModel.kt` |
| `NavigationEngine` | class | `features/navigation/NavigationEngine.kt` |
| `NavigationCameraController` | class | `features/navigation/NavigationCameraController.kt` |
| `SearchScreen` | composable | `features/search/SearchScreen.kt` |
| `SearchViewModel` | ViewModel | `features/search/SearchViewModel.kt` |
| `SearchRepository` | interface | `domain/repository/SearchRepository.kt` |
| `SearchRepositoryImpl` | class | `data/repository/SearchRepositoryImpl.kt` |
| `PoiLocalDataSource` | class | `data/local/search/PoiLocalDataSource.kt` |
| `PoiDatabaseHelper` | class | `data/local/search/PoiDatabaseHelper.kt` |
| `OfflineMapRepository` | interface | `domain/repository/OfflineMapRepository.kt` |
| `OfflineMapRepositoryImpl` | class | `data/repository/OfflineMapRepositoryImpl.kt` |
| `MbtilesLocalHttpServer` | class | `data/local/maps/MbtilesLocalHttpServer.kt` |
| `GraphHopperManager` | class | `data/local/graphhopper/GraphHopperManager.kt` |
| `GraphHopperLocalDataSource` | class | `data/local/graphhopper/GraphHopperLocalDataSource.kt` |
| `GpsLocationManager` | class | `features/location/GpsLocationManager.kt` |
| `AppDispatchers` | interface | `core/utils/AppDispatchers.kt` |
| `DefaultAppDispatchers` | class | `core/utils/AppDispatchers.kt` |
| `AppModule` | Dagger module | `di/AppModule.kt` |
| `PersistenceModule` | Dagger module | `di/PersistenceModule.kt` |

## 17. Release Preparation Updates

### 17.1 GPX Replay Debug Tool
- **Purpose**: Enable testing navigation without driving by replaying recorded GPX traces
- **Location**: `src/debug/java/com/jayesh/satnav/`
- **Components**:
  - `FakeLocationRepository`: Reads GPX files from assets/gpx/ and emits Location fixes at real time or configurable speed
  - `DebugLocationModule`: Hilt module that binds FakeLocationRepository instead of real one when debug flag is set
  - `DebugSettingsScreen`: Accessible from Settings in debug builds only with GPX replay controls
  - `DebugSettingsViewModel`: Manages debug settings state and GPX replay operations
- **Configuration**: DataStore key "debug_use_gpx_replay" controls whether to use GPX replay
- **Assets**: Sample GPX trace in `assets/gpx/gurugram_short_route.gpx` covering a short route in Gurugram

### 17.2 Instrumentation Tests
- **Location**: `src/androidTest/java/com/jayesh/satnav/CriticalFlowTest.kt`
- **Test Flow**:
  1. Launch app
  2. Tap search bar
  3. Type "connaught"
  4. Tap first result
  5. Wait for RoutePreview to load
  6. Assert 1–3 route chips visible
  7. Tap "Start"
  8. Assert NavigationScreen visible with maneuver banner
  9. Using GPX replay at 10x speed, wait for Arrived state
  10. Assert arrival screen
- **Dependencies**: Hilt Android Testing, Compose UI Testing

### 17.3 ProGuard/R8 Rules Enhancement
- **Updated File**: `app/proguard-rules.pro`
- **Key Additions**:
  - **GraphHopper Core**: Enhanced rules for reflection-based JSON/graph loading
  - **MapLibre Android SDK**: Preserved native methods and style classes
  - **Kotlinx Serialization**: Comprehensive rules for @Serializable classes and generated serializers
  - **NanoHTTPD**: Preserved server classes and methods
- **Verification**: Release build runs end-to-end with minification enabled

### 17.4 Release Signing Setup
- **Template**: `keystore.properties.template` at repo root
- **Configuration**: `app/build.gradle.kts` reads keystore.properties if present
- **Security**: keystore.properties and *.jks added to `.gitignore`
- **Properties**:
  - `storeFile`: Path to keystore file
  - `storePassword`: Keystore password
  - `keyAlias`: Key alias
  - `keyPassword`: Key password

### 17.5 Documentation
- **CHANGELOG.md**: Keep-a-Changelog format documenting all changes grouped by phase
- **RELEASE.md**: Comprehensive release checklist with:
  - Pre-release verification steps
  - Version bump instructions
  - Signing key location and management
  - Upload instructions (sideload/Play Console/F-Droid)
  - Post-release verification steps
  - Rollback plan

### 17.6 Build Variants
- **Debug**: Uses `DebugMainActivity` with debug navigation host including debug settings
- **Release**: Uses standard `MainActivity` with production navigation host
- **Manifest**: `src/debug/AndroidManifest.xml` overrides main activity for debug builds

### 17.7 Navigation Updates
- **Debug Routes**: `DebugNavRoutes.kt` adds debug-specific navigation routes
- **Navigation Host**: `DebugCockpitNavHost.kt` includes debug screens in debug builds
- **Settings Integration**: Debug version of SettingsScreen includes entry to debug settings

### 17.8 Architecture Improvements
- **Modularity**: Clear separation between debug and production code
- **Testability**: GPX replay enables reproducible testing of navigation flows
- **Maintainability**: Comprehensive documentation and release process
- **Security**: Proper signing configuration with secure key management

### 17.9 Diff Against Original Audit
The release preparation phase addressed several architectural concerns:
1. **Added comprehensive testing infrastructure** for critical user flows
2. **Enhanced build configuration** with proper release signing
3. **Improved code obfuscation** with library-specific ProGuard rules
4. **Added debug tooling** for development and testing without physical movement
5. **Documented release process** ensuring repeatable, reliable releases
6. **Maintained offline-first principle** while adding development utilities

The core architecture remains unchanged: offline-first, modular design with clear separation between map, routing, navigation, and location modules. Performance-critical handling of large datasets is preserved, with debug tools adding minimal overhead to production builds.