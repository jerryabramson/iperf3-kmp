# Build and install test matrix

Gate for releasing the 1.0.0 packages from `master`: every supported cell below is ✅ on the release commit, or is explicitly
marked as an accepted gap. Checklists being tested: [PLATFORM_BUILD_NOTES.md](../../PLATFORM_BUILD_NOTES.md).

**Status legend:** ✅ verified on the commit and date shown · 🟡 verified earlier, on older code; re-run needed · ⏳ in progress ·
⬜ **untested**: work still to do; blocks the gate unless listed as an accepted gap · ➖ **not applicable**: this artifact is never built or
run on that host or target, by design (reason in the row) · 🚫 **not supported**: a target we deliberately exclude.

## What "pass" means for a cell

Use a **clean machine or a VM snapshot** with only the checklist's prerequisites installed, so the checklist itself is what's tested:

1. Fresh clone of the release commit, submodule initialised, exactly as the checklist says (no extra steps from memory).
2. Build the artifact with the checklist's command. Record the artifact name and size.
3. **Install** it the way a user would (not just run from the build tree).
4. Launch, run a real iperf3 test (TCP, ~10 s) against a reachable server, stop and start one more run, save a result.
5. Record: date, commit SHA, machine/OS build, result, and any checklist correction needed. Fix the checklist, don't just note it.

## A. Install and run matrix (artifact × target machine)

Which package is **supposed** to run where (each package is built for one CPU architecture; there are no universal packages):

| Package | Built for | Must run on | Also expected to run on | Not supported |
|---|---|---|---|---|
| Android APK | arm64-v8a, armeabi-v7a, x86, x86_64 (one APK, all ABIs) | Android arm64 device/emulator, Android x86_64 emulator | ➖ | ➖ |
| iOS app | arm64 | iPhone/iPad (arm64), Simulator on Apple Silicon | ➖ | 🚫 Intel simulator |
| `.dmg` | arm64 (built on Apple Silicon) | macOS arm64 | ➖ | 🚫 Intel Macs |
| `.rpm` aarch64 | aarch64 | Fedora/RHEL aarch64 | ➖ | 🚫 x86_64 Linux |
| `.rpm` x86_64 | x86_64 | Fedora/RHEL x86_64 | ➖ | 🚫 aarch64 Linux |
| `.deb` arm64 | arm64 | Debian/Ubuntu arm64 | ➖ | 🚫 amd64 Linux |
| `.deb` amd64 | amd64 | Debian/Ubuntu amd64 | ➖ | 🚫 arm64 Linux |
| `.msi` built on x64 | x64 (x86_64 JVM, x86_64 `iperf3.exe`) | Windows 11 x64 | Windows 11 ARM64 via Prism emulation | 🚫 Windows 10 ARM64 (no x64 emulation of this kind) |
| `.msi` built on ARM64 | ARM64 JVM + x86_64 `iperf3.exe` | Windows 11 ARM64 | ➖ | 🚫 Windows x64 |

Results:

| Artifact | Target OS / arch | Status | Notes |
|---|---|---|---|
| Android APK | Android arm64 emulator | ✅ | 2026-10-04: `androidApp-debug.apk` installed and run on a Pixel 8 emulator per owner. Mac-built APK |
| Android APK (Mac-built) | Android x86_64 emulator | ➖ | Not run on Apple Silicon (arm64 emulator images only). x86_64 is tested on the Linux-built APK, see the rows below |
| Android APK (Linux-built, Fedora x86_64 host) | Android x86_64 emulator (Pixel 9 AVD) | ✅ | 2026-10-04: installed and launched per owner. Built from `2b2f376` with NDK 28.2 on Fedora; separate signing key from the Mac build |
| Android APK (Linux-built, Fedora x86_64 host) | Android physical device (moto g stylus 2025, arm64) | ✅ | 2026-10-04: installed over USB, launched, and test run verified per owner. Earlier install failed on signature mismatch with a Mac-signed copy, so the Mac copy was uninstalled first |
| iOS app | iOS Simulator (arm64, iPhone 17) | ✅ | 2026-10-04: Mac-built simulator app runs per owner. Earlier runs on several simulators |
| iOS app | iPhone / iPad (arm64) | ✅ | 2026-10-04: device build (Debug, owner development team) installed on iPhone 17 Pro, test run OK. Earlier: iPhone 17 Pro, iPad M4 |
| `.dmg` | macOS arm64 | ✅ | 2026-10-04: `iperf3KMP-1.0.0.dmg` installed and tested per owner. Clean user account still recommended (section C) |
| `.rpm` (aarch64) | Fedora 44 aarch64 | ✅ | 2026-10-04: `iperf3kmp-1.0.0-1.aarch64.rpm` installed and tested per owner. Built and `jvmTest` on 2026-09-23 |
| `.rpm` (x86_64) | Fedora x86_64 | ✅ | 2026-10-04: `iperf3kmp-1.0.0-1.x86_64.rpm` installed and tested per owner (Fedora 43 host). Built earlier via Podman |
| `.deb` (arm64) | Debian 13 aarch64 | ✅ | 2026-10-04: installed and tested per owner. `dpkg`: `iperf3kmp` 1.0.0 arm64, glibc 2.41 (`debian-gnu-linux-13.6-arm64.shared`) |
| `.deb` (arm64) | Ubuntu aarch64 (GB10) | ✅ | 2026-10-04: installed and ran per owner, retested 2026-10-04 (Ubuntu 24.04, server-class ARM CPU). `dpkg`: `iperf3kmp` 1.0.0, arm64, `install ok installed` |
| `.deb` (amd64) | Debian/Ubuntu x86_64 | ✅ | 2026-10-04 AMD Ryzen AI Developer Platform 1 (local x86_64 test host, Debian-derived, glibc 2.41): `sudo apt install ./iperf3kmp_1.0.0_amd64.deb` (built on Fedora x86_64 host) succeeded; launched and tested per owner; `sha256sum -c SHA256SUMS` all OK |
| `.rpm` (x86_64 / aarch64) | Fedora 42+ (current 44) | ✅ 2026-10-04 | aarch64: Fedora 44 installed and tested per owner. x86_64: Fedora 43 installed and tested per owner. Newer glibc than baseline |
| `.msi` (x64 build) | Windows 11 x64, clean | ✅ 2026-09-21 | Installed and run with no separate iperf3 |
| `.msi` (x64 build) | Windows 11 ARM64 (Prism) | ✅ 2026-09-21 | Runs under emulation |
| `.msi` (ARM64 build) | Windows 11 ARM64 | ✅ 2026-09-23 | Installed and launched. Mixed: ARM64 JVM, x86_64 `iperf3.exe` under Prism. Real network tests confirmed 2026-10-09; Save/History (SQLite) does not work — accepted gap, see section F |
| `.deb` (arm64) | Ubuntu 22.04 aarch64 | ⬜ | **Baseline.** Oldest supported; tests glibc 2.35 and the bundled JVM |
| `.deb` (arm64) | Debian 12 aarch64 | ⬜ | **Baseline** (glibc 2.36) |
| `.deb` (amd64) | Ubuntu 22.04 x86_64 | ⬜ | **Baseline** |
| `.deb` (amd64) | Debian 12 x86_64 | ⬜ | **Baseline** |
| `.rpm` (aarch64) | RHEL 9 family (Rocky/Alma 9) aarch64 | ⬜ | **Baseline** (glibc 2.34) |
| `.rpm` (x86_64) | RHEL 9 family (Rocky/Alma 9) x86_64 | ⬜ | **Baseline** |

## B. Build matrix (host × artifact)

Rows are what you build; columns are the machine you build on. A cell says whether the artifact built there **and** the
checklist worked as written.

| Artifact | macOS arm64 | Fedora arm64 | Debian arm64 | Ubuntu arm64 (GB10) | Fedora x86_64 | Debian/Ubuntu x86_64 | Windows 11 x64 | Windows 11 ARM64 |
|---|---|---|---|---|---|---|---|---|
| Android APK (`assembleDebug`) | ✅ 2026-10-04 (`2b2f376`) | ➖ | ➖ | ➖ | ➖ | ➖ | ⬜ | ⬜ |
| iOS app (Xcode; simulator + device) | ✅ 2026-10-04 (simulator build; device build installed and tested) | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ |
| macOS `.dmg` | ✅ 2026-10-04 (build; install not yet tested) | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ |
| Linux `.rpm`, native `packageRpm` | ➖ | ✅ 2026-09-23 (+39 jvmTest); ✅ 2026-10-04 (build, aarch64 desktop only) | ➖ | ➖ | ✅ 2026-10-04 (build + jvmTest) | ➖ | ➖ | ➖ |
| Linux `.deb`, native `packageDeb` | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ |
| Linux `.rpm`, container script (Rocky 9 base) | ✅ 2026-09-23 Docker Desktop (glibc max 2.34) | ⬜ Podman | ⬜ | ⬜ | ⬜ | ⬜ | ➖ | ➖ |
| Linux `.deb`, container script (Ubuntu 22.04 base) | ✅ 2026-09-23 Docker Desktop (glibc max 2.34) | ✅ 2026-09-23 Podman; ✅ 2026-10-04 (glibc max 2.34 vs 2.35) | ⬜ | ⬜ | ⬜ | ⬜ | ➖ | ➖ |
| Windows `.msi` (x64 host) | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ✅ 2026-10-04 (build, config-cache clean); ✅ 2026-09-21 (install) | ➖ |
| Windows `.msi` (ARM64 host, mixed arch) | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ✅ 2026-09-23 |

Notes: native `packageDeb` is ➖ everywhere by decision (2026-10-04). It needs a full build environment, and the container script builds the `.deb` on each arch we ship; each packaged `.deb` is tested by install. The container scripts produce a package for the **container's** CPU (arm64 on Apple Silicon and on aarch64 Linux;
x86_64 on x86_64 hosts). Building x86_64 packages on an Apple Silicon Mac needs emulation (see section D): usable as an early check,
not as the final evidence for x86_64.

## C. Machines: have vs. needed

| Machine | Have? | Covers |
|---|---|---|
| Apple Silicon Mac, Xcode, Docker Desktop | ✅ | Android, iOS, `.dmg`, container `.deb`/`.rpm` (arm64) |
| Windows 11 x64 | ✅ | `.msi` x64 |
| Windows 11 ARM64 VM (Parallels) | ✅ | `.msi` ARM64, x64 `.msi` under Prism (virtualized, not real silicon) |
| Real Windows 11 on Arm hardware: **NVIDIA RTX Windows-on-Arm platform (not yet released; owner plans to test)**, or any Snapdragon X device meanwhile | ⬜ pending hardware | Real Prism behaviour, real NIC and throughput; phase 0 of `WINDOWS_ARM64_IPERF3_PLAN.md`; not verifiable on the Parallels VM |
| Fedora 44 aarch64 VM | ✅ | `.rpm` arm64 native, Podman container builds |
| Debian 13 aarch64 VM | ✅ (install only; no build env) | `.deb` arm64 install, native `packageDeb` if a JDK/toolchain is added |
| Ubuntu aarch64 (GB10, Grace Blackwell) | ✅ | `.deb` arm64 native build and install on a second distro |
| Fedora x86_64 | ✅ (owner has it) | `.rpm` x86_64 native and container |
| AMD Ryzen AI Developer Platform 1 (local x86_64 test host), Debian-derived, glibc 2.41 | ✅ | `.deb` amd64 install and test (2026-10-04) |
| Debian or Ubuntu x86_64 | ⬜ No standard Debian/Ubuntu x86_64 machine. `amd-halo` (`rex`) is Debian-derived, glibc 2.41, and is only used for install tests | `.deb` amd64 container cell still needed |
| Clean Windows 11 x64 (snapshot) | ✅ (owner has it) | Re-verify the checklist from a truly clean start |
| **Clean Windows 11 ARM64 VM (snapshot)** | ⬜ recommended | Same, for ARM64 |
| **Clean macOS user account** | ⬜ recommended | `.dmg` install |

## D. Linux distro baseline (glibc)

The app's own native libraries take their glibc requirement from the machine that built them. Found on 2026-09-23:
`libiperf3native.so` built on Fedora 44 or Ubuntu 26.04 (glibc 2.43, GCC 15+/Clang 21+ default to C23) references
`__isoc23_strtol@GLIBC_2.38`, so **those packages will not load on glibc older than 2.38**: Ubuntu 22.04 (2.35), Debian 12 (2.36), RHEL/Rocky 9 (2.34).
Debian 13 (2.41) and Ubuntu 24.04 (2.39) are fine. `Dockerfile.deb` builds on `ubuntu:latest` and `Dockerfile.rpm` on
`fedora:latest`, both moving targets, so the baseline changes silently.

**Decided (2026-09-23):** supported baseline is **Ubuntu 22.04, Debian 12, RHEL 9 family, Fedora 42+**. The container scripts build
on `ubuntu:22.04` (glibc 2.35) and `rockylinux:9` (glibc 2.34) and fail if the native libraries need a newer glibc than that
(2.35 / 2.34). Native (non-container) builds on newer distros produce packages for that distro version and newer only.

## E. x86_64 coverage

All required hardware is available (real x86_64 Fedora, Debian/Ubuntu and Windows machines), so x86_64 results must come from
those, not from emulation. Emulated builds (Docker `--platform linux/amd64` on Apple Silicon) remain an optional early check only.

## F. Release gate (proposed)

Required ✅ before release (install tests on the **baseline** distros above are mandatory): Windows x64 and ARM64 rebuilt from a clean snapshot; Fedora aarch64 `.rpm` installed and launched;
Debian aarch64 `.deb` installed and launched; macOS `.dmg` and Android APK rebuilt on the release commit (they share build-script
changes); container scripts run once on Docker Desktop and once on Podman.

Also required: the distro-baseline decision in section D, and the x86_64 Linux container cells (Fedora and Debian/Ubuntu). Native `packageDeb` cells are ➖ by decision (see the build matrix notes), and the Ubuntu aarch64 (GB10) `.deb` install is done.

Accepted gap until the hardware exists: **real Windows-on-Arm hardware** (the ARM64 `.msi` is verified on a Parallels VM only; add a row to the results log when the NVIDIA RTX Windows-on-Arm machine can be tested, and run the throughput comparison in the ARM64 plan then).

Other accepted gaps (write the reason here if you decide to release without them): Windows-hosted Android builds.

Decided 2026-10-04: Android builds on Linux hosts are out of scope for now (owner decision), so those cells are ➖.

Accepted gap, documented 2026-10-09: **Save/History (Room/SQLite) does not work on Windows ARM64.** Build, install, run,
and real network tests all pass; only the database open fails (`Cannot find a suitable SQLite binary for windows 11 |
aarch64`). Root cause is upstream — neither `androidx.sqlite:sqlite-bundled` nor `org.xerial:sqlite-jdbc` ships a native
SQLite binary for Windows ARM64 as of 2026-10-09. See `platform-builds/windows-arm64.md`'s "Known limitation" section for
the detail and the options considered. Tracking: [issuetracker.google.com/issues/426464784](https://issuetracker.google.com/issues/426464784)
(open, unassigned, P3). Re-check when either library adds Windows ARM64 support, or that issue changes status.

## Results log

Add one line per run: date · cell · commit · machine/OS build · pass/fail · what changed in the checklist.

| Date | Cell | Commit | Machine | Result | Notes |
|---|---|---|---|---|---|
| 2026-09-21 | `.msi` x64 build, Windows x64 | branch tip | Windows 11 x64 | pass | Installed on a clean machine, ran a real test |
| 2026-09-23 | `.msi` ARM64 build, Windows ARM64 | `15c6f20` | Parallels VM | pass | Needed VS Build Tools + ARM64 JDK; checklist updated |
| 2026-09-23 | `.rpm` native, Fedora 44 aarch64 | `752125e` | Parallels VM | pass | 39 jvmTest, 0 failures |
| 2026-09-23 | `.deb` container (Podman), Fedora aarch64 | `752125e` | Parallels VM | pass (build) | Needed temp dir off `/`; script cleanup narrowed |
| 2026-09-23 | `.deb` container (Docker Desktop, Ubuntu 22.04 base), macOS arm64 | working tree | Mac, Docker 29.6 | pass (build) | glibc guard: max 2.34 vs baseline 2.35; install not yet tested |
| 2026-09-23 | `.rpm` container (Docker Desktop, Rocky 9 base), macOS arm64 | `2c3a0e0` | Mac, Docker 29.6 | pass (build) | glibc guard: max 2.34 vs baseline 2.34; install not yet tested |
| 2026-10-04 | Android APK `assembleDebug`, macOS arm64 | `2b2f376` | Mac | pass (build) | Not installed or run |
| 2026-10-04 | iOS app simulator (`xcodebuild`, Debug), macOS arm64 | `2b2f376` | Mac, Xcode 27.1 | pass (build) | Simulator build only |
| 2026-10-04 | iOS app device build, macOS arm64 | `2b2f376` | Mac, iPhone 17 Pro | pass | Installed and test run completed; `.ipa` is a Debug build |
| 2026-10-04 | `.dmg` native, macOS arm64 | `2b2f376` | Mac | pass (build) | Install not tested |
| 2026-10-04 | `.msi` x64 native, Windows 11 x64 | `2b2f376` | Windows 11 x64 host | pass (build) | Config-cache warnings gone (`compileIperf3ExeWindows` fixed); not installed |
| 2026-10-04 | `.rpm` native + `jvmTest`, Fedora 43 x86_64 | `2b2f376` | Fedora 43 host | pass | `./gradlew clean` failed until NDK 28.1/28.2 were reinstalled (installs had no symlinks); now passes |
| 2026-10-04 | `.rpm` native, Fedora 44 aarch64 (desktop only) | `2b2f376` | Parallels VM | pass (build) | Android skipped on this VM: NDK clang is x86_64 and no x86_64 runtime is available on aarch64 |
| 2026-10-04 | `.deb` container script (Ubuntu 22.04 base), Fedora 44 aarch64 | `2b2f376` | Parallels VM | pass (build) | glibc guard: max 2.34 vs baseline 2.35; install not yet tested |
| 2026-10-04 | `.deb` amd64 native (built on Fedora x86_64), installed on `rex` (Debian-derived, glibc 2.41) | `2b2f376` | AMD Ryzen AI Developer Platform 1 | pass | `apt install ./iperf3kmp_1.0.0_amd64.deb` clean; launched and tested per owner; `SHA256SUMS` verified (8/8 OK) |
| 2026-10-04 | `.rpm` aarch64 native, installed and tested on Fedora 44 aarch64 | `2b2f376` | Parallels VM | pass | Installed and tested per owner |
| 2026-10-04 | `.rpm` x86_64 native, installed and tested on Fedora x86_64 | `2b2f376` | Fedora 43 host | pass | Installed and tested per owner |
| 2026-10-04 | `.dmg` native, installed and tested on macOS arm64 | `2b2f376` | Mac | pass | Installed and tested per owner |
| 2026-10-04 | `.deb` arm64, installed and ran on GB10 (Ubuntu 24.04 aarch64) | `2b2f376` | GB10 | pass | Installed and ran per owner; `dpkg`: `iperf3kmp` 1.0.0 arm64 installed |
| 2026-10-04 | `.deb` arm64, installed and tested on Debian 13 aarch64 | `2b2f376` | Parallels VM (`debian-gnu-linux-13.6-arm64`) | pass | Installed and tested per owner; `dpkg`: 1.0.0 arm64 |
| 2026-10-04 | `androidApp-debug.apk`, installed and run on Pixel 8 emulator | `2b2f376` | Mac, Android emulator (Pixel 8) | pass | Installed and run per owner. Physical device tested separately (Linux-built APK, moto g stylus) |
| 2026-10-04 | iOS app, Debug simulator build, iPhone 17 simulator | `2b2f376` | Mac, Xcode 27.1 | pass | Runs per owner |
| 2026-10-04 | Android APK (Linux-built), installed and launched on Pixel 9 emulator (x86_64) | `2b2f376` | Fedora x86_64 host | pass | Installed and launched per owner |
| 2026-10-04 | Android APK (Linux-built), installed and launched on moto g stylus 2025 over USB | `2b2f376` | Fedora x86_64 host | pass | Installed, launched, and test run verified per owner |
| 2026-10-09 | `.msi` (ARM64 build), Windows 11 ARM64 | `1744c7a` | Parallels VM | pass (network); fail (SQLite) | Real iperf3 test confirmed. Save/History fails: no Windows ARM64 native SQLite binary upstream (androidx.sqlite-bundled or sqlite-jdbc) — accepted gap, see section F and `platform-builds/windows-arm64.md` |
