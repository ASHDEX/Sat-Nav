# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- GPX replay debug tool for testing navigation without driving
- FakeLocationRepository under src/debug/ that reads GPX files from assets/gpx/
- DebugSettingsScreen accessible from Settings in debug builds only
- Instrumentation tests for critical flow using Compose UI Testing
- Consumer ProGuard rules for MapLibre, GraphHopper, Kotlinx Serialization, and NanoHTTPD
- Release signing configuration with keystore.properties
- Comprehensive release checklist in docs/RELEASE.md

### Changed
- Updated proguard-rules.pro with more specific rules for third-party libraries
- Enhanced navigation system to support debug location sources
- Improved build.gradle.kts to support release signing

### Fixed
- ProGuard/R8 issues with GraphHopper reflection-based JSON loading
- MapLibre native method preservation in release builds
- Kotlinx Serialization class preservation for @Serializable classes

## [1.0.0] - 2024-04-11

### Added
- Initial release of Satnav offline GPS navigation system
- Offline map rendering using MBTiles and MapLibre
- Offline routing using GraphHopper with local graph cache
- Terrain visualization with raster DEM tiles
- Navigation with turn-by-turn instructions
- Search functionality with offline geocoding
- Multi-route planning with different profiles (fastest, shortest, balanced)
- Lane guidance and maneuver visualization
- Voice navigation instructions
- Traffic pattern prediction (offline)
- AI-powered route suggestions
- Multi-modal transportation routing
- Offline maps management
- Saved places functionality
- Settings screen with theme support

### Technical Features
- MVVM architecture with Hilt dependency injection
- Compose UI with Material 3 design
- Offline-first design with no internet dependency
- Modular architecture with clear separation of concerns
- Background routing computation
- Tile-based map loading for memory efficiency
- Local HTTP servers for serving map and terrain tiles
- Data persistence for navigation state and trips
- Comprehensive unit and integration tests

## Phase 0 (Initial Architecture)
- Project setup with AGENTS.md guidelines
- Core modules: map, routing, navigation, location
- Offline data storage design
- Threading model with background routing
- Performance considerations for large datasets

## Phase 1 (Map Foundation)
- MapLibre Android SDK integration
- MBTiles support for offline maps
- Tile server implementation
- Basic map rendering and interaction

## Phase 2 (Routing Engine)
- GraphHopper integration
- Local graph cache loading
- Route planning with multiple profiles
- Route visualization on map

## Phase 3 (Navigation Core)
- Turn-by-turn navigation
- Maneuver calculation and announcement
- Position interpolation for smooth movement
- Deviation detection and rerouting

## Phase 4 (User Interface)
- Compose UI implementation
- Home screen with quick actions
- Search screen with offline geocoding
- Route preview screen
- Navigation screen with maneuver display
- Settings and offline maps management

## Phase 5 (Advanced Features)
- Terrain visualization
- Lane guidance
- Voice navigation
- Traffic pattern prediction
- AI route suggestions
- Multi-modal routing

## Phase 6 (Release Preparation)
- GPX replay debug tool
- Instrumentation tests
- ProGuard/R8 optimization
- Release signing setup
- Documentation and release checklist