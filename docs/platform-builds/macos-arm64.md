# Build: macOS desktop (Apple Silicon)

**Status:** verified (`.dmg` built). Intel Macs are not supported.

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- Xcode Command Line Tools (`clang`). Native JNI libraries are compiled at build time for this machine only.

## Run / package
```shell
./gradlew :desktopApp:run
./gradlew :desktopApp:hotRun --auto          # hot reload
./gradlew :desktopApp:packageDmg             # desktopApp/build/compose/binaries/main/dmg/iperf3KMP-1.0.0.dmg
```

## Test
```shell
./gradlew :shared:jvmTest
```

## Gotchas
- No cross-compiling: a `.dmg` built here only runs on Apple Silicon.
- Switching a checkout between OSes/architectures: delete `third_party/iperf/src/iperf_config.h` and `version.h` (or run `./gradlew cleanIperf3Configure`, as its own invocation) before building.
- The app icon must be `.icns` (already provided), not a PNG.
