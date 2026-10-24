# AGENTS.md

Compact agent guide for `iperf3KMP`. **`CLAUDE.md` at the repo root is the single source of truth** (full architecture, native-bridge design, Room wiring history, packaging details) — this file holds only the facts most likely to trip up a session. If they disagree, trust the code, then CLAUDE.md.

## What this is

Kotlin Multiplatform network-testing app (Android / iOS / desktop JVM), Compose Multiplatform UI, driving **real iperf3 3.21 in-process** via `libiperf` — JNI on Android + desktop, cinterop on iOS. Windows desktop is supported, but spawns a bundled Cygwin-built `iperf3.exe` subprocess instead of the in-process JNI path (`docs/desktop/WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`). The desktop app also has a terminal CLI (`desktopApp/.../cli/`).

Toolchain: Gradle **9.7.1** wrapper (always `./gradlew`, never system gradle), Kotlin **2.4.0**, Compose MP **1.11.1**, AGP 9.1.1, KSP 2.3.6. foojay-resolver auto-provisions the JDK.

## Setup

- After clone: `git submodule update --init --recursive third_party/iperf` (pinned at iperf 3.21) — every target needs it; without it native compile tasks fail input validation.
- Android SDK (`sdk.dir` in gitignored `local.properties`, or `ANDROID_HOME`) is needed **even for desktop-only builds** — AGP configures all subprojects. Package ID is `platforms;android-37.0`, not `platforms;android-37`.
- Desktop builds need `clang` on PATH (JNI bridges compile at build time). Windows instead needs Cygwin at `C:\cygwin64`, a native Windows LLVM clang, and WiX — see `docs/platform-builds/windows-*.md`.
- Signing is **per-developer and never committed**: iOS team ID goes in gitignored `iosApp/Configuration/LocalSigning.xcconfig` (`docs/platform-builds/ios.md`); macOS signing/notarization in `~/.gradle/gradle.properties` (`docs/platform-builds/macos-arm64.md`).

## Commands

| Task | Command |
|------|---------|
| Run desktop app | `./gradlew :desktopApp:run` (hot reload: `:desktopApp:hotRun --auto`) |
| Build / install debug APK | `./gradlew :androidApp:assembleDebug` / `installDebug` |
| iOS build/run | Open `iosApp/iosApp.xcodeproj` in Xcode — Gradle only builds the Shared framework |
| Tests | `./gradlew :shared:allTests`, or per target: `jvmTest` · `testAndroidHostTest` · `iosSimulatorArm64Test` |
| Package (macOS) | `./gradlew :desktopApp:packageDmg` (notarizes) / `packageDmgLocal` (doesn't) |
| Package (Linux) | `./gradlew :desktopApp:packageDeb` / `packageRpm`; on a host that can't build that format: `scripts/packageDebianFromContainer.sh` / `scripts/packageRpmFromContainer.sh` (Podman/Docker) |
| Package (Windows) | `gradlew.bat :desktopApp:packageMsi` |
| Build + install this checkout | `scripts/installMacFromPackage.sh` (wraps `:desktopApp:installDmg`) / `scripts/installLinuxFromPackage.sh [rpm\|deb]` |

No lint/typecheck commands are configured. Test sources: `shared/src/{commonTest,androidHostTest,jvmTest,iosTest}/`.

## Modules

- `:shared` — all app logic + UI (KMP library). Source sets: `commonMain`, `androidMain`, `iosMain`, `jvmMain` (+ test variants).
- `:androidApp`, `:desktopApp` — thin platform shells (`:desktopApp` also holds the CLI).
- `:nativeformat`, `:iperf3native` — Android CMake/NDK JNI modules.
- `native/` (repo root, **not** a Gradle module) — single source of truth for shared C code (`native_format*`, `iperf3_bridge.*`, `iperf3_jni.c`).

## Gotchas that will bite you

**Build & native**
- **Stale native `.so` on Android**: AGP's CMake up-to-date check misses edits to `native/*.c` — run `./gradlew cleanNativeCxxCaches` after touching them.
- Desktop native libs compile for the host OS/arch only (no cross-compiling, no fat binaries). The iperf3 `./configure` result is cached and rebuilt automatically when the host OS/arch changes (marker: `build/iperf3-configure-host.txt`); `./gradlew cleanIperf3Configure` (as its own invocation) still forces a reconfigure by hand.
- The desktop app `System.load()`s its JNI libs, so `compose.desktop.application` sets `--enable-native-access=ALL-UNNAMED` (JDK 24+ warns without it, a future JDK blocks it). Keep it.
- `StringFormatterTest` is excluded from `androidHostTest` (Robolectric can't load a real `.so`) — covered by `jvmTest` + `iosSimulatorArm64Test`.
- Don't update Kotlin Multiplatform to 1.12 or otherwise bump `gradle/libs.versions.toml` — not currently compatible.

**Kotlin / KMP**
- In `shared/build.gradle.kts`, `sourceSets.getByName("iosMain")` **throws** — add iOS-only deps to both `iosArm64Main` and `iosSimulatorArm64Main`.
- **No `Dispatchers.IO` in commonMain** — internal on the coroutines version resolved for iOS; use `Dispatchers.Default`.
- `AppUUID` / `AppLocalDateTime` (`Platform.kt`) have **no `toString()` override** — use `.name`.
- In androidApp/desktopApp use `implementation(project(":shared"))`, not `projects.shared`.
- Keep `kotlin.mpp.enableCInteropCommonization=true` in `gradle.properties` — without it the IDE shows false unresolved references for cinterop declarations in `iosMain`.

**Room**
- Every DAO method must be `suspend`; bump `@Database(version = ...)` on schema changes and commit `shared/schemas/`.
- sqlite driver artifacts are per-source-set — `commonMain` gets only `room-runtime`, never `sqlite-framework` (no jvm variant; breaks `:desktopApp`).

**iOS**
- **Deliberately ARM-only** (`iosArm64` + `iosSimulatorArm64`; xcodeproj hardcodes `ARCHS = arm64`) — don't "fix" it to a universal arch list.
- Deployment target **18.5** lives in two places (`shared/build.gradle.kts:iperf3IosDeploymentTarget` + xcodeproj `IPHONEOS_DEPLOYMENT_TARGET`) — change both.
- `-lsqlite3` in `OTHER_LDFLAGS` at the **target level** of `project.pbxproj` is required (Shared.framework is static).
- `project.pbxproj` must keep `DEVELOPMENT_TEAM = "${TEAM_ID}"` — picking a team in Xcode's Signing UI writes a real ID into it; check the diff.

**Packaging & repo hygiene**
- Run `packageDmg` from the command line, not an IDE run config — it always notarizes (slow, rate-limited). Use `packageDmgLocal`/`installDmg` for iteration.
- Root `install.sh`/`install.ps1` download a **published release** and must stay at the root (README's `curl …/master/install.sh | bash` depends on the path). Developer build/install scripts live in `scripts/`.
- **Versioning**: repo-root `VERSION` is the single source of truth; Android/desktop read it at build time, iOS needs `./gradlew syncVersions` before an Xcode build.
- **Never edit license/copyright notices** in `third_party/` (upstream ESnet BSD notices), `gradlew`, or other vendored files. Project files use `Copyright (c) 2026, Jerold Abramson`.

## Current state

- Real iperf3 runs on all three targets; output parsing is text-based, not `--json-stream`.
- Room persistence, auto-save on run completion, History screen (`view/HistoryScreen.kt`) and CSV export (`utils/CsvExport.kt`) are implemented. `ResultDataMappers.toDomain()` remains an unpopulated stub — history reads `ResultDataEntity` directly.
- Open work lives in `docs/project/TODO.md`: automated Room round-trip test, native Windows ARM64 `iperf3.exe`, 1.0.0 release gate (Windows x64/ARM64 rebuilds from clean snapshot, Podman `.rpm` container run, install tests on baseline distros).
- **Known gap: Save/History doesn't work on Windows ARM64** — neither `androidx.sqlite:sqlite-bundled` nor `org.xerial:sqlite-jdbc` ships a native SQLite binary for that target. Everything else works including real network tests. Detail: `docs/platform-builds/windows-arm64.md`.

## Docs map

- `CLAUDE.md` — **single source of truth**: architecture, native bridge design, Room wiring + bug history, packaging, fresh-host setup.
- `PLATFORM_BUILD_NOTES.md` → `docs/platform-builds/*.md` — per-platform build, signing, and packaging checklists (includes `TEST_MATRIX.md` for the 1.0.0 release gate).
- `docs/architecture/IPERF3_NATIVE_INTEGRATION.md` §8–9 — iperf3 JNI/cinterop design rationale.
- `docs/desktop/` — Windows support history (`WINDOWS_DECISION_REVERSAL_CASE_STUDY.md`) and the ARM64 `iperf3.exe` plan (`WINDOWS_ARM64_IPERF3_PLAN.md`).
- `docs/README.md` — index of everything under `docs/`. The case studies in `docs/research/` are research artifacts, not build/architecture docs.
