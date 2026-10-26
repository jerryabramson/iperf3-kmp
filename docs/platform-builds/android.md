# Build: Android

**Status:** verified (emulator and real devices). Host: any OS with the Android SDK.

## Prerequisites
- Common setup: see [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md) (submodule, SDK, JDK).
- Android SDK with `platforms;android-37.0` and `build-tools;37.0.0`. NDK `28.2.13676358` is fetched by AGP for the CMake builds (`:nativeformat`, `:iperf3native`).

## Build
```shell
./gradlew :androidApp:assembleDebug          # APK: androidApp/build/outputs/apk/debug/
./gradlew :androidApp:installDebug           # install on a running emulator/device
```
Launch: `adb shell am start -n com.jabramson.iperf3_network_tester_kmp/.MainActivity`

## Release signing (Google Play)
Play uploads must be a signed `.aab`. With **Play App Signing** (the default for new apps), Google
holds the key that signs what users install; the key here is only your **upload key**. Keep it
outside the repo (`*.jks`/`*.keystore` are gitignored anyway) and back it up: if it's lost, the
upload key has to be reset through Play Console support.

1. Create an upload key once (it prompts for passwords; don't put them on the command line):
   ```shell
   keytool -genkeypair -v -keystore ~/keys/iperf3kmp-upload.jks -alias upload \
       -keyalg RSA -keysize 4096 -validity 10000
   ```
   If you already uploaded a signed bundle to Play, use **that** keystore instead: Play only
   accepts bundles signed with the upload key it has on record.
2. Add to your own `~/.gradle/gradle.properties` (never the repo's):
   ```properties
   androidApp.releaseStoreFile=/Users/you/keys/iperf3kmp-upload.jks
   androidApp.releaseKeyAlias=upload
   androidApp.releaseStorePassword=...
   androidApp.releaseKeyPassword=...
   ```
   The two passwords can come from `ANDROID_RELEASE_STORE_PASSWORD` / `ANDROID_RELEASE_KEY_PASSWORD`
   instead, if you'd rather not keep them in a file.
3. Build and check:
   ```shell
   ./gradlew :androidApp:bundleRelease        # androidApp/build/outputs/bundle/release/androidApp-release.aab
   jarsigner -verify androidApp/build/outputs/bundle/release/androidApp-release.aab   # "jar verified."
   ```
   Without `androidApp.releaseStoreFile` the bundle is unsigned (`jar is unsigned.`); with it set but
   another property missing, the build stops and names the missing one.

`versionCode` comes from `VERSION` (`1.0.1` → `10001`). Play rejects a second upload with the same
`versionCode`, so any re-upload needs a version bump.

## Test
```shell
./gradlew :shared:testAndroidHostTest        # excludes StringFormatterTest (Robolectric can't load the native .so)
```

## Gotchas
- `sdkmanager` package id is `platforms;android-37.0`, not `android-37`.
- Native code (JNI, all four ABIs) is compiled by the NDK via CMake, not by the host `clang`.
