# Build: Linux Fedora/RHEL (.rpm), x86_64 and aarch64

**Status:** verified on x86_64 and aarch64 (native builds, and via Podman/Docker containers).

## Prerequisites
- Common setup: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).
- `clang` on PATH, plus rpm-build (`sudo dnf install -y rpm-build`).

## Build
Native, on a Fedora/RHEL host:
```shell
./gradlew :desktopApp:packageRpm
```
From any other host (other Linux family, or macOS with Docker Desktop). Builds in a container matching the host CPU; auto-detects Podman or Docker:
```shell
./packageRpmFromContainer.sh
```
Output: `desktopApp/build/compose/binaries/main/rpm/iperf3kmp-1.0.0-1.x86_64.rpm (or .aarch64.rpm)`

## Install and run
```shell
sudo dnf install ./desktopApp/build/compose/binaries/main/rpm/iperf3kmp-1.0.0-1.x86_64.rpm
/opt/iperf3kmp/bin/iperf3KMP
```

## Gotchas
- Output architecture = build host architecture; there is no cross-compiling.
- The container scripts clear `third_party/iperf/src/iperf_config.h`/`version.h` first, because a configure run from another OS leaks in through the bind mount (macOS config breaks Linux with `'sys/endian.h' file not found`).
- If `packageRpm` fails with a jpackage exit code 1, install `rpm-build` (note the dash).
