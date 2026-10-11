# Build: Linux Debian/Ubuntu (.deb), x86_64 and aarch64

**Status:** verified on x86_64 and aarch64 (native builds, and via Podman/Docker containers).

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- `clang` on PATH, plus dpkg-dev, fakeroot.

## Build
Native, on a Debian/Ubuntu host:
```shell
./gradlew :desktopApp:packageDeb
```
From any other host (other Linux family, or macOS with Docker Desktop). Builds in a container matching the host CPU; auto-detects Podman or Docker:
```shell
./packageDebianFromContainer.sh
```
Output: `desktopApp/build/compose/binaries/main/deb/iperf3kmp_1.0.0_amd64.deb (or _arm64.deb)`

## Install and run
```shell
sudo apt install ./desktopApp/build/compose/binaries/main/deb/iperf3kmp_1.0.0_amd64.deb
/opt/iperf3kmp/bin/iperf3KMP
```

## Gotchas
- Output architecture = build host architecture; there is no cross-compiling.
- The container scripts clear `third_party/iperf/src/iperf_config.h`/`version.h` first, because a configure run from another OS leaks in through the bind mount (macOS config breaks Linux with `'sys/endian.h' file not found`).
- Remove with `sudo apt remove iperf3kmp` (package name is lowercased). A harmless "Download is performed unsandboxed as root" notice is normal.
- jpackage's postinst calls `xdg-desktop-menu`, which fails on systems without desktop menu support (e.g. WSL). `desktopApp/build.gradle.kts` patches the built `.deb` so this no longer aborts installation.
