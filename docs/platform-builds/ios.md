# Build: iOS

**Status:** verified (simulators, iPhone 17 Pro, iPad M4). Host: macOS Apple Silicon + Xcode only.

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- Xcode with the iOS SDK. Deployment target is **18.2**; don't lower it without changing both `shared/build.gradle.kts:iperf3IosDeploymentTarget` and the xcodeproj's `IPHONEOS_DEPLOYMENT_TARGET`.
- ARM-only by policy: `iosArm64` and `iosSimulatorArm64`. No Intel simulator.

## Signing setup (your own Apple team)
Signing values are never committed. `iosApp/Configuration/Config.xcconfig` holds defaults and then pulls in an optional, **gitignored** `iosApp/Configuration/LocalSigning.xcconfig` that overrides them for your machine:

| Setting | Default | What it's for |
|---|---|---|
| `TEAM_ID` | empty | Your Apple Developer Team ID. `project.pbxproj` uses `DEVELOPMENT_TEAM = "${TEAM_ID}"`. |
| `BUNDLE_ID_PREFIX` | `com.jabramson` | The app's bundle ID is `$(BUNDLE_ID_PREFIX).iperf3-network-tester-kmp`. |

**Simulator only:** nothing to set up. A fresh clone builds and runs on the simulator with an empty `TEAM_ID` (Xcode signs it "Sign to Run Locally").

**Real iPhone/iPad:**
1. Sign in to Xcode with your Apple ID: Xcode → Settings → Accounts. A free Apple ID works for personal-device installs (apps expire after 7 days); a paid Apple Developer Program membership lasts a year.
2. Find your Team ID: developer.apple.com → Account → Membership details, or Xcode → Settings → Accounts → select the team (the 10-character ID).
3. Create your local file from the template:
   ```shell
   cp iosApp/Configuration/LocalSigning.xcconfig.template iosApp/Configuration/LocalSigning.xcconfig
   ```
   Then edit it:
   ```
   TEAM_ID=ABCDE12345
   BUNDLE_ID_PREFIX=com.yourname
   ```
   You **must** change `BUNDLE_ID_PREFIX`: bundle IDs are unique across Apple, and `com.jabramson.iperf3-network-tester-kmp` already belongs to another team, so automatic signing fails with it.
4. On the device: enable Developer Mode (Settings → Privacy & Security → Developer Mode), connect it, pick it as the run destination in Xcode, and run. With a free account, also trust the developer profile on the device (Settings → General → VPN & Device Management).
5. On first launch, allow the **Local Network** prompt, or the app can't reach iperf3 servers on your LAN.

Check what Xcode resolved:
```shell
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -showBuildSettings \
  | grep -E '^\s+(DEVELOPMENT_TEAM|PRODUCT_BUNDLE_IDENTIFIER) ='
```

> **Don't pick a team in Xcode's Signing & Capabilities tab.** That writes your Team ID straight into the tracked `project.pbxproj` (replacing `"${TEAM_ID}"`). Set it in `LocalSigning.xcconfig` instead. If it happens anyway, run `git diff iosApp/iosApp.xcodeproj/project.pbxproj` before committing and put the `"${TEAM_ID}"` placeholder back. Close Xcode first, or it saves its in-memory copy over the fix.

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
xcrun simctl launch booted com.jabramson.iperf3-network-tester-kmp   # or $(BUNDLE_ID_PREFIX).iperf3-network-tester-kmp if you changed it
```

## Test
```shell
./gradlew :shared:iosSimulatorArm64Test
```

## Gotchas
- Never build iOS via Gradle alone, and don't edit `iosApp/` Gradle files.
- `Info.plist` must stay in the `iosApp` folder's membership exceptions (`PBXFileSystemSynchronizedBuildFileExceptionSet` in `project.pbxproj`). If Xcode drops it, the file is both copied as a resource and processed as the app's Info.plist, and the build fails with `Multiple commands produce '.../iperf3KMP.app/Info.plist'`.
- Xcode's final link needs `-lsqlite3` (already in the project's `OTHER_LDFLAGS`); don't remove it.
- `native_log.h` (os_log shim) exists because Kotlin/Native's `NSLog` variadic binding crashes.
