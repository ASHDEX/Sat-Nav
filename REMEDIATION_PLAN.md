# Security & Quality Remediation Plan

## Status: ✅ COMPLETED (2026-04-10)

---

## Issues Fixed

### ✅ 1. Broken Tests
- **File**: `app/src/test/java/com/jayesh/satnav/features/routing/RouteMergerTest.kt`
- **Root Cause**: Test used JUnit 4 (`org.junit.Assert`, `org.junit.Test`) but `build.gradle.kts` only declares JUnit 5 Jupiter dependencies — no `junit:junit:4.x` in classpath.
- **Note**: `RoutingProfile.Car` was a red herring — `RoutingProfile.CAR` already existed. `RouteMerger` also already existed as an `internal object` in `MultiRouteManager.kt`.
- **Fix**: Migrated all imports to `org.junit.jupiter.api.Assertions.*` and `org.junit.jupiter.api.Test`.

### ✅ 2. Security Improvements

#### ProGuard Rules (was empty)
- **Fix**: `app/proguard-rules.pro` now has comprehensive keep/dontwarn rules for:
  - Kotlin metadata and reflection
  - Kotlinx Serialization
  - Hilt / Dagger DI
  - Jetpack Compose
  - MapLibre
  - GraphHopper
  - NanoHTTPD
  - Play Services Location
  - Domain models (serialisation boundary)
  - Release Log stripping via `-assumenosideeffects`

#### Release Build Minification (was disabled)
- **Fix**: `app/build.gradle.kts` now sets `isMinifyEnabled = true` and `isShrinkResources = true` for release builds.

#### Information Leakage in HTTP Server
- **File**: `MbtilesLocalHttpServer.kt`
- **Fix 1**: General catch block no longer returns `exception.message` in HTTP response — returns `"Tile server error"` (generic).
- **Fix 2**: `TileReadResult.Error` branch no longer returns `throwable.message` — returns `"Tile unavailable"` (generic).
- Full detail still written to `Log.e(...)` for internal debugging.

### ✅ 3. Code Quality (Logging Security)
- `NavLog.kt` was already correctly gated behind `BuildConfig.DEBUG` — no change needed.
- Release Log suppression now also handled at R8 level via `-assumenosideeffects` in ProGuard rules.

### ⚠️ 4. File Permissions / External Storage Validation
- **Status**: Deferred — `MultiRouteManager` already validates lat/lon bounds on all waypoints.
- Future work: add path traversal check on `graphCacheDir` and `mbtilesPath` inputs.

---

## Verification

```bash
# Run unit tests
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew testDebugUnitTest

# Verify release build compiles with R8
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" \
./gradlew assembleRelease
```