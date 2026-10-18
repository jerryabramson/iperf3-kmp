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
scripts/packageRpmFromContainer.sh
```
Output: `desktopApp/build/compose/binaries/main/rpm/iperf3kmp-1.0.0-1.x86_64.rpm (or .aarch64.rpm)`

## Install and run
```shell
sudo dnf install ./desktopApp/build/compose/binaries/main/rpm/iperf3kmp-1.0.0-1.x86_64.rpm
/opt/iperf3kmp/bin/iperf3KMP
```
Or build and install (or reinstall) this checkout in one step, and remove it again:
```shell
scripts/installLinuxFromPackage.sh rpm
scripts/installLinuxFromPackage.sh --uninstall rpm
```

## Gotchas
- Output architecture = build host architecture; there is no cross-compiling.
- The container scripts clear `third_party/iperf/src/iperf_config.h`/`version.h` first, because a configure run from another OS leaks in through the bind mount (macOS config breaks Linux with `'sys/endian.h' file not found`).
- If `packageRpm` fails with a jpackage exit code 1, install `rpm-build` (note the dash).
- Under dnf5, every remove/reinstall/upgrade prints `error: can't create transaction lock on /usr/lib/sysimage/rpm/.rpm.lock` from jpackage's `%preun` scriptlet: it runs `rpm -q --whatprovides` while dnf holds the rpmdb lock. Harmless on reinstall/upgrade (the new package's `%post` has already reinstalled the menu entry). On a plain `dnf remove`, though, the menu entry under `/usr/local/share/applications/` is left behind — use `scripts/installLinuxFromPackage.sh --uninstall`, or run `sudo xdg-desktop-menu uninstall /opt/iperf3kmp/lib/iperf3kmp-iperf3KMP.desktop` before removing.
