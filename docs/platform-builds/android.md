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

## Test
```shell
./gradlew :shared:testAndroidHostTest        # excludes StringFormatterTest (Robolectric can't load the native .so)
```

## Gotchas
- `sdkmanager` package id is `platforms;android-37.0`, not `android-37`.
- Native code (JNI, all four ABIs) is compiled by the NDK via CMake, not by the host `clang`.
