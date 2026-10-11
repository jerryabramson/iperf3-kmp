# Research note: what "full desktop support" actually took

**Written:** 2026-09-23 · **Scope:** the desktop targets of iperf3KMP (macOS, Linux, Windows; x86_64 and arm64).
**Companion docs:** [WINDOWS_DECISION_REVERSAL_CASE_STUDY.md](WINDOWS_DECISION_REVERSAL_CASE_STUDY.md),
[WINDOWS_ARM64_IPERF3_PLAN.md](WINDOWS_ARM64_IPERF3_PLAN.md), [platform-builds/TEST_MATRIX.md](../platform-builds/TEST_MATRIX.md).
Every item below was hit for real in this project; nothing here is a generic list of "things that could go wrong".

## The finding in one paragraph

"Desktop" looked like one target on top of a JVM, added as a bonus after the mobile work. It is at least eight: three operating systems, two CPU architectures, and on
Linux two package families with a range of distro versions. The app code barely changes between them (the Windows runtime
delta was 247 net lines, all in `jvmMain`). What multiplies is everything around it: native toolchains, packaging,
and verification. The build-time logic for Windows (343 net lines at the time of the case study) was larger than the runtime
change, and none of that is counted by the code-sharing metric. Most defects found were not in application logic; they
were in the interaction between a toolchain, an OS, and a CPU.

## 0. Scope inversion: the bonus target became the hardest one

The project's stated primary goal was delivering the same app on the mobile platforms (Android and iOS) with Kotlin/Compose
Multiplatform. Desktop began as a **bonus** (macOS and Linux) and Windows was initially judged not worth supporting. It is now
the most complex part of the project. Evidence from this repository, not impressions:

- The install/test matrix has 21 install-and-run rows; **5 are mobile** and **16 are desktop** (and the desktop rows are mostly
  distinct machines, whereas the mobile rows are simulators and two device types).
- The most heavily modified file on the branch that added Windows was `shared/build.gradle.kts` (+391/-35 lines), holding
  per-OS native toolchain logic. Windows runtime, build and packaging code was about a quarter of all changed lines on that branch.
- Mobile platforms give the project one toolchain each, provided by the platform vendor (Xcode; the Android SDK/NDK), and one
  or two CPU architectures. "Desktop" gave three unrelated toolchains (Xcode tools, GCC/Clang plus Podman, Cygwin plus LLVM plus
  Visual Studio Build Tools), two package formats on Linux, and a mixed-architecture package on Windows on Arm.
- Mobile app stores and vendor tools also constrain the target (one OS build, one architecture, one packaging path); the
  desktop world has no such funnel, so the project had to make and test those decisions itself.

The lesson worth stating: the target that looks easiest to add to a KMP app (it is "just the JVM") can carry the most
integration cost once the app contains native code, because the JVM does not hide the operating system, the CPU or the
distribution format.

## 1. The app is not pure JVM, so nothing cross-compiles

The app bundles native C libraries (the printf helper and, off Windows, the iperf3 JNI bridge) that are compiled for the
build host at build time and loaded from jar resources by `os-arch`. Consequences: every package must be built on its target
OS and CPU; `configure` output for `third_party/iperf` is OS/arch-specific and is cached, so switching a checkout between
machines silently miscompiles unless it is cleared; and each new OS/CPU needs its own toolchain story.

## 2. Windows: a different architecture, then a toolchain gauntlet

- **In-process JNI is not viable.** Loading a Cygwin-linked DLL into `java.exe` crashes (`EXCEPTION_ACCESS_VIOLATION`; Cygwin's
  fixed-address shared memory collides with the JVM), reproduced even after `rebaseall`. So Windows spawns a real `iperf3.exe`
  as a subprocess: a second execution architecture beside the JNI one.
- **Runtime bugs in the new path:** a classic pipe-drain deadlock (an exception in the stdout callback stopped draining the
  pipe, the child blocked on `write()`, `waitFor()` never returned; the crash was a `java.lang.Error`, so `catch (e: Exception)`
  missed it); a stray duplicate `argv[0]`; and a cross-thread visibility/race gap in the stop logic.
- **Seven distinct build-toolchain root causes**, each found only by running the real build: bare `bash` resolving to WSL
  instead of Cygwin (in two places, silently producing nothing); Cygwin's PATH not set up under a non-login shell; Windows
  environment-variable casing (`Path` vs `PATH`) defeating a PATH prepend; `-fPIC` hard-erroring under Cygwin's old clang;
  that clang being incompatible with Cygwin's own newer headers (fixed by using Cygwin's gcc); a sibling-header include that
  only one compiler searched; and Gradle's task-graph planning happening before earlier tasks delete files.
- **Environment traps found on other Windows machines:** Git converting the submodule's shell scripts to CRLF so `./configure`
  fails; a JDK under `C:\Program Files` (a space) losing a `-I` path through Java's Windows argument quoting; and `clang` on
  Windows needing Visual Studio Build Tools for its C headers, which LLVM's installer does not provide.
- **Design rule that emerged:** fail loudly, never fall back to whatever is on `PATH`.

## 3. Windows on Arm: the mixed-architecture package

- Cygwin has no ARM64 port [sourced, see the ARM64 plan], so the ARM64 MSI is deliberately **mixed**: ARM64 JVM and
  `nativeformat.dll`, x86_64 `iperf3.exe` under Prism emulation. The x64 MSI also runs there. Whether emulation costs throughput
  is **unmeasured** and needs real hardware (the current evidence is a Parallels VM).
- Tooling friction unrelated to the app: the Gradle daemon JVM criteria pin a vendor with no Windows ARM64 auto-download; winget
  installed an x64 JDK, which Gradle happily accepts and which would have run the whole build emulated and produced x64 output;
  Kotlin/Native does not support a Windows ARM64 host (harmless warning).
- The JNI iperf3 library was being built and bundled on Windows though never loaded there, mislabeled `windows-aarch64` while
  actually x86_64. Found only by reading the PE header of every binary. It is now skipped.

## 4. Linux: distro versions are part of the target

- The app's own native libraries inherit the glibc requirement of the machine that built them. Built on Fedora 44 or
  Ubuntu 26.04 (glibc 2.43, C23 by default in current compilers), `libiperf3native.so` referenced `__isoc23_strtol@GLIBC_2.38`,
  so the package would not load on Ubuntu 22.04, Debian 12 or RHEL 9. The container Dockerfiles used `:latest` bases, so the
  minimum silently moves. Baseline is now pinned (Ubuntu 22.04, Rocky 9) with a build-time guard on the glibc version.
- Package-manager hazards: the `.deb` postinst calls `xdg-desktop-menu`, which fails on systems with no desktop menu (for
  example WSL) and aborts installation under `set -e`; we patch the built `.deb`.
- Tooling hazards (ours): a container script ran `podman system prune -a --volumes` and deleted 17 GB of unrelated images on a
  shared VM; rootless Podman leaves sub-uid-owned temp directories that plain `rm -rf` cannot remove; a small root
  filesystem can fail image builds unless the temp directory is moved. The scripts are now scoped to this project's own
  image and containers.
- A container builds for the **container's** CPU, so arm64 and x86_64 packages come from arm64 and x86_64 hosts respectively.

## 5. macOS

Apple Silicon only by policy. The installer icon must be `.icns` (a PNG silently yields no icon). Comparatively little
went wrong, which is itself informative: macOS is the platform the toolchain was originally written on.

## 6. Verification is the bottleneck

Counting the packages actually needed: `.dmg` (arm64), `.msi` (x64, ARM64), `.deb` and `.rpm` (arm64, x86_64), then the distro
baseline (Ubuntu 22.04, Debian 12, RHEL 9 family, Fedora 42+), plus Android and iOS. Each cell needs a clean machine, a
fresh clone, the checklist followed verbatim, an install, and a real network test. That is dozens of runs on real hardware
across at least seven machine types. Many bugs above only appeared on a machine nobody had built on yet. The matrix in
`platform-builds/TEST_MATRIX.md` exists because "it built on my machine" stopped being evidence.

## What this suggests

- **For the code-sharing question:** the metric (76.6 to 81.7% on this branch) counts source lines. It does not see toolchain,
  packaging or verification work, which dominated desktop effort here. A cross-platform project's real cost can sit almost
  entirely outside the number.
- **For the team-size thesis:** the hard parts were platform-specific systems knowledge (Windows process and pipe semantics,
  Cygwin, glibc versioning, package managers) and access to real machines. AI assistance was effective at diagnosis and at
  writing fixes once a failure was reproduced; it could not conjure the machines, and it made at least one destructive mistake
  (the prune above) that a checked-in script had already encoded.
- **Method that worked:** reproduce on the real target, fix one root cause, re-run, and record the checklist correction. Reading
  binary headers and glibc symbol tables found problems that "the build succeeded" hid.

## Open items

Real Windows-on-Arm hardware and the throughput comparison; x86_64 Linux and baseline-distro install tests; the bundled JVM's
glibc requirement on the oldest baseline; whether Docker Desktop and Podman builds produce identical packages.
