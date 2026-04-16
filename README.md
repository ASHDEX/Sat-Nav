# Satnav

Initial Android foundation for an offline-first navigation app in Kotlin.

## Architecture

The project starts with a single Android application module at `app/`, and the source layout inside `app/src/main/java/com/jayesh/satnav/` is organized around Clean Architecture:

- `core/`: shared constants and app-wide utilities.
- `data/`: local data sources and repository implementations for offline assets.
- `domain/`: business models, repository contracts, and use cases.
- `features/`: feature-owned presentation code such as ViewModels and screens.
- `ui/`: shared Compose components, navigation shell, and theme.
- `di/`: Hilt bindings and dependency wiring.

## Why this shape works

- Clean Architecture: the `domain` layer depends on nothing Android-specific.
- MVVM: each feature starts with its own `ViewModel` and screen entry point.
- Offline-first: the first repository seam is already modeled around local datasets such as MBTiles, terrain assets, and GraphHopper graphs.
- Feature-first growth: future map, routing, navigation, and location work can expand inside isolated feature packages without collapsing into one giant presentation layer.

## Current boilerplate

- Hilt application setup with an `@HiltAndroidApp` application class and `@AndroidEntryPoint` activity.
- Compose navigation shell with four top-level feature destinations.
- Repository, local data source, and use case scaffolding for offline asset readiness.
- Unit and instrumentation test placeholders to keep testing in the structure from day one.

## Phase 1 verification

- Build debug APK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew assembleDebug`
- Run unit tests: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew testDebugUnitTest`
- Generated APK: `app/build/outputs/apk/debug/app-debug.apk`

## Phase 2 status

- MapLibre is initialized in the application startup path.
- The map screen now hosts a real `MapView` inside Compose.
- Zoom and pan gestures are enabled for interactive map inspection.
- The current style source is `https://demotiles.maplibre.org/style.json` for Phase 2 validation only.
- Offline MBTiles integration is intentionally deferred to Phase 3.
