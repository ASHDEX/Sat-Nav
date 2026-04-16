# Release Process

This document outlines the steps for preparing and releasing a new version of Satnav.

## Pre-release Checklist

### 1. Code Quality & Testing
- [ ] All unit tests pass: `./gradlew test`
- [ ] All instrumentation tests pass: `./gradlew connectedAndroidTest`
- [ ] Critical flow test passes (search → route preview → navigation → arrival)
- [ ] GPX replay debug tool works in debug builds
- [ ] Manual smoke test on a physical device
- [ ] No lint warnings: `./gradlew lintDebug lintRelease`

### 2. Build Verification
- [ ] Debug build works: `./gradlew assembleDebug`
- [ ] Release build with R8 minification works: `./gradlew assembleRelease`
- [ ] ProGuard/R8 rules don't break functionality
- [ ] APK size is within acceptable limits
- [ ] Multi-ABI builds work (if applicable)

### 3. Configuration
- [ ] Version code incremented in `app/build.gradle.kts`
- [ ] Version name updated in `app/build.gradle.kts`
- [ ] `CHANGELOG.md` updated with release notes
- [ ] `README.md` updated if needed
- [ ] All debug features disabled in release builds

### 4. Signing
- [ ] `keystore.properties` file exists with correct values
- [ ] Keystore file exists at specified location
- [ ] Signing configuration works: `./gradlew signingReport`
- [ ] Backup of keystore stored securely

## Version Bump Instructions

1. Update version in `app/build.gradle.kts`:
   ```kotlin
   defaultConfig {
       versionCode = 2  // Increment by 1
       versionName = "1.0.1"  // Update according to semver
   }
   ```

2. Update `CHANGELOG.md`:
   - Move "Unreleased" section to new version
   - Add release date
   - Create new "Unreleased" section at top

3. Commit changes:
   ```bash
   git add app/build.gradle.kts docs/CHANGELOG.md
   git commit -m "Bump version to 1.0.1"
   git tag -a v1.0.1 -m "Release version 1.0.1"
   ```

## Signing Key Location

### Development (Debug)
- Debug signing uses Android debug keystore
- Located at `~/.android/debug.keystore`
- Automatically managed by Android Studio

### Production (Release)
- Release signing uses custom keystore
- Configuration in `keystore.properties` (not in version control)
- Template: `keystore.properties.template`
- Required properties:
  - `storeFile`: Path to .keystore or .jks file
  - `storePassword`: Keystore password
  - `keyAlias`: Key alias within keystore
  - `keyPassword`: Key-specific password

### Keystore Creation
```bash
# Create a new keystore
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias satnav-release \
  -keyalg RSA -keysize 2048 \
  -validity 10000 \
  -storetype JKS
```

## Build Commands

### Debug Build
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release Build
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Signed Release Build (with keystore.properties)
```bash
./gradlew assembleRelease
# Automatically signs with release config if keystore.properties exists
```

### Build for Specific ABI (optional)
```bash
./gradlew assembleReleaseArm64
./gradlew assembleReleaseArm32
./gradlew assembleReleaseX86
./gradlew assembleReleaseX64
```

## Upload Instructions

### Option A: Sideload (Direct APK)
1. Build signed release APK
2. Transfer APK to device
3. Enable "Install from unknown sources"
4. Install APK using file manager

### Option B: Play Console
1. Create new release in Google Play Console
2. Upload APK or App Bundle
3. Fill in release notes (from CHANGELOG)
4. Set release track (Internal/Alpha/Beta/Production)
5. Review and publish

### Option C: F-Droid
1. Build APK without proprietary dependencies
2. Create metadata file
3. Submit to F-Droid repository
4. Wait for build and inclusion

### Option D: GitHub Releases
1. Create new release on GitHub
2. Upload APK as asset
3. Add release notes
4. Publish release

## Post-release Verification

### 1. Installation Test
- [ ] APK installs successfully on clean device
- [ ] App launches without crashes
- [ ] All permissions granted correctly
- [ ] App appears in launcher

### 2. Functionality Test
- [ ] Map loads and displays correctly
- [ ] GPS location detection works
- [ ] Search functionality works
- [ ] Route calculation succeeds
- [ ] Navigation instructions work
- [ ] Voice guidance works (if enabled)
- [ ] Settings are persistent

### 3. Performance Test
- [ ] App starts within acceptable time
- [ ] Map rendering is smooth
- [ ] Route calculation completes in reasonable time
- [ ] Memory usage is within limits
- [ ] Battery consumption is acceptable

### 4. Regression Test
- [ ] Previous version features still work
- [ ] No new crashes in crash reporting
- [ ] User data migration works (if applicable)

## Monitoring

### Crash Reporting
- Enable Firebase Crashlytics or similar
- Monitor crash-free users metric
- Address critical crashes within 24 hours

### Analytics
- Track key user journeys
- Monitor feature usage
- Identify performance bottlenecks

### User Feedback
- Monitor app store reviews
- Respond to user feedback
- Address common issues in next release

## Rollback Plan

If critical issues are discovered:

1. **Minor issues**: Hotfix release with patch version
2. **Major issues**: 
   - Roll back release in Play Console
   - Notify users of issue
   - Work on fix and release as soon as possible
3. **Critical security issues**:
   - Immediate rollback
   - Security patch release
   - User notification

## Release Cadence

- **Major releases**: Every 3-6 months with significant new features
- **Minor releases**: Every 1-2 months with improvements and fixes
- **Patch releases**: As needed for critical bug fixes

## Team Responsibilities

- **Development Team**: Code changes, testing, build preparation
- **QA Team**: Manual testing, regression testing
- **Product Manager**: Release notes, feature prioritization
- **Release Manager**: Coordination, final verification, deployment