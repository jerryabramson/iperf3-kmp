# Build: iOS

**Status:** verified (simulators, iPhone 17 Pro, iPad M4). Host: macOS Apple Silicon + Xcode only.

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- Xcode with the iOS SDK. Deployment target is **18.2**; don't lower it without changing both `shared/build.gradle.kts:iperf3IosDeploymentTarget` and the xcodeproj's `IPHONEOS_DEPLOYMENT_TARGET`.
- ARM-only by policy: `iosArm64` and `iosSimulatorArm64`. No Intel simulator.

## Build and run
Before building/archiving in Xcode, sync the app version into `Config.xcconfig` from the repo-root `VERSION` file:
```shell
./gradlew syncVersions
```
This rewrites `Config.xcconfig`'s `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` to match `VERSION` -- Xcode has no automatic way to pick that up otherwise, since iOS isn't built via Gradle. See `PLATFORM_BUILD_NOTES.md` for the full versioning scheme.

Open `iosApp/iosApp.xcodeproj` in Xcode and run. Gradle only builds the shared framework, never the signed `.app`.

Command line, simulator:
```shell
open -a Simulator
xcrun simctl boot "iPhone 17"                      # skip if already booted
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator \
  -configuration Debug -derivedDataPath build/ios build
xcrun simctl install booted build/ios/Build/Products/Debug-iphonesimulator/iperf3KMP.app
xcrun simctl launch booted com.jabramson.iperf3-network-tester-kmp
```

## Test
```shell
./gradlew :shared:iosSimulatorArm64Test
```

## Gotchas
- Never build iOS via Gradle alone, and don't edit `iosApp/` Gradle files.
- Xcode's final link needs `-lsqlite3` (already in the project's `OTHER_LDFLAGS`); don't remove it.
- `native_log.h` (os_log shim) exists because Kotlin/Native's `NSLog` variadic binding crashes.
